package cloud.kitelang.provider.docgen;

import cloud.kitelang.provider.docgen.DocGeneratorBase.PropertyInfo;
import cloud.kitelang.provider.docgen.DocGeneratorBase.ResourceInfo;

import java.util.List;

/**
 * Generates syntax-highlighted code examples for resources.
 */
class SyntaxHighlightedHtmlGenerator {

    private final List<ResourceInfo> resources;

    SyntaxHighlightedHtmlGenerator(List<ResourceInfo> resources) {
        this.resources = resources;
    }

    String generateBasicExample(ResourceInfo resource) {
        var sb = new StringBuilder();
        var props = resource.getProperties().stream()
                .filter(p -> !p.isCloudManaged() && !p.isDeprecated())
                .toList();

        int maxLen = props.stream()
                .mapToInt(p -> p.getName().length())
                .max()
                .orElse(0);

        sb.append("<span class=\"kw\">resource</span> ");
        sb.append("<span class=\"type\">").append(resource.getName()).append("</span> ");
        sb.append("<span class=\"name\">example</span> ");
        sb.append("<span class=\"brace\">{</span>\n");

        for (var prop : props) {
            sb.append("    <span class=\"prop\">").append(prop.getName()).append("</span>");
            sb.append(" ".repeat(maxLen - prop.getName().length()));
            sb.append(" <span class=\"eq\">=</span> ");
            sb.append(getHighlightedValue(prop));
            sb.append("\n");
        }

        sb.append("<span class=\"brace\">}</span>");
        return sb.toString();
    }

    String generateReferencesExample(ResourceInfo resource, String domain) {
        var sb = new StringBuilder();
        var props = resource.getProperties().stream()
                .filter(p -> !p.isCloudManaged() && !p.isDeprecated())
                .toList();

        // Find related resources in same domain to reference
        var relatedResources = resources.stream()
                .filter(r -> domain.equals(r.getDomain() != null ? r.getDomain() : "general"))
                .filter(r -> !r.getName().equals(resource.getName()))
                .limit(2)
                .toList();

        sb.append("<span class=\"comment\">// Example with resource references</span>\n\n");

        // Add referenced resources first
        for (var related : relatedResources) {
            sb.append("<span class=\"kw\">resource</span> ");
            sb.append("<span class=\"type\">").append(related.getName()).append("</span> ");
            sb.append("<span class=\"name\">my_").append(related.getName().toLowerCase()).append("</span> ");
            sb.append("<span class=\"brace\">{</span>\n");
            sb.append("    <span class=\"comment\">// ... configuration</span>\n");
            sb.append("<span class=\"brace\">}</span>\n\n");
        }

        // Main resource with references
        sb.append("<span class=\"kw\">resource</span> ");
        sb.append("<span class=\"type\">").append(resource.getName()).append("</span> ");
        sb.append("<span class=\"name\">example</span> ");
        sb.append("<span class=\"brace\">{</span>\n");

        int count = 0;
        for (var prop : props) {
            sb.append("    <span class=\"prop\">").append(prop.getName()).append("</span>");
            sb.append(" <span class=\"eq\">=</span> ");

            // Use reference for first few props if we have related resources
            if (count < relatedResources.size() && prop.getType().equals("string")) {
                var related = relatedResources.get(count);
                sb.append("<span class=\"name\">my_").append(related.getName().toLowerCase())
                  .append("</span>.<span class=\"prop\">id</span>");
            } else {
                sb.append(getHighlightedValue(prop));
            }
            sb.append("\n");
            count++;
        }

        sb.append("<span class=\"brace\">}</span>");
        return sb.toString();
    }

    String generateCompleteExample(ResourceInfo resource) {
        var sb = new StringBuilder();
        var allProps = resource.getProperties().stream()
                .filter(p -> !p.isCloudManaged())
                .toList();

        int maxLen = allProps.stream()
                .mapToInt(p -> p.getName().length())
                .max()
                .orElse(0);

        sb.append("<span class=\"comment\">// Complete example with all properties</span>\n");
        sb.append("<span class=\"kw\">resource</span> ");
        sb.append("<span class=\"type\">").append(resource.getName()).append("</span> ");
        sb.append("<span class=\"name\">complete_example</span> ");
        sb.append("<span class=\"brace\">{</span>\n");

        for (var prop : allProps) {
            if (prop.isDeprecated()) {
                sb.append("    <span class=\"comment\">// ").append(prop.getName()).append(" (deprecated)</span>\n");
                continue;
            }
            sb.append("    <span class=\"prop\">").append(prop.getName()).append("</span>");
            sb.append(" ".repeat(maxLen - prop.getName().length()));
            sb.append(" <span class=\"eq\">=</span> ");
            sb.append(getHighlightedValue(prop));
            if (prop.getDescription() != null && !prop.getDescription().isEmpty()) {
                sb.append("  <span class=\"comment\">// ").append(escapeHtml(truncate(prop.getDescription(), 40))).append("</span>");
            }
            sb.append("\n");
        }

        sb.append("<span class=\"brace\">}</span>");
        return sb.toString();
    }

