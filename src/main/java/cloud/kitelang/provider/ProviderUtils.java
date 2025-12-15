package cloud.kitelang.provider;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Utility methods for provider implementations.
 */
public final class ProviderUtils {
    private static final SecureRandom RANDOM = new SecureRandom();

    private ProviderUtils() {}

    /**
     * Generate a unique resource ID.
     *
     * @return A random UUID string
     */
    public static String generateId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Generate a unique resource ID with a prefix.
     *
     * @param prefix The prefix (e.g., "bucket-")
     * @return A prefixed unique ID
     */
    public static String generateId(String prefix) {
        return prefix + generateId();
    }

    /**
     * Generate a short unique ID (8 characters).
     *
     * @return A short random ID
     */
    public static String generateShortId() {
        byte[] bytes = new byte[6];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Compute SHA256 hash of a string.
     *
     * @param input The input string
     * @return Hex-encoded hash
     */
    public static String sha256(String input) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute SHA256", e);
        }
    }

    /**
     * Compute SHA256 hash of bytes.
     *
     * @param input The input bytes
     * @return Hex-encoded hash
     */
    public static String sha256(byte[] input) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input);
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute SHA256", e);
        }
    }

    /**
     * Check if a string is null or blank.
     *
     * @param value The string to check
     * @return true if null or blank
     */
    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Check if a string is not null and not blank.
     *
     * @param value The string to check
     * @return true if not null and not blank
     */
    public static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Return the first non-null value.
     *
     * @param values The values to check
     * @return The first non-null value, or null if all are null
     */
    @SafeVarargs
    public static <T> T coalesce(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * Return the first non-blank string.
     *
     * @param values The strings to check
     * @return The first non-blank string, or null if all are blank
     */
    public static String coalesceBlank(String... values) {
        for (String value : values) {
            if (isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    /**
     * Format a resource ARN-like identifier.
     *
     * @param service The service name (e.g., "s3", "ec2")
     * @param resourceType The resource type (e.g., "bucket", "instance")
     * @param resourceId The resource identifier
     * @return A formatted resource identifier
     */
    public static String formatResourceId(String service, String resourceType, String resourceId) {
        return String.format("kite:%s:%s:%s", service, resourceType, resourceId);
    }
}
