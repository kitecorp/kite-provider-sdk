package cloud.kitelang.provider;

import cloud.kitelang.proto.v1.CreateResource;
import cloud.kitelang.proto.v1.DeleteResource;
import cloud.kitelang.proto.v1.ProviderGrpc;
import cloud.kitelang.proto.v1.ReadResource;
import cloud.kitelang.proto.v1.ResourcePayload;
import cloud.kitelang.proto.v1.UpdateResource;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.msgpack.jackson.dataformat.MessagePackMapper;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trip tests for prior state + provider-private bytes through the gRPC
 * service layer ({@link ProviderServiceImpl} served over an in-process channel).
 *
 * <p>Covers both directions of the contract from kitecorp/kite-provider-sdk#1:
 * handlers that override the {@link ResourceContext}-carrying overloads receive
 * prior state and private bytes and can return updated private bytes, while
 * legacy single-argument handlers keep working unchanged (incoming private
 * bytes are echoed back so the engine never loses them).</p>
 */
class ProviderServiceStateRoundTripTest {

    /** Simple resource payload; records serialize natively via msgpack Jackson. */
    record Bucket(String name, String region) {
    }

    /**
     * Handler that opts into the context-carrying overloads: records what it
     * observed and returns fresh private bytes per operation.
     */
    static final class StatefulBucketHandler extends ResourceTypeHandler<Bucket> {
        Bucket observedPriorState;
        byte[] observedPrivateData;

        StatefulBucketHandler() {
            super(Bucket.class, "Bucket");
        }

        // Legacy single-argument methods must never run for this handler —
        // the service layer must dispatch through the context overloads.
        @Override
        public Bucket create(Bucket resource) {
            throw new UnsupportedOperationException("context overload expected");
        }

        @Override
        public Bucket read(Bucket resource) {
            throw new UnsupportedOperationException("context overload expected");
        }

        @Override
        public Bucket update(Bucket resource) {
            throw new UnsupportedOperationException("context overload expected");
        }

        @Override
        public boolean delete(Bucket resource) {
            throw new UnsupportedOperationException("context overload expected");
        }

        @Override
        public Bucket create(Bucket resource, ResourceContext<Bucket> context) {
            observedPriorState = context.priorState();
            observedPrivateData = context.privateData();
            context.returnPrivateData("created-private".getBytes(StandardCharsets.UTF_8));
            return new Bucket(resource.name(), "eu-west-1");
        }

        @Override
        public Bucket read(Bucket resource, ResourceContext<Bucket> context) {
            observedPrivateData = context.privateData();
            context.returnPrivateData("read-private".getBytes(StandardCharsets.UTF_8));
            return resource;
        }

        @Override
        public Bucket update(Bucket resource, ResourceContext<Bucket> context) {
            observedPriorState = context.priorState();
            observedPrivateData = context.privateData();
            context.returnPrivateData("updated-private".getBytes(StandardCharsets.UTF_8));
            return resource;
        }

        @Override
        public boolean delete(Bucket resource, ResourceContext<Bucket> context) {
            observedPriorState = resource;
            observedPrivateData = context.privateData();
            // Deliberately does NOT call returnPrivateData: the service layer
            // must echo the incoming bytes back unchanged by default.
            return true;
        }
    }

    /** Handler that only implements the legacy single-argument methods. */
    static final class LegacyBucketHandler extends ResourceTypeHandler<Bucket> {
        LegacyBucketHandler() {
            super(Bucket.class, "Bucket");
        }

        @Override
        public Bucket create(Bucket resource) {
            return new Bucket(resource.name(), "legacy-region");
        }

        @Override
        public Bucket read(Bucket resource) {
            return resource;
        }

        @Override
        public Bucket update(Bucket resource) {
            return new Bucket(resource.name(), "legacy-updated");
        }

        @Override
        public boolean delete(Bucket resource) {
            return true;
        }
    }

    private final MessagePackMapper msgpack = new MessagePackMapper();
    private Server server;
    private ManagedChannel channel;

    @AfterEach
    void tearDown() {
        if (channel != null) {
            channel.shutdownNow();
        }
        if (server != null) {
            server.shutdownNow();
        }
    }

    /**
     * Starts an in-process gRPC server around a provider exposing the given
     * handler as type {@code Bucket} and returns a blocking client stub.
     */
    private ProviderGrpc.ProviderBlockingStub serve(ResourceTypeHandler<Bucket> handler) throws Exception {
        var provider = new KiteProvider("test-provider", "0.0.1", false) {
            {
                registerResource("Bucket", handler);
            }
        };
        var serverName = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(new ProviderServiceImpl(provider))
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
        return ProviderGrpc.newBlockingStub(channel);
    }

    private ResourcePayload payload(Bucket bucket) throws Exception {
        return ResourcePayload.newBuilder()
                .setMsgpack(ByteString.copyFrom(msgpack.writeValueAsBytes(bucket)))
                .build();
    }

    private Bucket decode(ResourcePayload payload) throws Exception {
        return msgpack.readValue(payload.getMsgpack().toByteArray(), Bucket.class);
    }

    @Test
    @DisplayName("create should pass empty private data in and return handler-set private bytes")
    void createReturnsHandlerPrivateData() throws Exception {
        var handler = new StatefulBucketHandler();
        var stub = serve(handler);

        var response = stub.createResource(CreateResource.Request.newBuilder()
                .setTypeName("Bucket")
                .setConfig(payload(new Bucket("logs", null)))
                .build());

        assertEquals(0, response.getDiagnosticsCount(),
                "unexpected diagnostics: " + response.getDiagnosticsList());
        assertEquals(new Bucket("logs", "eu-west-1"), decode(response.getNewState()));
        assertEquals("created-private", response.getPrivateData().toStringUtf8());
        assertNull(handler.observedPriorState, "create has no prior state");
        assertArrayEquals(new byte[0], handler.observedPrivateData);
    }

