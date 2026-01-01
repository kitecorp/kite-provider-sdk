# Kite Provider SDK for Java

A Java SDK for creating gRPC-based Kite providers with first-class support.

## Overview

The Kite Provider SDK enables you to create infrastructure providers that communicate with the Kite engine via gRPC. Providers can be written in any language, but this SDK provides first-class support for Java.

## Architecture

```
Engine                          Provider (any language)
  │                                    │
  │─── Start process ──────────────────►
  │    (KITE_PLUGIN_MAGIC_COOKIE)      │
  │                                    │
  │◄── KITE_PLUGIN|1|<port>|grpc ──────│
  │                                    │
  │─── gRPC: GetProviderSchema ────────►
  │◄── Schema definitions ─────────────│
  │                                    │
  │─── gRPC: CreateResource ───────────►
  │◄── Created state ──────────────────│
```

## Quick Start

### 1. Add Dependency

```groovy
dependencies {
    implementation project(':kite-provider-sdk')
}
```

### 2. Define Your Resource

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("File")
public class FileResource {
    @Property
    private String path;

    @Property
    private String content;

    @Property
    private String permissions;

    @Cloud  // Cloud-managed property (read-only, set by provider)
    @Property
    private String checksum;

    @Cloud
    @Property
    private String lastModified;
}
```

### 3. Implement Resource Type

```java
public class FileResourceType extends ResourceTypeHandler<FileResource> {

    @Override
    public FileResource create(FileResource resource) {
        // Create the resource
        Files.writeString(Path.of(resource.getPath()), resource.getContent());
        resource.setChecksum(computeChecksum(resource.getContent()));
        resource.setLastModified(Instant.now().toString());
        return resource;
    }

    @Override
    public FileResource read(FileResource resource) {
        // Read current state
        Path path = Path.of(resource.getPath());
        if (!Files.exists(path)) {
            return null;
        }
        resource.setContent(Files.readString(path));
        return resource;
    }

    @Override
    public FileResource update(FileResource resource) {
        // Update the resource
        Files.writeString(Path.of(resource.getPath()), resource.getContent());
        resource.setLastModified(Instant.now().toString());
        return resource;
    }

    @Override
    public boolean delete(FileResource resource) {
        // Delete the resource
        return Files.deleteIfExists(Path.of(resource.getPath()));
    }

    @Override
    public List<Diagnostic> validate(FileResource resource) {
        var diagnostics = new ArrayList<Diagnostic>();
        if (resource.getPath() == null || resource.getPath().isBlank()) {
            diagnostics.add(Diagnostic.error("Path is required")
                .withProperty("path"));
        }
        return diagnostics;
    }
}
```

### 4. Create Provider

```java
public class FilesProvider extends KiteProvider {

    public FilesProvider() {
        super("files", "1.0.0");
        // Auto-discover all ResourceType classes in this package
        discoverResources();
    }

    public static void main(String[] args) throws Exception {
        ProviderServer.serve(new FilesProvider());
    }
}
```

### 5. Configure Build

```groovy
plugins {
    id 'java'
    id 'application'
}

application {
    mainClass = 'com.example.FilesProvider'
}

tasks.named('startScripts') {
    applicationName = 'provider'
}
```

### 6. Create Provider Manifest

Create `provider.json` in your distribution:

```json
{
    "name": "files",
    "version": "1.0.0",
    "protocolVersion": 1,
    "executable": "provider"
}
```

## Design: ResourcePayload Pattern

The SDK uses **ResourcePayload** (msgpack-encoded bytes) instead of generating protobuf classes for each resource type.

### Why Not Protobuf Per Resource?

| Protobuf per resource | ResourcePayload (our approach) |
|----------------------|----------------------------|
| Generate .proto for each resource type | Use regular Java classes |
| Compile protos → Java stubs | Serialize at runtime via msgpack |
| Provider and engine must share generated code | Schema discovered via `GetProviderSchema` |
| Adding a resource = regenerate protos | Adding a resource = add a Java class |

### How It Works

```
provider.proto:
┌─────────────────────────────────────┐
│ message CreateResource.Request {    │
│     string type_name = 1;           │
│     ResourcePayload config = 2;  ◄──┼── msgpack bytes, not typed proto
│ }                                   │
└─────────────────────────────────────┘

dynamic.proto:
┌─────────────────────────────────────┐
│ message ResourcePayload {           │
│     bytes msgpack = 1;  ◄───────────┼── Resource serialized as msgpack
│     bytes json = 2;                 │
│ }                                   │
└─────────────────────────────────────┘
```

### The Flow

```
1. Provider starts
   └── Engine calls GetProviderSchema

