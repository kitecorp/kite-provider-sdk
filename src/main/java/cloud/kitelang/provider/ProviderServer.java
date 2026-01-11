package cloud.kitelang.provider;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Server that hosts a Kite provider as a gRPC service.
 *
 * <p>Uses a handshake protocol where the engine sets environment variables
 * (magic cookie, protocol version) and the provider outputs its port to stdout.</p>
 *
 * <p>Supports persistent mode with idle timeout - provider will auto-shutdown
 * after a configurable period of inactivity.</p>
 */
@Slf4j
public class ProviderServer {
    private static final String HANDSHAKE_PREFIX = "KITE_PLUGIN";
    private static final int PROTOCOL_VERSION = 1;
    private static final String MAGIC_COOKIE_ENV = "KITE_PLUGIN_MAGIC_COOKIE";
    private static final String PROTOCOL_VERSION_ENV = "KITE_PLUGIN_PROTOCOL_VERSION";
    private static final String IDLE_TIMEOUT_ENV = "KITE_PLUGIN_IDLE_TIMEOUT";
    private static final long DEFAULT_IDLE_TIMEOUT_MS = Duration.ofMinutes(30).toMillis();

    private final KiteProvider provider;
    private Server server;
    private ScheduledExecutorService idleChecker;
    private ProviderServiceImpl serviceImpl;

    public ProviderServer(KiteProvider provider) {
        this.provider = provider;
    }

    /**
     * Start the provider server.
     * This method validates the magic cookie, starts the gRPC server,
     * and prints the handshake line.
     *
     * @throws IOException if server cannot start
     */
    public void serve() throws IOException, InterruptedException {
        // Validate magic cookie
        String magicCookie = System.getenv(MAGIC_COOKIE_ENV);
        if (magicCookie == null || magicCookie.isEmpty()) {
            log.error("Missing {} environment variable. This plugin must be launched by Kite.", MAGIC_COOKIE_ENV);
            System.err.println("This is a Kite provider plugin. It must be launched by the Kite engine.");
            System.exit(1);
        }

        // Validate protocol version
        String protocolVersionStr = System.getenv(PROTOCOL_VERSION_ENV);
        if (protocolVersionStr == null) {
            log.error("Missing {} environment variable", PROTOCOL_VERSION_ENV);
            System.exit(1);
        }

        int requestedVersion;
        try {
            requestedVersion = Integer.parseInt(protocolVersionStr);
        } catch (NumberFormatException e) {
            log.error("Invalid protocol version: {}", protocolVersionStr);
            System.exit(1);
            return;
        }

        if (requestedVersion != PROTOCOL_VERSION) {
            log.error("Protocol version mismatch. Expected {}, got {}", PROTOCOL_VERSION, requestedVersion);
            System.exit(1);
        }

        // Read idle timeout from environment
        long idleTimeoutMs = DEFAULT_IDLE_TIMEOUT_MS;
        String idleTimeoutStr = System.getenv(IDLE_TIMEOUT_ENV);
        if (idleTimeoutStr != null && !idleTimeoutStr.isEmpty()) {
            try {
                idleTimeoutMs = Long.parseLong(idleTimeoutStr);
            } catch (NumberFormatException e) {
                log.warn("Invalid idle timeout '{}', using default {}ms", idleTimeoutStr, DEFAULT_IDLE_TIMEOUT_MS);
            }
        }

        // Find an available port
        int port = findAvailablePort();

        // Create the gRPC service implementation with idle tracking
        serviceImpl = new ProviderServiceImpl(provider, idleTimeoutMs);

        // Build and start the server
        server = ServerBuilder.forPort(port)
                .addService(serviceImpl)
                .build()
                .start();

        log.info("Provider server started on port {} (idle timeout: {}s)", port, idleTimeoutMs / 1000);

        // Print the handshake line to stdout
        // Format: KITE_PLUGIN|<protocol_version>|<port>|grpc
        System.out.println(HANDSHAKE_PREFIX + "|" + PROTOCOL_VERSION + "|" + port + "|grpc");
        System.out.flush();

        // Start idle timeout checker
        startIdleChecker(idleTimeoutMs);

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down provider server...");
            try {
                ProviderServer.this.stop();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));

        // Block until shutdown
        server.awaitTermination();
    }

    /**
     * Start a background thread that checks for idle timeout.
     */
    private void startIdleChecker(long idleTimeoutMs) {
        if (idleTimeoutMs <= 0) {
            log.info("Idle timeout disabled");
            return;
        }

        idleChecker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "idle-checker");
            t.setDaemon(true);
            return t;
        });

        // Check every 30 seconds
        long checkIntervalMs = Math.min(30_000, idleTimeoutMs / 2);
        idleChecker.scheduleAtFixedRate(() -> {
            long idleMs = serviceImpl.getIdleTimeMs();
            if (idleMs >= idleTimeoutMs) {
                log.info("Provider idle for {}s, shutting down", idleMs / 1000);
                try {
                    stop();
                    System.exit(0);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, checkIntervalMs, checkIntervalMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Stop the server gracefully.
     */
    public void stop() throws InterruptedException {
        if (idleChecker != null) {
            idleChecker.shutdownNow();
        }
        if (server != null) {
            server.shutdown();
            if (!server.awaitTermination(30, TimeUnit.SECONDS)) {
                server.shutdownNow();
            }
        }
    }

    /**
     * Find an available port for the server.
     */
    private int findAvailablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }

    /**
     * Convenience method to create and start a provider server.
     *
     * @param provider The provider to serve
     */
    public static void serve(KiteProvider provider) throws IOException, InterruptedException {
        new ProviderServer(provider).serve();
    }
}
