package cloud.kitelang.provider.docgen;

import cloud.kitelang.provider.KiteProvider;
import cloud.kitelang.provider.ResourceTypeHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates SEO-friendly HTML documentation for Kite provider resources.
 * Creates individual pages for each resource with Terraform-style layout:
 * - Left sidebar: scrollable navigation with categories
 * - Center: resource content
 * - Right sidebar: table of contents for current resource
 *
 * <p>Usage:</p>
 * <pre>{@code
 * var generator = new HtmlDocGenerator(provider);
 * generator.generateVersioned(Path.of("docs"), "1.0.0");
 * }</pre>
 */
public class HtmlDocGenerator extends DocGeneratorBase {

    private static final String BASE_URL = "https://docs.kitelang.cloud";

    private final SeoGenerator seoGenerator;
    private final JsonLdGenerator jsonLdGenerator;
    private final ExampleGenerator exampleGenerator;

    public HtmlDocGenerator(KiteProvider provider) {
        super(provider);
        this.seoGenerator = new SeoGenerator(BASE_URL, providerInfo, resources);
        this.jsonLdGenerator = new JsonLdGenerator(BASE_URL, providerInfo, resources);
        this.exampleGenerator = new ExampleGenerator(resources);
    }

    public HtmlDocGenerator(String providerName, String providerVersion,
                            Map<String, ResourceTypeHandler<?>> resourceTypes) {
        super(providerName, providerVersion, resourceTypes);
        this.seoGenerator = new SeoGenerator(BASE_URL, providerInfo, resources);
        this.jsonLdGenerator = new JsonLdGenerator(BASE_URL, providerInfo, resources);
        this.exampleGenerator = new ExampleGenerator(resources);
    }

