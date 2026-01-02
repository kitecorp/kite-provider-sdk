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
 * Generates SEO-friendly HTML documentation for Kite provider resources.
 * Creates individual pages for each resource with Terraform-style layout:
 * - Left sidebar: scrollable navigation with categories
 * - Center: resource content
 * - Right sidebar: table of contents for current resource
 *
 * SEO features:
 * - Separate HTML page per resource (crawlable URLs)
 * - Meta tags (title, description, Open Graph)
 * - JSON-LD structured data
 * - Sitemap.xml generation
 * - Semantic HTML structure
 *
 * <p>Usage:</p>
 * <pre>{@code
 * var generator = new HtmlDocGenerator(provider);
 * generator.generate(Path.of("docs/html"));
 * }</pre>
 */
public class HtmlDocGenerator extends DocGeneratorBase {

    private static final String BASE_URL = "https://docs.kitelang.cloud";

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

        // Generate individual resource pages
        for (var resource : resources) {
            var resourceHtml = generateResourcePage(resource);
            Files.writeString(outputDir.resolve(resource.getName() + ".html"), resourceHtml);
        }

        // Generate sitemap.xml
        var sitemap = generateSitemap();
        Files.writeString(outputDir.resolve("sitemap.xml"), sitemap);
    }

    private String generateIndex() {
        var sb = new StringBuilder();
        var byDomain = groupByDomain();
        var description = "Complete documentation for " + capitalize(providerInfo.getName()) +
                " provider resources. Learn how to configure and manage " + resources.size() +
                " infrastructure resources with Kite.";

        sb.append(generateHtmlHead(
                capitalize(providerInfo.getName()) + " Provider Documentation | Kite",
                description,
                "index.html"
        ));

        sb.append("""
            <body>
                <div class="layout">
            """);

        // Left sidebar navigation
        sb.append(generateNavigation(null));

        // Main content - welcome page
        sb.append("""
                    <main class="content">
                        <article class="resource-content">
                            <h1>%s Provider Documentation</h1>
                            <p class="welcome-text">Infrastructure as code resources for %s. Select a resource from the navigation to view its documentation.</p>

                            <div class="quick-stats">
                                <div class="stat-card">
                                    <span class="stat-value">%d</span>
                                    <span class="stat-label">Resources</span>
                                </div>
                                <div class="stat-card">
                                    <span class="stat-value">%d</span>
                                    <span class="stat-label">Categories</span>
                                </div>
                            </div>

                            <h2 id="categories">Categories</h2>
                            <div class="category-grid">
            """.formatted(
                capitalize(providerInfo.getName()),
                capitalize(providerInfo.getName()),
                resources.size(),
                byDomain.size()
            ));

        for (var entry : byDomain.entrySet()) {
            var domain = entry.getKey();
            var domainResources = entry.getValue();
            var firstResource = domainResources.isEmpty() ? null : domainResources.get(0);
            sb.append("""
                                <a href="%s.html" class="category-card">
                                    <span class="card-icon">%s</span>
                                    <span class="card-name">%s</span>
                                    <span class="card-count">%d resources</span>
                                </a>
                """.formatted(
                    firstResource != null ? firstResource.getName() : "#",
                    getDomainIcon(domain),
                    capitalize(domain),
                    domainResources.size()
                ));
        }

        sb.append("""
                            </div>

                            <h2 id="all-resources">All Resources</h2>
                            <div class="resource-grid">
            """);

        for (var resource : resources) {
            var domain = resource.getDomain() != null ? resource.getDomain() : "general";
            sb.append("""
                                <a href="%s.html" class="resource-card">
                                    <span class="resource-icon">%s</span>
                                    <span class="resource-name">%s</span>
                                    <span class="resource-props">%d properties</span>
                                </a>
                """.formatted(
                    resource.getName(),
                    getDomainIcon(domain),
                    resource.getName(),
                    resource.getProperties().size()
                ));
        }

        sb.append("""
                            </div>
                        </article>
                    </main>

                    <aside class="sidebar-right">
                        <div class="toc-header">On this page</div>
                        <nav class="toc">
                            <a href="#categories">Categories</a>
                            <a href="#all-resources">All Resources</a>
                        </nav>
                    </aside>
                </div>
                %s
            </body>
            </html>
            """.formatted(generateScript()));

        return sb.toString();
    }

    private String generateResourcePage(ResourceInfo resource) {
        var sb = new StringBuilder();
        var domain = resource.getDomain() != null ? resource.getDomain() : "general";

        var description = resource.getDescription() != null && !resource.getDescription().isEmpty()
                ? resource.getDescription()
                : "Documentation for " + resource.getName() + " resource in " +
                  capitalize(providerInfo.getName()) + " provider. Learn about properties, examples, and configuration.";

        sb.append(generateHtmlHead(
                resource.getName() + " | " + capitalize(providerInfo.getName()) + " Provider | Kite",
                description,
                resource.getName() + ".html"
        ));

        // JSON-LD structured data
        sb.append(generateJsonLd(resource));

        sb.append("""
            <body>
                <div class="layout">
            """);

        // Left sidebar navigation
        sb.append(generateNavigation(resource.getName()));

        // Main content
        sb.append("""
                    <main class="content">
                        <article class="resource-content" itemscope itemtype="https://schema.org/TechArticle">
                            <nav class="breadcrumbs" aria-label="Breadcrumb">
                                <a href="index.html">Home</a>
                                <span class="sep" aria-hidden="true">›</span>
                                <span class="domain">%s %s</span>
                                <span class="sep" aria-hidden="true">›</span>
                                <span class="current" aria-current="page">%s</span>
                            </nav>

                            <header>
                                <h1 itemprop="name">%s</h1>
            """.formatted(
                getDomainIcon(domain),
                capitalize(domain),
                resource.getName(),
                resource.getName()
            ));

        if (resource.getDescription() != null && !resource.getDescription().isEmpty()) {
            sb.append("<p class=\"resource-desc\" itemprop=\"description\">")
              .append(escapeHtml(resource.getDescription())).append("</p>\n");
        }

        sb.append("</header>\n");

        // Example section
        sb.append("""
                            <section id="example">
                                <h2>Example</h2>
                                <div class="code-wrapper">
                                    <button class="copy-btn" onclick="copyCode(this)" aria-label="Copy code">Copy</button>
                                    <pre class="code-block"><code itemprop="text">%s</code></pre>
                                </div>
                            </section>
            """.formatted(generateHighlightedExample(resource)));

        // Schema section
        sb.append("""
                            <section id="schema" class="schema-section">
                                <h3 class="schema-toggle" onclick="toggleSchema(this)" role="button" aria-expanded="false">
                                    <span class="toggle-icon" aria-hidden="true">▶</span> Schema Definition
                                </h3>
                                <div class="schema-content">
                                    <div class="code-wrapper">
                                        <button class="copy-btn" onclick="copyCode(this)" aria-label="Copy schema">Copy</button>
                                        <pre class="code-block"><code>%s</code></pre>
                                    </div>
                                </div>
                            </section>
            """.formatted(generateHighlightedSchema(resource)));

        // Properties tables
        var userProps = resource.getProperties().stream()
                .filter(p -> !p.isCloudManaged())
                .toList();
        var cloudProps = resource.getProperties().stream()
                .filter(PropertyInfo::isCloudManaged)
                .toList();

        if (!userProps.isEmpty()) {
            sb.append("<section id=\"properties\">\n");
            sb.append("<h2>Properties</h2>\n");
            sb.append(generatePropertiesTable(userProps, false));
            sb.append("</section>\n");
        }

        if (!cloudProps.isEmpty()) {
            sb.append("<section id=\"cloud-properties\">\n");
            sb.append("<h2>Cloud Properties</h2>\n");
            sb.append("<p class=\"cloud-desc\">Read-only properties set by the cloud provider after resource creation.</p>\n");
            sb.append(generatePropertiesTable(cloudProps, true));
            sb.append("</section>\n");
        }

        // Navigation to other resources
        var resourceIndex = resources.indexOf(resource);
        var prev = resourceIndex > 0 ? resources.get(resourceIndex - 1) : null;
        var next = resourceIndex < resources.size() - 1 ? resources.get(resourceIndex + 1) : null;

        sb.append("<nav class=\"resource-nav\" aria-label=\"Resource navigation\">\n");
        if (prev != null) {
            sb.append("<a href=\"").append(prev.getName()).append(".html\" class=\"nav-prev\" rel=\"prev\">")
              .append("← ").append(prev.getName()).append("</a>\n");
        } else {
            sb.append("<span class=\"nav-placeholder\"></span>\n");
        }
        if (next != null) {
            sb.append("<a href=\"").append(next.getName()).append(".html\" class=\"nav-next\" rel=\"next\">")
              .append(next.getName()).append(" →</a>\n");
        } else {
            sb.append("<span class=\"nav-placeholder\"></span>\n");
        }
        sb.append("</nav>\n");

        sb.append("""
                            <footer class="resource-footer">
                                <p>Generated on <time datetime="%s">%s</time></p>
                            </footer>
                        </article>
                    </main>
            """.formatted(
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))
            ));

        // Right sidebar TOC
        sb.append("""
                    <aside class="sidebar-right">
                        <div class="toc-header">On this page</div>
                        <nav class="toc" aria-label="Table of contents">
                            <a href="#example">Example</a>
                            <a href="#schema" class="toc-h3">Schema Definition</a>
            """);

        if (!userProps.isEmpty()) {
            sb.append("<a href=\"#properties\">Properties</a>\n");
        }
        if (!cloudProps.isEmpty()) {
            sb.append("<a href=\"#cloud-properties\">Cloud Properties</a>\n");
        }

        sb.append("""
                        </nav>
                    </aside>
                </div>
                %s
            </body>
            </html>
            """.formatted(generateScript()));

        return sb.toString();
    }

    private String generateNavigation(String currentResource) {
        var sb = new StringBuilder();
        var byDomain = groupByDomain();

        sb.append("""
                    <aside class="sidebar-left">
                        <div class="sidebar-header">
                            <a href="index.html" class="logo-link">
                                <h1>🪁 %s</h1>
                            </a>
                            <span class="version">v%s</span>
                        </div>
                        <div class="search-wrapper">
                            <input type="search" id="search" placeholder="Search resources..." class="search-input" aria-label="Search resources">
                        </div>
                        <nav class="nav-tree" aria-label="Resources navigation">
            """.formatted(capitalize(providerInfo.getName()), providerInfo.getVersion()));

        for (var entry : byDomain.entrySet()) {
            var domain = entry.getKey();
            var domainResources = entry.getValue();
            var hasActive = currentResource != null &&
                    domainResources.stream().anyMatch(r -> r.getName().equals(currentResource));

            sb.append("""
                            <div class="nav-category%s" data-domain="%s">
                                <div class="category-header" onclick="toggleCategory(this)" role="button" aria-expanded="%s">
                                    <span class="category-icon" aria-hidden="true">%s</span>
                                    <span class="category-name">%s</span>
                                    <span class="category-count">%d</span>
                                    <span class="category-arrow" aria-hidden="true">▾</span>
                                </div>
                                <ul class="category-items">
                """.formatted(
                    hasActive ? "" : " collapsed",
                    domain,
                    hasActive ? "true" : "false",
                    getDomainIcon(domain),
                    capitalize(domain),
                    domainResources.size()
                ));

            for (var resource : domainResources) {
                var isActive = resource.getName().equals(currentResource);
                sb.append("""
                                    <li class="nav-item%s" data-name="%s">
                                        <a href="%s.html"%s>%s</a>
                                    </li>
                    """.formatted(
                        isActive ? " active" : "",
                        resource.getName().toLowerCase(),
                        resource.getName(),
                        isActive ? " aria-current=\"page\"" : "",
                        resource.getName()
                    ));
            }

            sb.append("""
                                </ul>
                            </div>
                """);
        }

        sb.append("""
                        </nav>
                    </aside>
            """);

        return sb.toString();
    }

    private String generateHtmlHead(String title, String description, String canonicalPath) {
        var providerName = providerInfo.getName().toLowerCase();
        var canonicalUrl = BASE_URL + "/" + providerName + "/" + canonicalPath;

        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>%s</title>
                <meta name="description" content="%s">
                <meta name="keywords" content="kite, infrastructure as code, %s, cloud, terraform alternative, IaC">
                <meta name="author" content="Kite">
                <meta name="robots" content="index, follow">

                <!-- Canonical URL -->
                <link rel="canonical" href="%s">

                <!-- Open Graph -->
                <meta property="og:type" content="website">
                <meta property="og:title" content="%s">
                <meta property="og:description" content="%s">
                <meta property="og:url" content="%s">
                <meta property="og:site_name" content="Kite Documentation">

                <!-- Twitter Card -->
                <meta name="twitter:card" content="summary">
                <meta name="twitter:title" content="%s">
                <meta name="twitter:description" content="%s">

                %s
            </head>
            """.formatted(
                escapeHtml(title),
                escapeHtml(description),
                providerName,
                canonicalUrl,
                escapeHtml(title),
                escapeHtml(description),
                canonicalUrl,
                escapeHtml(title),
                escapeHtml(description),
                getStyles()
            );
    }

    private String generateJsonLd(ResourceInfo resource) {
        var domain = resource.getDomain() != null ? resource.getDomain() : "general";
        var providerName = providerInfo.getName().toLowerCase();

        return """
            <script type="application/ld+json">
            {
                "@context": "https://schema.org",
                "@type": "TechArticle",
                "headline": "%s Resource Documentation",
                "description": "%s",
                "author": {
                    "@type": "Organization",
                    "name": "Kite"
                },
                "publisher": {
                    "@type": "Organization",
                    "name": "Kite",
                    "url": "https://kitelang.cloud"
                },
                "mainEntityOfPage": {
                    "@type": "WebPage",
                    "@id": "%s/%s/%s.html"
                },
                "about": {
                    "@type": "SoftwareSourceCode",
                    "name": "%s",
                    "programmingLanguage": "Kite",
                    "codeRepository": "https://github.com/kitelang/kite"
                },
                "articleSection": "%s",
                "keywords": ["kite", "%s", "%s", "infrastructure as code", "cloud"]
            }
            </script>
            """.formatted(
                resource.getName(),
                escapeHtml(resource.getDescription() != null ? resource.getDescription() : ""),
                BASE_URL,
                providerName,
                resource.getName(),
                resource.getName(),
                capitalize(domain),
                providerName,
                resource.getName().toLowerCase()
            );
    }

    private String generateSitemap() {
        var sb = new StringBuilder();
        var providerName = providerInfo.getName().toLowerCase();
        var now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        sb.append("""
            <?xml version="1.0" encoding="UTF-8"?>
            <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                <url>
                    <loc>%s/%s/index.html</loc>
                    <lastmod>%s</lastmod>
                    <changefreq>weekly</changefreq>
                    <priority>1.0</priority>
                </url>
            """.formatted(BASE_URL, providerName, now));

        for (var resource : resources) {
            sb.append("""
                <url>
                    <loc>%s/%s/%s.html</loc>
                    <lastmod>%s</lastmod>
                    <changefreq>weekly</changefreq>
                    <priority>0.8</priority>
                </url>
                """.formatted(BASE_URL, providerName, resource.getName(), now));
        }

        sb.append("</urlset>\n");
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

        sb.append("<span class=\"comment\">// ").append(resource.getName()).append("</span>\n");

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

            sb.append("    <span class=\"type\">").append(prop.getType()).append("</span> ");
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
            case "map" -> "<span class=\"brace\">{</span> <span class=\"prop\">key</span> <span class=\"eq\">=</span> <span class=\"str\">\"value\"</span> <span class=\"brace\">}</span>";
            default -> "<span class=\"str\">\"...\"</span>";
        };
    }

    private String generatePropertiesTable(List<PropertyInfo> properties, boolean isCloudSection) {
        var sb = new StringBuilder();

        if (isCloudSection) {
            sb.append("""
                <div class="table-wrapper">
                <table>
                    <thead>
                        <tr>
                            <th scope="col">Name</th>
                            <th scope="col">Type</th>
                            <th scope="col">Description</th>
                        </tr>
                    </thead>
                    <tbody>
                """);
        } else {
            sb.append("""
                <div class="table-wrapper">
                <table>
                    <thead>
                        <tr>
                            <th scope="col">Name</th>
                            <th scope="col">Type</th>
                            <th scope="col">Default</th>
                            <th scope="col">Required</th>
                            <th scope="col">Description</th>
                        </tr>
                    </thead>
                    <tbody>
                """);
        }

        for (var prop : properties) {
            sb.append("<tr id=\"prop-").append(prop.getName()).append("\">\n");
            sb.append("  <td><code class=\"prop-name\">").append(prop.getName()).append("</code>");
            if (prop.isDeprecated()) {
                sb.append(" <span class=\"badge badge-deprecated\">deprecated</span>");
            }
            sb.append("</td>\n");
            sb.append("  <td><code class=\"prop-type\">").append(prop.getType()).append("</code></td>\n");

            if (!isCloudSection) {
                sb.append("  <td>");
                if (prop.getDefaultValue() != null && !prop.getDefaultValue().isEmpty()) {
                    sb.append("<code class=\"default-value\">").append(escapeHtml(prop.getDefaultValue())).append("</code>");
                } else {
                    sb.append("<span class=\"no-value\">—</span>");
                }
                sb.append("</td>\n");

                sb.append("  <td>");
                if (prop.isRequired()) {
                    sb.append("<span class=\"badge badge-required\">Yes</span>");
                } else {
                    sb.append("<span class=\"badge badge-optional\">No</span>");
                }
                sb.append("</td>\n");
            }

            sb.append("  <td>");
            if (prop.getDescription() != null && !prop.getDescription().isEmpty()) {
                sb.append(escapeHtml(prop.getDescription()));
            }
            if (prop.getValidValues() != null && !prop.getValidValues().isEmpty()) {
                sb.append("<br><span class=\"valid-values-label\">Valid values: </span>");
                sb.append(prop.getValidValues().stream()
                        .map(v -> "<code class=\"valid-value\">" + escapeHtml(v) + "</code>")
                        .reduce((a, b) -> a + " " + b)
                        .orElse(""));
            }
            sb.append("</td>\n");
            sb.append("</tr>\n");
        }

        sb.append("""
                </tbody>
            </table>
            </div>
            """);
        return sb.toString();
    }

    private String generateScript() {
        return """
            <script>
                function toggleCategory(header) {
                    const category = header.parentElement;
                    const isCollapsed = category.classList.toggle('collapsed');
                    header.setAttribute('aria-expanded', !isCollapsed);
                }

                function toggleSchema(header) {
                    const section = header.parentElement;
                    const isExpanded = section.classList.toggle('expanded');
                    header.setAttribute('aria-expanded', isExpanded);
                }

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

                // Search functionality
                document.getElementById('search')?.addEventListener('input', function(e) {
                    const query = e.target.value.toLowerCase();

                    document.querySelectorAll('.nav-category').forEach(category => {
                        let hasVisible = false;

                        category.querySelectorAll('.nav-item').forEach(item => {
                            const name = item.dataset.name;
                            const visible = name.includes(query);
                            item.style.display = visible ? '' : 'none';
                            if (visible) hasVisible = true;
                        });

                        category.style.display = hasVisible ? '' : 'none';

                        if (query && hasVisible) {
                            category.classList.remove('collapsed');
                        }
                    });
                });

                // Highlight current TOC item on scroll
                const observerOptions = {
                    rootMargin: '-20% 0px -80% 0px'
                };

                const observer = new IntersectionObserver((entries) => {
                    entries.forEach(entry => {
                        const id = entry.target.getAttribute('id');
                        const tocLink = document.querySelector(`.toc a[href="#${id}"]`);
                        if (tocLink) {
                            if (entry.isIntersecting) {
                                document.querySelectorAll('.toc a').forEach(a => a.classList.remove('active'));
                                tocLink.classList.add('active');
                            }
                        }
                    });
                }, observerOptions);

                document.querySelectorAll('section[id], h2[id]').forEach(section => {
                    observer.observe(section);
                });
            </script>
            """;
    }

    private String getStyles() {
        return """
            <style>
                :root {
                    --kite-primary: #7c3aed;
                    --kite-primary-light: #a78bfa;
                    --kite-primary-dark: #5b21b6;
                    --kite-accent: #06b6d4;
                    --kite-success: #10b981;
                    --kite-warning: #f59e0b;
                    --kite-error: #ef4444;

                    --bg-body: #f8fafc;
                    --bg-sidebar: #ffffff;
                    --bg-content: #ffffff;
                    --bg-code: #1e1e2e;
                    --bg-hover: #f1f5f9;

                    --text-primary: #1e293b;
                    --text-secondary: #64748b;
                    --text-muted: #94a3b8;
                    --text-code: #e2e8f0;

                    --border-color: #e2e8f0;
                    --shadow-sm: 0 1px 2px rgba(0,0,0,0.05);
                    --shadow-md: 0 4px 6px rgba(0,0,0,0.07);

                    --sidebar-width: 280px;
                    --toc-width: 220px;
                }

                @media (prefers-color-scheme: dark) {
                    :root {
                        --bg-body: #0f172a;
                        --bg-sidebar: #1e293b;
                        --bg-content: #1e293b;
                        --bg-code: #0f172a;
                        --bg-hover: #334155;

                        --text-primary: #f1f5f9;
                        --text-secondary: #94a3b8;
                        --text-muted: #64748b;

                        --border-color: #334155;
                    }
                }

                * { box-sizing: border-box; margin: 0; padding: 0; }

                body {
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', sans-serif;
                    background: var(--bg-body);
                    color: var(--text-primary);
                    line-height: 1.6;
                }

                .layout {
                    display: grid;
                    grid-template-columns: var(--sidebar-width) 1fr var(--toc-width);
                    min-height: 100vh;
                }

                /* Left Sidebar */
                .sidebar-left {
                    background: var(--bg-sidebar);
                    border-right: 1px solid var(--border-color);
                    position: sticky;
                    top: 0;
                    height: 100vh;
                    overflow-y: auto;
                    display: flex;
                    flex-direction: column;
                }

                .sidebar-header {
                    padding: 1.5rem;
                    border-bottom: 1px solid var(--border-color);
                }

                .logo-link { text-decoration: none; }
                .logo-link:hover { text-decoration: none; }

                .sidebar-header h1 {
                    font-size: 1.25rem;
                    color: var(--kite-primary);
                    margin-bottom: 0.25rem;
                }

                .version {
                    font-size: 0.75rem;
                    color: var(--text-muted);
                    background: var(--bg-hover);
                    padding: 0.125rem 0.5rem;
                    border-radius: 9999px;
                }

                .search-wrapper {
                    padding: 1rem 1.5rem;
                    border-bottom: 1px solid var(--border-color);
                }

                .search-input {
                    width: 100%%;
                    padding: 0.5rem 0.75rem;
                    border: 1px solid var(--border-color);
                    border-radius: 0.375rem;
                    font-size: 0.875rem;
                    background: var(--bg-body);
                    color: var(--text-primary);
                }

                .search-input:focus {
                    outline: none;
                    border-color: var(--kite-primary);
                    box-shadow: 0 0 0 3px rgba(124, 58, 237, 0.1);
                }

                .nav-tree {
                    flex: 1;
                    overflow-y: auto;
                    padding: 1rem 0;
                }

                .nav-category { margin-bottom: 0.5rem; }

                .category-header {
                    display: flex;
                    align-items: center;
                    gap: 0.5rem;
                    padding: 0.5rem 1.5rem;
                    cursor: pointer;
                    font-weight: 500;
                    color: var(--text-primary);
                    transition: background 0.15s;
                }

                .category-header:hover { background: var(--bg-hover); }

                .category-icon { font-size: 1rem; }
                .category-name { flex: 1; font-size: 0.875rem; }
                .category-count {
                    font-size: 0.75rem;
                    color: var(--text-muted);
                    background: var(--bg-hover);
                    padding: 0.125rem 0.5rem;
                    border-radius: 9999px;
                }
                .category-arrow {
                    font-size: 0.75rem;
                    color: var(--text-muted);
                    transition: transform 0.2s;
                }

                .nav-category.collapsed .category-arrow { transform: rotate(-90deg); }
                .nav-category.collapsed .category-items { display: none; }

                .category-items { list-style: none; }

                .nav-item a {
                    display: block;
                    padding: 0.375rem 1.5rem 0.375rem 3rem;
                    color: var(--text-secondary);
                    text-decoration: none;
                    font-size: 0.875rem;
                    transition: all 0.15s;
                    border-left: 2px solid transparent;
                }

                .nav-item a:hover {
                    background: var(--bg-hover);
                    color: var(--text-primary);
                }

                .nav-item.active a {
                    background: rgba(124, 58, 237, 0.1);
                    color: var(--kite-primary);
                    border-left-color: var(--kite-primary);
                    font-weight: 500;
                }

                /* Main Content */
                .content {
                    background: var(--bg-content);
                    padding: 2rem 3rem;
                    min-width: 0;
                }

                .resource-content { max-width: 100%%; }

                .breadcrumbs {
                    display: flex;
                    align-items: center;
                    gap: 0.5rem;
                    font-size: 0.875rem;
                    color: var(--text-muted);
                    margin-bottom: 1.5rem;
                }

                .breadcrumbs a {
                    color: var(--text-secondary);
                    text-decoration: none;
                }

                .breadcrumbs a:hover { color: var(--kite-primary); }
                .breadcrumbs .sep { color: var(--text-muted); }
                .breadcrumbs .current { color: var(--text-primary); font-weight: 500; }

                h1 {
                    font-size: 2rem;
                    margin-bottom: 0.5rem;
                    color: var(--text-primary);
                }

                h2 {
                    font-size: 1.25rem;
                    margin: 2rem 0 1rem;
                    padding-bottom: 0.5rem;
                    border-bottom: 1px solid var(--border-color);
                    color: var(--text-primary);
                }

                .resource-desc, .cloud-desc, .welcome-text {
                    color: var(--text-secondary);
                    margin-bottom: 1.5rem;
                }

                .welcome-text { font-size: 1.125rem; }

                .quick-stats {
                    display: flex;
                    gap: 1.5rem;
                    margin-bottom: 2rem;
                }

                .stat-card {
                    background: var(--bg-hover);
                    padding: 1.5rem 2rem;
                    border-radius: 0.5rem;
                    text-align: center;
                }

                .stat-value {
                    display: block;
                    font-size: 2rem;
                    font-weight: 700;
                    color: var(--kite-primary);
                }

                .stat-label {
                    font-size: 0.875rem;
                    color: var(--text-secondary);
                }

                .category-grid, .resource-grid {
                    display: grid;
                    grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
                    gap: 1rem;
                    margin-top: 1rem;
                }

                .category-card, .resource-card {
                    display: block;
                    background: var(--bg-hover);
                    padding: 1.25rem;
                    border-radius: 0.5rem;
                    text-decoration: none;
                    color: var(--text-primary);
                    transition: all 0.15s;
                    border: 1px solid transparent;
                }

                .category-card:hover, .resource-card:hover {
                    border-color: var(--kite-primary);
                    transform: translateY(-2px);
                    box-shadow: var(--shadow-md);
                }

                .card-icon, .resource-icon { font-size: 1.5rem; display: block; margin-bottom: 0.5rem; }
                .card-name, .resource-name { font-weight: 600; display: block; margin-bottom: 0.25rem; }
                .card-count, .resource-props { font-size: 0.75rem; color: var(--text-muted); }

                /* Code blocks */
                .code-wrapper {
                    position: relative;
                    margin: 1rem 0;
                }

                .code-block {
                    background: var(--bg-code);
                    border-radius: 0.5rem;
                    padding: 1.25rem;
                    overflow-x: auto;
                    font-family: 'SF Mono', 'Fira Code', 'Consolas', monospace;
                    font-size: 0.875rem;
                    line-height: 1.7;
                    color: var(--text-code);
                    margin: 0;
                }

                .code-block code {
                    white-space: pre;
                    display: block;
                }

                .copy-btn {
                    position: absolute;
                    top: 0.5rem;
                    right: 0.5rem;
                    padding: 0.375rem 0.75rem;
                    background: rgba(255, 255, 255, 0.1);
                    border: 1px solid rgba(255, 255, 255, 0.2);
                    border-radius: 0.25rem;
                    color: var(--text-code);
                    font-size: 0.75rem;
                    cursor: pointer;
                    opacity: 0;
                    transition: all 0.15s;
                }

                .code-wrapper:hover .copy-btn { opacity: 1; }
                .copy-btn:hover { background: rgba(255, 255, 255, 0.2); }
                .copy-btn.copied { background: var(--kite-success); color: white; }

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

                /* Schema section */
                .schema-section { margin: 1.5rem 0; }

                .schema-toggle {
                    display: flex;
                    align-items: center;
                    gap: 0.5rem;
                    cursor: pointer;
                    font-size: 0.875rem;
                    font-weight: 500;
                    color: var(--text-secondary);
                    padding: 0.5rem 0;
                    border: none;
                    background: none;
                }

                .schema-toggle:hover { color: var(--kite-primary); }

                .toggle-icon {
                    font-size: 0.75rem;
                    transition: transform 0.2s;
                }

                .schema-section.expanded .toggle-icon { transform: rotate(90deg); }
                .schema-content { display: none; }
                .schema-section.expanded .schema-content { display: block; }

                /* Tables */
                .table-wrapper {
                    overflow-x: auto;
                    margin: 1rem 0;
                }

                table {
                    width: 100%%;
                    border-collapse: collapse;
                    font-size: 0.875rem;
                }

                th, td {
                    text-align: left;
                    padding: 0.75rem 1rem;
                    border-bottom: 1px solid var(--border-color);
                }

                th {
                    background: var(--bg-hover);
                    font-weight: 600;
                    color: var(--text-secondary);
                    font-size: 0.75rem;
                    text-transform: uppercase;
                    letter-spacing: 0.05em;
                }

                tr:hover { background: var(--bg-hover); }

                .prop-name { color: var(--kite-primary); font-weight: 500; }

                .prop-type {
                    color: var(--kite-accent);
                    background: rgba(6, 182, 212, 0.1);
                    padding: 0.125rem 0.375rem;
                    border-radius: 0.25rem;
                }

                .default-value {
                    background: rgba(16, 185, 129, 0.1);
                    color: var(--kite-success);
                    padding: 0.125rem 0.375rem;
                    border-radius: 0.25rem;
                }

                .valid-values-label { font-size: 0.75rem; color: var(--text-muted); }

                .valid-value {
                    background: rgba(124, 58, 237, 0.1);
                    color: var(--kite-primary);
                    padding: 0.125rem 0.375rem;
                    border-radius: 0.25rem;
                    font-size: 0.75rem;
                }

                .no-value { color: var(--text-muted); }

                .badge {
                    display: inline-block;
                    padding: 0.125rem 0.5rem;
                    border-radius: 9999px;
                    font-size: 0.75rem;
                    font-weight: 500;
                }

                .badge-required { background: rgba(239, 68, 68, 0.1); color: var(--kite-error); }
                .badge-optional { background: rgba(16, 185, 129, 0.1); color: var(--kite-success); }
                .badge-deprecated { background: rgba(245, 158, 11, 0.1); color: var(--kite-warning); margin-left: 0.5rem; }

                /* Resource navigation */
                .resource-nav {
                    display: flex;
                    justify-content: space-between;
                    margin-top: 2rem;
                    padding-top: 1.5rem;
                    border-top: 1px solid var(--border-color);
                }

                .nav-prev, .nav-next {
                    padding: 0.75rem 1.25rem;
                    background: var(--bg-hover);
                    border: 1px solid var(--border-color);
                    border-radius: 0.5rem;
                    text-decoration: none;
                    color: var(--text-primary);
                    font-size: 0.875rem;
                    transition: all 0.15s;
                }

                .nav-prev:hover, .nav-next:hover {
                    border-color: var(--kite-primary);
                    color: var(--kite-primary);
                }

                .nav-placeholder { width: 120px; }

                .resource-footer {
                    margin-top: 3rem;
                    padding-top: 1rem;
                    border-top: 1px solid var(--border-color);
                    color: var(--text-muted);
                    font-size: 0.75rem;
                }

                /* Right Sidebar: TOC */
                .sidebar-right {
                    background: var(--bg-sidebar);
                    border-left: 1px solid var(--border-color);
                    position: sticky;
                    top: 0;
                    height: 100vh;
                    overflow-y: auto;
                    padding: 1.5rem;
                }

                .toc-header {
                    font-size: 0.75rem;
                    font-weight: 600;
                    text-transform: uppercase;
                    letter-spacing: 0.05em;
                    color: var(--text-muted);
                    margin-bottom: 1rem;
                }

                .toc { font-size: 0.875rem; }

                .toc a {
                    display: block;
                    padding: 0.375rem 0;
                    color: var(--text-secondary);
                    text-decoration: none;
                    border-left: 2px solid transparent;
                    padding-left: 0.75rem;
                    margin-left: -0.75rem;
                    transition: all 0.15s;
                }

                .toc a:hover { color: var(--kite-primary); }

                .toc a.active {
                    color: var(--kite-primary);
                    border-left-color: var(--kite-primary);
                }

                .toc a.toc-h3 {
                    padding-left: 1.5rem;
                    font-size: 0.8125rem;
                }

                /* Responsive */
                @media (max-width: 1200px) {
                    .sidebar-right { display: none; }
                    .layout { grid-template-columns: var(--sidebar-width) 1fr; }
                }

                @media (max-width: 768px) {
                    .sidebar-left {
                        position: fixed;
                        left: -100%%;
                        width: 100%%;
                        max-width: 300px;
                        z-index: 100;
                        transition: left 0.3s;
                    }

                    .sidebar-left.open { left: 0; }
                    .layout { grid-template-columns: 1fr; }
                    .content { padding: 1.5rem; }
                }

                /* Print styles */
                @media print {
                    .sidebar-left, .sidebar-right, .copy-btn, .resource-nav { display: none; }
                    .layout { display: block; }
                    .content { padding: 0; }
                    .code-block { background: #f5f5f5; color: #333; border: 1px solid #ddd; }
                }
            </style>
            """;
    }
}
