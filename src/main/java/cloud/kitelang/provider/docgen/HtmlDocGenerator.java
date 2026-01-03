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

        // Generate shared CSS file (cached by browser)
        Files.writeString(outputDir.resolve("styles.css"), generateStyles());

        // Generate shared JavaScript file (cached by browser)
        Files.writeString(outputDir.resolve("scripts.js"), generateScripts());

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

        // Generate robots.txt
        Files.writeString(outputDir.resolve("robots.txt"), generateRobotsTxt());

        // Generate RSS feed
        Files.writeString(outputDir.resolve("feed.xml"), generateRssFeed());

        // Generate OpenSearch description
        Files.writeString(outputDir.resolve("opensearch.xml"), generateOpenSearch());
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

        // SoftwareApplication schema for the provider
        sb.append(generateIndexJsonLd());

        sb.append("""
            <body>
                <a href="#main-content" class="skip-link">Skip to main content</a>
                <button class="mobile-menu-btn" onclick="toggleMobileMenu()" aria-label="Toggle navigation menu">
                    <span class="hamburger-icon"></span>
                </button>
                <div class="mobile-overlay" onclick="toggleMobileMenu()"></div>
                <div class="layout">
            """);

        // Left sidebar navigation
        sb.append(generateNavigation(null));

        // Main content - welcome page
        sb.append("""
                    <main id="main-content" class="content">
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

                            <h2 id="quick-start">Quick Start</h2>
                            <div class="quick-start-section">
                                <h3>Installation</h3>
                                <p>Install the %s provider:</p>
                                <div class="code-wrapper">
                                    <button class="copy-btn" onclick="copyCode(this)" aria-label="Copy code">Copy</button>
                                    <pre class="code-block"><code><span class="kw">kite</span> <span class="name">providers</span> <span class="str">install</span> %s@%s</code></pre>
                                </div>

                                <h3>Import in your .kite file</h3>
                                <div class="code-wrapper">
                                    <button class="copy-btn" onclick="copyCode(this)" aria-label="Copy code">Copy</button>
                                    <pre class="code-block"><code><span class="kw">import</span> <span class="name">*</span> <span class="kw">from</span> <span class="str">"%s"</span></code></pre>
                                </div>

                                <h3>Define a resource</h3>
                                <div class="code-wrapper">
                                    <button class="copy-btn" onclick="copyCode(this)" aria-label="Copy code">Copy</button>
                                    <pre class="code-block"><code><span class="kw">resource</span> <span class="type">%s</span> <span class="name">myResource</span> <span class="brace">{</span>
    <span class="comment">// Configure properties here</span>
<span class="brace">}</span></code></pre>
                                </div>
                            </div>

                            <h2 id="categories">Categories</h2>
                            <div class="category-grid">
            """.formatted(
                capitalize(providerInfo.getName()),
                capitalize(providerInfo.getName()),
                resources.size(),
                byDomain.size(),
                capitalize(providerInfo.getName()),
                providerInfo.getName().toLowerCase(),
                providerInfo.getVersion(),
                providerInfo.getName().toLowerCase(),
                resources.isEmpty() ? "ResourceType" : resources.get(0).getName()
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
                            <a href="#quick-start">Quick Start</a>
                            <a href="#categories">Categories</a>
                            <a href="#all-resources">All Resources</a>
                        </nav>
                    </aside>
                </div>
                <button class="back-to-top" onclick="scrollToTop()" aria-label="Back to top" title="Back to top">↑</button>
                <div id="toast" class="toast" role="alert" aria-live="polite"></div>
            </body>
            </html>
            """);

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
                <a href="#main-content" class="skip-link">Skip to main content</a>
                <button class="mobile-menu-btn" onclick="toggleMobileMenu()" aria-label="Toggle navigation menu">
                    <span class="hamburger-icon"></span>
                </button>
                <div class="mobile-overlay" onclick="toggleMobileMenu()"></div>
                <div class="layout">
            """);

        // Left sidebar navigation
        sb.append(generateNavigation(resource.getName()));

        // Main content
        sb.append("""
                    <main id="main-content" class="content">
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

        // Import section
        var importPath = "%s/%s/%s.kite".formatted(
                providerInfo.getName().toLowerCase(),
                domain.toLowerCase(),
                resource.getName()
        );
        sb.append("""
                            <section id="import">
                                <h2>Import</h2>
                                <div class="code-wrapper">
                                    <button class="copy-btn" onclick="copyCode(this)" aria-label="Copy code">Copy</button>
                                    <pre class="code-block"><code><span class="kw">import</span> <span class="type">%s</span> <span class="kw">from</span> <span class="str">"%s"</span></code></pre>
                                </div>
                            </section>
            """.formatted(resource.getName(), importPath));

        // Example section with tabs
        sb.append("""
                            <section id="example">
                                <h2>Examples</h2>
                                <div class="example-tabs">
                                    <button class="example-tab active" onclick="showExample(this, 'basic')">Basic</button>
                                    <button class="example-tab" onclick="showExample(this, 'references')">With References</button>
                                    <button class="example-tab" onclick="showExample(this, 'complete')">Complete</button>
                                </div>
                                <div class="example-content active" id="example-basic">
                                    <div class="code-wrapper">
                                        <button class="copy-btn" onclick="copyCode(this)" aria-label="Copy code">Copy</button>
                                        <pre class="code-block"><code itemprop="text">%s</code></pre>
                                    </div>
                                </div>
                                <div class="example-content" id="example-references">
                                    <div class="code-wrapper">
                                        <button class="copy-btn" onclick="copyCode(this)" aria-label="Copy code">Copy</button>
                                        <pre class="code-block"><code>%s</code></pre>
                                    </div>
                                </div>
                                <div class="example-content" id="example-complete">
                                    <div class="code-wrapper">
                                        <button class="copy-btn" onclick="copyCode(this)" aria-label="Copy code">Copy</button>
                                        <pre class="code-block"><code>%s</code></pre>
                                    </div>
                                </div>
                            </section>
            """.formatted(
                generateHighlightedExample(resource),
                generateReferencesExample(resource, domain),
                generateCompleteExample(resource)
            ));

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

        // Related resources in the same domain
        var relatedResources = resources.stream()
                .filter(r -> domain.equals(r.getDomain() != null ? r.getDomain() : "general"))
                .filter(r -> !r.getName().equals(resource.getName()))
                .limit(5)
                .toList();

        if (!relatedResources.isEmpty()) {
            sb.append("<section id=\"related\" class=\"related-section\">\n");
            sb.append("<h2>Related Resources</h2>\n");
            sb.append("<p class=\"related-desc\">Other resources in the ").append(capitalize(domain)).append(" category:</p>\n");
            sb.append("<div class=\"related-grid\">\n");
            for (var related : relatedResources) {
                sb.append("<a href=\"").append(related.getName()).append(".html\" class=\"related-card\">");
                sb.append("<span class=\"related-icon\">").append(getDomainIcon(domain)).append("</span>");
                sb.append("<span class=\"related-name\">").append(related.getName()).append("</span>");
                sb.append("</a>\n");
            }
            sb.append("</div>\n");
            sb.append("</section>\n");
        }

        // Navigation to other resources in the same category
        var domainResources = resources.stream()
                .filter(r -> domain.equals(r.getDomain() != null ? r.getDomain() : "general"))
                .toList();
        var domainIndex = domainResources.indexOf(resource);
        var prev = domainIndex > 0 ? domainResources.get(domainIndex - 1) : null;
        var next = domainIndex < domainResources.size() - 1 ? domainResources.get(domainIndex + 1) : null;

        sb.append("<nav class=\"resource-nav\" aria-label=\"Resource navigation\">\n");
        if (prev != null) {
            sb.append("<a href=\"").append(prev.getName()).append(".html\" class=\"nav-prev\" rel=\"prev\" title=\"Previous in ").append(capitalize(domain)).append("\">")
              .append("← ").append(prev.getName()).append("</a>\n");
        } else {
            sb.append("<span class=\"nav-placeholder\"></span>\n");
        }
        sb.append("<span class=\"nav-category-label\">").append(capitalize(domain)).append("</span>\n");
        if (next != null) {
            sb.append("<a href=\"").append(next.getName()).append(".html\" class=\"nav-next\" rel=\"next\" title=\"Next in ").append(capitalize(domain)).append("\">")
              .append(next.getName()).append(" →</a>\n");
        } else {
            sb.append("<span class=\"nav-placeholder\"></span>\n");
        }
        sb.append("</nav>\n");

        var pageUrl = BASE_URL + "/" + providerInfo.getName().toLowerCase() + "/" + resource.getName() + ".html";
        var pageTitle = resource.getName() + " - Kite " + capitalize(providerInfo.getName()) + " Provider";
        var issueTitle = "Docs: " + resource.getName() + " - ";
        var issueBody = "**Resource:** " + resource.getName() + "%0A**Provider:** " + providerInfo.getName() + "%0A**Page:** " + pageUrl + "%0A%0A**Issue:**%0A";

        sb.append("""
                            <footer class="resource-footer">
                                <div class="footer-actions">
                                    <div class="share-buttons">
                                        <span class="share-label">Share:</span>
                                        <a href="https://twitter.com/intent/tweet?text=%s&url=%s" target="_blank" rel="noopener" class="share-btn share-twitter" title="Share on Twitter">
                                            <span>𝕏</span>
                                        </a>
                                        <a href="https://www.linkedin.com/sharing/share-offsite/?url=%s" target="_blank" rel="noopener" class="share-btn share-linkedin" title="Share on LinkedIn">
                                            <span>in</span>
                                        </a>
                                    </div>
                                    <a href="https://github.com/kitecorp/kite-providers/issues/new?title=%s&body=%s" target="_blank" rel="noopener" class="report-issue">
                                        Report an issue
                                    </a>
                                </div>
                                <p class="footer-date">Generated on <time datetime="%s">%s</time></p>
                            </footer>
                        </article>
                    </main>
            """.formatted(
                escapeHtml(pageTitle).replace(" ", "%20"),
                pageUrl,
                pageUrl,
                escapeHtml(issueTitle).replace(" ", "%20"),
                issueBody,
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))
            ));

        // Right sidebar TOC
        sb.append("""
                    <aside class="sidebar-right">
                        <div class="toc-header">On this page</div>
                        <nav class="toc" aria-label="Table of contents">
                            <a href="#import">Import</a>
                            <a href="#example">Example</a>
                            <a href="#schema" class="toc-h3">Schema Definition</a>
            """);

        if (!userProps.isEmpty()) {
            sb.append("<a href=\"#properties\">Properties</a>\n");
        }
        if (!cloudProps.isEmpty()) {
            sb.append("<a href=\"#cloud-properties\">Cloud Properties</a>\n");
        }
        if (!relatedResources.isEmpty()) {
            sb.append("<a href=\"#related\">Related Resources</a>\n");
        }

        sb.append("""
                        </nav>
                    </aside>
                </div>
                <button class="back-to-top" onclick="scrollToTop()" aria-label="Back to top" title="Back to top">↑</button>
                <div id="toast" class="toast" role="alert" aria-live="polite"></div>
            </body>
            </html>
            """);

        return sb.toString();
    }

    private String generateNavigation(String currentResource) {
        var sb = new StringBuilder();
        var byDomain = groupByDomain();

        sb.append("""
                    <aside class="sidebar-left">
                        <div class="sidebar-header">
                            <div class="header-top">
                                <a href="index.html" class="logo-link">
                                    <h1>🪁 %s</h1>
                                </a>
                                <button class="theme-toggle" onclick="toggleTheme()" aria-label="Toggle dark/light mode" title="Toggle theme">
                                    <span class="theme-icon-light">☀️</span>
                                    <span class="theme-icon-dark">🌙</span>
                                </button>
                            </div>
                            <span class="version">v%s</span>
                        </div>
                        <div class="search-wrapper">
                            <div class="search-container">
                                <input type="search" id="search" placeholder="Search resources..." class="search-input" aria-label="Search resources">
                                <kbd class="search-hint">/</kbd>
                            </div>
                        </div>
                        <div id="recently-viewed" class="recently-viewed" style="display: none;">
                            <div class="recently-header">Recently Viewed</div>
                            <ul class="recently-list"></ul>
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
                var desc = resource.getDescription() != null ? resource.getDescription().toLowerCase() : "";
                sb.append("""
                                    <li class="nav-item%s" data-name="%s" data-desc="%s">
                                        <a href="%s.html"%s>%s</a>
                                    </li>
                    """.formatted(
                        isActive ? " active" : "",
                        resource.getName().toLowerCase(),
                        escapeHtml(desc),
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

                <!-- Favicon -->
                <link rel="icon" href="data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 100 100'><text y='.9em' font-size='90'>🪁</text></svg>">

                <!-- Preload critical resources -->
                <link rel="preload" href="styles.css" as="style">
                <link rel="preload" href="scripts.js" as="script">

                <!-- Stylesheet -->
                <link rel="stylesheet" href="styles.css">

                <!-- Scripts -->
                <script src="scripts.js" defer></script>

                <!-- RSS Feed -->
                <link rel="alternate" type="application/rss+xml" title="%s Provider Resources" href="feed.xml">

                <!-- OpenSearch -->
                <link rel="search" type="application/opensearchdescription+xml" title="Kite %s Docs" href="opensearch.xml">

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
            </head>
            """.formatted(
                escapeHtml(title),
                escapeHtml(description),
                providerName,
                canonicalUrl,
                capitalize(providerInfo.getName()),  // RSS feed title
                capitalize(providerInfo.getName()),  // OpenSearch title
                escapeHtml(title),
                escapeHtml(description),
                canonicalUrl,
                escapeHtml(title),
                escapeHtml(description)
            );
    }

    private String generateJsonLd(ResourceInfo resource) {
        var domain = resource.getDomain() != null ? resource.getDomain() : "general";
        var providerName = providerInfo.getName().toLowerCase();
        var providerDisplay = capitalize(providerInfo.getName());
        var resourceDesc = resource.getDescription() != null ? resource.getDescription() : "";
        var importPath = "%s/%s/%s.kite".formatted(providerName, domain.toLowerCase(), resource.getName());

        return """
            <script type="application/ld+json">
            [
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
                },
                {
                    "@context": "https://schema.org",
                    "@type": "BreadcrumbList",
                    "itemListElement": [
                        {
                            "@type": "ListItem",
                            "position": 1,
                            "name": "%s Provider",
                            "item": "%s/%s/"
                        },
                        {
                            "@type": "ListItem",
                            "position": 2,
                            "name": "%s",
                            "item": "%s/%s/%s.html"
                        },
                        {
                            "@type": "ListItem",
                            "position": 3,
                            "name": "%s"
                        }
                    ]
                },
                {
                    "@context": "https://schema.org",
                    "@type": "FAQPage",
                    "mainEntity": [
                        {
                            "@type": "Question",
                            "name": "What is %s in Kite?",
                            "acceptedAnswer": {
                                "@type": "Answer",
                                "text": "%s is a %s resource in the %s provider. %s"
                            }
                        },
                        {
                            "@type": "Question",
                            "name": "How do I import %s in Kite?",
                            "acceptedAnswer": {
                                "@type": "Answer",
                                "text": "Use: import %s from \\"%s\\""
                            }
                        },
                        {
                            "@type": "Question",
                            "name": "What properties does %s have?",
                            "acceptedAnswer": {
                                "@type": "Answer",
                                "text": "%s has %d configurable properties. See the Properties section for details."
                            }
                        }
                    ]
                }
            ]
            </script>
            """.formatted(
                // TechArticle
                resource.getName(),
                escapeHtml(resourceDesc),
                BASE_URL, providerName, resource.getName(),
                resource.getName(),
                capitalize(domain),
                providerName,
                resource.getName().toLowerCase(),
                // BreadcrumbList
                providerDisplay,
                BASE_URL, providerName,
                capitalize(domain),
                BASE_URL, providerName, resources.stream()
                    .filter(r -> domain.equals(r.getDomain() != null ? r.getDomain() : "general"))
                    .findFirst().map(ResourceInfo::getName).orElse(resource.getName()),
                resource.getName(),
                // FAQPage Q1
                resource.getName(),
                resource.getName(), domain.toLowerCase(), providerDisplay,
                resourceDesc.isEmpty() ? "Use it to manage your cloud infrastructure." : escapeHtml(resourceDesc),
                // FAQPage Q2
                resource.getName(),
                resource.getName(), importPath,
                // FAQPage Q3
                resource.getName(),
                resource.getName(),
                resource.getProperties().stream().filter(p -> !p.isCloudManaged()).count()
            );
    }

    private String generateIndexJsonLd() {
        var providerName = providerInfo.getName().toLowerCase();
        var providerDisplay = capitalize(providerInfo.getName());
        var byDomain = groupByDomain();

        return """
            <script type="application/ld+json">
            [
                {
                    "@context": "https://schema.org",
                    "@type": "SoftwareApplication",
                    "name": "Kite %s Provider",
                    "applicationCategory": "DeveloperApplication",
                    "applicationSubCategory": "Infrastructure as Code",
                    "operatingSystem": "Cross-platform",
                    "softwareVersion": "%s",
                    "description": "Infrastructure as code provider for %s. Manage %d cloud resources with Kite.",
                    "author": {
                        "@type": "Organization",
                        "name": "Kite",
                        "url": "https://kitelang.cloud"
                    },
                    "offers": {
                        "@type": "Offer",
                        "price": "0",
                        "priceCurrency": "USD"
                    },
                    "featureList": [
                        "%d infrastructure resources",
                        "%d resource categories",
                        "Declarative configuration",
                        "Multi-cloud support"
                    ]
                },
                {
                    "@context": "https://schema.org",
                    "@type": "WebSite",
                    "name": "Kite %s Provider Documentation",
                    "url": "%s/%s/",
                    "potentialAction": {
                        "@type": "SearchAction",
                        "target": "%s/%s/index.html?q={search_term_string}",
                        "query-input": "required name=search_term_string"
                    }
                }
            ]
            </script>
            """.formatted(
                providerDisplay,
                providerInfo.getVersion(),
                providerDisplay,
                resources.size(),
                resources.size(),
                byDomain.size(),
                providerDisplay,
                BASE_URL, providerName,
                BASE_URL, providerName
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

    private String generateRobotsTxt() {
        var providerName = providerInfo.getName().toLowerCase();
        return """
            # Kite %s Provider Documentation
            # https://docs.kitelang.cloud/%s/

            User-agent: *
            Allow: /

            # Sitemap location
            Sitemap: %s/%s/sitemap.xml
            """.formatted(
                capitalize(providerInfo.getName()),
                providerName,
                BASE_URL,
                providerName
            );
    }

    private String generateRssFeed() {
        var sb = new StringBuilder();
        var providerName = providerInfo.getName().toLowerCase();
        var buildDate = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        sb.append("""
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom">
                <channel>
                    <title>%s Provider Resources | Kite</title>
                    <description>Infrastructure as code resources for %s provider. Stay updated with the latest resource documentation.</description>
                    <link>%s/%s/</link>
                    <atom:link href="%s/%s/feed.xml" rel="self" type="application/rss+xml"/>
                    <language>en-us</language>
                    <lastBuildDate>%s</lastBuildDate>
                    <generator>Kite Documentation Generator</generator>
            """.formatted(
                capitalize(providerInfo.getName()),
                capitalize(providerInfo.getName()),
                BASE_URL, providerName,
                BASE_URL, providerName,
                buildDate
            ));

        for (var resource : resources) {
            var domain = resource.getDomain() != null ? resource.getDomain() : "general";
            var description = resource.getDescription() != null && !resource.getDescription().isEmpty()
                    ? escapeHtml(resource.getDescription())
                    : "Documentation for " + resource.getName() + " resource.";

            sb.append("""
                    <item>
                        <title>%s</title>
                        <description>%s</description>
                        <link>%s/%s/%s.html</link>
                        <guid isPermaLink="true">%s/%s/%s.html</guid>
                        <category>%s</category>
                    </item>
                """.formatted(
                    resource.getName(),
                    description,
                    BASE_URL, providerName, resource.getName(),
                    BASE_URL, providerName, resource.getName(),
                    capitalize(domain)
                ));
        }

        sb.append("""
                </channel>
            </rss>
            """);
        return sb.toString();
    }

    private String generateOpenSearch() {
        var providerName = providerInfo.getName().toLowerCase();
        var displayName = capitalize(providerInfo.getName());

        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <OpenSearchDescription xmlns="http://a9.com/-/spec/opensearch/1.1/">
                <ShortName>Kite %s</ShortName>
                <Description>Search %s provider documentation for Kite infrastructure as code</Description>
                <Tags>kite iac infrastructure cloud %s terraform</Tags>
                <Contact>support@kitelang.cloud</Contact>
                <Url type="text/html" template="%s/%s/index.html?q={searchTerms}"/>
                <Image width="16" height="16" type="image/x-icon">data:image/svg+xml,&lt;svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 100 100'&gt;&lt;text y='.9em' font-size='90'&gt;🪁&lt;/text&gt;&lt;/svg&gt;</Image>
                <InputEncoding>UTF-8</InputEncoding>
                <OutputEncoding>UTF-8</OutputEncoding>
            </OpenSearchDescription>
            """.formatted(
                displayName,
                displayName,
                providerName,
                BASE_URL,
                providerName
            );
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

    private String generateReferencesExample(ResourceInfo resource, String domain) {
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
                sb.append(getHighlightedExampleValue(prop));
            }
            sb.append("\n");
            count++;
        }

        sb.append("<span class=\"brace\">}</span>");
        return sb.toString();
    }

    private String generateCompleteExample(ResourceInfo resource) {
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
            sb.append(getHighlightedExampleValue(prop));
            if (prop.getDescription() != null && !prop.getDescription().isEmpty()) {
                sb.append("  <span class=\"comment\">// ").append(escapeHtml(truncate(prop.getDescription(), 40))).append("</span>");
            }
            sb.append("\n");
        }

        sb.append("<span class=\"brace\">}</span>");
        return sb.toString();
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
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

    private String generateScripts() {
        return """
            // Theme toggle (dark/light mode)
            function toggleTheme() {
                const html = document.documentElement;
                const currentTheme = html.getAttribute('data-theme');
                const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
                html.setAttribute('data-theme', newTheme);
                localStorage.setItem('kite-theme', newTheme);
            }

            // Initialize theme from localStorage or system preference
            (function initTheme() {
                const saved = localStorage.getItem('kite-theme');
                if (saved) {
                    document.documentElement.setAttribute('data-theme', saved);
                } else if (window.matchMedia('(prefers-color-scheme: dark)').matches) {
                    document.documentElement.setAttribute('data-theme', 'dark');
                }
            })();

            // Back to top button
            function scrollToTop() {
                window.scrollTo({ top: 0, behavior: 'smooth' });
            }

            // Show/hide back to top button based on scroll position
            window.addEventListener('scroll', function() {
                const btn = document.querySelector('.back-to-top');
                if (btn) {
                    btn.classList.toggle('visible', window.scrollY > 300);
                }
            });

            // Toast notification
            function showToast(message, duration = 2000) {
                const toast = document.getElementById('toast');
                if (toast) {
                    toast.textContent = message;
                    toast.classList.add('show');
                    setTimeout(() => toast.classList.remove('show'), duration);
                }
            }

            // Recently viewed resources (localStorage) - scoped per provider
            function getProviderName() {
                // Extract provider from path: /aws/Vpc.html -> aws
                const match = window.location.pathname.match(/\\/([^/]+)\\/[^/]+\\.html$/);
                return match ? match[1] : 'default';
            }

            function getRecentKey() {
                return 'kite-recent-' + getProviderName();
            }

            function addToRecentlyViewed(name) {
                if (!name || name === 'index') return;
                const key = getRecentKey();
                let recent = JSON.parse(localStorage.getItem(key) || '[]');
                recent = recent.filter(r => r !== name);
                recent.unshift(name);
                recent = recent.slice(0, 5);
                localStorage.setItem(key, JSON.stringify(recent));
            }

            function renderRecentlyViewed() {
                const container = document.getElementById('recently-viewed');
                const list = container?.querySelector('.recently-list');
                if (!container || !list) return;

                const key = getRecentKey();
                const recent = JSON.parse(localStorage.getItem(key) || '[]');
                if (recent.length === 0) {
                    container.style.display = 'none';
                    return;
                }

                container.style.display = 'block';
                list.innerHTML = recent.map(name =>
                    `<li><a href="${name}.html">${name}</a></li>`
                ).join('');
            }

            // Track current page view
            (function trackPageView() {
                const path = window.location.pathname;
                const match = path.match(/\\/([^/]+)\\.html$/);
                if (match && match[1] !== 'index') {
                    addToRecentlyViewed(match[1]);
                }
                renderRecentlyViewed();
            })();

            // Mobile menu toggle
            function toggleMobileMenu() {
                const sidebar = document.querySelector('.sidebar-left');
                const overlay = document.querySelector('.mobile-overlay');
                const btn = document.querySelector('.mobile-menu-btn');
                sidebar.classList.toggle('open');
                overlay.classList.toggle('open');
                btn.classList.toggle('open');
            }

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
                    showToast('Copied to clipboard!');
                    setTimeout(() => {
                        btn.textContent = 'Copy';
                        btn.classList.remove('copied');
                    }, 2000);
                });
            }

            // Fuzzy search with descriptions
            document.getElementById('search')?.addEventListener('input', function(e) {
                const query = e.target.value.toLowerCase().trim();

                document.querySelectorAll('.nav-category').forEach(category => {
                    let hasVisible = false;

                    category.querySelectorAll('.nav-item').forEach(item => {
                        const name = item.dataset.name || '';
                        const desc = item.dataset.desc || '';
                        // Fuzzy match: check if all characters appear in order
                        const fuzzyMatch = (text, pattern) => {
                            if (!pattern) return true;
                            let pi = 0;
                            for (let i = 0; i < text.length && pi < pattern.length; i++) {
                                if (text[i] === pattern[pi]) pi++;
                            }
                            return pi === pattern.length;
                        };
                        const visible = fuzzyMatch(name, query) || desc.includes(query);
                        item.style.display = visible ? '' : 'none';
                        if (visible) hasVisible = true;
                    });

                    category.style.display = hasVisible ? '' : 'none';

                    if (query && hasVisible) {
                        category.classList.remove('collapsed');
                    }
                });
            });

            // Keyboard shortcuts
            document.addEventListener('keydown', function(e) {
                const searchInput = document.getElementById('search');
                const sidebar = document.querySelector('.sidebar-left');

                // "/" to focus search (when not in input)
                if (e.key === '/' && document.activeElement.tagName !== 'INPUT') {
                    e.preventDefault();
                    searchInput?.focus();
                    searchInput?.select();
                }

                // Escape to close mobile menu or blur search
                if (e.key === 'Escape') {
                    if (sidebar?.classList.contains('open')) {
                        toggleMobileMenu();
                    } else if (document.activeElement === searchInput) {
                        searchInput.blur();
                        searchInput.value = '';
                        searchInput.dispatchEvent(new Event('input'));
                    }
                }

                // Arrow key navigation in search results
                if (searchInput && document.activeElement === searchInput) {
                    const visibleItems = Array.from(document.querySelectorAll('.nav-item'))
                        .filter(item => item.style.display !== 'none');

                    if (e.key === 'ArrowDown' && visibleItems.length > 0) {
                        e.preventDefault();
                        visibleItems[0].querySelector('a')?.focus();
                    }
                }

                // Navigate between visible items with arrow keys
                if (document.activeElement.closest('.nav-item')) {
                    const visibleItems = Array.from(document.querySelectorAll('.nav-item'))
                        .filter(item => item.style.display !== 'none');
                    const currentItem = document.activeElement.closest('.nav-item');
                    const currentIndex = visibleItems.indexOf(currentItem);

                    if (e.key === 'ArrowDown' && currentIndex < visibleItems.length - 1) {
                        e.preventDefault();
                        visibleItems[currentIndex + 1].querySelector('a')?.focus();
                    } else if (e.key === 'ArrowUp') {
                        e.preventDefault();
                        if (currentIndex > 0) {
                            visibleItems[currentIndex - 1].querySelector('a')?.focus();
                        } else {
                            searchInput?.focus();
                        }
                    } else if (e.key === 'Enter') {
                        // Already handled by link click
                    }
                }

                // j/k navigation between resources (like GitHub)
                if ((e.key === 'j' || e.key === 'k') && document.activeElement.tagName !== 'INPUT') {
                    const prevLink = document.querySelector('.nav-prev');
                    const nextLink = document.querySelector('.nav-next');

                    if (e.key === 'j' && nextLink) {
                        window.location.href = nextLink.href;
                    } else if (e.key === 'k' && prevLink) {
                        window.location.href = prevLink.href;
                    }
                }
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

            // Add anchor links to headings with IDs
            document.querySelectorAll('h2[id], section[id] > h2').forEach(heading => {
                const id = heading.id || heading.parentElement.id;
                if (id) {
                    const anchor = document.createElement('a');
                    anchor.className = 'anchor-link';
                    anchor.href = '#' + id;
                    anchor.textContent = '#';
                    anchor.setAttribute('aria-label', 'Link to this section');
                    heading.insertBefore(anchor, heading.firstChild);
                }
            });

            // Smooth scroll for anchor links
            document.querySelectorAll('a[href^="#"]').forEach(anchor => {
                anchor.addEventListener('click', function(e) {
                    const target = document.querySelector(this.getAttribute('href'));
                    if (target) {
                        e.preventDefault();
                        target.scrollIntoView({ behavior: 'smooth', block: 'start' });
                        history.pushState(null, '', this.getAttribute('href'));
                    }
                });
            });

            // Example tabs switching
            function showExample(btn, type) {
                // Hide all example content
                document.querySelectorAll('.example-content').forEach(c => c.classList.remove('active'));
                document.querySelectorAll('.example-tab').forEach(t => t.classList.remove('active'));
                // Show selected
                document.getElementById('example-' + type)?.classList.add('active');
                btn.classList.add('active');
            }
            """;
    }

    private String generateStyles() {
        return """
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
                    :root:not([data-theme="light"]) {
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

                /* Manual dark theme override */
                :root[data-theme="dark"] {
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

                * { box-sizing: border-box; margin: 0; padding: 0; }

                html { scroll-behavior: smooth; }

                body {
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', sans-serif;
                    background: var(--bg-body);
                    color: var(--text-primary);
                    line-height: 1.6;
                }

                /* Mobile menu button */
                .mobile-menu-btn {
                    display: none;
                    position: fixed;
                    top: 1rem;
                    left: 1rem;
                    z-index: 200;
                    width: 44px;
                    height: 44px;
                    border: none;
                    background: var(--bg-sidebar);
                    border-radius: 0.5rem;
                    box-shadow: var(--shadow-md);
                    cursor: pointer;
                    align-items: center;
                    justify-content: center;
                }

                .hamburger-icon {
                    display: block;
                    width: 20px;
                    height: 2px;
                    background: var(--text-primary);
                    position: relative;
                    transition: background 0.2s;
                }

                .hamburger-icon::before,
                .hamburger-icon::after {
                    content: '';
                    position: absolute;
                    width: 20px;
                    height: 2px;
                    background: var(--text-primary);
                    left: 0;
                    transition: transform 0.2s;
                }

                .hamburger-icon::before { top: -6px; }
                .hamburger-icon::after { top: 6px; }

                .mobile-menu-btn.open .hamburger-icon { background: transparent; }
                .mobile-menu-btn.open .hamburger-icon::before { transform: rotate(45deg) translate(4px, 4px); }
                .mobile-menu-btn.open .hamburger-icon::after { transform: rotate(-45deg) translate(4px, -4px); }

                .mobile-overlay {
                    display: none;
                    position: fixed;
                    top: 0;
                    left: 0;
                    right: 0;
                    bottom: 0;
                    background: rgba(0, 0, 0, 0.5);
                    z-index: 90;
                    opacity: 0;
                    transition: opacity 0.3s;
                }

                .mobile-overlay.open {
                    opacity: 1;
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

                .header-top {
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    margin-bottom: 0.25rem;
                }

                .theme-toggle {
                    background: none;
                    border: none;
                    cursor: pointer;
                    font-size: 1.25rem;
                    padding: 0.25rem;
                    border-radius: 0.25rem;
                    transition: background 0.15s;
                }

                .theme-toggle:hover {
                    background: var(--bg-hover);
                }

                .theme-icon-dark { display: none; }
                :root[data-theme="dark"] .theme-icon-light { display: none; }
                :root[data-theme="dark"] .theme-icon-dark { display: inline; }
                @media (prefers-color-scheme: dark) {
                    :root:not([data-theme="light"]) .theme-icon-light { display: none; }
                    :root:not([data-theme="light"]) .theme-icon-dark { display: inline; }
                }

                /* Recently viewed */
                .recently-viewed {
                    padding: 0.75rem 1.5rem;
                    border-bottom: 1px solid var(--border-color);
                }

                .recently-header {
                    font-size: 0.7rem;
                    font-weight: 600;
                    text-transform: uppercase;
                    letter-spacing: 0.05em;
                    color: var(--text-muted);
                    margin-bottom: 0.5rem;
                }

                .recently-list {
                    list-style: none;
                }

                .recently-list li {
                    margin-bottom: 0.25rem;
                }

                .recently-list a {
                    color: var(--text-secondary);
                    text-decoration: none;
                    font-size: 0.875rem;
                    transition: color 0.15s;
                }

                .recently-list a:hover {
                    color: var(--kite-primary);
                }

                .search-wrapper {
                    padding: 1rem 1.5rem;
                    border-bottom: 1px solid var(--border-color);
                }

                .search-container {
                    position: relative;
                    display: flex;
                    align-items: center;
                }

                .search-input {
                    width: 100%;
                    padding: 0.5rem 2.5rem 0.5rem 0.75rem;
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

                .search-input:focus + .search-hint {
                    opacity: 0;
                }

                .search-hint {
                    position: absolute;
                    right: 0.5rem;
                    padding: 0.125rem 0.375rem;
                    background: var(--bg-hover);
                    border: 1px solid var(--border-color);
                    border-radius: 0.25rem;
                    font-size: 0.75rem;
                    font-family: monospace;
                    color: var(--text-muted);
                    pointer-events: none;
                    transition: opacity 0.15s;
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

                .resource-content { max-width: 100%; }

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

                /* Quick start section */
                .quick-start-section {
                    background: var(--bg-hover);
                    border-radius: 0.5rem;
                    padding: 1.5rem;
                    margin-bottom: 2rem;
                }

                .quick-start-section h3 {
                    font-size: 1rem;
                    margin: 1.5rem 0 0.75rem;
                    color: var(--text-primary);
                }

                .quick-start-section h3:first-child {
                    margin-top: 0;
                }

                .quick-start-section p {
                    color: var(--text-secondary);
                    margin-bottom: 0.5rem;
                    font-size: 0.875rem;
                }

                .quick-start-section .code-wrapper {
                    margin: 0.5rem 0 0 0;
                }

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
                    width: 100%;
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
                    position: sticky;
                    top: 0;
                    z-index: 10;
                }

                tr:hover { background: var(--bg-hover); }

                .prop-name { color: var(--text-primary); font-weight: 500; }

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
                    color: var(--kite-success);
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

                .nav-category-label {
                    font-size: 0.75rem;
                    color: var(--text-muted);
                    text-transform: uppercase;
                    letter-spacing: 0.05em;
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
                    .mobile-menu-btn {
                        display: flex;
                    }

                    .mobile-overlay {
                        display: block;
                    }

                    .sidebar-left {
                        position: fixed;
                        left: -100%;
                        width: 100%;
                        max-width: 300px;
                        z-index: 100;
                        transition: left 0.3s;
                    }

                    .sidebar-left.open { left: 0; }
                    .layout { grid-template-columns: 1fr; }
                    .content { padding: 4rem 1.5rem 1.5rem; }

                    .quick-stats {
                        flex-direction: column;
                        gap: 1rem;
                    }
                }

                /* Skip link for accessibility */
                .skip-link {
                    position: absolute;
                    top: -40px;
                    left: 0;
                    background: var(--kite-primary);
                    color: white;
                    padding: 0.5rem 1rem;
                    z-index: 300;
                    text-decoration: none;
                    border-radius: 0 0 0.25rem 0;
                    transition: top 0.2s;
                }

                .skip-link:focus {
                    top: 0;
                }

                /* Related resources section */
                .related-section {
                    margin-top: 2rem;
                    padding-top: 1.5rem;
                    border-top: 1px solid var(--border-color);
                }

                .related-desc {
                    color: var(--text-secondary);
                    font-size: 0.875rem;
                    margin-bottom: 1rem;
                }

                .related-grid {
                    display: flex;
                    flex-wrap: wrap;
                    gap: 0.75rem;
                }

                .related-card {
                    display: flex;
                    align-items: center;
                    gap: 0.5rem;
                    padding: 0.5rem 1rem;
                    background: var(--bg-hover);
                    border: 1px solid var(--border-color);
                    border-radius: 0.375rem;
                    text-decoration: none;
                    color: var(--text-primary);
                    font-size: 0.875rem;
                    transition: all 0.15s;
                }

                .related-card:hover {
                    border-color: var(--kite-primary);
                    background: rgba(124, 58, 237, 0.05);
                }

                .related-icon { font-size: 1rem; }
                .related-name { font-weight: 500; }

                /* Anchor links on headings */
                h2 {
                    position: relative;
                }

                h2:hover .anchor-link {
                    opacity: 1;
                }

                .anchor-link {
                    position: absolute;
                    left: -1.5rem;
                    color: var(--text-muted);
                    text-decoration: none;
                    opacity: 0;
                    transition: opacity 0.15s;
                    font-weight: normal;
                }

                .anchor-link:hover {
                    color: var(--kite-primary);
                }

                /* Back to top button */
                .back-to-top {
                    position: fixed;
                    bottom: 2rem;
                    right: 2rem;
                    width: 44px;
                    height: 44px;
                    background: var(--kite-primary);
                    color: white;
                    border: none;
                    border-radius: 50%;
                    font-size: 1.25rem;
                    cursor: pointer;
                    opacity: 0;
                    visibility: hidden;
                    transform: translateY(20px);
                    transition: all 0.3s ease;
                    box-shadow: var(--shadow-md);
                    z-index: 100;
                }

                .back-to-top.visible {
                    opacity: 1;
                    visibility: visible;
                    transform: translateY(0);
                }

                .back-to-top:hover {
                    background: var(--kite-primary-dark);
                    transform: translateY(-2px);
                }

                /* Toast notification */
                .toast {
                    position: fixed;
                    bottom: 2rem;
                    left: 50%;
                    transform: translateX(-50%) translateY(100px);
                    background: var(--bg-code);
                    color: var(--text-code);
                    padding: 0.75rem 1.5rem;
                    border-radius: 0.5rem;
                    font-size: 0.875rem;
                    box-shadow: var(--shadow-md);
                    opacity: 0;
                    visibility: hidden;
                    transition: all 0.3s ease;
                    z-index: 200;
                }

                .toast.show {
                    opacity: 1;
                    visibility: visible;
                    transform: translateX(-50%) translateY(0);
                }

                /* Example tabs */
                .example-tabs {
                    display: flex;
                    gap: 0.5rem;
                    margin-bottom: 1rem;
                    border-bottom: 1px solid var(--border-color);
                    padding-bottom: 0.5rem;
                }

                .example-tab {
                    padding: 0.5rem 1rem;
                    background: none;
                    border: none;
                    color: var(--text-secondary);
                    font-size: 0.875rem;
                    cursor: pointer;
                    border-radius: 0.375rem 0.375rem 0 0;
                    transition: all 0.15s;
                }

                .example-tab:hover {
                    color: var(--text-primary);
                    background: var(--bg-hover);
                }

                .example-tab.active {
                    color: var(--kite-primary);
                    background: rgba(124, 58, 237, 0.1);
                    font-weight: 500;
                }

                .example-content {
                    display: none;
                }

                .example-content.active {
                    display: block;
                }

                /* Footer actions */
                .footer-actions {
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    margin-bottom: 0.75rem;
                }

                .share-buttons {
                    display: flex;
                    align-items: center;
                    gap: 0.5rem;
                }

                .share-label {
                    font-size: 0.75rem;
                    color: var(--text-muted);
                }

                .share-btn {
                    display: inline-flex;
                    align-items: center;
                    justify-content: center;
                    width: 32px;
                    height: 32px;
                    border-radius: 0.375rem;
                    text-decoration: none;
                    font-weight: 600;
                    font-size: 0.875rem;
                    transition: all 0.15s;
                }

                .share-twitter {
                    background: #1da1f2;
                    color: white;
                }

                .share-twitter:hover {
                    background: #0d8cd9;
                }

                .share-linkedin {
                    background: #0a66c2;
                    color: white;
                }

                .share-linkedin:hover {
                    background: #084d92;
                }

                .report-issue {
                    font-size: 0.75rem;
                    color: var(--text-muted);
                    text-decoration: none;
                    padding: 0.375rem 0.75rem;
                    border: 1px solid var(--border-color);
                    border-radius: 0.375rem;
                    transition: all 0.15s;
                }

                .report-issue:hover {
                    color: var(--kite-primary);
                    border-color: var(--kite-primary);
                }

                .footer-date {
                    color: var(--text-muted);
                }

                /* Print styles */
                @media print {
                    .sidebar-left, .sidebar-right, .copy-btn, .resource-nav { display: none; }
                    .layout { display: block; }
                    .content { padding: 0; }
                    .code-block { background: #f5f5f5; color: #333; border: 1px solid #ddd; }
                }
            """;
    }
}
