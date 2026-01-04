package cloud.kitelang.provider.docgen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

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

    private static final String TEMPLATE_PATH = "/docgen/global/";

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
        Files.writeString(docsRoot.resolve("index.html"), readResource("global-index.html"));
        Files.writeString(docsRoot.resolve("styles.css"), readResource("global-styles.css"));
        Files.writeString(docsRoot.resolve("scripts.js"), readResource("global-scripts.js"));
        Files.writeString(docsRoot.resolve("robots.txt"), renderTemplate("robots.txt", Map.of(
                "BASE_URL", baseUrl
        )));
    }

    /**
     * Generates provider-level index.html (version selector, resource list).
     */
    public void generateProviderIndex(Path providerDir, String providerName) throws IOException {
        Files.createDirectories(providerDir);
        Files.writeString(providerDir.resolve("index.html"), renderTemplate("provider-index.html", Map.of(
                "PROVIDER_NAME", providerName,
                "PROVIDER_DISPLAY", capitalize(providerName)
        )));
    }

    private String readResource(String name) {
        try (var is = getClass().getResourceAsStream(TEMPLATE_PATH + name)) {
            if (is == null) throw new IllegalStateException("Resource not found: " + TEMPLATE_PATH + name);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read resource: " + name, e);
        }
    }

    private String renderTemplate(String name, Map<String, String> vars) {
        var template = readResource(name);
        for (var entry : vars.entrySet()) {
            template = template.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return template;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
