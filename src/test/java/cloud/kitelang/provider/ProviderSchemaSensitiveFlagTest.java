package cloud.kitelang.provider;

import cloud.kitelang.api.resource.Property;
import cloud.kitelang.api.schema.Schema;
import cloud.kitelang.proto.v1.GetProviderSchema;
import cloud.kitelang.proto.v1.ProviderGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code kite.v1.Schema.Property.sensitive} proto field must round-trip
 * from {@link cloud.kitelang.api.resource.Property#isSensitive()} through
 * {@code GetProviderSchema} — this is the only channel by which the engine can
 * learn which bridged/dynamic attributes to mask in rendered plans
 * (kitecorp/kite-provider-sdk#4, part of kitecorp/kite-providers#6).
 */
class ProviderSchemaSensitiveFlagTest {

    /**
     * Handler shaped like the Terraform bridge's: {@code Map}-based shell
     * construction with {@link #getSchema()} overridden to serve a schema whose
     * properties are built directly (not derived from class annotations).
     */
    static final class PasswordHandler extends ResourceTypeHandler<Map<String, Object>> {
        PasswordHandler() {
            super(Map.class, "Password");
        }

        @Override
        public Schema getSchema() {
            var properties = new LinkedHashSet<Property>();
            properties.add(Property.builder().name("length").type("number").build());
            properties.add(Property.builder().name("result").type("string").cloud(true).sensitive(true).build());
            return Schema.builder().name("Password").properties(properties).build();
        }

        @Override
        public Map<String, Object> create(Map<String, Object> resource) {
            return resource;
        }

        @Override
        public Map<String, Object> read(Map<String, Object> resource) {
            return resource;
        }

        @Override
        public Map<String, Object> update(Map<String, Object> resource) {
            return resource;
        }

        @Override
        public boolean delete(Map<String, Object> resource) {
            return true;
        }
    }

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

    @Test
    @DisplayName("GetProviderSchema carries the sensitive flag per property")
    void sensitiveFlagRoundTripsThroughGetProviderSchema() throws Exception {
        var provider = new KiteProvider("test-provider", "0.0.1", false) {
            {
                registerResource("Password", new PasswordHandler());
            }
        };
        var serverName = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(new ProviderServiceImpl(provider))
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
        var stub = ProviderGrpc.newBlockingStub(channel);

        var response = stub.getProviderSchema(GetProviderSchema.Request.getDefaultInstance());

        var block = response.getResourceSchemasMap().get("Password").getBlock();
        assertEquals(2, block.getPropertiesCount(), "expected both declared properties, got: " + block);
        var byName = block.getPropertiesList().stream()
                .collect(java.util.stream.Collectors.toMap(
                        cloud.kitelang.proto.v1.Property::getName, p -> p));
        assertTrue(byName.get("result").getSensitive(), "result is declared sensitive");
        assertTrue(byName.get("result").getComputed(), "result stays computed");
        assertFalse(byName.get("length").getSensitive(), "length is not sensitive");
    }
}
