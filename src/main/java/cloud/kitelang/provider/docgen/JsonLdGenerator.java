package cloud.kitelang.provider.docgen;

import cloud.kitelang.provider.docgen.DocGeneratorBase.ProviderInfo;
import cloud.kitelang.provider.docgen.DocGeneratorBase.ResourceInfo;

import java.util.List;
import java.util.Map;

/**
 * Generates JSON-LD structured data for SEO (Schema.org markup).
 */
class JsonLdGenerator {

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
                baseUrl, providerName, resource.getName(),
                resource.getName(),
                capitalize(domain),
                providerName,
                resource.getName().toLowerCase(),
                // BreadcrumbList
                providerDisplay,
                baseUrl, providerName,
                capitalize(domain),
                baseUrl, providerName, getFirstResourceInDomain(domain, resource),
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

    String generateIndexJsonLd(Map<String, List<ResourceInfo>> byDomain) {
        var providerName = providerInfo.getName().toLowerCase();
        var providerDisplay = capitalize(providerInfo.getName());

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
                baseUrl, providerName,
                baseUrl, providerName
            );
    }

    private String getFirstResourceInDomain(String domain, ResourceInfo current) {
        return resources.stream()
                .filter(r -> domain.equals(r.getDomain() != null ? r.getDomain() : "general"))
                .findFirst()
                .map(ResourceInfo::getName)
                .orElse(current.getName());
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