2. SDK reflects on FileResource.class
   └── Returns schema (property names, types, @Cloud flags)

3. Engine calls CreateResource
   └── Sends msgpack-encoded FileResource

4. SDK deserializes msgpack → FileResource object
   └── Calls your create() method

5. Your code returns FileResource
   └── SDK serializes to msgpack → sends response
```

### Benefits

- **Familiar Java classes** - Use POJOs with Lombok, no generated code
- **No protobuf compilation** - No build step for resource definitions
- **IDE support** - Autocomplete, refactoring work naturally
- **Runtime flexibility** - Add resources without regenerating anything

### Proto Defines Service, Not Data

The `.proto` files define the gRPC **service contract** (CreateResource, ReadResource, etc.), not individual resource schemas. Resource data flows as opaque bytes that the SDK serializes/deserializes using Jackson + msgpack:

```java
// Inside ProviderServiceImpl
private final ObjectMapper msgpackMapper = new MessagePackMapper();

private <T> T fromResourcePayload(ResourcePayload value, Class<T> clazz) {
    return msgpackMapper.readValue(value.getMsgpack().toByteArray(), clazz);
}

private ResourcePayload toResourcePayload(Object value) {
    byte[] msgpack = msgpackMapper.writeValueAsBytes(value);
    return ResourcePayload.newBuilder()
            .setMsgpack(ByteString.copyFrom(msgpack))
            .build();
}
```

## SDK Components

### KiteProvider

Base class for all providers. Extend this and register your resource types:

```java
public class MyProvider extends KiteProvider {
    public MyProvider() {
        super("my-provider", "1.0.0");

        // Option 1: Auto-discover all ResourceType classes in this package (recommended)
        discoverResources();

        // Option 2: Auto-discover in a specific package
        // discoverResources("com.example.resources");

        // Option 3: Manual registration (type name from @TypeName annotation)
        // registerResource(new MyResourceType());

        // Option 4: Manual with explicit type name (overrides @TypeName)
        // registerResource("CustomName", new MyResourceType());
    }
}
```

**Auto-Discovery Rules:**
- Scans for classes that extend `ResourceTypeHandler`
- Resource class must have `@TypeName` annotation (classes without it are silently ignored)
- ResourceTypeHandler class must have a no-arg constructor
- Scans recursively through subdirectories

**GraalVM Native-Image Support:**

The SDK includes an annotation processor that generates `META-INF/kite/resource-types.txt` at compile time.
Add the SDK as an annotation processor in your build.gradle:

```groovy
dependencies {
    implementation project(':kite-provider-sdk')
    annotationProcessor project(':kite-provider-sdk')  // Generates manifest for GraalVM
}
```

The manifest is generated automatically - no manual steps needed.
The base provider checks for this manifest first, then falls back to runtime scanning.

### ResourceTypeHandler<T>

Abstract class for implementing CRUD operations on a resource type:

| Method | Description |
|--------|-------------|
| `create(T)` | Create a new resource, return with cloud-managed fields populated |
| `read(T)` | Read current state of a resource |
| `update(T)` | Update an existing resource |
| `delete(T)` | Delete a resource, return true if deleted |
| `validate(T)` | Validate resource configuration (optional) |
| `plan(T, T)` | Preview changes (optional) |

### ProviderServer

Handles the gRPC server and handshake protocol:

```java
// Simple usage
ProviderServer.serve(new MyProvider());

// Or with more control
var server = new ProviderServer(new MyProvider());
server.serve();
```

### Diagnostic

Report validation errors and warnings:

```java
// Error
Diagnostic.error("Name is required");
Diagnostic.error("Invalid format", "Expected ISO-8601 date");

// Warning
Diagnostic.warning("Deprecated field");

// With property path
Diagnostic.error("Invalid value").withProperty("config", "timeout");
```

### ProviderUtils

Utility methods for common operations:

```java
// Generate IDs
String id = ProviderUtils.generateId();           // UUID
String shortId = ProviderUtils.generateShortId(); // 8 chars

// Checksums
String hash = ProviderUtils.sha256("content");

