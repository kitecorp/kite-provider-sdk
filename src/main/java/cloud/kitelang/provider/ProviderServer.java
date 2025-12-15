package cloud.kitelang.provider;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.TimeUnit;

/**
 * Server that hosts a Kite provider as a gRPC service.
 *
 * <p>Uses a handshake protocol where the engine sets environment variables
 * (magic cookie, protocol version) and the provider outputs its port to stdout.</p>
 */
@Log4j2
public class ProviderServer {
    private static final String HANDSHAKE_PREFIX = "KITE_PLUGIN";
    private static final int PROTOCOL_VERSION = 1;
    private static final String MAGIC_COOKIE_ENV = "KITE_PLUGIN_MAGIC_COOKIE";
    private static final String PROTOCOL_VERSION_ENV = "KITE_PLUGIN_PROTOCOL_VERSION";

    private final KiteProvider provider;
    private Server server;

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

        // Find an available port
        int port = findAvailablePort();

        // Create the gRPC service implementation
        var serviceImpl = new ProviderServiceImpl(provider);

        // Build and start the server
        server = ServerBuilder.forPort(port)
                .addService(serviceImpl)
                .build()
                .start();

        log.info("Provider server started on port {}", port);

        // Print the handshake line to stdout
        // Format: KITE_PLUGIN|<protocol_version>|<port>|grpc
        System.out.println(HANDSHAKE_PREFIX + "|" + PROTOCOL_VERSION + "|" + port + "|grpc");
        System.out.flush();

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
     * Stop the server gracefully.
     */
    public void stop() throws InterruptedException {
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
