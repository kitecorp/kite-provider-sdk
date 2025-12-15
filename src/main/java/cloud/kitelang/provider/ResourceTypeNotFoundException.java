package cloud.kitelang.provider;

/**
 * Exception thrown when a requested resource type is not found in the provider.
 */
public class ResourceTypeNotFoundException extends ProviderException {

    private final String typeName;

    public ResourceTypeNotFoundException(String typeName) {
        super("Resource type not found: " + typeName);
        this.typeName = typeName;
    }

    /**
     * Get the name of the resource type that was not found.
     */
    public String getTypeName() {
        return typeName;
    }
}
