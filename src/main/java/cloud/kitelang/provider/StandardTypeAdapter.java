package cloud.kitelang.provider;

import java.util.Map;

/**
 * Adapts a standard library resource type to a provider-specific concrete type.
 * <p>
 * Providers implement this interface for each standard type they support.
 * For example, an AWS provider would implement {@code StandardTypeAdapter<Ec2Instance>}
 * to map the abstract {@code Server} type to {@code Ec2Instance}.
 * <p>
 * The adapter handles bidirectional property mapping:
 * <ul>
 *   <li>{@link #toConcreteProperties} — maps abstract properties (cpu, memory) to
 *       provider-specific properties (instanceType, imageId)</li>
 *   <li>{@link #toAbstractProperties} — maps cloud response back to abstract properties
 *       (publicIp, state, etc.)</li>
 * </ul>
 *
 * <p>Example:</p>
 * <pre>{@code
 * public class ServerAdapter implements StandardTypeAdapter<Ec2Instance> {
 *     private final Ec2InstanceResourceType handler = new Ec2InstanceResourceType();
 *
 *     public String standardTypeName() { return "Server"; }
 *     public String concreteTypeName() { return "Ec2Instance"; }
 *
 *     public Ec2Instance toConcreteProperties(Map<String, Object> abstractProps) {
 *         return Ec2Instance.builder()
 *             .instanceType(resolveInstanceType(abstractProps))
 *             .imageId(resolveAmi(abstractProps))
 *             .build();
 *     }
 *
 *     public Map<String, Object> toAbstractProperties(Ec2Instance concrete) {
 *         return Map.of(
 *             "publicIp", concrete.getPublicIp(),
 *             "privateIp", concrete.getPrivateIp(),
 *             "id", concrete.getInstanceId(),
 *             "state", concrete.getState()
 *         );
 *     }
 *
 *     public ResourceTypeHandler<Ec2Instance> getConcreteHandler() {
 *         return handler;
 *     }
 * }
 * }</pre>
 *
 * @param <C> The concrete resource type (e.g., Ec2Instance, VirtualMachine)
 */
public interface StandardTypeAdapter<C> {

    /**
     * The standard library type name this adapter handles (e.g., "Server", "Network", "Bucket").
     */
    String standardTypeName();

    /**
     * The provider-specific concrete type name (e.g., "Ec2Instance", "Vpc", "S3Bucket").
     */
    String concreteTypeName();

    /**
     * Map abstract standard properties to concrete provider-specific properties.
     * Called before CRUD operations.
     *
     * @param abstractProps property map from the standard resource (e.g., {cpu: 4, memory: 16})
     * @return the concrete resource instance ready for the provider handler
     */
    C toConcreteProperties(Map<String, Object> abstractProps);

    /**
     * Map concrete provider properties back to abstract standard properties.
     * Called after read/create to populate cloud-managed properties on the standard resource.
     *
     * @param concreteProps the concrete resource returned by the provider
     * @return abstract property map with cloud-managed values (e.g., {publicIp: "1.2.3.4", state: "running"})
     */
    Map<String, Object> toAbstractProperties(C concreteProps);

    /**
     * The concrete resource type handler that performs actual CRUD operations.
     * The adapter delegates to this handler after property translation.
     */
    ResourceTypeHandler<C> getConcreteHandler();
}
