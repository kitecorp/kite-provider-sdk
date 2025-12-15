package cloud.kitelang.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.msgpack.jackson.dataformat.MessagePackMapper;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ResourcePayload serialization/deserialization using msgpack.
 */
class ResourcePayloadSerializationTest {

    private ObjectMapper msgpackMapper;

    @BeforeEach
    void setUp() {
        msgpackMapper = new MessagePackMapper();
    }

    @Test
    void serializeAndDeserializeSimpleObject() throws Exception {
        var original = new TestResource("test-id", "Test Name", 42);

        byte[] serialized = msgpackMapper.writeValueAsBytes(original);
        assertNotNull(serialized);
        assertTrue(serialized.length > 0);

        var deserialized = msgpackMapper.readValue(serialized, TestResource.class);
        assertEquals(original.id(), deserialized.id());
        assertEquals(original.name(), deserialized.name());
        assertEquals(original.count(), deserialized.count());
    }

    @Test
    void serializeNull() throws Exception {
        byte[] serialized = msgpackMapper.writeValueAsBytes(null);
        assertNotNull(serialized);
    }

    @Test
    void deserializeToNull() throws Exception {
        byte[] nullBytes = msgpackMapper.writeValueAsBytes(null);
        var result = msgpackMapper.readValue(nullBytes, TestResource.class);
        assertNull(result);
    }

    @Test
    void serializeWithNullFields() throws Exception {
        var original = new TestResource("id-only", null, 0);

        byte[] serialized = msgpackMapper.writeValueAsBytes(original);
        var deserialized = msgpackMapper.readValue(serialized, TestResource.class);

        assertEquals(original.id(), deserialized.id());
        assertNull(deserialized.name());
        assertEquals(0, deserialized.count());
    }

    /**
     * Simple test resource record.
     */
    public record TestResource(String id, String name, int count) {}
}