    String generateSchema(ResourceInfo resource) {
        var sb = new StringBuilder();

        sb.append("<span class=\"comment\">// ").append(resource.getName()).append("</span>\n");

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
                        len += 3 + KiteSchemaGenerator.formatDefaultValue(p.getDefaultValue(), p.getType()).length();
                    }
                    return len;
                })
                .max()
                .orElse(0);

        sb.append("<span class=\"kw\">schema</span> <span class=\"type\">").append(resource.getName()).append("</span> <span class=\"brace\">{</span>\n");

        for (var prop : resource.getProperties()) {
            if (prop.getValidValues() != null && !prop.getValidValues().isEmpty()) {
                sb.append("    <span class=\"decorator\">@allowed</span><span class=\"brace\">([</span>");
                sb.append(prop.getValidValues().stream()
                        .map(v -> "<span class=\"str\">\"" + escapeHtml(v) + "\"</span>")
                        .reduce((a, b) -> a + ", " + b)
                        .orElse(""));
                sb.append("<span class=\"brace\">])</span>\n");
            }

            if (prop.isCloudManaged()) {
                if (prop.isImportable()) {
                    sb.append("    <span class=\"decorator\">@cloud</span><span class=\"brace\">(</span><span class=\"name\">importable</span><span class=\"brace\">)</span>\n");
                } else {
                    sb.append("    <span class=\"decorator\">@cloud</span>\n");
                }
            }

            // Type (aligned)
            sb.append("    <span class=\"type\">").append(prop.getType()).append("</span>");
            sb.append(" ".repeat(maxTypeLen - prop.getType().length() + 1));

            // Name
            sb.append("<span class=\"prop\">").append(prop.getName()).append("</span>");

            String nameWithDefault = prop.getName();
            if (prop.getDefaultValue() != null && !prop.getDefaultValue().isEmpty()) {
                var formattedDefault = KiteSchemaGenerator.formatDefaultValue(prop.getDefaultValue(), prop.getType());
                sb.append(" <span class=\"eq\">=</span> ");
                if (formattedDefault.startsWith("\"")) {
                    sb.append("<span class=\"str\">").append(escapeHtml(formattedDefault)).append("</span>");
                } else if ("true".equals(formattedDefault) || "false".equals(formattedDefault)) {
                    sb.append("<span class=\"bool\">").append(formattedDefault).append("</span>");
                } else {
                    sb.append("<span class=\"num\">").append(formattedDefault).append("</span>");
                }
                nameWithDefault = prop.getName() + " = " + formattedDefault;
            }

            // Comment (aligned)
            if (prop.getDescription() != null && !prop.getDescription().isEmpty()) {
                sb.append(" ".repeat(Math.max(1, maxNameLen - nameWithDefault.length() + 2)));
                sb.append("<span class=\"comment\">// ").append(escapeHtml(prop.getDescription())).append("</span>");
            }
            sb.append("\n");
        }

        sb.append("<span class=\"brace\">}</span>");
        return sb.toString();
    }

    private String getHighlightedValue(PropertyInfo prop) {
        if (prop.getDefaultValue() != null && !prop.getDefaultValue().isEmpty()) {
            return switch (prop.getType()) {
                case "string" -> "<span class=\"str\">\"" + escapeHtml(prop.getDefaultValue()) + "\"</span>";
                case "boolean" -> "<span class=\"bool\">" + prop.getDefaultValue() + "</span>";
                case "integer", "number" -> "<span class=\"num\">" + prop.getDefaultValue() + "</span>";
                default -> "<span class=\"str\">\"" + escapeHtml(prop.getDefaultValue()) + "\"</span>";
            };
        }
        if (prop.getValidValues() != null && !prop.getValidValues().isEmpty()) {
            return "<span class=\"str\">\"" + escapeHtml(prop.getValidValues().get(0)) + "\"</span>";
        }
        return switch (prop.getType()) {
            case "string" -> "<span class=\"str\">\"example-value\"</span>";
            case "integer" -> "<span class=\"num\">42</span>";
            case "boolean" -> "<span class=\"bool\">true</span>";
            case "number" -> "<span class=\"num\">3.14</span>";
            case "list" -> "<span class=\"brace\">[</span><span class=\"str\">\"item1\"</span>, <span class=\"str\">\"item2\"</span><span class=\"brace\">]</span>";
            case "map" -> "<span class=\"brace\">{</span> <span class=\"prop\">key</span><span class=\"colon\">:</span> <span class=\"str\">\"value\"</span> <span class=\"brace\">}</span>";
            default -> "<span class=\"str\">\"...\"</span>";
        };
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
