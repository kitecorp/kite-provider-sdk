package cloud.kitelang.provider.docgen;

import cloud.kitelang.provider.KiteProvider;
import cloud.kitelang.provider.ResourceTypeHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Generates HTML documentation for Kite provider resources.
 *
 * <p>Usage:</p>
 * <pre>{@code
 * var generator = new HtmlDocGenerator(provider);
 * generator.generate(Path.of("docs/html"));
 * }</pre>
 */
public class HtmlDocGenerator extends DocGeneratorBase {

    public HtmlDocGenerator(KiteProvider provider) {
        super(provider);
    }

    public HtmlDocGenerator(String providerName, String providerVersion,
                            Map<String, ResourceTypeHandler<?>> resourceTypes) {
        super(providerName, providerVersion, resourceTypes);
    }

    /**
     * Generates HTML documentation files.
     *
     * @param outputDir the directory to write files to
     */
    public void generate(Path outputDir) throws IOException {
        Files.createDirectories(outputDir);

        // Generate index.html
        var indexHtml = generateIndex();
        Files.writeString(outputDir.resolve("index.html"), indexHtml);

        // Generate individual resource pages with prev/next navigation
        for (int i = 0; i < resources.size(); i++) {
            var resource = resources.get(i);
            var prev = i > 0 ? resources.get(i - 1) : null;
            var next = i < resources.size() - 1 ? resources.get(i + 1) : null;
            var resourceHtml = generateResource(resource, prev, next);
            Files.writeString(outputDir.resolve(resource.getName() + ".html"), resourceHtml);
        }
    }

