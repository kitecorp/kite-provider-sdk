/**
 * Kite Provider SDK for Java.
 *
 * <p>This package provides the foundation for building gRPC-based Kite providers in Java.
 * Providers are responsible for managing infrastructure resources and communicating with
 * the Kite engine via gRPC.</p>
 *
 * <h2>Getting Started</h2>
 *
 * <p>To create a provider, extend {@link cloud.kitelang.provider.KiteProvider} and
 * register your resource types:</p>
 *
 * <pre>{@code
 * public class MyProvider extends KiteProvider {
 *     public MyProvider() {
 *         super("my-provider", "1.0.0");
 *         registerResource("MyResource", new MyResourceType());
 *     }
 *
 *     public static void main(String[] args) throws Exception {
 *         ProviderServer.serve(new MyProvider());
 *     }
 * }
 * }</pre>
 *
 * <h2>Resource Types</h2>
 *
 * <p>For each resource your provider manages, extend {@link cloud.kitelang.provider.ResourceTypeHandler}:</p>
 *
 * <pre>{@code
 * public class MyResourceType extends ResourceTypeHandler<MyResource> {
 *     public MyResourceType() {
 *         super(MyResource.class);
 *     }
 *
 *     @Override
 *     public MyResource create(MyResource resource) {
 *         // Create the resource
 *         return resource;
 *     }
 *
 *     @Override
 *     public MyResource read(MyResource resource) {
 *         // Read current state
 *         return resource;
 *     }
 *
 *     @Override
 *     public MyResource update(MyResource resource) {
 *         // Update the resource
 *         return resource;
 *     }
 *
 *     @Override
 *     public boolean delete(MyResource resource) {
 *         // Delete the resource
 *         return true;
 *     }
 * }
 * }</pre>
 *
 * <h2>Resource Classes</h2>
 *
 * <p>Define resource classes using Kite's annotations:</p>
 *
 * <pre>{@code
 * @Data
 * @TypeName("MyResource")
 * public class MyResource {
 *     @Property
 *     private String name;
 *
 *     @Property
 *     private String config;
 *
 *     @Cloud  // Cloud-managed property
 *     @Property
 *     private String id;
 * }
 * }</pre>
 *
 * <h2>Diagnostics</h2>
 *
 * <p>Use {@link cloud.kitelang.provider.Diagnostic} to report errors and warnings:</p>
 *
 * <pre>{@code
 * @Override
 * public List<Diagnostic> validate(MyResource resource) {
 *     var diagnostics = new ArrayList<Diagnostic>();
 *     if (resource.getName() == null) {
 *         diagnostics.add(Diagnostic.error("Name is required")
 *             .withAttribute("name"));
 *     }
 *     return diagnostics;
 * }
 * }</pre>
 *
 * @see cloud.kitelang.provider.KiteProvider
 * @see cloud.kitelang.provider.ResourceTypeHandler
 * @see cloud.kitelang.provider.ProviderServer
 * @see cloud.kitelang.provider.Diagnostic
 */
package cloud.kitelang.provider;
