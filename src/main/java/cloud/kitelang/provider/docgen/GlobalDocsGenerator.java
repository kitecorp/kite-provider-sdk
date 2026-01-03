package cloud.kitelang.provider.docgen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates global documentation assets shared across all providers.
 *
 * <p>Structure:</p>
 * <pre>
 * docs/
 * ├── index.html       <- Global landing (provider selector)
 * ├── styles.css       <- Shared styles
 * ├── scripts.js       <- Shared scripts
 * ├── providers.json   <- Provider manifest
 * └── {provider}/
 *     ├── index.html   <- Provider landing (version/resource selector)
 *     ├── versions.json
 *     └── {version}/
 *         ├── manifest.json
 *         └── {Resource}.html
 * </pre>
 */
public class GlobalDocsGenerator {

    private final String baseUrl;

    public GlobalDocsGenerator() {
        this("https://kitelang.cloud/providers");
    }

    public GlobalDocsGenerator(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * Generates global assets (index.html, styles.css, scripts.js).
     */
    public void generateGlobalAssets(Path docsRoot) throws IOException {
        Files.createDirectories(docsRoot);
        Files.writeString(docsRoot.resolve("index.html"), generateGlobalIndex());
        Files.writeString(docsRoot.resolve("styles.css"), generateStyles());
        Files.writeString(docsRoot.resolve("scripts.js"), generateScripts());
        Files.writeString(docsRoot.resolve("robots.txt"), generateRobotsTxt());
    }

    /**
     * Generates provider-level index.html (version selector, resource list).
     */
    public void generateProviderIndex(Path providerDir, String providerName) throws IOException {
        Files.createDirectories(providerDir);
        Files.writeString(providerDir.resolve("index.html"), generateProviderIndex(providerName));
    }

    private String generateGlobalIndex() {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Kite Providers Documentation</title>
                <meta name="description" content="Documentation for Kite infrastructure as code providers - AWS, Azure, GCP and more.">
                <link rel="stylesheet" href="styles.css">
                <script src="scripts.js" defer></script>
                <link rel="icon" href="data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 100 100'><text y='.9em' font-size='90'>🪁</text></svg>">
            </head>
            <body>
                <div class="layout">
                    <nav class="sidebar">
                        <div class="sidebar-header">
                            <a href="index.html" class="logo">🪁 Kite Providers</a>
                            <button class="theme-toggle" onclick="toggleTheme()" title="Toggle theme">🌙</button>
                        </div>
                        <div class="sidebar-search">
                            <input type="text" id="provider-search" placeholder="Search providers..." oninput="filterProviders(this.value)">
                        </div>
                        <div class="sidebar-content">
                            <div id="provider-list" class="nav-section">
                                <div class="loading">Loading providers...</div>
                            </div>
                        </div>
                    </nav>
                    <main class="content">
                        <div class="content-header">
                            <h1>Kite Provider Documentation</h1>
                        </div>
                        <div class="content-body">
                            <p class="lead">Infrastructure as code providers for managing cloud resources.</p>

                            <div id="provider-cards" class="provider-cards">
                                <div class="loading">Loading providers...</div>
                            </div>

                            <section class="getting-started">
                                <h2>Getting Started</h2>
                                <p>Select a provider from the sidebar or cards above to view its resources and documentation.</p>

                                <h3>Example</h3>
                                <pre class="code-block"><code>// Configure a provider
provider "aws" {
    region = "us-east-1"
}

// Create a resource
resource Vpc main {
    cidrBlock = "10.0.0.0/16"
    tags = { Name: "main-vpc" }
}</code></pre>
                            </section>
                        </div>
                    </main>
                </div>
                <script>
                    // Load providers on page load
                    document.addEventListener('DOMContentLoaded', loadProviders);

                    async function loadProviders() {
                        try {
                            const response = await fetch('providers.json');
                            const data = await response.json();
                            renderProviderList(data.providers);
                            renderProviderCards(data.providers);
                        } catch (e) {
                            document.getElementById('provider-list').innerHTML = '<div class="error">Failed to load providers</div>';
                            document.getElementById('provider-cards').innerHTML = '<div class="error">Failed to load providers</div>';
                        }
                    }

                    function renderProviderList(providers) {
                        const list = document.getElementById('provider-list');
                        list.innerHTML = providers.map(p => `
                            <a href="${p.name}/index.html" class="nav-item" data-provider="${p.name}">
                                <span class="provider-icon">${getProviderIcon(p.name)}</span>
                                <span class="provider-name">${capitalize(p.name)}</span>
                                <span class="provider-version">${p.latestVersion}</span>
                            </a>
                        `).join('');
                    }

                    function renderProviderCards(providers) {
                        const cards = document.getElementById('provider-cards');
                        cards.innerHTML = providers.map(p => `
                            <a href="${p.name}/index.html" class="provider-card">
                                <div class="card-icon">${getProviderIcon(p.name)}</div>
                                <div class="card-content">
                                    <h3>${capitalize(p.name)} Provider</h3>
                                    <p>${p.resourceCount} resources</p>
                                    <span class="card-version">v${p.latestVersion}</span>
                                </div>
                            </a>
                        `).join('');
                    }

                    function getProviderIcon(name) {
                        const icons = { aws: '☁️', azure: '🔷', gcp: '🌐', files: '📁' };
                        return icons[name.toLowerCase()] || '📦';
                    }

                    function capitalize(s) {
                        return s.charAt(0).toUpperCase() + s.slice(1);
                    }

                    function filterProviders(query) {
                        const items = document.querySelectorAll('#provider-list .nav-item');
                        const q = query.toLowerCase();
                        items.forEach(item => {
                            const name = item.dataset.provider.toLowerCase();
                            item.style.display = name.includes(q) ? '' : 'none';
                        });
                    }
                </script>
            </body>
            </html>
            """;
    }

    private String generateProviderIndex(String providerName) {
        var displayName = capitalize(providerName);
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>%s Provider - Kite Documentation</title>
                <meta name="description" content="Documentation for the Kite %s provider - manage %s infrastructure as code.">
                <link rel="stylesheet" href="../styles.css">
                <script src="../scripts.js" defer></script>
                <link rel="icon" href="data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 100 100'><text y='.9em' font-size='90'>🪁</text></svg>">
            </head>
            <body>
                <div class="layout">
                    <nav class="sidebar">
                        <div class="sidebar-header">
                            <a href="../index.html" class="logo">🪁 Kite Providers</a>
                            <button class="theme-toggle" onclick="toggleTheme()" title="Toggle theme">🌙</button>
                        </div>
                        <div class="sidebar-controls">
                            <select id="version-select" class="version-select" onchange="changeVersion(this.value)">
                                <option>Loading...</option>
                            </select>
                        </div>
                        <div class="sidebar-search">
                            <input type="text" id="resource-search" placeholder="Search resources..." oninput="filterResources(this.value)">
                        </div>
                        <div class="sidebar-content">
                            <div id="resource-list" class="nav-section">
                                <div class="loading">Loading resources...</div>
                            </div>
                        </div>
                    </nav>
                    <main class="content">
                        <div class="content-header">
                            <nav class="breadcrumb">
                                <a href="../index.html">Providers</a> / <span>%s</span>
                            </nav>
                            <h1>%s Provider</h1>
                        </div>
                        <div class="content-body">
                            <p class="lead">Infrastructure as code resources for %s.</p>

                            <section id="resources-overview">
                                <h2>Resources</h2>
                                <div id="resource-cards" class="resource-cards">
                                    <div class="loading">Loading resources...</div>
                                </div>
                            </section>

                            <section id="changelog-section" style="display:none;">
                                <h2>What's New</h2>
                                <div id="changelog-content"></div>
                            </section>
                        </div>
                    </main>
                </div>
                <script>
                    const providerName = '%s';
                    let currentVersion = null;
                    let versions = [];

                    document.addEventListener('DOMContentLoaded', init);

                    async function init() {
                        await loadVersions();
                        if (versions.length > 0) {
                            currentVersion = versions[0];
                            await loadResources(currentVersion);
                            await loadChangelog(currentVersion);
                        }
                    }

                    async function loadVersions() {
                        try {
                            const response = await fetch('versions.json');
                            const data = await response.json();
                            versions = data.versions;
                            const select = document.getElementById('version-select');
                            select.innerHTML = versions.map(v =>
                                `<option value="${v}">${v}${v === data.latest ? ' (latest)' : ''}</option>`
                            ).join('');
                        } catch (e) {
                            console.error('Failed to load versions', e);
                        }
                    }

                    async function loadResources(version) {
                        try {
                            const response = await fetch(`${version}/manifest.json`);
                            const data = await response.json();
                            renderResourceList(data.resources, version);
                            renderResourceCards(data.resources, version);
                        } catch (e) {
                            document.getElementById('resource-list').innerHTML = '<div class="error">Failed to load resources</div>';
                        }
                    }

                    async function loadChangelog(version) {
                        try {
                            const response = await fetch(`${version}/changelog.json`);
                            if (response.ok) {
                                const data = await response.json();
                                if (data.changes && (data.changes.added.length > 0 || data.changes.removed.length > 0 || Object.keys(data.changes.modified).length > 0)) {
                                    renderChangelog(data);
                                    document.getElementById('changelog-section').style.display = '';
                                }
                            }
                        } catch (e) {
                            // No changelog available
                        }
                    }

                    function renderResourceList(resources, version) {
                        const byDomain = groupByDomain(resources);
                        const list = document.getElementById('resource-list');
                        let html = '';
                        for (const [domain, items] of Object.entries(byDomain)) {
                            html += `<div class="nav-group">
                                <div class="nav-group-header">${getDomainIcon(domain)} ${capitalize(domain)}</div>
                                ${items.map(r => `<a href="${version}/${r.name}.html" class="nav-item">${r.name}</a>`).join('')}
                            </div>`;
                        }
                        list.innerHTML = html;
                    }

                    function renderResourceCards(resources, version) {
                        const byDomain = groupByDomain(resources);
                        const cards = document.getElementById('resource-cards');
                        let html = '';
                        for (const [domain, items] of Object.entries(byDomain)) {
                            html += `<div class="domain-section">
                                <h3>${getDomainIcon(domain)} ${capitalize(domain)}</h3>
                                <div class="card-grid">
                                    ${items.map(r => `
                                        <a href="${version}/${r.name}.html" class="resource-card">
                                            <div class="card-title">${r.name}</div>
                                            <div class="card-meta">${r.propertyCount} properties</div>
                                        </a>
                                    `).join('')}
                                </div>
                            </div>`;
                        }
                        cards.innerHTML = html;
                    }

                    function renderChangelog(data) {
                        let html = `<p>Changes from ${data.previousVersion} to ${data.version}:</p>`;
                        if (data.changes.added.length > 0) {
                            html += `<h4>Added</h4><ul>${data.changes.added.map(r => `<li class="added">${r}</li>`).join('')}</ul>`;
                        }
                        if (data.changes.removed.length > 0) {
                            html += `<h4>Removed</h4><ul>${data.changes.removed.map(r => `<li class="removed">${r}</li>`).join('')}</ul>`;
                        }
                        const modified = Object.entries(data.changes.modified);
                        if (modified.length > 0) {
                            html += `<h4>Modified</h4><ul>${modified.map(([name, changes]) => {
                                let details = [];
                                if (changes.addedProperties?.length) details.push(`+${changes.addedProperties.length} properties`);
                                if (changes.removedProperties?.length) details.push(`-${changes.removedProperties.length} properties`);
                                return `<li class="modified">${name} (${details.join(', ')})</li>`;
                            }).join('')}</ul>`;
                        }
                        document.getElementById('changelog-content').innerHTML = html;
                    }

                    function changeVersion(version) {
                        currentVersion = version;
                        loadResources(version);
                        loadChangelog(version);
                    }

                    function groupByDomain(resources) {
                        const groups = {};
                        resources.forEach(r => {
                            const domain = r.domain || 'general';
                            if (!groups[domain]) groups[domain] = [];
                            groups[domain].push(r);
                        });
                        return groups;
                    }

                    function getDomainIcon(domain) {
                        const icons = {networking:'🌐',compute:'💻',storage:'💾',database:'🗄️',dns:'📍',loadbalancing:'⚖️',security:'🔒',monitoring:'📊',container:'📦',core:'⚙️'};
                        return icons[domain] || '📄';
                    }

                    function capitalize(s) { return s.charAt(0).toUpperCase() + s.slice(1); }

                    function filterResources(query) {
                        const items = document.querySelectorAll('#resource-list .nav-item');
                        const q = query.toLowerCase();
                        items.forEach(item => {
                            item.style.display = item.textContent.toLowerCase().includes(q) ? '' : 'none';
                        });
                    }
                </script>
            </body>
            </html>
            """.formatted(displayName, displayName, displayName, displayName, displayName, displayName, providerName);
    }

    private String generateStyles() {
        return """
            :root {
                --bg-primary: #ffffff;
                --bg-secondary: #f8f9fa;
                --bg-tertiary: #e9ecef;
                --text-primary: #212529;
                --text-secondary: #6c757d;
                --text-muted: #adb5bd;
                --border-color: #dee2e6;
                --accent-color: #0d6efd;
                --accent-hover: #0b5ed7;
                --success-color: #198754;
                --warning-color: #ffc107;
                --error-color: #dc3545;
                --code-bg: #f8f9fa;
                --sidebar-width: 280px;
            }

            [data-theme="dark"] {
                --bg-primary: #1a1a2e;
                --bg-secondary: #16213e;
                --bg-tertiary: #0f3460;
                --text-primary: #e9ecef;
                --text-secondary: #adb5bd;
                --text-muted: #6c757d;
                --border-color: #343a40;
                --accent-color: #4dabf7;
                --accent-hover: #74c0fc;
                --code-bg: #0f3460;
            }

            * { box-sizing: border-box; margin: 0; padding: 0; }

            body {
                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                background: var(--bg-primary);
                color: var(--text-primary);
                line-height: 1.6;
            }

            .layout {
                display: flex;
                min-height: 100vh;
            }

            /* Sidebar */
            .sidebar {
                width: var(--sidebar-width);
                background: var(--bg-secondary);
                border-right: 1px solid var(--border-color);
                display: flex;
                flex-direction: column;
                position: fixed;
                height: 100vh;
                overflow: hidden;
            }

            .sidebar-header {
                padding: 1rem;
                display: flex;
                justify-content: space-between;
                align-items: center;
                border-bottom: 1px solid var(--border-color);
            }

            .logo {
                font-size: 1.1rem;
                font-weight: 600;
                color: var(--text-primary);
                text-decoration: none;
            }

            .theme-toggle {
                background: none;
                border: none;
                font-size: 1.2rem;
                cursor: pointer;
                padding: 0.25rem;
                border-radius: 4px;
            }

            .theme-toggle:hover { background: var(--bg-tertiary); }

            .sidebar-controls {
                padding: 0.75rem 1rem;
                border-bottom: 1px solid var(--border-color);
            }

            .version-select {
                width: 100%;
                padding: 0.5rem;
                border: 1px solid var(--border-color);
                border-radius: 4px;
                background: var(--bg-primary);
                color: var(--text-primary);
                font-size: 0.9rem;
            }

            .sidebar-search {
                padding: 0.75rem 1rem;
                border-bottom: 1px solid var(--border-color);
            }

            .sidebar-search input {
                width: 100%;
                padding: 0.5rem 0.75rem;
                border: 1px solid var(--border-color);
                border-radius: 4px;
                background: var(--bg-primary);
                color: var(--text-primary);
                font-size: 0.9rem;
            }

            .sidebar-content {
                flex: 1;
                overflow-y: auto;
                padding: 0.5rem 0;
            }

            .nav-section { padding: 0.5rem 0; }

            .nav-group { margin-bottom: 0.5rem; }

            .nav-group-header {
                padding: 0.5rem 1rem;
                font-size: 0.75rem;
                font-weight: 600;
                text-transform: uppercase;
                color: var(--text-secondary);
                letter-spacing: 0.05em;
            }

            .nav-item {
                display: flex;
                align-items: center;
                gap: 0.5rem;
                padding: 0.5rem 1rem 0.5rem 1.5rem;
                color: var(--text-primary);
                text-decoration: none;
                font-size: 0.9rem;
                transition: background 0.15s;
            }

            .nav-item:hover { background: var(--bg-tertiary); }
            .nav-item.active { background: var(--accent-color); color: white; }

            .provider-icon { font-size: 1.1rem; }
            .provider-version {
                margin-left: auto;
                font-size: 0.75rem;
                color: var(--text-muted);
            }

            /* Main Content */
            .content {
                flex: 1;
                margin-left: var(--sidebar-width);
                min-height: 100vh;
            }

            .content-header {
                padding: 1.5rem 2rem;
                border-bottom: 1px solid var(--border-color);
                background: var(--bg-secondary);
            }

            .breadcrumb {
                font-size: 0.85rem;
                color: var(--text-secondary);
                margin-bottom: 0.5rem;
            }

            .breadcrumb a { color: var(--accent-color); text-decoration: none; }
            .breadcrumb a:hover { text-decoration: underline; }

            .content-header h1 {
                font-size: 1.75rem;
                font-weight: 600;
            }

            .content-body {
                padding: 2rem;
                max-width: 1200px;
            }

            .lead {
                font-size: 1.1rem;
                color: var(--text-secondary);
                margin-bottom: 2rem;
            }

            h2 {
                font-size: 1.4rem;
                margin: 2rem 0 1rem;
                padding-bottom: 0.5rem;
                border-bottom: 1px solid var(--border-color);
            }

            h3 {
                font-size: 1.1rem;
                margin: 1.5rem 0 0.75rem;
            }

            /* Provider Cards */
            .provider-cards {
                display: grid;
                grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
                gap: 1rem;
                margin: 1.5rem 0;
            }

            .provider-card {
                display: flex;
                align-items: center;
                gap: 1rem;
                padding: 1.25rem;
                background: var(--bg-secondary);
                border: 1px solid var(--border-color);
                border-radius: 8px;
                text-decoration: none;
                color: var(--text-primary);
                transition: all 0.2s;
            }

            .provider-card:hover {
                border-color: var(--accent-color);
                transform: translateY(-2px);
                box-shadow: 0 4px 12px rgba(0,0,0,0.1);
            }

            .card-icon { font-size: 2rem; }
            .card-content h3 { margin: 0 0 0.25rem; font-size: 1rem; }
            .card-content p { margin: 0; font-size: 0.85rem; color: var(--text-secondary); }
            .card-version {
                font-size: 0.75rem;
                color: var(--text-muted);
                background: var(--bg-tertiary);
                padding: 0.15rem 0.5rem;
                border-radius: 4px;
                margin-top: 0.5rem;
                display: inline-block;
            }

            /* Resource Cards */
            .resource-cards { margin: 1rem 0; }

            .domain-section { margin-bottom: 2rem; }
            .domain-section h3 { margin-bottom: 1rem; }

            .card-grid {
                display: grid;
                grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
                gap: 0.75rem;
            }

            .resource-card {
                padding: 1rem;
                background: var(--bg-secondary);
                border: 1px solid var(--border-color);
                border-radius: 6px;
                text-decoration: none;
                color: var(--text-primary);
                transition: all 0.2s;
            }

            .resource-card:hover {
                border-color: var(--accent-color);
                background: var(--bg-tertiary);
            }

            .card-title { font-weight: 500; margin-bottom: 0.25rem; }
            .card-meta { font-size: 0.8rem; color: var(--text-secondary); }

            /* Code blocks */
            .code-block {
                background: var(--code-bg);
                border: 1px solid var(--border-color);
                border-radius: 6px;
                padding: 1rem;
                overflow-x: auto;
                font-family: 'SF Mono', Monaco, 'Courier New', monospace;
                font-size: 0.85rem;
                line-height: 1.5;
            }

            /* Changelog */
            .added { color: var(--success-color); }
            .removed { color: var(--error-color); }
            .modified { color: var(--warning-color); }

            /* Loading/Error states */
            .loading, .error {
                padding: 2rem;
                text-align: center;
                color: var(--text-secondary);
            }

            .error { color: var(--error-color); }

            /* Responsive */
            @media (max-width: 768px) {
                .sidebar {
                    position: fixed;
                    left: -100%;
                    z-index: 100;
                    transition: left 0.3s;
                }
                .sidebar.open { left: 0; }
                .content { margin-left: 0; }
            }
            """;
    }

    private String generateScripts() {
        return """
            // Theme management
            function initTheme() {
                const saved = localStorage.getItem('kite-theme');
                const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
                const theme = saved || (prefersDark ? 'dark' : 'light');
                document.documentElement.setAttribute('data-theme', theme);
                updateThemeIcon(theme);
            }

            function toggleTheme() {
                const current = document.documentElement.getAttribute('data-theme');
                const next = current === 'dark' ? 'light' : 'dark';
                document.documentElement.setAttribute('data-theme', next);
                localStorage.setItem('kite-theme', next);
                updateThemeIcon(next);
            }

            function updateThemeIcon(theme) {
                const btn = document.querySelector('.theme-toggle');
                if (btn) btn.textContent = theme === 'dark' ? '☀️' : '🌙';
            }

            // Initialize on load
            initTheme();
            """;
    }

    private String generateRobotsTxt() {
        return """
            # Kite Provider Documentation
            # %s

            User-agent: *
            Allow: /

            Sitemap: %s/sitemap.xml
            """.formatted(baseUrl, baseUrl);
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
