package cloud.kitelang.provider.docgen;

import cloud.kitelang.provider.KiteProvider;
import cloud.kitelang.provider.ResourceTypeHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generates Kite schema files (.kite) for provider resources.
 *
 * <p>Files are organized by domain (networking/, compute/, storage/, etc.)</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * var generator = new KiteSchemaGenerator(provider);
 * generator.generate(Path.of("schemas"));
 * }</pre>
 */
public class KiteSchemaGenerator extends DocGeneratorBase {

    public KiteSchemaGenerator(KiteProvider provider) {
        super(provider);
    }

    public KiteSchemaGenerator(String providerName, String providerVersion,
                               Map<String, ResourceTypeHandler<?>> resourceTypes) {
        super(providerName, providerVersion, resourceTypes);
    }

    /**
     * Generates Kite schema files organized by domain.
     *
     * @param outputDir the directory to write files to
     */
    public void generate(Path outputDir) throws IOException {
        Files.createDirectories(outputDir);

        // Group resources by domain
        var byDomain = resources.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getDomain() != null ? r.getDomain() : "general"));

        // Generate schema files organized by domain
        for (var entry : byDomain.entrySet()) {
            var domain = entry.getKey();
            var domainResources = entry.getValue();
            var domainDir = outputDir.resolve(domain);
            Files.createDirectories(domainDir);

            for (var resource : domainResources) {
                var kiteSchema = generateSchema(resource);
                Files.writeString(domainDir.resolve(resource.getName() + ".kite"), kiteSchema);
            }
        }
    }

    /**
     * Generates a single combined schema file with all resources.
     *
     * @param outputFile the file to write to
     */
    public void generateCombined(Path outputFile) throws IOException {
        Files.createDirectories(outputFile.getParent());

        var sb = new StringBuilder();
        sb.append("// ").append(capitalize(providerInfo.getName())).append(" Provider Schemas\n");
        sb.append("// Version: ").append(providerInfo.getVersion()).append("\n\n");

        var byDomain = groupByDomain();

        for (var entry : byDomain.entrySet()) {
            var domain = entry.getKey();
            var domainResources = entry.getValue();

            sb.append("// ").append("=".repeat(60)).append("\n");
            sb.append("// ").append(getDomainIcon(domain)).append(" ").append(capitalize(domain)).append("\n");
            sb.append("// ").append("=".repeat(60)).append("\n\n");

            for (var resource : domainResources) {
                sb.append(generateSchema(resource));
                sb.append("\n");
            }
        }

        Files.writeString(outputFile, sb.toString());
    }

    private String generateSchema(ResourceInfo resource) {
        var sb = new StringBuilder();

        // Header comment with description
        sb.append("// ").append(resource.getName());
        if (resource.getDescription() != null && !resource.getDescription().isEmpty()) {
            sb.append(" - ").append(resource.getDescription());
        }
        sb.append("\n");

        // Calculate max property name length for alignment
        int maxLen = resource.getProperties().stream()
                .mapToInt(p -> p.getName().length())
                .max()
                .orElse(0);

        sb.append("schema ").append(resource.getName()).append(" {\n");

        for (var prop : resource.getProperties()) {
            // Cloud decorator for cloud-managed properties
            if (prop.isCloudManaged()) {
                if (prop.isImportable()) {
                    sb.append("    @cloud(importable)\n");
                } else {
                    sb.append("    @cloud\n");
                }
            }

            // Type and name with alignment
            sb.append("    ").append(prop.getType());
            sb.append(" ".repeat(Math.max(1, 10 - prop.getType().length())));
            sb.append(prop.getName());

            // Build comment with description, default, and valid values
            var comments = new ArrayList<String>();
            if (prop.getDescription() != null && !prop.getDescription().isEmpty()) {
                comments.add(prop.getDescription());
            }
            if (prop.getDefaultValue() != null && !prop.getDefaultValue().isEmpty()) {
                comments.add("default: " + prop.getDefaultValue());
            }
            if (prop.getValidValues() != null && !prop.getValidValues().isEmpty()) {
                comments.add("valid: " + String.join(", ", prop.getValidValues()));
            }

            if (!comments.isEmpty()) {
                sb.append(" ".repeat(Math.max(1, maxLen - prop.getName().length() + 2)));
                sb.append("// ").append(String.join(" | ", comments));
            }
            sb.append("\n");
        }

        sb.append("}\n");
        return sb.toString();
    }
}
