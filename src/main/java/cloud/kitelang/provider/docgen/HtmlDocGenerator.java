package cloud.kitelang.provider.docgen;

import cloud.kitelang.provider.KiteProvider;
import cloud.kitelang.provider.ResourceTypeHandler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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

    private String readResource(String path) {
        try (var is = getClass().getResourceAsStream(path)) {
            if (is == null) throw new IllegalStateException("Resource not found: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read resource: " + path, e);
        }
    }

    /**
     * Generates HTML documentation with versioned structure.
     *
     * <p>Output structure:</p>
     * <pre>
     * docsRoot/
     * ├── index.html       <- NOT versioned (dynamic, loads from selected version)
     * ├── styles.css       <- NOT versioned (shared)
     * ├── scripts.js       <- NOT versioned (shared)
     * ├── versions.json    <- NOT versioned (version list)
     * ├── sitemap.xml      <- NOT versioned
     * ├── robots.txt       <- NOT versioned
     * └── {version}/
     *     ├── manifest.json
     *     ├── html/
     *     │   ├── Vpc.html
     *     │   └── ...
     *     ├── markdown/
     *     └── schemas/
     * </pre>
     *
     * @param docsRoot the root docs directory (e.g., aws/docs/)
     * @param version  the current version being generated
     */
    public void generateVersioned(Path docsRoot, String version) throws IOException {
        Files.createDirectories(docsRoot);
        var versionDir = docsRoot.resolve(version);
        var htmlDir = versionDir.resolve("html");
        Files.createDirectories(htmlDir);

        // Generate shared assets at docs root (NOT versioned)
        Files.writeString(docsRoot.resolve("styles.css"), generateStyles());
        Files.writeString(docsRoot.resolve("scripts.js"), generateScripts());
        Files.writeString(docsRoot.resolve("index.html"), generateVersionedIndex());
        Files.writeString(docsRoot.resolve("sitemap.xml"), generateSitemap());
        Files.writeString(docsRoot.resolve("robots.txt"), generateRobotsTxt());
        Files.writeString(docsRoot.resolve("feed.xml"), generateRssFeed());
        Files.writeString(docsRoot.resolve("opensearch.xml"), generateOpenSearch());

        // Generate manifest at version root (for changelog diffing)
        Files.writeString(versionDir.resolve("manifest.json"), generateManifest());

        // Generate resource pages in html/ subdirectory with ../../ prefix for assets
        for (var resource : resources) {
            var resourceHtml = generateVersionedResourcePage(resource);
            Files.writeString(htmlDir.resolve(resource.getName() + ".html"), resourceHtml);
        }
    }

    /**
     * Generates HTML documentation files (legacy - all in one directory).
     *
     * @param outputDir the directory to write files to
     * @deprecated Use {@link #generateVersioned(Path, String)} for versioned docs
     */
    @Deprecated
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

        // Generate manifest.json for version diffing
        Files.writeString(outputDir.resolve("manifest.json"), generateManifest());

        // Generate changelog.html (loads changelog.json dynamically)
        Files.writeString(outputDir.resolve("changelog.html"), generateChangelogPage());
    }

    /**
     * Generates a dynamic index.html that loads resources based on selected version.
     * Uses the same HTML structure as the legacy generateIndex() for CSS compatibility.
     */
    private String generateVersionedIndex() {
        var sb = new StringBuilder();
        var providerName = providerInfo.getName();
        var displayName = capitalize(providerName);
        var description = "Complete documentation for " + displayName +
                " provider resources. Learn how to configure and manage infrastructure resources with Kite.";

        sb.append(generateHtmlHead(
                displayName + " Provider Documentation | Kite",
                description,
                "index.html"
        ));

        sb.append("""
            <body>
                <a href="#main-content" class="skip-link">Skip to main content</a>
                <button class="mobile-menu-btn" onclick="toggleMobileMenu()" aria-label="Toggle navigation menu">
                    <span class="hamburger-icon"></span>
                </button>
                <div class="mobile-overlay" onclick="toggleMobileMenu()"></div>
                <div class="layout">
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
                            <div class="version-selector">
                                <select id="version-select" onchange="switchVersion(this.value)" aria-label="Select version">
                                    <option>Loading...</option>
                                </select>
                            </div>
                        </div>
                        <div class="search-wrapper">
                            <div class="search-container">
                                <input type="search" id="search" placeholder="Search resources..." class="search-input" aria-label="Search resources" oninput="filterResources(this.value)">
                                <kbd class="search-hint">/</kbd>
                            </div>
                        </div>
                        <nav id="nav-tree" class="nav-tree" aria-label="Resources navigation">
                            <div class="loading">Loading resources...</div>
                        </nav>
                    </aside>
                    <main id="main-content" class="content">
                        <article class="resource-content">
                            <h1>%s Provider Documentation</h1>
                            <p class="welcome-text">Infrastructure as code resources for %s. Select a resource from the navigation to view its documentation.</p>

                            <div class="quick-stats">
                                <div class="stat-card">
                                    <span class="stat-value" id="resource-count">-</span>
                                    <span class="stat-label">Resources</span>
                                </div>
                                <div class="stat-card">
                                    <span class="stat-value" id="category-count">-</span>
                                    <span class="stat-label">Categories</span>
                                </div>
                            </div>

                            <h2 id="categories">Categories</h2>
                            <div id="category-grid" class="category-grid">
                                <div class="loading">Loading...</div>
                            </div>

                            <h2 id="all-resources">All Resources</h2>
                            <div id="resource-grid" class="resource-grid">
                                <div class="loading">Loading...</div>
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
                <button class="back-to-top" onclick="scrollToTop()" aria-label="Back to top" title="Back to top">↑</button>
                <div id="toast" class="toast" role="alert" aria-live="polite"></div>
                <script>
                    let currentVersion = null;

                    async function initVersionedIndex() {
                        try {
                            const res = await fetch('versions.json');
                            const data = await res.json();
                            const versions = data.versions || [];
                            currentVersion = data.latest || (versions[0] && versions[0].path);

                            const select = document.getElementById('version-select');
                            select.innerHTML = versions.map(v =>
                                `<option value="${v.path}" ${v.version === data.latest ? 'selected' : ''}>v${v.version}${v.version === data.latest ? ' (latest)' : ''}</option>`
                            ).join('');

                            await loadResources(currentVersion);
                        } catch (e) {
                            console.error('Failed to load versions', e);
                            document.getElementById('nav-tree').innerHTML = '<div class="error">Failed to load</div>';
                        }
                    }

                    async function loadResources(version) {
                        try {
                            const res = await fetch(version + '/manifest.json');
                            const data = await res.json();
                            const resourcesObj = data.resources || {};
                            const resources = Object.entries(resourcesObj).map(([name, info]) => ({
                                name,
                                domain: info.domain || 'general',
                                propertyCount: Object.keys(info.properties || {}).length
                            }));

                            renderNavigation(resources, version);
                            renderCategoryGrid(resources, version);
                            renderResourceGrid(resources, version);
                            updateStats(resources);
                        } catch (e) {
                            console.error('Failed to load resources', e);
                        }
                    }

                    function renderNavigation(resources, version) {
                        const byDomain = groupByDomain(resources);
                        let html = '';
                        for (const [domain, items] of Object.entries(byDomain)) {
                            html += `<div class="nav-category" data-domain="${domain}">
                                <div class="category-header" onclick="toggleCategory(this)" role="button" aria-expanded="true">
                                    <span class="category-icon">${getDomainIcon(domain)}</span>
                                    <span class="category-name">${capitalize(domain)}</span>
                                    <span class="category-count">${items.length}</span>
                                    <span class="category-arrow">▾</span>
                                </div>
                                <ul class="category-items">
                                    ${items.map(r => `<li class="nav-item" data-name="${r.name.toLowerCase()}"><a href="${version}/html/${r.name}.html">${r.name}</a></li>`).join('')}
                                </ul>
                            </div>`;
                        }
                        document.getElementById('nav-tree').innerHTML = html;
                    }

                    function renderCategoryGrid(resources, version) {
                        const byDomain = groupByDomain(resources);
                        let html = '';
                        for (const [domain, items] of Object.entries(byDomain)) {
                            const firstResource = items[0];
                            html += `<a href="${version}/html/${firstResource.name}.html" class="category-card">
                                <span class="card-icon">${getDomainIcon(domain)}</span>
                                <span class="card-name">${capitalize(domain)}</span>
                                <span class="card-count">${items.length} resources</span>
                            </a>`;
                        }
                        document.getElementById('category-grid').innerHTML = html;
                    }

                    function renderResourceGrid(resources, version) {
                        let html = '';
                        for (const r of resources) {
                            html += `<a href="${version}/html/${r.name}.html" class="resource-card">
                                <span class="resource-icon">${getDomainIcon(r.domain)}</span>
                                <span class="resource-name">${r.name}</span>
                                <span class="resource-props">${r.propertyCount} properties</span>
                            </a>`;
                        }
                        document.getElementById('resource-grid').innerHTML = html;
                    }

                    function updateStats(resources) {
                        const domains = new Set(resources.map(r => r.domain));
                        document.getElementById('resource-count').textContent = resources.length;
                        document.getElementById('category-count').textContent = domains.size;
                    }

                    function switchVersion(version) {
                        currentVersion = version;
                        loadResources(version);
                    }

                    function filterResources(query) {
                        const q = query.toLowerCase();
                        document.querySelectorAll('.nav-item').forEach(item => {
                            const name = item.dataset.name || item.textContent.toLowerCase();
                            item.style.display = name.includes(q) ? '' : 'none';
                        });
                        document.querySelectorAll('.resource-card').forEach(card => {
                            card.style.display = card.textContent.toLowerCase().includes(q) ? '' : 'none';
                        });
                    }

                    function groupByDomain(resources) {
                        const groups = {};
                        resources.forEach(r => {
                            if (!groups[r.domain]) groups[r.domain] = [];
                            groups[r.domain].push(r);
                        });
                        return groups;
                    }

                    function getDomainIcon(domain) {
                        const icons = {networking:'🌐',compute:'💻',storage:'💾',database:'🗄️',dns:'📍',loadbalancing:'⚖️',security:'🔒',core:'⚙️'};
                        return icons[domain] || '📦';
                    }

                    function capitalize(s) {
                        return s.charAt(0).toUpperCase() + s.slice(1);
                    }

                    initVersionedIndex();
                </script>
            </body>
            </html>
            """.formatted(displayName, displayName, displayName));

        return sb.toString();
    }

    /**
     * Generates a resource page for versioned structure.
     * Reuses the legacy generateResourcePage() and adjusts paths for the versioned structure.
     * Resource pages are at {version}/html/{Resource}.html, so root assets need ../../ prefix.
     */
    private String generateVersionedResourcePage(ResourceInfo resource) {
        // Generate using the legacy method (which has the correct HTML structure)
        var html = generateResourcePage(resource);

        // Adjust paths for versioned structure:
        // - Root assets (styles.css, scripts.js, feed.xml, etc.) need ../../ prefix
        // - index.html needs ../../ prefix
        // - Resource links (.html files) stay as-is (same directory)
        html = html.replace("href=\"styles.css\"", "href=\"../../styles.css\"");
        html = html.replace("href=\"scripts.js\"", "href=\"../../scripts.js\"");
        html = html.replace("src=\"scripts.js\"", "src=\"../../scripts.js\"");
        html = html.replace("href=\"feed.xml\"", "href=\"../../feed.xml\"");
        html = html.replace("href=\"opensearch.xml\"", "href=\"../../opensearch.xml\"");
        html = html.replace("href=\"index.html\"", "href=\"../../index.html\"");
        html = html.replace("href=\"changelog.html\"", "href=\"../../changelog.html\"");

        // Fix manifest.json path (it's in parent directory, not same directory)
        html = html.replace("fetch('manifest.json')", "fetch('../manifest.json')");

        return html;
    }

    private String generateChangelogPage() {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>What's New - %s Provider | Kite</title>
                <link rel="stylesheet" href="styles.css">
            </head>
            <body>
                <a href="#main-content" class="skip-link">Skip to main content</a>
                %s
                <main id="main-content" class="content">
                    <article class="resource-content">
                        <h1>What's New in v%s</h1>
                        <div id="changelog-content">
                            <p class="loading">Loading changelog...</p>
                        </div>
                    </article>
                </main>
                <script src="scripts.js"></script>
                <script>
                    fetch('changelog.json')
                        .then(r => r.ok ? r.json() : null)
                        .catch(() => null)
                        .then(data => {
                            const container = document.getElementById('changelog-content');
                            if (!data) {
                                container.innerHTML = '<p class="no-changes">This is the first version. No previous version to compare.</p>';
                                return;
                            }

                            let html = '<p class="changelog-meta">Changes from <strong>v' + data.fromVersion + '</strong> to <strong>v' + data.toVersion + '</strong></p>';

                            // Added resources
                            if (data.addedResources && data.addedResources.length > 0) {
                                html += '<section class="changelog-section"><h2>✨ New Resources</h2><ul class="changelog-list added">';
                                data.addedResources.forEach(r => {
                                    html += '<li><a href="' + r + '.html">' + r + '</a></li>';
                                });
                                html += '</ul></section>';
                            }

                            // Removed resources
                            if (data.removedResources && data.removedResources.length > 0) {
                                html += '<section class="changelog-section"><h2>🗑️ Removed Resources</h2><ul class="changelog-list removed">';
                                data.removedResources.forEach(r => {
                                    html += '<li>' + r + '</li>';
                                });
                                html += '</ul></section>';
                            }

                            // Changed resources
                            if (data.changedResources && Object.keys(data.changedResources).length > 0) {
                                html += '<section class="changelog-section"><h2>🔄 Changed Resources</h2>';
                                for (const [resource, changes] of Object.entries(data.changedResources)) {
                                    html += '<div class="resource-changes"><h3><a href="' + resource + '.html">' + resource + '</a></h3>';
                                    if (changes.addedProperties && changes.addedProperties.length > 0) {
                                        html += '<p class="prop-change added">Added: <code>' + changes.addedProperties.join('</code>, <code>') + '</code></p>';
                                    }
                                    if (changes.removedProperties && changes.removedProperties.length > 0) {
                                        html += '<p class="prop-change removed">Removed: <code>' + changes.removedProperties.join('</code>, <code>') + '</code></p>';
                                    }
                                    html += '</div>';
                                }
                                html += '</section>';
                            }

                            if (!data.addedResources?.length && !data.removedResources?.length && !Object.keys(data.changedResources || {}).length) {
                                html += '<p class="no-changes">No changes detected in this version.</p>';
                            }

                            container.innerHTML = html;
                        });
                </script>
            </body>
            </html>
            """.formatted(
                capitalize(providerInfo.getName()),
                generateNavigation(null),
                providerInfo.getVersion()
            );
    }

    private String generateManifest() {
        var sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"provider\": \"").append(providerInfo.getName().toLowerCase()).append("\",\n");
        sb.append("  \"version\": \"").append(providerInfo.getVersion()).append("\",\n");
        sb.append("  \"generatedAt\": \"").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\",\n");
        sb.append("  \"resources\": {\n");

        var resourceList = new ArrayList<>(resources);
        for (int i = 0; i < resourceList.size(); i++) {
            var resource = resourceList.get(i);
            sb.append("    \"").append(resource.getName()).append("\": {\n");
            sb.append("      \"domain\": \"").append(resource.getDomain() != null ? resource.getDomain() : "general").append("\",\n");
            sb.append("      \"properties\": {\n");

            var props = resource.getProperties();
            var propList = new ArrayList<>(props);
            for (int j = 0; j < propList.size(); j++) {
                var prop = propList.get(j);
                sb.append("        \"").append(prop.getName()).append("\": {\n");
                sb.append("          \"type\": \"").append(escapeJson(prop.getType())).append("\",\n");
                sb.append("          \"required\": ").append(prop.isRequired()).append(",\n");
                sb.append("          \"cloudManaged\": ").append(prop.isCloudManaged()).append(",\n");
                sb.append("          \"deprecated\": ").append(prop.isDeprecated());
                if (prop.getDefaultValue() != null && !prop.getDefaultValue().isEmpty()) {
                    sb.append(",\n          \"default\": \"").append(escapeJson(prop.getDefaultValue())).append("\"");
                }
                sb.append("\n        }");
                if (j < propList.size() - 1) sb.append(",");
                sb.append("\n");
            }

            sb.append("      }\n");
            sb.append("    }");
            if (i < resourceList.size() - 1) sb.append(",");
            sb.append("\n");
        }

        sb.append("  }\n");
        sb.append("}\n");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
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

        // Properties section with tabs (if both user and cloud props exist)
        if (!userProps.isEmpty() || !cloudProps.isEmpty()) {
            sb.append("<section id=\"properties\">\n");
            sb.append("<h2>Properties</h2>\n");

            if (!userProps.isEmpty() && !cloudProps.isEmpty()) {
                // Tabbed view when both types exist
                sb.append("<div class=\"property-tabs\">\n");
                sb.append("<button class=\"property-tab active\" onclick=\"showPropertyTab(this, 'user')\">User Properties</button>\n");
                sb.append("<button class=\"property-tab\" onclick=\"showPropertyTab(this, 'cloud')\">Cloud Properties</button>\n");
                sb.append("</div>\n");
                sb.append("<div class=\"property-content active\" id=\"props-user\">\n");
                sb.append("<p class=\"props-desc\">Properties you configure when defining the resource.</p>\n");
                sb.append(generatePropertiesTable(userProps, false));
                sb.append("</div>\n");
                sb.append("<div class=\"property-content\" id=\"props-cloud\">\n");
                sb.append("<p class=\"cloud-desc\">Read-only properties set by the cloud provider after resource creation.</p>\n");
                sb.append(generatePropertiesTable(cloudProps, true));
                sb.append("</div>\n");
            } else if (!userProps.isEmpty()) {
                // Only user props
                sb.append(generatePropertiesTable(userProps, false));
            } else {
                // Only cloud props
                sb.append("<p class=\"cloud-desc\">Read-only properties set by the cloud provider after resource creation.</p>\n");
                sb.append(generatePropertiesTable(cloudProps, true));
            }

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

        if (!userProps.isEmpty() || !cloudProps.isEmpty()) {
            sb.append("<a href=\"#properties\">Properties</a>\n");
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
                            <div class="version-selector">
                                <select id="version-select" onchange="switchVersion(this.value)" aria-label="Select version">
                                    <option value="%s" selected>v%s</option>
                                </select>
                            </div>
                            <a href="changelog.html" class="whats-new-link">✨ What's New</a>
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
            """.formatted(capitalize(providerInfo.getName()), providerInfo.getVersion(), providerInfo.getVersion()));

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
            sb.append("  <td><code class=\"prop-name\" onclick=\"copyPropLink('").append(prop.getName()).append("')\" title=\"Click to copy link\">").append(prop.getName()).append("<span class=\"link-icon\">#</span></code>");
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
        return readResource("/docgen/scripts.js");
    }

    private String generateStyles() {
        return readResource("/docgen/styles.css");
    }
}
