package cloud.kitelang.provider.docgen;

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
                    .defaultValue(prop.defaultValue())
                    .validValues(prop.validValues())
                    .build());
        }

        return ResourceInfo.builder()
                .name(typeName)
                .domain(extractDomain(handler.getClass()))
                .description(schema.getDescription())
                .properties(properties)
                .build();
    }

    /**
     * Extracts the domain from a handler's package name.
     * e.g., "cloud.kitelang.provider.aws.networking.VpcResourceType" -> "networking"
     */
    private String extractDomain(Class<?> handlerClass) {
        var packageName = handlerClass.getPackageName();
        var parts = packageName.split("\\.");
        // Look for domain package after provider name (aws, azure, gcp, etc.)
        // Pattern: cloud.kitelang.provider.<provider>.<domain>
        if (parts.length >= 5) {
            var lastPart = parts[parts.length - 1];
            // Check if it's a known domain category
            if (isKnownDomain(lastPart)) {
                return lastPart;
            }
        }
        return null;  // No domain grouping
    }

    private boolean isKnownDomain(String name) {
        return switch (name) {
            case "networking", "compute", "storage", "dns", "loadbalancing",
                 "database", "security", "monitoring", "core", "container" -> true;
            default -> false;
        };
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

        // Generate individual resource pages with prev/next navigation
        for (int i = 0; i < resources.size(); i++) {
            var resource = resources.get(i);
            var prev = i > 0 ? resources.get(i - 1) : null;
            var next = i < resources.size() - 1 ? resources.get(i + 1) : null;
            var resourceHtml = generateHtmlResource(resource, prev, next);
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

    /**
     * Generates Kite schema files (.kite) for each resource type.
     * Files are organized by domain (networking/, compute/, storage/, etc.)
     *
     * @param outputDir the directory to write files to
     */
    public void generateKite(Path outputDir) throws IOException {
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

            // Generate individual schema files in domain subdirectory
            for (var resource : domainResources) {
                var kiteSchema = generateKiteSchema(resource);
                Files.writeString(domainDir.resolve(resource.getName() + ".kite"), kiteSchema);
            }
        }
    }

    /**
     * Generates a Kite schema definition for a resource.
     */
    private String generateKiteSchema(ResourceInfo resource) {
        var sb = new StringBuilder();

        // Header comment with description
        sb.append("// ").append(resource.getName()).append(" schema\n");
        sb.append("// Provider: ").append(providerInfo.getName()).append("\n\n");

        // Calculate max property name length for alignment
        int maxLen = resource.getProperties().stream()
                .mapToInt(p -> p.getName().length())
                .max()
                .orElse(0);

        sb.append("schema ").append(resource.getName()).append(" {\n");

        for (var prop : resource.getProperties()) {
            // Cloud decorator for cloud-managed properties
            if (prop.isCloudManaged()) {
                sb.append("    @cloud\n");
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

    private String generateHtmlIndex() {
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
                getSharedStyles(),
                capitalize(providerInfo.getName()),
                providerInfo.getVersion(),
                resources.size()
        ));

        // Group resources by domain
        var byDomain = resources.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getDomain() != null ? r.getDomain() : "general",
                        LinkedHashMap::new,
                        Collectors.toList()));

        // Sort domains: known domains first, then alphabetically
        var sortedDomains = byDomain.keySet().stream()
                .sorted((a, b) -> {
                    int aOrder = getDomainOrder(a);
                    int bOrder = getDomainOrder(b);
                    if (aOrder != bOrder) return aOrder - bOrder;
                    return a.compareTo(b);
                })
                .toList();

        for (var domain : sortedDomains) {
            var domainResources = byDomain.get(domain);
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

    private int getDomainOrder(String domain) {
        return switch (domain) {
            case "networking" -> 1;
            case "compute" -> 2;
            case "storage" -> 3;
            case "database" -> 4;
            case "dns" -> 5;
            case "loadbalancing" -> 6;
            case "security" -> 7;
            case "monitoring" -> 8;
            case "container" -> 9;
            case "core" -> 10;
            default -> 99;
        };
    }

    private String getDomainIcon(String domain) {
        return switch (domain) {
            case "networking" -> "🌐";
            case "compute" -> "💻";
            case "storage" -> "💾";
            case "database" -> "🗄️";
            case "dns" -> "📍";
            case "loadbalancing" -> "⚖️";
            case "security" -> "🔒";
            case "monitoring" -> "📊";
            case "container" -> "📦";
            case "core" -> "⚙️";
            default -> "📄";
        };
    }

    /**
     * Returns shared CSS styles used by both index and resource pages.
     */
    private String getSharedStyles() {
        return """
            <style>
                /* Kite color palette - matching engine/CLI colors */
                :root {
                    --kite-primary: #0ea5e9;      /* cyan - primary brand */
                    --kite-primary-dark: #0284c7;
                    --kite-success: #22c55e;      /* green - add/success */
                    --kite-warning: #eab308;      /* yellow - change/warning */
                    --kite-error: #ef4444;        /* red - remove/error */
                    --kite-info: #06b6d4;         /* cyan - info/existing */
                    --kite-special: #a855f7;      /* magenta - replace/special */

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

                .container {
                    max-width: 960px;
                    margin: 0 auto;
                    padding: 2rem;
                }

                header {
                    margin-bottom: 2rem;
                    padding-bottom: 1rem;
                    border-bottom: 2px solid var(--kite-primary);
                }

                h1 {
                    color: var(--kite-primary);
                    margin: 0 0 0.5rem 0;
                    font-size: 2rem;
                }

                h2 {
                    color: var(--text-primary);
                    margin-top: 2rem;
                    font-size: 1.25rem;
                }

                .version {
                    color: var(--text-secondary);
                    margin: 0;
                }

                a { color: var(--kite-primary); text-decoration: none; }
                a:hover { text-decoration: underline; }

                /* Search */
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

                /* Resource list */
                .resource-list {
                    list-style: none;
                    padding: 0;
                    margin: 0;
                }
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
                .resource-list li:hover {
                    border-color: var(--kite-primary);
                }
                .resource-list a {
                    font-weight: 600;
                    font-size: 1rem;
                }
                .property-count {
                    color: var(--text-secondary);
                    font-size: 0.875rem;
                }
                .property-breakdown {
                    color: var(--text-secondary);
                    font-size: 0.75rem;
                    margin-left: auto;
                }

                /* Badges */
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

                /* Code blocks with syntax highlighting */
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
                .code-block code {
                    color: var(--text-code);
                    white-space: pre;
                    display: block;
                }
                /* Syntax highlighting - Kite language */
                .kw { color: #c084fc; }              /* keywords: resource, provider */
                .type { color: #38bdf8; }            /* type names: Vpc, Subnet */
                .name { color: #a5b4fc; }            /* resource names: main, example */
                .prop { color: #f8fafc; }            /* property names */
                .eq { color: #64748b; }              /* equals sign */
                .str { color: #86efac; }             /* strings */
                .num { color: #fcd34d; }             /* numbers */
                .bool { color: #fb923c; }            /* booleans */
                .brace { color: #94a3b8; }           /* braces */
                .comment { color: #64748b; font-style: italic; }

                /* Properties table */
                table {
                    width: 100%%;
                    border-collapse: collapse;
                    margin-top: 1rem;
                }
                th, td {
                    text-align: left;
                    padding: 0.75rem 1rem;
                    border-bottom: 1px solid var(--border);
                }
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

                /* Sortable table headers */
                .sortable-header {
                    cursor: pointer;
                    user-select: none;
                    transition: color 0.15s;
                }
                .sortable-header:hover {
                    color: var(--kite-primary);
                }
                .sort-icon {
                    opacity: 0.4;
                    margin-left: 0.25rem;
                    font-size: 0.875rem;
                }
                .sortable-header.asc .sort-icon::after { content: '↑'; }
                .sortable-header.desc .sort-icon::after { content: '↓'; }
                .sortable-header.asc .sort-icon,
                .sortable-header.desc .sort-icon { opacity: 1; color: var(--kite-primary); }
                .prop-name {
                    font-family: 'SF Mono', monospace;
                    font-weight: 500;
                    color: var(--kite-primary);
                }
                .default-value {
                    font-family: 'SF Mono', monospace;
                    font-size: 0.875rem;
                    background: rgba(34, 197, 94, 0.1);
                    color: var(--kite-success);
                    padding: 0.125rem 0.375rem;
                    border-radius: 0.25rem;
                }
                .valid-values {
                    display: flex;
                    flex-wrap: wrap;
                    gap: 0.25rem;
                }
                .valid-value {
                    font-family: 'SF Mono', monospace;
                    font-size: 0.75rem;
                    background: rgba(14, 165, 233, 0.1);
                    color: var(--kite-primary);
                    padding: 0.125rem 0.375rem;
                    border-radius: 0.25rem;
                }
                .no-value {
                    color: var(--text-secondary);
                }
                .prop-type {
                    font-family: 'SF Mono', monospace;
                    font-size: 0.875rem;
                    color: var(--kite-special);
                    background: rgba(168, 85, 247, 0.1);
                    padding: 0.125rem 0.375rem;
                    border-radius: 0.25rem;
                }
                .prop-badges {
                    display: flex;
                    gap: 0.25rem;
                    flex-wrap: wrap;
                    margin-top: 0.25rem;
                }

                /* Navigation */
                .nav {
                    margin-bottom: 1.5rem;
                }
                .nav a {
                    display: inline-flex;
                    align-items: center;
                    gap: 0.25rem;
                    color: var(--text-secondary);
                    font-size: 0.875rem;
                }
                .nav a:hover { color: var(--kite-primary); }

                /* Footer */
                .meta {
                    margin-top: 3rem;
                    padding-top: 1rem;
                    border-top: 1px solid var(--border);
                    color: var(--text-secondary);
                    font-size: 0.875rem;
                }
                .meta p { margin: 0.25rem 0; }

                /* Domain groups */
                .domain-group {
                    margin-bottom: 1.5rem;
                }
                .domain-header {
                    display: flex;
                    align-items: center;
                    gap: 0.5rem;
                    padding: 0.75rem 1rem;
                    background: var(--bg-secondary);
                    border: 1px solid var(--border);
                    border-radius: 0.5rem;
                    margin-bottom: 0.5rem;
                    cursor: pointer;
                    user-select: none;
                    font-size: 1rem;
                    transition: border-color 0.15s;
                }
                .domain-header:hover { border-color: var(--kite-primary); }
                .domain-icon { font-size: 1.25rem; }
                .collapse-icon {
                    margin-left: auto;
                    transition: transform 0.2s;
                    color: var(--text-secondary);
                }
                .domain-group.collapsed .collapse-icon { transform: rotate(-90deg); }
                .domain-group.collapsed .resource-list { display: none; }

                /* Breadcrumbs */
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

                /* Sticky table headers */
                thead th {
                    position: sticky;
                    top: 0;
                    z-index: 10;
                }

                /* Code block with copy button */
                .code-wrapper {
                    position: relative;
                }
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

                /* Property anchors */
                .prop-name-link {
                    color: var(--kite-primary);
                    text-decoration: none;
                }
                .prop-name-link:hover { text-decoration: underline; }
                .anchor-icon {
                    opacity: 0;
                    margin-left: 0.25rem;
                    color: var(--text-secondary);
                    transition: opacity 0.15s;
                }
                tr:hover .anchor-icon { opacity: 1; }

                /* Previous/Next navigation */
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

                /* Print styles */
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

    private String generateHtmlResource(ResourceInfo resource, ResourceInfo prev, ResourceInfo next) {
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
                getSharedStyles(),
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

        // Example usage with syntax highlighting, aligned "=", and copy button
        sb.append("<h2>Example</h2>\n");
        sb.append("<div class=\"code-wrapper\">\n");
        sb.append("<button class=\"copy-btn\" onclick=\"copyCode(this)\">Copy</button>\n");
        sb.append("<div class=\"code-block\"><code>");
        sb.append(generateHighlightedExample(resource));
        sb.append("</code></div>\n");
        sb.append("</div>\n");

        // Properties table - split into user-configurable and cloud-managed
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
                    // Copy code button
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

                    // Sortable table headers
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

    /**
     * Generates syntax-highlighted example code with vertically aligned "=" signs.
     */
    private String generateHighlightedExample(ResourceInfo resource) {
        var sb = new StringBuilder();
        var props = resource.getProperties().stream()
                .filter(p -> !p.isCloudManaged() && !p.isDeprecated())
                .toList();

        // Calculate max property name length for alignment
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
            // Add padding for alignment
            sb.append(" ".repeat(maxLen - prop.getName().length()));
            sb.append(" <span class=\"eq\">=</span> ");
            sb.append(getHighlightedExampleValue(prop));
            sb.append("\n");
        }

        sb.append("<span class=\"brace\">}</span>");
        return sb.toString();
    }

    /**
     * Returns an example value with appropriate syntax highlighting.
     */
    private String getHighlightedExampleValue(PropertyInfo prop) {
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

    /**
     * Generates an HTML table for a list of properties with sortable columns.
     *
     * @param properties the properties to display
     * @param isCloudSection true if this table is for Cloud Properties (hides redundant cloud badge)
     */
    private String generatePropertiesTable(List<PropertyInfo> properties, boolean isCloudSection) {
        var sb = new StringBuilder();
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

        for (var prop : properties) {
            sb.append("<tr id=\"").append(prop.getName()).append("\" data-name=\"").append(prop.getName().toLowerCase())
              .append("\" data-type=\"").append(prop.getType())
              .append("\" data-optional=\"").append(!prop.isRequired()).append("\">\n");
            sb.append("  <td>");
            sb.append("<a href=\"#").append(prop.getName()).append("\" class=\"prop-name-link\">");
            sb.append("<span class=\"prop-name\">").append(prop.getName()).append("</span>");
            sb.append("<span class=\"anchor-icon\">#</span></a>");

            // Only show badges if there are any to show (excluding cloud-managed badge in cloud section)
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
            sb.append("  <td>");

            if (prop.getDescription() != null && !prop.getDescription().isEmpty()) {
                sb.append(escapeHtml(prop.getDescription()));
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

        // Example with aligned "=" signs
        sb.append("## Example\n\n");
        sb.append("```kite\n");
        sb.append(generateAlignedMarkdownExample(resource));
        sb.append("```\n\n");

        // Split properties into user-configurable and cloud-managed
        var userProps = resource.getProperties().stream()
                .filter(p -> !p.isCloudManaged())
                .toList();
        var cloudProps = resource.getProperties().stream()
                .filter(PropertyInfo::isCloudManaged)
                .toList();

        // User properties
        if (!userProps.isEmpty()) {
            sb.append("## Properties\n\n");
            sb.append(generateMarkdownPropertiesTable(userProps));
        }

        // Cloud Properties
        if (!cloudProps.isEmpty()) {
            sb.append("## Cloud Properties\n\n");
            sb.append("_These properties are set by the cloud provider after resource creation._\n\n");
            sb.append(generateMarkdownPropertiesTable(cloudProps));
        }

        sb.append("\n[← Back to Index](README.md)\n");

        return sb.toString();
    }

    /**
     * Generates a Markdown example code block with vertically aligned "=" signs.
     */
    private String generateAlignedMarkdownExample(ResourceInfo resource) {
        var sb = new StringBuilder();
        var props = resource.getProperties().stream()
                .filter(p -> !p.isCloudManaged() && !p.isDeprecated())
                .toList();

        // Calculate max property name length for alignment
        int maxLen = props.stream()
                .mapToInt(p -> p.getName().length())
                .max()
                .orElse(0);

        sb.append("resource ").append(resource.getName()).append(" example {\n");

        for (var prop : props) {
            sb.append("    ").append(prop.getName());
            // Add padding for alignment
            sb.append(" ".repeat(maxLen - prop.getName().length()));
            sb.append(" = ").append(getExampleValue(prop)).append("\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Generates a Markdown properties table.
     */
    private String generateMarkdownPropertiesTable(List<PropertyInfo> properties) {
        var sb = new StringBuilder();
        sb.append("| Name | Type | Default | Valid Values | Required | Description |\n");
        sb.append("|------|------|---------|--------------|----------|-------------|\n");

        for (var prop : properties) {
            var badges = new ArrayList<String>();
            if (prop.isCloudManaged()) badges.add("☁️ cloud-managed");
            if (prop.isImportable()) badges.add("📥 importable");
            if (prop.isDeprecated()) badges.add("⚠️ deprecated");

            var desc = prop.getDescription() != null ? prop.getDescription() : "";
            if (!badges.isEmpty()) {
                desc = "*" + String.join(", ", badges) + "* " + desc;
            }

            // Default value
            var defaultVal = prop.getDefaultValue() != null && !prop.getDefaultValue().isEmpty()
                    ? "`" + prop.getDefaultValue() + "`"
                    : "—";

            // Valid values
            var validVals = prop.getValidValues() != null && !prop.getValidValues().isEmpty()
                    ? prop.getValidValues().stream().map(v -> "`" + v + "`").collect(Collectors.joining(", "))
                    : "—";

            sb.append("| `").append(prop.getName()).append("` | `").append(prop.getType()).append("` | ")
              .append(defaultVal).append(" | ").append(validVals).append(" | ")
              .append(prop.isRequired() ? "Yes" : "No").append(" | ")
              .append(desc.replace("|", "\\|")).append(" |\n");
        }
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
        private String domain;  // e.g., "networking", "compute", "storage"
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
        private String defaultValue;
        private List<String> validValues;
    }
}
