package cloud.kitelang.provider.docgen;

import cloud.kitelang.provider.docgen.DocGeneratorBase.ProviderInfo;
import cloud.kitelang.provider.docgen.DocGeneratorBase.ResourceInfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Generates JSON-LD structured data for SEO (Schema.org markup).
 */
class JsonLdGenerator {

    private static final String TEMPLATE_PATH = "/docgen/templates/";

    private final String baseUrl;
    private final ProviderInfo providerInfo;
    private final List<ResourceInfo> resources;

    JsonLdGenerator(String baseUrl, ProviderInfo providerInfo, List<ResourceInfo> resources) {
        this.baseUrl = baseUrl;
        this.providerInfo = providerInfo;
        this.resources = resources;
    }

    String generateResourceJsonLd(ResourceInfo resource) {
        var domain = resource.getDomain() != null ? resource.getDomain() : "general";
        var provider = providerInfo.getName().toLowerCase();
        var providerDisplay = capitalize(providerInfo.getName());
        var description = resource.getDescription() != null ? escapeJson(resource.getDescription()) : "";
        var importPath = "%s/%s/%s.kite".formatted(provider, domain.toLowerCase(), resource.getName());
        var faqDescription = description.isEmpty()
                ? "Use it to manage your cloud infrastructure."
                : description;
        var propertyCount = String.valueOf(resource.getProperties().stream()
                .filter(p -> !p.isCloudManaged()).count());

        return render(readResource("jsonld-resource.json"), Map.ofEntries(
                Map.entry("BASE_URL", baseUrl),
                Map.entry("PROVIDER", provider),
                Map.entry("PROVIDER_DISPLAY", providerDisplay),
                Map.entry("RESOURCE", resource.getName()),
                Map.entry("RESOURCE_LOWER", resource.getName().toLowerCase()),
                Map.entry("DESCRIPTION", description),
                Map.entry("DOMAIN", capitalize(domain)),
                Map.entry("DOMAIN_LOWER", domain.toLowerCase()),
                Map.entry("FIRST_IN_DOMAIN", getFirstResourceInDomain(domain, resource)),
                Map.entry("IMPORT_PATH", importPath),
                Map.entry("FAQ_DESCRIPTION", faqDescription),
                Map.entry("PROPERTY_COUNT", propertyCount)
        ));
    }

    String generateIndexJsonLd(Map<String, List<ResourceInfo>> byDomain) {
        var provider = providerInfo.getName().toLowerCase();
        var providerDisplay = capitalize(providerInfo.getName());

        return render(readResource("jsonld-index.json"), Map.of(
                "BASE_URL", baseUrl,
                "PROVIDER", provider,
                "PROVIDER_DISPLAY", providerDisplay,
                "VERSION", providerInfo.getVersion(),
                "RESOURCE_COUNT", String.valueOf(resources.size()),
                "CATEGORY_COUNT", String.valueOf(byDomain.size())
        ));
    }

    private String getFirstResourceInDomain(String domain, ResourceInfo current) {
        return resources.stream()
                .filter(r -> domain.equals(r.getDomain() != null ? r.getDomain() : "general"))
                .findFirst()
                .map(ResourceInfo::getName)
                .orElse(current.getName());
    }

    private String readResource(String name) {
        try (var is = getClass().getResourceAsStream(TEMPLATE_PATH + name)) {
            if (is == null) throw new IllegalStateException("Resource not found: " + TEMPLATE_PATH + name);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read resource: " + name, e);
        }
    }

    private String render(String template, Map<String, String> vars) {
        for (var entry : vars.entrySet()) {
            template = template.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return template;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
    }
}
