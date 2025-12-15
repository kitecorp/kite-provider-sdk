package cloud.kitelang.provider;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ProviderUtils.
 */
class ProviderUtilsTest {

    @Test
    void generateId() {
        var id = ProviderUtils.generateId();

        assertNotNull(id);
        assertEquals(36, id.length()); // UUID format: 8-4-4-4-12
        assertTrue(id.contains("-"));
    }

    @Test
    void generateShortId() {
        var id = ProviderUtils.generateShortId();

        assertNotNull(id);
        assertEquals(8, id.length());
    }

    @Test
    void sha256() {
        var hash = ProviderUtils.sha256("hello world");

        assertNotNull(hash);
        assertEquals(64, hash.length()); // SHA-256 = 32 bytes = 64 hex chars
    }

    @Test
    void sha256Consistency() {
        var hash1 = ProviderUtils.sha256("test");
        var hash2 = ProviderUtils.sha256("test");

        assertEquals(hash1, hash2);
    }

    @Test
    void sha256DifferentInputs() {
        var hash1 = ProviderUtils.sha256("hello");
        var hash2 = ProviderUtils.sha256("world");

        assertNotEquals(hash1, hash2);
    }

    @Test
    void isBlank() {
        assertTrue(ProviderUtils.isBlank(null));
        assertTrue(ProviderUtils.isBlank(""));
        assertTrue(ProviderUtils.isBlank("   "));
        assertFalse(ProviderUtils.isBlank("hello"));
        assertFalse(ProviderUtils.isBlank(" x "));
    }

    @Test
    void coalesce() {
        assertEquals("first", ProviderUtils.coalesce("first", "second"));
        assertEquals("second", ProviderUtils.coalesce(null, "second"));
        assertEquals("third", ProviderUtils.coalesce(null, null, "third"));
        assertNull(ProviderUtils.coalesce(null, null));
    }
}
