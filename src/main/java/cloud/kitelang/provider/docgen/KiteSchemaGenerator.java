package cloud.kitelang.provider.docgen;

import cloud.kitelang.provider.KiteProvider;
import cloud.kitelang.provider.ResourceTypeHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
                var kiteSchema = formatSchema(resource) + "\n";
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
                sb.append(formatSchema(resource));
                sb.append("\n\n");
            }
        }

        Files.writeString(outputFile, sb.toString());
    }

    /**
     * Formats a resource as a Kite schema string.
     * This format matches the docs/schemas/*.kite files.
     *
     * @param resource the resource to format
     * @return the formatted schema string
     */
    public static String formatSchema(ResourceInfo resource) {
        var sb = new StringBuilder();

        // Header comment
        sb.append("// ").append(resource.getName()).append("\n");

        // Calculate max type length for alignment
        int maxTypeLen = resource.getProperties().stream()
                .mapToInt(p -> p.getType().length())
                .max()
                .orElse(0);

        // Calculate max name+default length for comment alignment
        int maxNameLen = resource.getProperties().stream()
                .mapToInt(p -> {
                    int len = p.getName().length();
                    if (p.getDefaultValue() != null && !p.getDefaultValue().isEmpty()) {
                        len += 3 + formatDefaultValue(p.getDefaultValue(), p.getType()).length();
                    }
                    return len;
                })
                .max()
                .orElse(0);

        sb.append("schema ").append(resource.getName()).append(" {\n");

        for (var prop : resource.getProperties()) {
            // @allowed decorator for valid values
            if (prop.getValidValues() != null && !prop.getValidValues().isEmpty()) {
                sb.append("    @allowed([");
                sb.append(prop.getValidValues().stream()
                        .map(v -> "\"" + v + "\"")
                        .reduce((a, b) -> a + ", " + b)
                        .orElse(""));
                sb.append("])\n");
            }

            // Cloud decorator for cloud-managed properties
            if (prop.isCloudManaged()) {
                if (prop.isImportable()) {
                    sb.append("    @cloud(importable)\n");
                } else {
                    sb.append("    @cloud\n");
                }
            }

            // Type (aligned)
            sb.append("    ").append(prop.getType());
            sb.append(" ".repeat(maxTypeLen - prop.getType().length() + 1));

            // Name
            sb.append(prop.getName());

            // Default value assignment
            String nameWithDefault = prop.getName();
            if (prop.getDefaultValue() != null && !prop.getDefaultValue().isEmpty()) {
                var formattedDefault = formatDefaultValue(prop.getDefaultValue(), prop.getType());
                sb.append(" = ").append(formattedDefault);
                nameWithDefault = prop.getName() + " = " + formattedDefault;
            }

            // Comment with description (aligned)
            if (prop.getDescription() != null && !prop.getDescription().isEmpty()) {
                sb.append(" ".repeat(Math.max(1, maxNameLen - nameWithDefault.length() + 2)));
                sb.append("// ").append(prop.getDescription());
            }
            sb.append("\n");
        }

        sb.append("}");
        return sb.toString();
    }

    /**
     * Formats a default value based on the property type.
     * Strings are quoted, booleans and numbers are literal.
     */
    public static String formatDefaultValue(String value, String type) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        // Boolean values - no quotes
        if ("boolean".equalsIgnoreCase(type) ||
            "true".equalsIgnoreCase(value) ||
            "false".equalsIgnoreCase(value)) {
            return value.toLowerCase();
        }

        // Numeric values - no quotes
        if ("number".equalsIgnoreCase(type) || "integer".equalsIgnoreCase(type)) {
            return value;
        }

        // Try to detect if it's a number
        try {
            Double.parseDouble(value);
            return value;
        } catch (NumberFormatException ignored) {
            // Not a number, quote it as a string
        }

        // String values - add quotes
        return "\"" + value + "\"";
    }
}
