package cloud.kitelang.provider.docgen;

import cloud.kitelang.provider.docgen.DocGeneratorBase.ProviderInfo;
import cloud.kitelang.provider.docgen.DocGeneratorBase.ResourceInfo;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generates SEO-related files for documentation: sitemap, robots.txt, RSS feed, OpenSearch.
 */
class SeoGenerator {

    private final String baseUrl;
    private final ProviderInfo providerInfo;
    private final List<ResourceInfo> resources;

    SeoGenerator(String baseUrl, ProviderInfo providerInfo, List<ResourceInfo> resources) {
        this.baseUrl = baseUrl;
        this.providerInfo = providerInfo;
        this.resources = resources;
    }

    String generateSitemap() {
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
            """.formatted(baseUrl, providerName, now));

        for (var resource : resources) {
            sb.append("""
                <url>
                    <loc>%s/%s/%s.html</loc>
                    <lastmod>%s</lastmod>
                    <changefreq>weekly</changefreq>
                    <priority>0.8</priority>
                </url>
                """.formatted(baseUrl, providerName, resource.getName(), now));
        }

        sb.append("</urlset>\n");
        return sb.toString();
    }

    String generateRobotsTxt() {
        var providerName = providerInfo.getName().toLowerCase();
        return """
            # Kite %s Provider Documentation
            # %s/%s/

            User-agent: *
            Allow: /

            # Sitemap location
            Sitemap: %s/%s/sitemap.xml
            """.formatted(
                capitalize(providerInfo.getName()),
                baseUrl,
                providerName,
                baseUrl,
                providerName
            );
    }

    String generateRssFeed() {
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
                baseUrl, providerName,
                baseUrl, providerName,
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
                    baseUrl, providerName, resource.getName(),
                    baseUrl, providerName, resource.getName(),
                    capitalize(domain)
                ));
        }

        sb.append("""
                </channel>
            </rss>
            """);
        return sb.toString();
    }

    String generateOpenSearch() {
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
                baseUrl,
                providerName
            );
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