// String utilities
if (ProviderUtils.isBlank(value)) { ... }
String result = ProviderUtils.coalesce(a, b, c);
```

## Annotations

| Annotation | Description |
|------------|-------------|
| `@TypeName("Name")` | Resource type name |
| `@Property` | Marks a field as a resource property |
| `@Cloud` | Cloud-managed property (read-only, set by provider) |

### @Property Attributes

The `@Property` annotation supports several attributes for documentation and validation:

```java
@Property(
    name = "custom_name",           // Override field name in schema
    description = "Field docs",     // Documentation text
    optional = true,                // Whether field is required (default: true)
    validValues = {"a", "b", "c"},  // Allowed values (generates @allowed decorator)
    hidden = false,                 // Hide from schema output
    deprecationMessage = "Use X"    // Mark as deprecated
)
private String field;
```

### @Property validValues → @allowed

The `validValues` attribute defines allowed values for a property. When documentation is generated, this produces the `@allowed` decorator in Kite schemas:

**Java source:**
```java
@Property(description = "The volume type",
          validValues = {"gp2", "gp3", "io1", "io2", "st1", "sc1"})
private String volumeType = "gp3";
```

**Generated Kite schema:**
```kite
@allowed(["gp2", "gp3", "io1", "io2", "st1", "sc1"])
string volumeType = "gp3"  // The volume type
```

**Generated HTML/Markdown docs:**

The `validValues` also populate the "Valid Values" column in the generated HTML and Markdown documentation tables.

### @Cloud Attributes

```java
@Cloud                      // Cloud-managed, not importable
@Cloud(importable = true)   // Can be used to import existing resources
```

## Handshake Protocol

The SDK implements a handshake protocol for secure provider launching:

1. Engine sets environment variables:
   - `KITE_PLUGIN_MAGIC_COOKIE` - Random authentication token
   - `KITE_PLUGIN_PROTOCOL_VERSION` - Protocol version (1)

2. Provider validates cookie and starts gRPC server on random port

3. Provider outputs handshake line to stdout:
   ```
   KITE_PLUGIN|1|<port>|grpc
   ```

4. Engine connects to `localhost:<port>`

## Distribution

### Registry Distribution

Providers can be published to the Kite registry:

```yaml
# kitefile.yaml
providers:
  - name: aws
    version: "1.0.0"
```

### Git Distribution

Providers can be distributed via Git:

```yaml
# kitefile.yaml
providers:
  - name: custom
    git: "github.com/org/kite-provider-custom"
    ref: "v1.0.0"
```

## Example

See `kite-providers/grpc-example` for a complete example provider.

## Documentation Generation

The SDK includes a documentation generator that creates HTML and Markdown reference documentation for your provider's resources.

### Automatic Generation (Gradle Plugin)

If you're using the `kite-provider-gradle-plugin`, documentation is generated automatically during build:

```groovy
plugins {
    id 'cloud.kitelang.provider'
}

kiteProvider {
    name = 'my-provider'

    // Documentation options (all optional, shown with defaults)
    docsEnabled = true                    // Generate docs during build
    docsFormats = 'html,markdown'         // Output formats
    docsOutputDir = 'build/docs/provider' // Output directory
}
```

Run `./gradlew generateProviderDocs` to generate documentation, or it runs automatically after `./gradlew build`.

### Programmatic Generation

You can also generate documentation programmatically:

```java
var provider = new MyProvider();
var generator = new DocGenerator(provider);

// Generate separate HTML pages
generator.generateHtml(Path.of("docs/html"));

// Generate separate Markdown pages
generator.generateMarkdown(Path.of("docs/markdown"));

// Generate single combined Markdown file
generator.generateCombinedMarkdown(Path.of("docs/REFERENCE.md"));
```

### CLI Usage

The SDK includes a CLI for generating documentation:

```bash
java -cp my-provider.jar cloud.kitelang.provider.docgen.DocGeneratorCli \
    --provider com.example.MyProvider \
    --output docs \
    --format html,markdown,combined-markdown
```

### Generated Output

**HTML Output (`docs/html/`):**
- `index.html` - Provider overview with resource list
- `{ResourceName}.html` - Individual resource documentation

**Markdown Output (`docs/markdown/`):**
- `README.md` - Provider overview with resource links
- `{ResourceName}.md` - Individual resource documentation

**Combined Markdown (`docs/REFERENCE.md`):**
- Single file with all resources for easy distribution

## Module Structure

```
kite-provider-sdk/
├── build.gradle
├── README.md
└── src/main/java/cloud/kitelang/provider/
    ├── package-info.java      # Package documentation
    ├── KiteProvider.java      # Base provider class
    ├── ResourceTypeHandler.java # Resource type abstraction
    ├── ProviderServer.java    # gRPC server and handshake
    ├── ProviderServiceImpl.java # gRPC service implementation
    ├── Diagnostic.java        # Error/warning reporting
    ├── ProviderUtils.java     # Utility methods
    └── docgen/                # Documentation generation
        ├── DocGenerator.java  # Core documentation generator
        └── DocGeneratorCli.java # CLI wrapper
```
