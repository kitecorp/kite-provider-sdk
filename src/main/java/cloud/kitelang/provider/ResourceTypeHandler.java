package cloud.kitelang.provider;

import cloud.kitelang.api.schema.Schema;
import lombok.Getter;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Defines a resource type handler with CRUD operations.
 * Extend this class for each resource type your provider supports.
 * The resource class type is inferred automatically from the generic parameter.
 *
 * <p>Example:</p>
 * <pre>{@code
 * public class FileResourceType extends ResourceTypeHandler<FileResource> {
 *
 *     @Override
 *     public FileResource create(FileResource resource) {
 *         // Create the file
 *         Files.createFile(Path.of(resource.getPath()));
 *         return resource;
 *     }
 *
 *     @Override
 *     public FileResource read(FileResource resource) {
 *         // Read file metadata
 *         return resource;
 *     }
 *
 *     @Override
 *     public FileResource update(FileResource resource) {
 *         // Update the file
 *         return resource;
 *     }
 *
 *     @Override
 *     public boolean delete(FileResource resource) {
 *         // Delete the file
 *         return Files.deleteIfExists(Path.of(resource.getPath()));
 *     }
 * }
 * }</pre>
 *
 * @param <T> The resource class type
 */
@Getter
public abstract class ResourceTypeHandler<T> {
    private final Class<T> resourceClass;
    private final Schema schema;

    /**
     * Creates a resource type handler. The resource class is inferred
     * automatically from the generic type parameter.
     */
    protected ResourceTypeHandler() {
        this.resourceClass = (Class<T>) resolveGenericParameter(getClass());
        this.schema = Schema.toSchema(resourceClass);
    }

    private Class<?> resolveGenericParameter(Class<?> clazz) {
        while (clazz != null) {
            Type type = clazz.getGenericSuperclass();
            if (type instanceof ParameterizedType pType) {
                if (pType.getRawType().equals(ResourceTypeHandler.class)) {
                    Type actualType = pType.getActualTypeArguments()[0];
                    if (actualType instanceof Class<?> c) return c;
                    if (actualType instanceof ParameterizedType pt) return (Class<?>) pt.getRawType();
                }
            }
            clazz = clazz.getSuperclass();
        }
        throw new IllegalStateException("Could not resolve generic parameter for ResourceTypeHandler<T>");
    }

    /**
     * Get the resource type name.
     */
    public String getTypeName() {
        return schema.getName();
    }

    /**
     * Create a new resource.
     *
     * @param resource The resource configuration
     * @return The created resource with any cloud-assigned values populated
     */
    public abstract T create(T resource);

    /**
     * Read the current state of a resource.
     *
     * @param resource The resource to read (with identifying fields populated)
     * @return The current state, or null if not found
     */
    public abstract T read(T resource);

    /**
     * Update an existing resource.
     *
     * @param resource The desired resource state
     * @return The updated resource state
     */
    public abstract T update(T resource);

    /**
     * Delete a resource.
     *
     * @param resource The resource to delete
     * @return true if deleted, false if not found
     */
    public abstract boolean delete(T resource);

    /**
     * Validate resource configuration before create/update.
     * Override to add custom validation.
     *
     * @param resource The resource to validate
     * @return Diagnostics from validation (empty if valid)
     */
    public java.util.List<Diagnostic> validate(T resource) {
        return java.util.List.of();
    }

    /**
     * Plan a resource change (for diff preview).
     * Override to customize planned changes.
     *
     * @param priorState    The current state (null for create)
     * @param proposedState The desired state
     * @return The planned new state
     */
    public T plan(T priorState, T proposedState) {
        return proposedState;
    }
}