    /**
     * Generates HTML documentation with versioned structure.
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
        Files.writeString(docsRoot.resolve("styles.css"), readResource("/docgen/styles.css"));
        Files.writeString(docsRoot.resolve("scripts.js"), readResource("/docgen/scripts.js"));
        Files.writeString(docsRoot.resolve("index.html"), generateIndex(true));
        Files.writeString(docsRoot.resolve("sitemap.xml"), seoGenerator.generateSitemap());
        Files.writeString(docsRoot.resolve("robots.txt"), seoGenerator.generateRobotsTxt());
        Files.writeString(docsRoot.resolve("feed.xml"), seoGenerator.generateRssFeed());
        Files.writeString(docsRoot.resolve("opensearch.xml"), seoGenerator.generateOpenSearch());
        Files.writeString(docsRoot.resolve("changelog.html"), generateChangelog(version));

        // Generate manifest at version root
        Files.writeString(versionDir.resolve("manifest.json"), generateManifest());

        // Generate resource pages in html/ subdirectory
        for (var resource : resources) {
            var resourceHtml = generateResourcePage(resource, "../../");
            Files.writeString(htmlDir.resolve(resource.getName() + ".html"), resourceHtml);
        }
    }

    // ========== Template Rendering ==========

    private String readResource(String path) {
        try (var is = getClass().getResourceAsStream(path)) {
            if (is == null) throw new IllegalStateException("Resource not found: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read resource: " + path, e);
        }
    }

    private String render(String template, Map<String, String> vars) {
        var result = template;
        for (var entry : vars.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    private String renderTemplate(String templatePath, Map<String, String> vars) {
        return render(readResource(templatePath), vars);
    }

    // ========== Page Generation ==========

    private String generateIndex(boolean versioned) {
        var displayName = capitalize(providerInfo.getName());
        var description = "Complete documentation for " + displayName +
                " provider resources. Learn how to configure and manage infrastructure resources with Kite.";
        var exampleResource = resources.isEmpty() ? "ResourceType" : resources.get(0).getName();

        var head = generateHtmlHead(displayName + " Provider Documentation | Kite", description, "index.html");
        var body = renderTemplate("/docgen/templates/index.html", Map.of(
                "PROVIDER_NAME", displayName,
                "PROVIDER_NAME_LOWER", providerInfo.getName().toLowerCase(),
                "VERSION", providerInfo.getVersion(),
                "EXAMPLE_RESOURCE", exampleResource
        ));

        return head + body;
    }

    private String generateChangelog(String version) {
        var displayName = capitalize(providerInfo.getName());

        // The changelog template fetches changelog.json from the version directory
        // We need to update the fetch URL to point to the current version
        var template = readResource("/docgen/templates/changelog.html");
        template = template.replace("{{PROVIDER_NAME}}", displayName);
        template = template.replace("{{VERSION}}", version);
        template = template.replace("{{NAVIGATION}}", ""); // No navigation on changelog page
        template = template.replace("fetch('changelog.json')", "fetch('" + version + "/changelog.json')");

        return template;
    }

    private String generateResourcePage(ResourceInfo resource, String assetPrefix) {
        var sb = new StringBuilder();
        var domain = resource.getDomain() != null ? resource.getDomain() : "general";

        var description = resource.getDescription() != null && !resource.getDescription().isEmpty()
                ? resource.getDescription()
                : "Documentation for " + resource.getName() + " resource in " +
                  capitalize(providerInfo.getName()) + " provider.";

        // Head
        sb.append(generateHtmlHead(
                resource.getName() + " | " + capitalize(providerInfo.getName()) + " Provider | Kite",
                description,
                resource.getName() + ".html"
        ));

        // JSON-LD
        sb.append(jsonLdGenerator.generateResourceJsonLd(resource));

        // Body start
        sb.append(renderTemplate("/docgen/templates/resource-body-start.html", Map.of()));

        // Navigation
        sb.append(generateNavigation(resource.getName()));

        // Resource header
        var descHtml = resource.getDescription() != null && !resource.getDescription().isEmpty()
                ? "<p class=\"resource-desc\" itemprop=\"description\">" + escapeHtml(resource.getDescription()) + "</p>"
                : "";
        sb.append(renderTemplate("/docgen/templates/resource-header.html", Map.of(
                "DOMAIN_ICON", getDomainIcon(domain),
                "DOMAIN_NAME", capitalize(domain),
                "RESOURCE_NAME", resource.getName(),
                "DESCRIPTION", descHtml
        )));

        // Import section
        var importPath = "%s/%s/%s.kite".formatted(
                providerInfo.getName().toLowerCase(),
                domain.toLowerCase(),
                resource.getName()
        );
        sb.append(renderTemplate("/docgen/templates/resource-import.html", Map.of(
                "RESOURCE_NAME", resource.getName(),
                "IMPORT_PATH", importPath
        )));

        // Examples section
        sb.append(renderTemplate("/docgen/templates/resource-examples.html", Map.of(
                "EXAMPLE_BASIC", exampleGenerator.generateBasicExample(resource),
                "EXAMPLE_REFERENCES", exampleGenerator.generateReferencesExample(resource, domain),
                "EXAMPLE_COMPLETE", exampleGenerator.generateCompleteExample(resource)
        )));

        // Schema section
        sb.append(renderTemplate("/docgen/templates/resource-schema.html", Map.of(
                "SCHEMA", exampleGenerator.generateSchema(resource)
        )));

        // Properties section
        sb.append(generatePropertiesSection(resource));

        // Related resources
        sb.append(generateRelatedResources(resource, domain));

        // Resource navigation (prev/next)
        sb.append(generateResourceNav(resource, domain));

        // Footer
        var pageUrl = BASE_URL + "/" + providerInfo.getName().toLowerCase() + "/" + resource.getName() + ".html";
        var pageTitle = resource.getName() + " - Kite " + capitalize(providerInfo.getName()) + " Provider";
        var issueTitle = "Docs: " + resource.getName() + " - ";
        var issueBody = "**Resource:** " + resource.getName() + "%0A**Provider:** " + providerInfo.getName() + "%0A**Page:** " + pageUrl + "%0A%0A**Issue:**%0A";

        sb.append(renderTemplate("/docgen/templates/resource-footer.html", Map.of(
                "PAGE_TITLE_ENCODED", escapeHtml(pageTitle).replace(" ", "%20"),
                "PAGE_URL", pageUrl,
                "ISSUE_TITLE", escapeHtml(issueTitle).replace(" ", "%20"),
                "ISSUE_BODY", issueBody,
                "DATETIME_ISO", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "DATETIME_DISPLAY", LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))
        )));

        // TOC sidebar
        var userProps = resource.getProperties().stream().filter(p -> !p.isCloudManaged()).toList();
        var cloudProps = resource.getProperties().stream().filter(PropertyInfo::isCloudManaged).toList();
        var relatedResources = getRelatedResources(resource, domain);

        var tocExtra = new StringBuilder();
        if (!userProps.isEmpty() || !cloudProps.isEmpty()) {
            tocExtra.append("                <a href=\"#properties\">Properties</a>\n");
        }
        if (!relatedResources.isEmpty()) {
            tocExtra.append("                <a href=\"#related\">Related Resources</a>\n");
        }

        sb.append(renderTemplate("/docgen/templates/resource-toc.html", Map.of(
                "TOC_EXTRA", tocExtra.toString()
        )));

        // Fix asset paths for versioned structure
        var result = sb.toString();
        if (!assetPrefix.isEmpty()) {
            result = result.replace("href=\"styles.css\"", "href=\"" + assetPrefix + "styles.css\"");
            result = result.replace("href=\"scripts.js\"", "href=\"" + assetPrefix + "scripts.js\"");
            result = result.replace("src=\"scripts.js\"", "src=\"" + assetPrefix + "scripts.js\"");
            result = result.replace("href=\"feed.xml\"", "href=\"" + assetPrefix + "feed.xml\"");
            result = result.replace("href=\"opensearch.xml\"", "href=\"" + assetPrefix + "opensearch.xml\"");
            result = result.replace("href=\"index.html\"", "href=\"" + assetPrefix + "index.html\"");
            result = result.replace("href=\"changelog.html\"", "href=\"" + assetPrefix + "changelog.html\"");
            result = result.replace("fetch('manifest.json')", "fetch('../manifest.json')");
        }

        return result;
    }

    private String generateHtmlHead(String title, String description, String canonicalPath) {
        var providerNameLower = providerInfo.getName().toLowerCase();
        var canonicalUrl = BASE_URL + "/" + providerNameLower + "/" + canonicalPath;

        return renderTemplate("/docgen/templates/head.html", Map.of(
            "TITLE", escapeHtml(title),
            "DESCRIPTION", escapeHtml(description),
            "PROVIDER_NAME", capitalize(providerInfo.getName()),
            "PROVIDER_NAME_LOWER", providerNameLower,
            "CANONICAL_URL", canonicalUrl
        ));
    }

    private String generateNavigation(String currentResource) {
        var byDomain = groupByDomain();
        var categories = new StringBuilder();

        for (var entry : byDomain.entrySet()) {
            var domain = entry.getKey();
            var domainResources = entry.getValue();
            var hasActive = currentResource != null &&
                    domainResources.stream().anyMatch(r -> r.getName().equals(currentResource));

            categories.append("""
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
                categories.append("""
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

            categories.append("""
                                </ul>
                            </div>
                """);
        }

        return renderTemplate("/docgen/templates/navigation.html", Map.of(
                "PROVIDER_NAME", capitalize(providerInfo.getName()),
                "VERSION", providerInfo.getVersion(),
                "NAV_CATEGORIES", categories.toString()
        ));
    }

    private String generatePropertiesSection(ResourceInfo resource) {
        var userProps = resource.getProperties().stream()
                .filter(p -> !p.isCloudManaged())
                .toList();
        var cloudProps = resource.getProperties().stream()
                .filter(PropertyInfo::isCloudManaged)
                .toList();

        if (userProps.isEmpty() && cloudProps.isEmpty()) {
            return "";
        }

        var sb = new StringBuilder();
        sb.append("<section id=\"properties\">\n");
        sb.append("<h2>Properties</h2>\n");

        if (!userProps.isEmpty() && !cloudProps.isEmpty()) {
            // Tabbed view
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
            sb.append(generatePropertiesTable(userProps, false));
        } else {
            sb.append("<p class=\"cloud-desc\">Read-only properties set by the cloud provider after resource creation.</p>\n");
            sb.append(generatePropertiesTable(cloudProps, true));
        }

        sb.append("</section>\n");
        return sb.toString();
    }

    private String generatePropertiesTable(List<PropertyInfo> properties, boolean isCloudSection) {
        var rowTemplate = readResource(isCloudSection
                ? "/docgen/templates/properties-row-cloud.html"
                : "/docgen/templates/properties-row-user.html");
        var tableTemplate = readResource(isCloudSection
                ? "/docgen/templates/properties-table-cloud.html"
                : "/docgen/templates/properties-table-user.html");

        var rows = new StringBuilder();
        for (var prop : properties) {
            var deprecatedBadge = prop.isDeprecated()
                    ? " <span class=\"badge badge-deprecated\">deprecated</span>" : "";
            var defaultValue = prop.getDefaultValue() != null && !prop.getDefaultValue().isEmpty()
                    ? "<code class=\"default-value\">" + escapeHtml(prop.getDefaultValue()) + "</code>"
                    : "<span class=\"no-value\">—</span>";
            var requiredBadge = prop.isRequired()
                    ? "<span class=\"badge badge-required\">Yes</span>"
                    : "<span class=\"badge badge-optional\">No</span>";
            var description = prop.getDescription() != null ? escapeHtml(prop.getDescription()) : "";
            var validValues = "";
            if (prop.getValidValues() != null && !prop.getValidValues().isEmpty()) {
                validValues = "<br><span class=\"valid-values-label\">Valid values: </span>" +
                        prop.getValidValues().stream()
                                .map(v -> "<code class=\"valid-value\">" + escapeHtml(v) + "</code>")
                                .reduce((a, b) -> a + " " + b)
                                .orElse("");
            }

            rows.append(render(rowTemplate, Map.of(
                    "NAME", prop.getName(),
                    "TYPE", prop.getType(),
                    "DEPRECATED_BADGE", deprecatedBadge,
                    "DEFAULT", defaultValue,
                    "REQUIRED_BADGE", requiredBadge,
                    "DESCRIPTION", description,
                    "VALID_VALUES", validValues
            )));
        }

        return render(tableTemplate, Map.of("ROWS", rows.toString()));
    }

    private List<ResourceInfo> getRelatedResources(ResourceInfo resource, String domain) {
        return resources.stream()
                .filter(r -> domain.equals(r.getDomain() != null ? r.getDomain() : "general"))
                .filter(r -> !r.getName().equals(resource.getName()))
                .limit(5)
                .toList();
    }

    private String generateRelatedResources(ResourceInfo resource, String domain) {
        var relatedResources = getRelatedResources(resource, domain);
        if (relatedResources.isEmpty()) {
            return "";
        }

        var sb = new StringBuilder();
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

        sb.append("</div>\n</section>\n");
        return sb.toString();
    }

    private String generateResourceNav(ResourceInfo resource, String domain) {
        var domainResources = resources.stream()
                .filter(r -> domain.equals(r.getDomain() != null ? r.getDomain() : "general"))
                .toList();
        var domainIndex = domainResources.indexOf(resource);
        var prev = domainIndex > 0 ? domainResources.get(domainIndex - 1) : null;
        var next = domainIndex < domainResources.size() - 1 ? domainResources.get(domainIndex + 1) : null;

        var sb = new StringBuilder();
        sb.append("<nav class=\"resource-nav\" aria-label=\"Resource navigation\">\n");

        if (prev != null) {
            sb.append("<a href=\"").append(prev.getName()).append(".html\" class=\"nav-prev\" rel=\"prev\" title=\"Previous in ")
              .append(capitalize(domain)).append("\">← ").append(prev.getName()).append("</a>\n");
        } else {
            sb.append("<span class=\"nav-placeholder\"></span>\n");
        }

        sb.append("<span class=\"nav-category-label\">").append(capitalize(domain)).append("</span>\n");

        if (next != null) {
            sb.append("<a href=\"").append(next.getName()).append(".html\" class=\"nav-next\" rel=\"next\" title=\"Next in ")
              .append(capitalize(domain)).append("\">").append(next.getName()).append(" →</a>\n");
        } else {
            sb.append("<span class=\"nav-placeholder\"></span>\n");
        }

        sb.append("</nav>\n");
        return sb.toString();
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

            sb.append("      }\n    }");
            if (i < resourceList.size() - 1) sb.append(",");
            sb.append("\n");
        }

        sb.append("  }\n}\n");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
