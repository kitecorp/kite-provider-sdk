package cloud.kitelang.provider;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Represents an error or warning from a provider operation.
 */
@Data
@Builder
public class Diagnostic {
    /**
     * Severity level of the diagnostic.
     */
    public enum Severity {
        ERROR,
        WARNING
    }

    /**
     * The severity level.
     */
    private Severity severity;

    /**
     * A brief summary of the issue.
     */
    private String summary;

    /**
     * A detailed explanation of the issue.
     */
    private String detail;

    /**
     * Path to the property that caused this diagnostic (optional).
     */
    private List<String> propertyPath;

    /**
     * Create an error diagnostic.
     */
    public static Diagnostic error(String summary) {
        return Diagnostic.builder()
                .severity(Severity.ERROR)
                .summary(summary)
                .build();
    }

    /**
     * Create an error diagnostic with detail.
     */
    public static Diagnostic error(String summary, String detail) {
        return Diagnostic.builder()
                .severity(Severity.ERROR)
                .summary(summary)
                .detail(detail)
                .build();
    }

    /**
     * Create a warning diagnostic.
     */
    public static Diagnostic warning(String summary) {
        return Diagnostic.builder()
                .severity(Severity.WARNING)
                .summary(summary)
                .build();
    }

    /**
     * Create a warning diagnostic with detail.
     */
    public static Diagnostic warning(String summary, String detail) {
        return Diagnostic.builder()
                .severity(Severity.WARNING)
                .summary(summary)
                .detail(detail)
                .build();
    }

    /**
     * Create a diagnostic for a property.
     */
    public Diagnostic withProperty(String... path) {
        this.propertyPath = List.of(path);
        return this;
    }
}
