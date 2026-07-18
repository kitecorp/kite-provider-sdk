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

    /**
     * Creates a resource type handler with an explicit resource class and type name.
     *
     * <p>Use this constructor when the resource class does not have an {@code @TypeName}
     * annotation (e.g. {@code Map<String, Object>} in the Terraform bridge). The schema
     * is built as a minimal shell with just the name populated.</p>
     *
     * @param resourceClass the resource class
     * @param typeName      the Kite type name for this resource
     */
    @SuppressWarnings("unchecked")
    protected ResourceTypeHandler(Class<?> resourceClass, String typeName) {
        this.resourceClass = (Class<T>) resourceClass;
        this.schema = Schema.builder().name(typeName).build();
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

    // -----------------------------------------------------------------
    // Context-carrying overloads (engine-stored prior state + private bytes)
    //
    // The gRPC service layer always dispatches through these. The defaults
    // delegate to the single-argument methods above, so handlers that don't
    // need stored state keep working unchanged. Handlers that do need it
    // (e.g. the Terraform bridge) override these instead.
    // -----------------------------------------------------------------

    /**
     * Create a new resource, with access to engine-stored state.
     *
     * @param resource The resource configuration
     * @param context  Prior state + private bytes in, updated private bytes out
     * @return The created resource with any cloud-assigned values populated
     * @see ResourceContext
     */
    public T create(T resource, ResourceContext<T> context) {
        return create(resource);
    }

    /**
     * Read the current state of a resource, with access to engine-stored state.
     *
     * @param resource The resource to read (with identifying fields populated)
     * @param context  Private bytes in, updated private bytes out
     * @return The current state, or null if not found
     * @see ResourceContext
     */
    public T read(T resource, ResourceContext<T> context) {
        return read(resource);
    }

    /**
     * Update an existing resource, with access to engine-stored state.
     *
     * @param resource The desired resource state
     * @param context  Prior state + private bytes in, updated private bytes out
     * @return The updated resource state
     * @see ResourceContext
     */
    public T update(T resource, ResourceContext<T> context) {
        return update(resource);
    }

    /**
     * Delete a resource, with access to engine-stored state.
     *
     * @param resource The resource to delete (the engine passes the stored prior state)
     * @param context  Private bytes in, updated private bytes out
     * @return true if deleted, false if not found
     * @see ResourceContext
     */
    public boolean delete(T resource, ResourceContext<T> context) {
        return delete(resource);
    }

    /**
     * Import (adopt) a pre-existing cloud resource by its identifier — the
     * counterpart of Terraform's {@code ImportResourceState}. Unlike
     * {@link #read(Object, ResourceContext)}, which refreshes from a known
     * state, import starts from nothing but a provider-interpreted id
     * (instance id, ARN, resource name, ...).
     *
     * <p>The default returns {@code null}: import is not supported and callers
     * fall back to a query-based read. Handlers that do support it return the
     * full resource state and hand private bytes to persist back through the
     * context, exactly like {@link #create(Object, ResourceContext)}.</p>
     *
     * @param importId the provider-interpreted identifier of the resource to adopt
     * @param context  private bytes out (nothing is stored yet on import)
     * @return the imported resource state, or null when import is unsupported
     *         or nothing exists for the id
     * @see ResourceContext
     */
    public T importResource(String importId, ResourceContext<T> context) {
        return null;
    }

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