    @Test
    @DisplayName("update should deliver prior state + private bytes and return new private bytes")
    void updateDeliversPriorStateAndPrivateData() throws Exception {
        var handler = new StatefulBucketHandler();
        var stub = serve(handler);

        var response = stub.updateResource(UpdateResource.Request.newBuilder()
                .setTypeName("Bucket")
                .setPlannedState(payload(new Bucket("logs", "us-east-1")))
                .setPriorState(payload(new Bucket("logs", "eu-west-1")))
                .setPrivateData(ByteString.copyFromUtf8("stored-bytes"))
                .build());

        assertEquals(0, response.getDiagnosticsCount(),
                "unexpected diagnostics: " + response.getDiagnosticsList());
        assertEquals(new Bucket("logs", "eu-west-1"), handler.observedPriorState);
        assertEquals("stored-bytes", new String(handler.observedPrivateData, StandardCharsets.UTF_8));
        assertEquals(new Bucket("logs", "us-east-1"), decode(response.getNewState()));
        assertEquals("updated-private", response.getPrivateData().toStringUtf8());
    }

    @Test
    @DisplayName("read should deliver private bytes and return handler-set private bytes")
    void readRoundTripsPrivateData() throws Exception {
        var handler = new StatefulBucketHandler();
        var stub = serve(handler);

        var response = stub.readResource(ReadResource.Request.newBuilder()
                .setTypeName("Bucket")
                .setCurrentState(payload(new Bucket("logs", "eu-west-1")))
                .setPrivateData(ByteString.copyFromUtf8("refresh-bytes"))
                .build());

        assertEquals(0, response.getDiagnosticsCount(),
                "unexpected diagnostics: " + response.getDiagnosticsList());
        assertEquals("refresh-bytes", new String(handler.observedPrivateData, StandardCharsets.UTF_8));
        assertEquals(new Bucket("logs", "eu-west-1"), decode(response.getNewState()));
        assertEquals("read-private", response.getPrivateData().toStringUtf8());
    }

    @Test
    @DisplayName("delete should deliver prior state + private bytes and echo private bytes by default")
    void deleteDeliversPriorStateAndEchoesPrivateData() throws Exception {
        var handler = new StatefulBucketHandler();
        var stub = serve(handler);

        var response = stub.deleteResource(DeleteResource.Request.newBuilder()
                .setTypeName("Bucket")
                .setPriorState(payload(new Bucket("logs", "eu-west-1")))
                .setPrivateData(ByteString.copyFromUtf8("stored-bytes"))
                .build());

        assertEquals(0, response.getDiagnosticsCount(),
                "unexpected diagnostics: " + response.getDiagnosticsList());
        assertEquals(new Bucket("logs", "eu-west-1"), handler.observedPriorState);
        assertEquals("stored-bytes", new String(handler.observedPrivateData, StandardCharsets.UTF_8));
        // Handler never called returnPrivateData -> incoming bytes come back unchanged
        assertEquals("stored-bytes", response.getPrivateData().toStringUtf8());
    }

    @Test
    @DisplayName("legacy single-argument handlers keep working and echo private bytes unchanged")
    void legacyHandlerStillWorksAndEchoesPrivateData() throws Exception {
        var stub = serve(new LegacyBucketHandler());

        var createResponse = stub.createResource(CreateResource.Request.newBuilder()
                .setTypeName("Bucket")
                .setConfig(payload(new Bucket("logs", null)))
                .build());
        assertEquals(new Bucket("logs", "legacy-region"), decode(createResponse.getNewState()));
        assertEquals(ByteString.EMPTY, createResponse.getPrivateData());

        var updateResponse = stub.updateResource(UpdateResource.Request.newBuilder()
                .setTypeName("Bucket")
                .setPlannedState(payload(new Bucket("logs", "us-east-1")))
                .setPriorState(payload(new Bucket("logs", "legacy-region")))
                .setPrivateData(ByteString.copyFromUtf8("engine-stored"))
                .build());
        assertEquals(new Bucket("logs", "legacy-updated"), decode(updateResponse.getNewState()));
        // A handler unaware of private state must not lose the engine's stored bytes
        assertEquals("engine-stored", updateResponse.getPrivateData().toStringUtf8());

        var deleteResponse = stub.deleteResource(DeleteResource.Request.newBuilder()
                .setTypeName("Bucket")
                .setPriorState(payload(new Bucket("logs", "legacy-updated")))
                .setPrivateData(ByteString.copyFromUtf8("engine-stored"))
                .build());
        assertEquals(0, deleteResponse.getDiagnosticsCount());
        assertEquals("engine-stored", deleteResponse.getPrivateData().toStringUtf8());
    }

    @Test
    @DisplayName("ResourceContext normalizes null private data to empty and defaults the return value")
    void resourceContextDefaults() {
        var empty = ResourceContext.<Bucket>empty();
        assertNull(empty.priorState());
        assertArrayEquals(new byte[0], empty.privateData());
        assertArrayEquals(new byte[0], empty.privateDataToReturn());

        var context = ResourceContext.of(new Bucket("logs", "eu-west-1"), null);
        assertEquals(new Bucket("logs", "eu-west-1"), context.priorState());
        assertArrayEquals(new byte[0], context.privateData());

        context.returnPrivateData("next".getBytes(StandardCharsets.UTF_8));
        assertEquals("next", new String(context.privateDataToReturn(), StandardCharsets.UTF_8));
        assertTrue(context.privateData().length == 0,
                "returning bytes must not mutate the incoming private data");
    }
}
