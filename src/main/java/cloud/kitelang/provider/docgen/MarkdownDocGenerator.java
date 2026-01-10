package cloud.kitelang.provider.docgen;

import cloud.kitelang.provider.KiteProvider;
import cloud.kitelang.provider.ResourceTypeHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generates Markdown documentation for Kite provider resources.
 *
 * <p>Usage:</p>
 * <pre>{@code
 * var generator = new MarkdownDocGenerator(provider);
 * generator.generate(Path.of("docs/md"));
 * generator.generateCombined(Path.of("docs/REFERENCE.md"));
 * }</pre>
 */
public class MarkdownDocGenerator extends DocGeneratorBase {

    public MarkdownDocGenerator(KiteProvider provider) {
        super(provider);
    }

    public MarkdownDocGenerator(String providerName, String providerVersion,
                                Map<String, ResourceTypeHandler<?>> resourceTypes) {
        super(providerName, providerVersion, resourceTypes);
    }

    /**
     * Generates Markdown documentation files.
     *
     * @param outputDir the directory to write files to
     */
    public void generate(Path outputDir) throws IOException {
        Files.createDirectories(outputDir);

        // Generate README.md (index)
        var indexMd = generateIndex();
        Files.writeString(outputDir.resolve("README.md"), indexMd);

        // Generate individual resource pages
        for (var resource : resources) {
            var resourceMd = generateResource(resource);
            Files.writeString(outputDir.resolve(resource.getName() + ".md"), resourceMd);
        }
    }

    /**
     * Generates a single combined Markdown file.
     *
     * @param outputFile the file to write to
     */
    public void generateCombined(Path outputFile) throws IOException {
        Files.createDirectories(outputFile.getParent());
        var combined = generateCombinedContent();
        Files.writeString(outputFile, combined);
    }

