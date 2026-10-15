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

The SDK is on Maven Central as `cloud.kitelang:kite-provider-sdk`, and brings `cloud.kitelang:kite-api` and `cloud.kitelang:kite-proto` with it. It is compiled for Java 25. The `cloud.kitelang.provider` Gradle plugin does not add it, so a build that uses the plugin needs these lines too:

```groovy
repositories {
    mavenCentral()
}

dependencies {
    implementation 'cloud.kitelang:kite-provider-sdk:0.4.0'
    // Lists your ResourceTypeHandler classes in META-INF/kite/resource-types.txt,
    // which is where KiteProvider finds them
    annotationProcessor 'cloud.kitelang:kite-provider-sdk:0.4.0'
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}
```

A provider built with 0.4.0 needs an engine that sends the magic cookie on every call; see [Handshake Protocol](#handshake-protocol).

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
It runs when the SDK is also on the annotation processor path, as in [Add Dependency](#1-add-dependency):

```groovy
dependencies {
    implementation 'cloud.kitelang:kite-provider-sdk:0.4.0'
    annotationProcessor 'cloud.kitelang:kite-provider-sdk:0.4.0'
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
   - `KITE_PLUGIN_MAGIC_COOKIE` - Random secret, a new one for each provider process
   - `KITE_PLUGIN_PROTOCOL_VERSION` - Protocol version (1)

2. Provider exits unless both are set, then starts its gRPC server on a free port of
   `127.0.0.1` (never on all interfaces)

3. Provider outputs handshake line to stdout:
   ```
   KITE_PLUGIN|1|<port>|grpc
   ```

4. Engine connects to `127.0.0.1:<port>` and sends the cookie as gRPC metadata
   `kite-plugin-magic-cookie: <cookie>` on every call

5. Provider rejects any call without that metadata, or with a different value, with
   `UNAUTHENTICATED` before the call reaches the provider code

Since SDK 0.4.0. Providers built with 0.3.0 or earlier listen on every interface and
only check that the cookie variable is set at startup. An engine that does not send the
cookie cannot use a provider built with 0.4.0, so the engine must be upgraded first.
`MagicCookieAuth` holds both sides of the check; a provider written in another
language must check the same metadata key.

## Distribution

Users list providers under `dependencies` in `kitefile.yml`. The official providers (`aws`, `azure`,
`files`, `gcp`) need only a name and version; they come from the
[kitecorp/kite-providers](https://github.com/kitecorp/kite-providers/releases) GitHub releases.

Any other provider is installed from its git repository:

```yaml
# kitefile.yml
dependencies:
  - name: custom
    git: "github.com/org/kite-provider-custom"
    ref: "v1.0.0"
```

Kite downloads the repository's GitHub release for that ref when it has a `custom.zip` asset (the name
from the entry), and otherwise clones the ref and builds it.

## Example

See `kite-providers/grpc-example` for a complete example provider.

## Documentation Generation

The SDK includes a documentation generator that creates HTML and Markdown reference documentation for your provider's resources.

### Automatic Generation (Gradle Plugin)

If you're using the `kite-provider-gradle-plugin`, its `generateProviderDocs` task generates documentation:

```groovy
plugins {
    id 'cloud.kitelang.provider'
}

kiteProvider {
    name = 'my-provider'

    // Documentation options (all optional, shown with defaults)
    docsEnabled = true                    // Register generateProviderDocs
    docsFormats = 'html,markdown,schemas' // Output formats
    docsOutputDir = 'docs'                // Output directory
}
```

Run `./gradlew generateProviderDocs` to generate documentation. It runs only when called: `build` and `installDist` never write `docs/`.

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
├── settings.gradle
├── README.md
├── .github/workflows/
│   └── publish-maven-central.yml # Releases the SDK, see Releasing
└── src/main/java/cloud/kitelang/provider/
    ├── package-info.java      # Package documentation
    ├── KiteProvider.java      # Base provider class
    ├── ResourceTypeHandler.java # Resource type abstraction
    ├── ProviderServer.java    # gRPC server and handshake
    ├── MagicCookieAuth.java   # Cookie metadata: engine attaches, server checks
    ├── ProviderServiceImpl.java # gRPC service implementation
    ├── Diagnostic.java        # Error/warning reporting
    ├── ProviderUtils.java     # Utility methods
    └── docgen/                # Documentation generation
        ├── DocGenerator.java  # Core documentation generator
        └── DocGeneratorCli.java # CLI wrapper
```

## Building

`./gradlew build` builds and tests the SDK on its own, with JDK 25. Built this way it takes kite-api and
kite-proto from Maven Central, at the versions `build.gradle` names. Inside the kite superproject, and in
the SDK build of kite-providers, it uses those two as sibling projects instead.

## Releasing

Releases go to Maven Central as `cloud.kitelang:kite-provider-sdk` through the **Publish to Maven Central**
workflow (`.github/workflows/publish-maven-central.yml`), which runs only when started by hand. The
published POM depends on the kite-api and kite-proto versions that `build.gradle` names, and the release
builds against those versions from Maven Central, so they must be published before the SDK.

1. If kite-api or kite-proto changed, release them first (their READMEs have the same steps), then
   raise their versions in this `build.gradle`.
2. Raise `version` in `build.gradle`. Maven Central never accepts the same version twice.
3. Merge to `main`, then start the workflow on `main`, from the Actions tab or with
   `gh workflow run publish-maven-central.yml --repo kitecorp/kite-provider-sdk --ref main`.
   It refuses to run on any other branch.
4. The run builds and tests the SDK and stages it in `build/staging-deploy`. JReleaser then signs every
   file, writes its checksums, checks the POM and the sources and javadoc jars against Maven Central's
   rules, uploads the bundle and waits until Central has published it. When a run fails, its
   `jreleaser-output` artifact holds JReleaser's log.
5. Raise the SDK version where authors read it: this README, the kite-provider-gradle-plugin README,
   and the provider page of kitelang.cloud (`kitelang/content/docs/providers.mdx`).

The workflow reads four secrets, the same ones kite-provider-gradle-plugin publishes with, from this
repository or from the organisation:

| Secret | Value |
|---|---|
| `MAVEN_USERNAME` | Username part of a Central Portal user token (central.sonatype.com/account) |
| `MAVEN_PASSWORD` | Password part of that token |
| `GPG_PUBLIC_KEY` | `gpg --armor --export <key id>`; the key must be on a public keyserver |
| `GPG_PRIVATE_KEY` | `gpg --armor --export-secret-keys <key id>`, for a key without a passphrase |

To see what a release would upload, without publishing anything, run
`./gradlew publishAllPublicationsToStagingDeployRepository` and look in `build/staging-deploy`. JReleaser
adds the `.asc` signatures when it deploys.
