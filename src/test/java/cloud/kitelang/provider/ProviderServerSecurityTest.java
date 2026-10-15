package cloud.kitelang.provider;

import cloud.kitelang.proto.v1.HealthCheck;
import cloud.kitelang.proto.v1.ProviderGrpc;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.ClientCalls;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.net.ConnectException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The two defences of a provider server against callers other than the engine that
 * launched it: it listens on 127.0.0.1 only, and it serves only calls that carry the
 * magic cookie from its launch environment. The server under test is the real Netty
 * server {@link ProviderServer#serve()} runs, reached over TCP like the engine does.
 */
class ProviderServerSecurityTest {

    private static final String LAUNCH_COOKIE = "cookie-the-engine-put-in-the-environment";
    private static final InetAddress LOOPBACK = InetAddress.ofLiteral("127.0.0.1");

    private ProviderServer providerServer;
    private Server server;
    private final List<ManagedChannel> channels = new ArrayList<>();

    @BeforeEach
    void startServer() throws IOException {
        providerServer = new ProviderServer(new KiteProvider("security-test", "0.0.1", false) {
        });
        server = providerServer.start(LAUNCH_COOKIE, 0);
    }

    @AfterEach
    void stopServer() throws InterruptedException {
        channels.forEach(ManagedChannel::shutdownNow);
        providerServer.stop();
    }

    @Test
    void listensOnLoopbackOnly() {
        assertEquals(List.of(new InetSocketAddress(LOOPBACK, server.getPort())), server.getListenSockets());
    }

    @Test
    void callCarryingTheLaunchCookie_isServed() {
        var response = stub(LOOPBACK, LAUNCH_COOKIE).healthCheck(HealthCheck.Request.getDefaultInstance());

        assertEquals(true, response.getHealthy());
    }

    @Test
    void callWithoutCookie_isUnauthenticated() {
        var ex = assertThrows(StatusRuntimeException.class,
                () -> stub(LOOPBACK, null).healthCheck(HealthCheck.Request.getDefaultInstance()));

        assertEquals(Status.Code.UNAUTHENTICATED, ex.getStatus().getCode());
        assertEquals("Missing magic cookie: the call has no kite-plugin-magic-cookie metadata."
                        + " Only the Kite engine that launched this provider may call it.",
                ex.getStatus().getDescription());
    }

    @Test
    void callWithWrongCookie_isUnauthenticatedAndEchoesNeitherValue() {
        var ex = assertThrows(StatusRuntimeException.class,
                () -> stub(LOOPBACK, "guessed-cookie").healthCheck(HealthCheck.Request.getDefaultInstance()));

        assertEquals(Status.Code.UNAUTHENTICATED, ex.getStatus().getCode());
        // A literal message is the proof that neither the presented nor the expected value is echoed
        assertEquals("Wrong magic cookie: the kite-plugin-magic-cookie metadata does not match the value"
                        + " this provider was launched with.",
                ex.getStatus().getDescription());
    }

    /** A blank expected cookie would accept every call that sends a blank one. */
    @Test
    void requiringABlankCookie_isRefused() {
        var ex = assertThrows(IllegalArgumentException.class, () -> MagicCookieAuth.require(" "));

        assertEquals("The magic cookie a provider requires must not be blank", ex.getMessage());
    }

    static Stream<Named<MethodDescriptor<?, ?>>> everyProviderRpc() {
        return ProviderGrpc.getServiceDescriptor().getMethods().stream()
                .map(method -> Named.of(method.getBareMethodName(), method));
    }

    /**
     * Every RPC, not only the ones that touch the cloud: an unauthenticated StopProvider
     * would let anyone stop the provider, and an unauthenticated HealthCheck tells a
     * scanner that a provider listens here.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("everyProviderRpc")
    void everyRpcWithoutCookie_isUnauthenticated(MethodDescriptor<?, ?> method) {
        var ex = assertThrows(StatusRuntimeException.class, () -> callWithDefaultRequest(channel(LOOPBACK, null), method));

        assertEquals(Status.Code.UNAUTHENTICATED, ex.getStatus().getCode());
    }

    @Test
    void connectionToANonLoopbackAddressOfThisMachine_isRefused() throws IOException {
        var address = nonLoopbackAddress();
        assumeTrue(address.isPresent(), "This machine has no up, non-loopback, non-link-local IPv4 address,"
                + " so a connection from a non-loopback address cannot be attempted here");
        // Control: the same address accepts a connection when something listens on every
        // interface, so the refusal below comes from the provider's binding and not from
        // the address being unreachable (a firewall, a down interface)
        assertEquals(true, acceptsConnection(address.get()));

        var ex = assertThrows(StatusRuntimeException.class,
                () -> stub(address.get(), LAUNCH_COOKIE).healthCheck(HealthCheck.Request.getDefaultInstance()));

        assertEquals(Status.Code.UNAVAILABLE, ex.getStatus().getCode());
        assertInstanceOf(ConnectException.class, ex.getCause());
    }

    private ProviderGrpc.ProviderBlockingStub stub(InetAddress host, String cookie) {
        return ProviderGrpc.newBlockingStub(channel(host, cookie)).withDeadlineAfter(10, TimeUnit.SECONDS);
    }

    private Channel channel(InetAddress host, String cookie) {
        var channel = NettyChannelBuilder.forAddress(new InetSocketAddress(host, server.getPort()))
                .usePlaintext()
                .build();
        channels.add(channel);
        return cookie == null ? channel : ClientInterceptors.intercept(channel, MagicCookieAuth.attach(cookie));
    }

    @SuppressWarnings("unchecked")
    private static <Q, R> R callWithDefaultRequest(Channel channel, MethodDescriptor<Q, R> method) {
        var request = ((MethodDescriptor.PrototypeMarshaller<Q>) method.getRequestMarshaller()).getMessagePrototype();
        return ClientCalls.blockingUnaryCall(channel, method, CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.SECONDS), request);
    }

    private static Optional<InetAddress> nonLoopbackAddress() throws SocketException {
        return NetworkInterface.networkInterfaces()
                .filter(ProviderServerSecurityTest::isUp)
                .flatMap(NetworkInterface::inetAddresses)
                .filter(address -> address instanceof Inet4Address)
                .filter(address -> !address.isLoopbackAddress() && !address.isLinkLocalAddress())
                .findFirst();
    }

    private static boolean isUp(NetworkInterface networkInterface) {
        try {
            return networkInterface.isUp() && !networkInterface.isLoopback();
        } catch (SocketException e) {
            return false;
        }
    }

    private static boolean acceptsConnection(InetAddress address) throws IOException {
        try (var listener = new ServerSocket(0); var socket = new Socket()) {
            socket.connect(new InetSocketAddress(address, listener.getLocalPort()), 2_000);
            return socket.isConnected();
        }
    }
}