    private String generateIndex() {
        var sb = new StringBuilder();
        sb.append("# ").append(capitalize(providerInfo.getName())).append(" Provider\n\n");
        sb.append("**Version:** ").append(providerInfo.getVersion()).append("\n\n");

        // Group by domain
        var byDomain = groupByDomain();

        for (var entry : byDomain.entrySet()) {
            var domain = entry.getKey();
            var domainResources = entry.getValue();

            sb.append("## ").append(getDomainIcon(domain)).append(" ").append(capitalize(domain)).append("\n\n");
            sb.append("| Resource | Properties | Description |\n");
            sb.append("|----------|------------|-------------|\n");

            for (var resource : domainResources) {
                var desc = resource.getDescription() != null ? resource.getDescription() : "";
                if (desc.length() > 60) desc = desc.substring(0, 57) + "...";
                sb.append("| [").append(resource.getName()).append("](").append(resource.getName())
                  .append(".md) | ").append(resource.getProperties().size())
                  .append(" | ").append(desc.replace("|", "\\|")).append(" |\n");
            }
            sb.append("\n");
        }

        sb.append("---\n");
        sb.append("*Generated on ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE)).append("*\n");

        return sb.toString();
    }

    private String generateResource(ResourceInfo resource) {
        var sb = new StringBuilder();
        sb.append("# ").append(resource.getName()).append("\n\n");

        if (resource.getDescription() != null && !resource.getDescription().isEmpty()) {
            sb.append(resource.getDescription()).append("\n\n");
        }

        // Example with aligned "=" signs
        sb.append("## Example\n\n");
        sb.append("```kite\n");
        sb.append(generateAlignedExample(resource));
        sb.append("```\n\n");

        // Split properties
        var userProps = resource.getProperties().stream()
                .filter(p -> !p.isCloudManaged())
                .toList();
        var cloudProps = resource.getProperties().stream()
                .filter(PropertyInfo::isCloudManaged)
                .toList();

        if (!userProps.isEmpty()) {
            sb.append("## Properties\n\n");
            sb.append(generatePropertiesTable(userProps, false));
        }

        if (!cloudProps.isEmpty()) {
            sb.append("## Cloud Properties\n\n");
            sb.append("_These properties are set by the cloud provider after resource creation._\n\n");
            sb.append(generatePropertiesTable(cloudProps, true));
        }

        sb.append("\n[← Back to Index](README.md)\n");

        return sb.toString();
    }

    private String generateAlignedExample(ResourceInfo resource) {
        var sb = new StringBuilder();
        var props = resource.getProperties().stream()
                .filter(p -> !p.isCloudManaged() && !p.isDeprecated())
                .toList();

        int maxLen = props.stream()
                .mapToInt(p -> p.getName().length())
                .max()
                .orElse(0);

        sb.append("resource ").append(resource.getName()).append(" example {\n");

        for (var prop : props) {
            sb.append("    ").append(prop.getName());
            sb.append(" ".repeat(maxLen - prop.getName().length()));
            sb.append(" = ").append(getExampleValue(prop)).append("\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    private String getExampleValue(PropertyInfo prop) {
        // Use default value if available
        if (prop.getDefaultValue() != null && !prop.getDefaultValue().isEmpty()) {
            return switch (prop.getType()) {
                case "string" -> "\"" + prop.getDefaultValue() + "\"";
                case "boolean", "integer", "number" -> prop.getDefaultValue();
                default -> "\"" + prop.getDefaultValue() + "\"";
            };
        }
        // Use first valid value if available
        if (prop.getValidValues() != null && !prop.getValidValues().isEmpty()) {
            return "\"" + prop.getValidValues().get(0) + "\"";
        }
        return switch (prop.getType()) {
            case "string" -> "\"example-value\"";
            case "integer" -> "42";
            case "boolean" -> "true";
            case "number" -> "3.14";
            case "list" -> "[\"item1\", \"item2\"]";
            case "map" -> "{ key: \"value\" }";
            default -> "\"...\"";
        };
    }

    private String generatePropertiesTable(List<PropertyInfo> properties, boolean isCloudSection) {
        var sb = new StringBuilder();

        // Cloud properties only show Name, Type, Description
        if (isCloudSection) {
            sb.append("| Name | Type | Description |\n");
            sb.append("|------|------|-------------|\n");

            for (var prop : properties) {
                var desc = prop.getDescription() != null ? prop.getDescription() : "";
                if (prop.isImportable()) {
                    desc = "*📥 importable* " + desc;
                }

                sb.append("| `").append(prop.getName()).append("` | `").append(prop.getType()).append("` | ")
                  .append(desc.replace("|", "\\|")).append(" |\n");
            }
        } else {
            sb.append("| Name | Type | Default | Valid Values | Required | Description |\n");
            sb.append("|------|------|---------|--------------|----------|-------------|\n");

            for (var prop : properties) {
                var badges = new ArrayList<String>();
                if (prop.isDeprecated()) badges.add("⚠️ deprecated");

                var desc = prop.getDescription() != null ? prop.getDescription() : "";
                if (!badges.isEmpty()) {
                    desc = "*" + String.join(", ", badges) + "* " + desc;
                }

                var defaultVal = prop.getDefaultValue() != null && !prop.getDefaultValue().isEmpty()
                        ? "`" + prop.getDefaultValue() + "`"
                        : "—";

                var validVals = prop.getValidValues() != null && !prop.getValidValues().isEmpty()
                        ? prop.getValidValues().stream().map(v -> "`" + v + "`").collect(Collectors.joining(", "))
                        : "—";

                sb.append("| `").append(prop.getName()).append("` | `").append(prop.getType()).append("` | ")
                  .append(defaultVal).append(" | ").append(validVals).append(" | ")
                  .append(prop.isRequired() ? "Yes" : "No").append(" | ")
                  .append(desc.replace("|", "\\|")).append(" |\n");
            }
        }
        return sb.toString();
    }

    private String generateCombinedContent() {
        var sb = new StringBuilder();
        sb.append("# ").append(capitalize(providerInfo.getName())).append(" Provider Reference\n\n");
        sb.append("**Version:** ").append(providerInfo.getVersion()).append("\n\n");
        sb.append("## Table of Contents\n\n");

        for (var resource : resources) {
            sb.append("- [").append(resource.getName()).append("](#")
              .append(resource.getName().toLowerCase()).append(")\n");
        }

        sb.append("\n---\n\n");

        for (var resource : resources) {
            sb.append(generateResource(resource));
            sb.append("\n---\n\n");
        }

        return sb.toString();
    }
}
