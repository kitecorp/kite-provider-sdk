package cloud.kitelang.provider.docgen;

import cloud.kitelang.api.resource.Property;
import cloud.kitelang.api.schema.Schema;
import cloud.kitelang.provider.KiteProvider;
import cloud.kitelang.provider.ResourceTypeHandler;
import lombok.Builder;
import lombok.Data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates documentation for Kite provider entities.
 * Produces both HTML and Markdown formats.
 *
 * <p>Usage:</p>
 * <pre>{@code
 * var provider = new AwsProvider();
 * var generator = new DocGenerator(provider);
 * generator.generateHtml(Path.of("docs"));
 * generator.generateMarkdown(Path.of("docs"));
 * }</pre>
 */
public class DocGenerator {

    private final ProviderInfo providerInfo;
    private final List<ResourceInfo> resources;

    /**
     * Creates a documentation generator from a provider instance.
     */
    public DocGenerator(KiteProvider provider) {
        this.providerInfo = ProviderInfo.builder()
                .name(provider.getName())
                .version(provider.getVersion())
                .build();

        this.resources = provider.getResourceTypes().entrySet().stream()
                .map(entry -> extractResourceInfo(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(ResourceInfo::getName))
                .toList();
    }

    /**
     * Creates a documentation generator from explicit provider info.
     */
    public DocGenerator(String providerName, String providerVersion,
                        Map<String, ResourceTypeHandler<?>> resourceTypes) {
        this.providerInfo = ProviderInfo.builder()
                .name(providerName)
                .version(providerVersion)
                .build();

        this.resources = resourceTypes.entrySet().stream()
                .map(entry -> extractResourceInfo(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(ResourceInfo::getName))
                .toList();
    }

    private ResourceInfo extractResourceInfo(String typeName, ResourceTypeHandler<?> handler) {
        var schema = handler.getSchema();
        var properties = new ArrayList<PropertyInfo>();

        for (var prop : schema.getProperties()) {
            if (prop.hidden()) continue;

            properties.add(PropertyInfo.builder()
                    .name(prop.name())
                    .type(formatType(prop.type(), prop.typeClass()))
                    .description(prop.description())
                    .required(!isOptionalType(prop.typeClass()))
                    .cloudManaged(prop.cloud())
                    .importable(prop.importable())
                    .deprecated(prop.deprecationMessage() != null && !prop.deprecationMessage().isEmpty())
                    .deprecationMessage(prop.deprecationMessage())
                    .build());
        }

        return ResourceInfo.builder()
                .name(typeName)
                .description(schema.getDescription())
                .properties(properties)
                .build();
    }

    private String formatType(Object type, Class<?> typeClass) {
        if (typeClass == null) {
            return type != null ? type.toString() : "any";
        }

        // Handle common types
        if (typeClass == String.class) return "string";
        if (typeClass == Integer.class || typeClass == int.class) return "integer";
        if (typeClass == Long.class || typeClass == long.class) return "integer";
        if (typeClass == Boolean.class || typeClass == boolean.class) return "boolean";
        if (typeClass == Double.class || typeClass == double.class) return "number";
        if (typeClass == Float.class || typeClass == float.class) return "number";
        if (List.class.isAssignableFrom(typeClass)) return "list";
        if (Map.class.isAssignableFrom(typeClass)) return "map";
        if (Set.class.isAssignableFrom(typeClass)) return "set";

        return typeClass.getSimpleName().toLowerCase();
    }

    private boolean isOptionalType(Class<?> typeClass) {
        // Primitive types are considered required
        return typeClass != null && !typeClass.isPrimitive();
    }

    /**
     * Generates HTML documentation.
     *
     * @param outputDir the directory to write files to
     */
    public void generateHtml(Path outputDir) throws IOException {
        Files.createDirectories(outputDir);

        // Generate index.html
        var indexHtml = generateHtmlIndex();
        Files.writeString(outputDir.resolve("index.html"), indexHtml);

        // Generate individual resource pages
        for (var resource : resources) {
            var resourceHtml = generateHtmlResource(resource);
            Files.writeString(outputDir.resolve(resource.getName() + ".html"), resourceHtml);
        }
    }

    /**
     * Generates Markdown documentation.
     *
     * @param outputDir the directory to write files to
     */
    public void generateMarkdown(Path outputDir) throws IOException {
        Files.createDirectories(outputDir);

        // Generate README.md (index)
        var indexMd = generateMarkdownIndex();
        Files.writeString(outputDir.resolve("README.md"), indexMd);

        // Generate individual resource pages
        for (var resource : resources) {
            var resourceMd = generateMarkdownResource(resource);
            Files.writeString(outputDir.resolve(resource.getName() + ".md"), resourceMd);
        }
    }

    /**
     * Generates a single combined Markdown file.
     *
     * @param outputFile the file to write to
     */
    public void generateCombinedMarkdown(Path outputFile) throws IOException {
        Files.createDirectories(outputFile.getParent());
        var combined = generateCombinedMarkdownContent();
        Files.writeString(outputFile, combined);
    }

    private String generateHtmlIndex() {
        var sb = new StringBuilder();
        sb.append("""
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>%s Provider Documentation</title>
                <style>
                    :root { --primary: #2563eb; --gray: #6b7280; --border: #e5e7eb; }
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
                           max-width: 900px; margin: 0 auto; padding: 2rem; line-height: 1.6; }
                    h1 { color: var(--primary); border-bottom: 2px solid var(--primary); padding-bottom: 0.5rem; }
                    h2 { color: #1f2937; margin-top: 2rem; }
                    .resource-list { list-style: none; padding: 0; }
                    .resource-list li { padding: 0.75rem 1rem; border: 1px solid var(--border);
                                        margin-bottom: 0.5rem; border-radius: 0.5rem; }
                    .resource-list a { color: var(--primary); text-decoration: none; font-weight: 500; }
                    .resource-list a:hover { text-decoration: underline; }
                    .meta { color: var(--gray); font-size: 0.875rem; margin-top: 2rem; }
                    .badge { display: inline-block; padding: 0.125rem 0.5rem; border-radius: 9999px;
                             font-size: 0.75rem; font-weight: 500; margin-left: 0.5rem; }
                    .badge-count { background: #dbeafe; color: var(--primary); }
                </style>
            </head>
            <body>
                <h1>%s Provider</h1>
                <p>Version: <strong>%s</strong></p>

                <h2>Resources <span class="badge badge-count">%d</span></h2>
                <ul class="resource-list">
            """.formatted(
                capitalize(providerInfo.getName()),
                capitalize(providerInfo.getName()),
                providerInfo.getVersion(),
                resources.size()
        ));

        for (var resource : resources) {
            sb.append("""
                    <li>
                        <a href="%s.html">%s</a>
                        <span style="color: var(--gray); margin-left: 0.5rem;">%d properties</span>
                    </li>
                """.formatted(resource.getName(), resource.getName(), resource.getProperties().size()));
        }

        sb.append("""
                </ul>
                <p class="meta">Generated on %s</p>
            </body>
            </html>
            """.formatted(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));

        return sb.toString();
    }

    private String generateHtmlResource(ResourceInfo resource) {
        var sb = new StringBuilder();
        sb.append("""
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>%s - %s Provider</title>
                <style>
                    :root { --primary: #2563eb; --gray: #6b7280; --border: #e5e7eb;
                            --green: #059669; --yellow: #d97706; --red: #dc2626; }
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
                           max-width: 900px; margin: 0 auto; padding: 2rem; line-height: 1.6; }
                    h1 { color: var(--primary); }
                    h2 { color: #1f2937; margin-top: 2rem; border-bottom: 1px solid var(--border); padding-bottom: 0.5rem; }
                    a { color: var(--primary); }
                    table { width: 100%%; border-collapse: collapse; margin-top: 1rem; }
                    th, td { text-align: left; padding: 0.75rem; border-bottom: 1px solid var(--border); }
                    th { background: #f9fafb; font-weight: 600; }
                    .type { font-family: monospace; background: #f3f4f6; padding: 0.125rem 0.375rem;
                            border-radius: 0.25rem; font-size: 0.875rem; }
                    .badge { display: inline-block; padding: 0.125rem 0.5rem; border-radius: 9999px;
                             font-size: 0.75rem; font-weight: 500; margin-right: 0.25rem; }
                    .badge-required { background: #fee2e2; color: var(--red); }
                    .badge-optional { background: #d1fae5; color: var(--green); }
                    .badge-cloud { background: #dbeafe; color: var(--primary); }
                    .badge-deprecated { background: #fef3c7; color: var(--yellow); }
                    .example { background: #1f2937; color: #e5e7eb; padding: 1rem; border-radius: 0.5rem;
                               font-family: monospace; white-space: pre; overflow-x: auto; }
                    .nav { margin-bottom: 1rem; }
                </style>
            </head>
            <body>
                <nav class="nav"><a href="index.html">← Back to Index</a></nav>
                <h1>%s</h1>
            """.formatted(resource.getName(), capitalize(providerInfo.getName()), resource.getName()));

        if (resource.getDescription() != null && !resource.getDescription().isEmpty()) {
            sb.append("<p>").append(escapeHtml(resource.getDescription())).append("</p>\n");
        }

        // Example usage
        sb.append("""
                <h2>Example</h2>
                <div class="example">resource %s example {
            """.formatted(resource.getName()));

        for (var prop : resource.getProperties()) {
            if (!prop.isCloudManaged() && !prop.isDeprecated()) {
                sb.append("    ").append(prop.getName()).append(" = ")
                  .append(getExampleValue(prop)).append("\n");
            }
        }
        sb.append("}</div>\n");

        // Properties table
        sb.append("""
                <h2>Properties</h2>
                <table>
                    <thead>
                        <tr>
                            <th>Name</th>
                            <th>Type</th>
                            <th>Description</th>
                        </tr>
                    </thead>
                    <tbody>
            """);

        for (var prop : resource.getProperties()) {
            sb.append("<tr><td><strong>").append(prop.getName()).append("</strong><br>");

            if (prop.isRequired()) {
                sb.append("<span class=\"badge badge-required\">required</span>");
            } else {
                sb.append("<span class=\"badge badge-optional\">optional</span>");
            }
            if (prop.isCloudManaged()) {
                sb.append("<span class=\"badge badge-cloud\">cloud-managed</span>");
            }
            if (prop.isDeprecated()) {
                sb.append("<span class=\"badge badge-deprecated\">deprecated</span>");
            }

            sb.append("</td><td><span class=\"type\">").append(prop.getType()).append("</span></td>");
            sb.append("<td>");
            if (prop.getDescription() != null && !prop.getDescription().isEmpty()) {
                sb.append(escapeHtml(prop.getDescription()));
            }
            if (prop.isDeprecated() && prop.getDeprecationMessage() != null) {
                sb.append("<br><em style=\"color: var(--yellow);\">Deprecated: ")
                  .append(escapeHtml(prop.getDeprecationMessage())).append("</em>");
            }
            sb.append("</td></tr>\n");
        }

        sb.append("""
                    </tbody>
                </table>
            </body>
            </html>
            """);

        return sb.toString();
    }

    private String generateMarkdownIndex() {
        var sb = new StringBuilder();
        sb.append("# ").append(capitalize(providerInfo.getName())).append(" Provider\n\n");
        sb.append("**Version:** ").append(providerInfo.getVersion()).append("\n\n");
        sb.append("## Resources\n\n");
        sb.append("| Resource | Properties |\n");
        sb.append("|----------|------------|\n");

        for (var resource : resources) {
            sb.append("| [").append(resource.getName()).append("](").append(resource.getName())
              .append(".md) | ").append(resource.getProperties().size()).append(" |\n");
        }

        sb.append("\n---\n");
        sb.append("*Generated on ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE)).append("*\n");

        return sb.toString();
    }

    private String generateMarkdownResource(ResourceInfo resource) {
        var sb = new StringBuilder();
        sb.append("# ").append(resource.getName()).append("\n\n");

        if (resource.getDescription() != null && !resource.getDescription().isEmpty()) {
            sb.append(resource.getDescription()).append("\n\n");
        }

        // Example
        sb.append("## Example\n\n");
        sb.append("```kite\n");
        sb.append("resource ").append(resource.getName()).append(" example {\n");
        for (var prop : resource.getProperties()) {
            if (!prop.isCloudManaged() && !prop.isDeprecated()) {
                sb.append("    ").append(prop.getName()).append(" = ").append(getExampleValue(prop)).append("\n");
            }
        }
        sb.append("}\n");
        sb.append("```\n\n");

        // Properties
        sb.append("## Properties\n\n");
        sb.append("| Name | Type | Required | Description |\n");
        sb.append("|------|------|----------|-------------|\n");

        for (var prop : resource.getProperties()) {
            var badges = new ArrayList<String>();
            if (prop.isCloudManaged()) badges.add("cloud-managed");
            if (prop.isImportable()) badges.add("importable");
            if (prop.isDeprecated()) badges.add("⚠️ deprecated");

            var desc = prop.getDescription() != null ? prop.getDescription() : "";
            if (!badges.isEmpty()) {
                desc = "*" + String.join(", ", badges) + "* " + desc;
            }

            sb.append("| `").append(prop.getName()).append("` | `").append(prop.getType()).append("` | ")
              .append(prop.isRequired() ? "Yes" : "No").append(" | ")
              .append(desc.replace("|", "\\|")).append(" |\n");
        }

        sb.append("\n[← Back to Index](README.md)\n");

        return sb.toString();
    }

    private String generateCombinedMarkdownContent() {
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
            sb.append(generateMarkdownResource(resource));
            sb.append("\n---\n\n");
        }

        return sb.toString();
    }

    private String getExampleValue(PropertyInfo prop) {
        return switch (prop.getType()) {
            case "string" -> "\"example-value\"";
            case "integer" -> "42";
            case "boolean" -> "true";
            case "number" -> "3.14";
            case "list" -> "[\"item1\", \"item2\"]";
            case "map" -> "{ key = \"value\" }";
            default -> "\"...\"";
        };
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    // Data classes
    @Data
    @Builder
    public static class ProviderInfo {
        private String name;
        private String version;
    }

    @Data
    @Builder
    public static class ResourceInfo {
        private String name;
        private String description;
        private List<PropertyInfo> properties;
    }

    @Data
    @Builder
    public static class PropertyInfo {
        private String name;
        private String type;
        private String description;
        private boolean required;
        private boolean cloudManaged;
        private boolean importable;
        private boolean deprecated;
        private String deprecationMessage;
    }
}