    private String generateIndex() {
        var sb = new StringBuilder();
        sb.append("""
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>%s Provider Documentation</title>
                %s
            </head>
            <body>
                <div class="container">
                    <header>
                        <h1>🪁 %s Provider</h1>
                        <p class="version">Version <strong>%s</strong></p>
                    </header>

                    <section class="search-section">
                        <input type="text" id="search" placeholder="Search resources..." class="search-input">
                    </section>

                    <h2>Resources <span class="badge badge-count">%d</span></h2>
            """.formatted(
                capitalize(providerInfo.getName()),
                getStyles(),
                capitalize(providerInfo.getName()),
                providerInfo.getVersion(),
                resources.size()
        ));

        // Group resources by domain
        var byDomain = groupByDomain();

        for (var entry : byDomain.entrySet()) {
            var domain = entry.getKey();
            var domainResources = entry.getValue();
            sb.append("""
                    <div class="domain-group" data-domain="%s">
                        <h3 class="domain-header" onclick="toggleDomain(this)">
                            <span class="domain-icon">%s</span>
                            %s
                            <span class="badge badge-count">%d</span>
                            <span class="collapse-icon">▼</span>
                        </h3>
                        <ul class="resource-list">
                """.formatted(
                    domain,
                    getDomainIcon(domain),
                    capitalize(domain),
                    domainResources.size()
                ));

            for (var resource : domainResources) {
                var cloudManagedCount = resource.getProperties().stream().filter(PropertyInfo::isCloudManaged).count();
                var userCount = resource.getProperties().size() - cloudManagedCount;
                sb.append("""
                            <li data-name="%s" data-domain="%s">
                                <a href="%s.html">%s</a>
                                <span class="property-count">%d properties</span>
                                <span class="property-breakdown">(%d user, %d cloud)</span>
                            </li>
                    """.formatted(
                        resource.getName().toLowerCase(),
                        domain,
                        resource.getName(),
                        resource.getName(),
                        resource.getProperties().size(),
                        userCount,
                        cloudManagedCount
                    ));
            }

            sb.append("""
                        </ul>
                    </div>
                """);
        }

        sb.append("""
                    <footer class="meta">
                        <p>Generated on %s</p>
                        <p>Powered by <a href="https://kitelang.cloud">Kite</a></p>
                    </footer>
                </div>
                <script>
                    function toggleDomain(header) {
                        const group = header.parentElement;
                        group.classList.toggle('collapsed');
                    }

                    document.getElementById('search').addEventListener('input', function(e) {
                        const query = e.target.value.toLowerCase();
                        document.querySelectorAll('.domain-group').forEach(group => {
                            let hasVisible = false;
                            group.querySelectorAll('li').forEach(li => {
                                const name = li.dataset.name;
                                const visible = name.includes(query);
                                li.style.display = visible ? '' : 'none';
                                if (visible) hasVisible = true;
                            });
                            group.style.display = hasVisible ? '' : 'none';
                            if (query && hasVisible) group.classList.remove('collapsed');
                        });
                    });
                </script>
            </body>
            </html>
            """.formatted(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))));

        return sb.toString();
    }

    private String generateResource(ResourceInfo resource, ResourceInfo prev, ResourceInfo next) {
        var sb = new StringBuilder();
        var domain = resource.getDomain() != null ? resource.getDomain() : "general";

        sb.append("""
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>%s - %s Provider</title>
                %s
            </head>
            <body>
                <div class="container">
                    <nav class="breadcrumbs">
                        <a href="index.html">%s</a>
                        <span class="separator">→</span>
                        <a href="index.html#%s">%s %s</a>
                        <span class="separator">→</span>
                        <span class="current">%s</span>
                    </nav>

                    <header>
                        <h1>%s</h1>
            """.formatted(
                resource.getName(),
                capitalize(providerInfo.getName()),
                getStyles(),
                capitalize(providerInfo.getName()),
                domain,
                getDomainIcon(domain),
                capitalize(domain),
                resource.getName(),
                resource.getName()
            ));

        if (resource.getDescription() != null && !resource.getDescription().isEmpty()) {
            sb.append("<p class=\"description\">").append(escapeHtml(resource.getDescription())).append("</p>\n");
        }
        sb.append("</header>\n");

        // Example with copy button
        sb.append("<h2>Example</h2>\n");
        sb.append("<div class=\"code-wrapper\">\n");
        sb.append("<button class=\"copy-btn\" onclick=\"copyCode(this)\">Copy</button>\n");
        sb.append("<div class=\"code-block\"><code>");
        sb.append(generateHighlightedExample(resource));
        sb.append("</code></div>\n");
        sb.append("</div>\n");

        // Collapsible schema section
        sb.append("<div class=\"schema-section collapsed\">\n");
        sb.append("<div class=\"schema-header\" onclick=\"toggleSchema(this)\">\n");
        sb.append("<span class=\"schema-toggle\">▶</span> Schema\n");
        sb.append("</div>\n");
        sb.append("<div class=\"schema-content\">\n");
        sb.append("<div class=\"code-wrapper\">\n");
        sb.append("<button class=\"copy-btn\" onclick=\"copyCode(this)\">Copy</button>\n");
        sb.append("<div class=\"code-block\"><code>");
        sb.append(generateHighlightedSchema(resource));
        sb.append("</code></div>\n");
        sb.append("</div>\n");
        sb.append("</div>\n");
        sb.append("</div>\n");

        // Properties tables
        var userProps = resource.getProperties().stream()
                .filter(p -> !p.isCloudManaged())
                .toList();
        var cloudProps = resource.getProperties().stream()
                .filter(PropertyInfo::isCloudManaged)
                .toList();

        if (!userProps.isEmpty()) {
            sb.append("<h2>Properties</h2>\n");
            sb.append(generatePropertiesTable(userProps, false));
        }

        if (!cloudProps.isEmpty()) {
            sb.append("<h2>Cloud Properties</h2>\n");
            sb.append("<p class=\"description\">These properties are set by the cloud provider and available after resource creation.</p>\n");
            sb.append(generatePropertiesTable(cloudProps, true));
        }

        // Previous/Next navigation
        sb.append("<nav class=\"resource-nav\">\n");
        if (prev != null) {
            sb.append("<a href=\"").append(prev.getName()).append(".html\" class=\"prev\">")
              .append(prev.getName()).append("</a>\n");
        } else {
            sb.append("<span class=\"placeholder\">←</span>\n");
        }
        if (next != null) {
            sb.append("<a href=\"").append(next.getName()).append(".html\" class=\"next\">")
              .append(next.getName()).append("</a>\n");
        } else {
            sb.append("<span class=\"placeholder\">→</span>\n");
        }
        sb.append("</nav>\n");

        sb.append("""
                    <footer class="meta">
                        <p><a href="index.html">← Back to Index</a></p>
                    </footer>
                </div>
                <script>
                    function copyCode(btn) {
                        const code = btn.parentElement.querySelector('code').textContent;
                        navigator.clipboard.writeText(code).then(() => {
                            btn.textContent = 'Copied!';
                            btn.classList.add('copied');
                            setTimeout(() => {
                                btn.textContent = 'Copy';
                                btn.classList.remove('copied');
                            }, 2000);
                        });
                    }

                    function toggleSchema(header) {
                        const section = header.parentElement;
                        section.classList.toggle('collapsed');
                    }

                    document.querySelectorAll('.sortable-header').forEach(header => {
                        header.addEventListener('click', () => {
                            const table = header.closest('table');
                            const tbody = table.querySelector('tbody');
                            const rows = Array.from(tbody.querySelectorAll('tr'));
                            const sortKey = header.dataset.sort;
                            const isAsc = header.classList.contains('asc');

                            table.querySelectorAll('.sortable-header').forEach(h => {
                                h.classList.remove('asc', 'desc');
                                h.querySelector('.sort-icon').textContent = '↕';
                            });

                            rows.sort((a, b) => {
                                let aVal = a.dataset[sortKey];
                                let bVal = b.dataset[sortKey];
                                if (sortKey === 'optional') {
                                    aVal = aVal === 'true' ? 1 : 0;
                                    bVal = bVal === 'true' ? 1 : 0;
                                    return isAsc ? bVal - aVal : aVal - bVal;
                                }
                                return isAsc ? bVal.localeCompare(aVal) : aVal.localeCompare(bVal);
                            });

                            header.classList.add(isAsc ? 'desc' : 'asc');
                            header.querySelector('.sort-icon').textContent = isAsc ? '↓' : '↑';
                            rows.forEach(row => tbody.appendChild(row));
                        });
                    });
                </script>
            </body>
            </html>
            """);

        return sb.toString();
    }

    private String generateHighlightedExample(ResourceInfo resource) {
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
            sb.append(getHighlightedExampleValue(prop));
            sb.append("\n");
        }

        sb.append("<span class=\"brace\">}</span>");
        return sb.toString();
    }

    private String generateHighlightedSchema(ResourceInfo resource) {
        var sb = new StringBuilder();

        // Header comment
        sb.append("<span class=\"comment\">// ").append(resource.getName()).append("</span>\n");

        // Calculate max property name length for alignment
        int maxLen = resource.getProperties().stream()
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
            // @allowed decorator for valid values
            if (prop.getValidValues() != null && !prop.getValidValues().isEmpty()) {
                sb.append("    <span class=\"decorator\">@allowed</span><span class=\"brace\">([</span>");
                sb.append(prop.getValidValues().stream()
                        .map(v -> "<span class=\"str\">\"" + escapeHtml(v) + "\"</span>")
                        .reduce((a, b) -> a + ", " + b)
                        .orElse(""));
                sb.append("<span class=\"brace\">])</span>\n");
            }

            // Cloud decorator for cloud-managed properties
            if (prop.isCloudManaged()) {
                if (prop.isImportable()) {
                    sb.append("    <span class=\"decorator\">@cloud</span><span class=\"brace\">(</span><span class=\"name\">importable</span><span class=\"brace\">)</span>\n");
                } else {
                    sb.append("    <span class=\"decorator\">@cloud</span>\n");
                }
            }

            // Type and name
            sb.append("    <span class=\"type\">").append(prop.getType()).append("</span> ");
            sb.append("<span class=\"prop\">").append(prop.getName()).append("</span>");

            // Default value assignment
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

            // Comment with description
            if (prop.getDescription() != null && !prop.getDescription().isEmpty()) {
                sb.append(" ".repeat(Math.max(1, maxLen - nameWithDefault.length() + 2)));
                sb.append("<span class=\"comment\">// ").append(escapeHtml(prop.getDescription())).append("</span>");
            }
            sb.append("\n");
        }

        sb.append("<span class=\"brace\">}</span>");
        return sb.toString();
    }

    private String getHighlightedExampleValue(PropertyInfo prop) {
        // Use default value if available
        if (prop.getDefaultValue() != null && !prop.getDefaultValue().isEmpty()) {
            return switch (prop.getType()) {
                case "string" -> "<span class=\"str\">\"" + escapeHtml(prop.getDefaultValue()) + "\"</span>";
                case "boolean" -> "<span class=\"bool\">" + prop.getDefaultValue() + "</span>";
                case "integer", "number" -> "<span class=\"num\">" + prop.getDefaultValue() + "</span>";
                default -> "<span class=\"str\">\"" + escapeHtml(prop.getDefaultValue()) + "\"</span>";
            };
        }
        // Use first valid value if available
        if (prop.getValidValues() != null && !prop.getValidValues().isEmpty()) {
            return "<span class=\"str\">\"" + escapeHtml(prop.getValidValues().get(0)) + "\"</span>";
        }
        return switch (prop.getType()) {
            case "string" -> "<span class=\"str\">\"example-value\"</span>";
            case "integer" -> "<span class=\"num\">42</span>";
            case "boolean" -> "<span class=\"bool\">true</span>";
            case "number" -> "<span class=\"num\">3.14</span>";
            case "list" -> "<span class=\"brace\">[</span><span class=\"str\">\"item1\"</span>, <span class=\"str\">\"item2\"</span><span class=\"brace\">]</span>";
            case "map" -> "<span class=\"brace\">{</span> <span class=\"prop\">key</span> <span class=\"eq\">=</span> <span class=\"str\">\"value\"</span> <span class=\"brace\">}</span>";
            default -> "<span class=\"str\">\"...\"</span>";
        };
    }

    private String generatePropertiesTable(List<PropertyInfo> properties, boolean isCloudSection) {
        var sb = new StringBuilder();
        // Cloud properties don't have defaults - they're set by the provider
        if (isCloudSection) {
            sb.append("""
                <table class="sortable">
                    <thead>
                        <tr>
                            <th data-sort="name" class="sortable-header">Name <span class="sort-icon">↕</span></th>
                            <th data-sort="type" class="sortable-header">Type <span class="sort-icon">↕</span></th>
                            <th>Description</th>
                        </tr>
                    </thead>
                    <tbody>
                """);
        } else {
            sb.append("""
                <table class="sortable">
                    <thead>
                        <tr>
                            <th data-sort="name" class="sortable-header">Name <span class="sort-icon">↕</span></th>
                            <th data-sort="type" class="sortable-header">Type <span class="sort-icon">↕</span></th>
                            <th>Default</th>
                            <th>Valid Values</th>
                            <th data-sort="optional" class="sortable-header">Optional <span class="sort-icon">↕</span></th>
                            <th>Description</th>
                        </tr>
                    </thead>
                    <tbody>
                """);
        }

        for (var prop : properties) {
            sb.append("<tr id=\"").append(prop.getName()).append("\" data-name=\"").append(prop.getName().toLowerCase())
              .append("\" data-type=\"").append(prop.getType())
              .append("\" data-optional=\"").append(!prop.isRequired()).append("\">\n");
            sb.append("  <td>");
            sb.append("<a href=\"#").append(prop.getName()).append("\" class=\"prop-name-link\">");
            sb.append("<span class=\"prop-name\">").append(prop.getName()).append("</span>");
            sb.append("<span class=\"anchor-icon\">#</span></a>");

            var hasBadges = (!isCloudSection && prop.isCloudManaged()) || prop.isImportable() || prop.isDeprecated();
            if (hasBadges) {
                sb.append("\n    <div class=\"prop-badges\">\n");
                if (!isCloudSection && prop.isCloudManaged()) {
                    sb.append("      <span class=\"badge badge-cloud\">cloud-managed</span>\n");
                }
                if (prop.isImportable()) {
                    sb.append("      <span class=\"badge badge-importable\">importable</span>\n");
                }
                if (prop.isDeprecated()) {
                    sb.append("      <span class=\"badge badge-deprecated\">deprecated</span>\n");
                }
                sb.append("    </div>");
            }

            sb.append("</td>\n");
            sb.append("  <td><span class=\"prop-type\">").append(prop.getType()).append("</span></td>\n");

            // Cloud properties only show Name, Type, Description
            if (!isCloudSection) {
                // Default value column
                sb.append("  <td>");
                if (prop.getDefaultValue() != null && !prop.getDefaultValue().isEmpty()) {
                    sb.append("<code class=\"default-value\">").append(escapeHtml(prop.getDefaultValue())).append("</code>");
                } else {
                    sb.append("<span class=\"no-value\">—</span>");
                }
                sb.append("</td>\n");

                // Valid values column
                sb.append("  <td>");
                if (prop.getValidValues() != null && !prop.getValidValues().isEmpty()) {
                    sb.append("<div class=\"valid-values\">");
                    for (var value : prop.getValidValues()) {
                        sb.append("<code class=\"valid-value\">").append(escapeHtml(value)).append("</code> ");
                    }
                    sb.append("</div>");
                } else {
                    sb.append("<span class=\"no-value\">—</span>");
                }
                sb.append("</td>\n");

                sb.append("  <td class=\"optional-cell\">");
                if (prop.isRequired()) {
                    sb.append("<span class=\"badge badge-required\">No</span>");
                } else {
                    sb.append("<span class=\"badge badge-optional\">Yes</span>");
                }
                sb.append("</td>\n");
            }

            // Description column
            sb.append("  <td>");
            if (prop.getDescription() != null && !prop.getDescription().isEmpty()) {
                sb.append(escapeHtml(prop.getDescription()));
            }
            if (prop.isImportable()) {
                sb.append(" <span class=\"badge badge-importable\">importable</span>");
            }
            if (prop.isDeprecated() && prop.getDeprecationMessage() != null) {
                sb.append("<br><em style=\"color: var(--kite-warning);\">Deprecated: ")
                  .append(escapeHtml(prop.getDeprecationMessage())).append("</em>");
            }
            sb.append("</td>\n");
            sb.append("</tr>\n");
        }

        sb.append("""
                </tbody>
            </table>
            """);
        return sb.toString();
    }

    private String getStyles() {
        return """
            <style>
                :root {
                    --kite-primary: #0ea5e9;
                    --kite-primary-dark: #0284c7;
                    --kite-success: #22c55e;
                    --kite-warning: #eab308;
                    --kite-error: #ef4444;
                    --kite-info: #06b6d4;
                    --kite-special: #a855f7;

                    --bg-primary: #ffffff;
                    --bg-secondary: #f8fafc;
                    --bg-code: #1e293b;
                    --text-primary: #0f172a;
                    --text-secondary: #64748b;
                    --text-code: #e2e8f0;
                    --border: #e2e8f0;
                }

                @media (prefers-color-scheme: dark) {
                    :root {
                        --bg-primary: #0f172a;
                        --bg-secondary: #1e293b;
                        --bg-code: #0f172a;
                        --text-primary: #f1f5f9;
                        --text-secondary: #94a3b8;
                        --text-code: #e2e8f0;
                        --border: #334155;
                    }
                }

                * { box-sizing: border-box; }

                body {
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                    background: var(--bg-primary);
                    color: var(--text-primary);
                    margin: 0;
                    padding: 0;
                    line-height: 1.6;
                }

                .container { max-width: 960px; margin: 0 auto; padding: 2rem; }

                header { margin-bottom: 2rem; padding-bottom: 1rem; border-bottom: 2px solid var(--kite-primary); }

                h1 { color: var(--kite-primary); margin: 0 0 0.5rem 0; font-size: 2rem; }
                h2 { color: var(--text-primary); margin-top: 2rem; font-size: 1.25rem; }

                .version { color: var(--text-secondary); margin: 0; }

                a { color: var(--kite-primary); text-decoration: none; }
                a:hover { text-decoration: underline; }

                .search-section { margin-bottom: 1.5rem; }
                .search-input {
                    width: 100%%;
                    padding: 0.75rem 1rem;
                    border: 1px solid var(--border);
                    border-radius: 0.5rem;
                    font-size: 1rem;
                    background: var(--bg-secondary);
                    color: var(--text-primary);
                }
                .search-input:focus {
                    outline: none;
                    border-color: var(--kite-primary);
                    box-shadow: 0 0 0 3px rgba(14, 165, 233, 0.1);
                }

                .resource-list { list-style: none; padding: 0; margin: 0; }
                .resource-list li {
                    padding: 1rem;
                    border: 1px solid var(--border);
                    border-radius: 0.5rem;
                    margin-bottom: 0.5rem;
                    background: var(--bg-secondary);
                    display: flex;
                    align-items: center;
                    gap: 1rem;
                    transition: border-color 0.15s;
                }
                .resource-list li:hover { border-color: var(--kite-primary); }
                .resource-list a { font-weight: 600; font-size: 1rem; }
                .property-count { color: var(--text-secondary); font-size: 0.875rem; }
                .property-breakdown { color: var(--text-secondary); font-size: 0.75rem; margin-left: auto; }

                .badge {
                    display: inline-flex;
                    align-items: center;
                    padding: 0.125rem 0.5rem;
                    border-radius: 9999px;
                    font-size: 0.75rem;
                    font-weight: 500;
                }
                .badge-count { background: rgba(14, 165, 233, 0.1); color: var(--kite-primary); }
                .badge-required { background: rgba(239, 68, 68, 0.1); color: var(--kite-error); }
                .badge-optional { background: rgba(34, 197, 94, 0.1); color: var(--kite-success); }
                .badge-cloud { background: rgba(6, 182, 212, 0.1); color: var(--kite-info); }
                .badge-deprecated { background: rgba(234, 179, 8, 0.1); color: var(--kite-warning); }
                .badge-importable { background: rgba(168, 85, 247, 0.1); color: var(--kite-special); }

                .code-block {
                    background: var(--bg-code);
                    border-radius: 0.5rem;
                    padding: 1.25rem;
                    overflow-x: auto;
                    font-family: 'SF Mono', 'Fira Code', 'Consolas', monospace;
                    font-size: 0.875rem;
                    line-height: 1.7;
                    white-space: pre;
                }
                .code-block code { color: var(--text-code); white-space: pre; display: block; }

                .kw { color: #c084fc; }
                .type { color: #38bdf8; }
                .name { color: #a5b4fc; }
                .prop { color: #f8fafc; }
                .eq { color: #64748b; }
                .str { color: #86efac; }
                .num { color: #fcd34d; }
                .bool { color: #fb923c; }
                .brace { color: #94a3b8; }
                .comment { color: #64748b; font-style: italic; }
                .decorator { color: #fb923c; }

                .schema-section {
                    margin-top: 1rem;
                    margin-bottom: 1.5rem;
                }
                .schema-header {
                    display: flex;
                    align-items: center;
                    gap: 0.5rem;
                    padding: 0.5rem 0;
                    cursor: pointer;
                    user-select: none;
                    color: var(--text-secondary);
                    font-size: 0.875rem;
                    font-weight: 500;
                    transition: color 0.15s;
                }
                .schema-header:hover { color: var(--kite-primary); }
                .schema-toggle {
                    font-size: 0.75rem;
                    transition: transform 0.2s;
                }
                .schema-section:not(.collapsed) .schema-toggle { transform: rotate(90deg); }
                .schema-section.collapsed .schema-content { display: none; }
                .schema-content { margin-top: 0.5rem; }

                table { width: 100%%; border-collapse: collapse; margin-top: 1rem; }
                th, td { text-align: left; padding: 0.75rem 1rem; border-bottom: 1px solid var(--border); }
                th {
                    background: var(--bg-secondary);
                    font-weight: 600;
                    color: var(--text-secondary);
                    font-size: 0.75rem;
                    text-transform: uppercase;
                    letter-spacing: 0.05em;
                }
                td { vertical-align: top; }
                .optional-cell { text-align: center; }

                .sortable-header { cursor: pointer; user-select: none; transition: color 0.15s; }
                .sortable-header:hover { color: var(--kite-primary); }
                .sort-icon { opacity: 0.4; margin-left: 0.25rem; font-size: 0.875rem; }
                .sortable-header.asc .sort-icon::after { content: '↑'; }
                .sortable-header.desc .sort-icon::after { content: '↓'; }
                .sortable-header.asc .sort-icon,
                .sortable-header.desc .sort-icon { opacity: 1; color: var(--kite-primary); }

                .prop-name { font-family: 'SF Mono', monospace; font-weight: 500; color: var(--kite-primary); }
                .default-value {
                    font-family: 'SF Mono', monospace;
                    font-size: 0.875rem;
                    background: rgba(34, 197, 94, 0.1);
                    color: var(--kite-success);
                    padding: 0.125rem 0.375rem;
                    border-radius: 0.25rem;
                }
                .valid-values { display: flex; flex-wrap: wrap; gap: 0.25rem; }
                .valid-value {
                    font-family: 'SF Mono', monospace;
                    font-size: 0.75rem;
                    background: rgba(14, 165, 233, 0.1);
                    color: var(--kite-primary);
                    padding: 0.125rem 0.375rem;
                    border-radius: 0.25rem;
                }
                .no-value { color: var(--text-secondary); }
                .prop-type {
                    font-family: 'SF Mono', monospace;
                    font-size: 0.875rem;
                    color: var(--kite-special);
                    background: rgba(168, 85, 247, 0.1);
                    padding: 0.125rem 0.375rem;
                    border-radius: 0.25rem;
                }
                .prop-badges { display: flex; gap: 0.25rem; flex-wrap: wrap; margin-top: 0.25rem; }

                .nav { margin-bottom: 1.5rem; }
                .nav a { display: inline-flex; align-items: center; gap: 0.25rem; color: var(--text-secondary); font-size: 0.875rem; }
                .nav a:hover { color: var(--kite-primary); }

                .meta {
                    margin-top: 3rem;
                    padding-top: 1rem;
                    border-top: 1px solid var(--border);
                    color: var(--text-secondary);
                    font-size: 0.875rem;
                }
                .meta p { margin: 0.25rem 0; }

                .domain-group { margin-bottom: 2rem; }
                .domain-header {
                    display: flex;
                    align-items: center;
                    gap: 0.5rem;
                    padding: 0.5rem 0;
                    border-bottom: 2px solid var(--border);
                    margin-bottom: 0.75rem;
                    cursor: pointer;
                    user-select: none;
                    font-size: 1.125rem;
                    font-weight: 600;
                    color: var(--text-primary);
                    transition: color 0.15s;
                }
                .domain-header:hover { color: var(--kite-primary); }
                .domain-icon { font-size: 1.25rem; }
                .collapse-icon { margin-left: auto; transition: transform 0.2s; color: var(--text-secondary); font-size: 0.875rem; }
                .domain-group.collapsed .collapse-icon { transform: rotate(-90deg); }
                .domain-group.collapsed .resource-list { display: none; }
                .resource-list { padding-left: 0.5rem; }

                .breadcrumbs {
                    display: flex;
                    align-items: center;
                    gap: 0.5rem;
                    font-size: 0.875rem;
                    color: var(--text-secondary);
                    margin-bottom: 1rem;
                }
                .breadcrumbs a { color: var(--text-secondary); }
                .breadcrumbs a:hover { color: var(--kite-primary); }
                .breadcrumbs .separator { color: var(--text-secondary); }
                .breadcrumbs .current { color: var(--text-primary); font-weight: 500; }

                thead th { position: sticky; top: 0; z-index: 10; }

                .code-wrapper { position: relative; }
                .copy-btn {
                    position: absolute;
                    top: 0.5rem;
                    right: 0.5rem;
                    padding: 0.375rem 0.75rem;
                    background: rgba(255, 255, 255, 0.1);
                    border: 1px solid rgba(255, 255, 255, 0.2);
                    border-radius: 0.375rem;
                    color: var(--text-code);
                    font-size: 0.75rem;
                    cursor: pointer;
                    opacity: 0;
                    transition: opacity 0.15s, background 0.15s;
                }
                .code-wrapper:hover .copy-btn { opacity: 1; }
                .copy-btn:hover { background: rgba(255, 255, 255, 0.2); }
                .copy-btn.copied { background: var(--kite-success); color: white; }

                .prop-name-link { color: var(--kite-primary); text-decoration: none; }
                .prop-name-link:hover { text-decoration: underline; }
                .anchor-icon { opacity: 0; margin-left: 0.25rem; color: var(--text-secondary); transition: opacity 0.15s; }
                tr:hover .anchor-icon { opacity: 1; }

                .resource-nav {
                    display: flex;
                    justify-content: space-between;
                    margin-top: 2rem;
                    padding-top: 1rem;
                    border-top: 1px solid var(--border);
                }
                .resource-nav a {
                    display: flex;
                    align-items: center;
                    gap: 0.5rem;
                    padding: 0.5rem 1rem;
                    background: var(--bg-secondary);
                    border: 1px solid var(--border);
                    border-radius: 0.5rem;
                    font-size: 0.875rem;
                    transition: border-color 0.15s;
                }
                .resource-nav a:hover { border-color: var(--kite-primary); text-decoration: none; }
                .resource-nav .prev::before { content: '←'; }
                .resource-nav .next::after { content: '→'; }
                .resource-nav .placeholder { visibility: hidden; }

                @media print {
                    body { background: white; color: black; }
                    .container { max-width: 100%%; padding: 0; }
                    .nav, .copy-btn, .resource-nav, .search-section { display: none; }
                    .code-block { background: #f5f5f5; color: black; border: 1px solid #ddd; }
                    a { color: black; text-decoration: underline; }
                    thead th { position: static; }
                    .domain-group.collapsed .resource-list { display: block; }
                }
            </style>
            """;
    }
}
