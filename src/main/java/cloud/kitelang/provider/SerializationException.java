package cloud.kitelang.provider;

/**
 * Exception thrown when resource serialization or deserialization fails.
 */
public class SerializationException extends ProviderException {

    public SerializationException(String message) {
        super(message);
    }

    public SerializationException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Create exception for serialization failure.
     */
    public static SerializationException serializationFailed(Class<?> resourceClass, Throwable cause) {
        return new SerializationException(
                "Failed to serialize resource of type " + resourceClass.getName(),
                cause);
    }

    /**
     * Create exception for deserialization failure.
     */
    public static SerializationException deserializationFailed(Class<?> resourceClass, Throwable cause) {
        return new SerializationException(
                "Failed to deserialize resource of type " + resourceClass.getName(),
                cause);
    }
}
