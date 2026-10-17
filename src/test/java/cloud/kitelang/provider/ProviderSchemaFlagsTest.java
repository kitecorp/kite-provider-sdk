package cloud.kitelang.provider;

import cloud.kitelang.api.annotations.Cloud;
import cloud.kitelang.api.annotations.TypeName;
import cloud.kitelang.api.resource.Property;
import cloud.kitelang.api.schema.Schema;
import cloud.kitelang.proto.v1.Block;
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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The per-property flags of {@code kite.v1.Property} must round-trip from the api schema through
 * {@code GetProviderSchema}, the only channel by which the engine learns them:
 * {@code sensitive} (kitecorp/kite-provider-sdk#4, part of kitecorp/kite-providers#6), and
 * {@code required} and {@code optional} (kitecorp/kite-provider-sdk#12).
 */
class ProviderSchemaFlagsTest {

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

    @TypeName("Instance")
    static class Instance {
        @cloud.kitelang.api.annotations.Property(optional = false)
        private String ami;

        @cloud.kitelang.api.annotations.Property
        private String keyName;

        @Cloud
        @cloud.kitelang.api.annotations.Property
        private String publicIp;
    }

    /** Handler whose schema is built from the annotations of {@link Instance}, like a provider's. */
    static final class InstanceHandler extends ResourceTypeHandler<Instance> {
        @Override
        public Instance create(Instance resource) {
            return resource;
        }

        @Override
        public Instance read(Instance resource) {
            return resource;
        }

        @Override
        public Instance update(Instance resource) {
            return resource;
        }

        @Override
        public boolean delete(Instance resource) {
            return true;
        }
    }

    /** The validation flags of one property on the wire, in the order the proto declares them. */
    record Flags(boolean required, boolean optional, boolean computed) {
        static Flags of(cloud.kitelang.proto.v1.Property property) {
            return new Flags(property.getRequired(), property.getOptional(), property.getComputed());
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
        var block = fetchSchemaBlock("Password", new PasswordHandler());

        assertEquals(2, block.getPropertiesCount(), "expected both declared properties, got: " + block);
        var byName = propertiesByName(block);
        assertTrue(byName.get("result").getSensitive(), "result is declared sensitive");
        assertTrue(byName.get("result").getComputed(), "result stays computed");
        assertFalse(byName.get("length").getSensitive(), "length is not sensitive");
    }

    @Test
    @DisplayName("GetProviderSchema marks required, optional and cloud properties from @Property and @Cloud")
    void requiredAndOptionalFlagsFollowTheAnnotations() throws Exception {
        var block = fetchSchemaBlock("Instance", new InstanceHandler());

        var flagsByName = block.getPropertiesList().stream()
                .collect(Collectors.toMap(cloud.kitelang.proto.v1.Property::getName, Flags::of));
        assertEquals(Map.of(
                "ami", new Flags(true, false, false),
                "keyName", new Flags(false, true, false),
                // Set by the cloud provider: the user may not set it, so it is neither required nor optional
                "publicIp", new Flags(false, false, true)), flagsByName);
    }

    @Test
    @DisplayName("GetProviderSchema treats a property built without required as optional")
    void builtPropertyWithoutRequiredIsOptional() throws Exception {
        var block = fetchSchemaBlock("Password", new PasswordHandler());

        assertEquals(new Flags(false, true, false), Flags.of(propertiesByName(block).get("length")));
    }

    /** Serves one resource type in process and returns its schema block as the engine receives it. */
    private Block fetchSchemaBlock(String typeName, ResourceTypeHandler<?> handler) throws Exception {
        var provider = new KiteProvider("test-provider", "0.0.1", false) {
            {
                registerResource(typeName, handler);
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
        return response.getResourceSchemasMap().get(typeName).getBlock();
    }

    private static Map<String, cloud.kitelang.proto.v1.Property> propertiesByName(Block block) {
        return block.getPropertiesList().stream()
                .collect(Collectors.toMap(cloud.kitelang.proto.v1.Property::getName, p -> p));
    }
}
