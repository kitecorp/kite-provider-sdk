package cloud.kitelang.provider;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Diagnostic class.
 */
class DiagnosticTest {

    @Test
    void createError() {
        var diagnostic = Diagnostic.error("Something went wrong");

        assertEquals(Diagnostic.Severity.ERROR, diagnostic.getSeverity());
        assertEquals("Something went wrong", diagnostic.getSummary());
        assertNull(diagnostic.getDetail());
        assertNull(diagnostic.getPropertyPath());
    }

    @Test
    void createErrorWithDetail() {
        var diagnostic = Diagnostic.error("Validation failed", "Field must not be empty");

        assertEquals(Diagnostic.Severity.ERROR, diagnostic.getSeverity());
        assertEquals("Validation failed", diagnostic.getSummary());
        assertEquals("Field must not be empty", diagnostic.getDetail());
    }

    @Test
    void createWarning() {
        var diagnostic = Diagnostic.warning("Deprecated field");

        assertEquals(Diagnostic.Severity.WARNING, diagnostic.getSeverity());
        assertEquals("Deprecated field", diagnostic.getSummary());
    }

    @Test
    void withProperty() {
        var diagnostic = Diagnostic.error("Invalid value")
                .withProperty("config", "timeout");

        assertEquals(2, diagnostic.getPropertyPath().size());
        assertEquals("config", diagnostic.getPropertyPath().get(0));
        assertEquals("timeout", diagnostic.getPropertyPath().get(1));
    }

    @Test
    void withSingleProperty() {
        var diagnostic = Diagnostic.error("Required field missing")
                .withProperty("name");

        assertEquals(1, diagnostic.getPropertyPath().size());
        assertEquals("name", diagnostic.getPropertyPath().get(0));
    }
}
