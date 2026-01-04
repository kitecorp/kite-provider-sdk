package cloud.kitelang.provider.docgen;

import cloud.kitelang.provider.KiteProvider;
import cloud.kitelang.provider.ResourceTypeHandler;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Facade for generating documentation in multiple formats.
 *
 * <p>This class provides convenience methods that delegate to specialized generators:</p>
 * <ul>
 *   <li>{@link HtmlDocGenerator} - HTML documentation with search and navigation</li>
 *   <li>{@link MarkdownDocGenerator} - Markdown documentation for GitHub/GitLab</li>
 *   <li>{@link KiteSchemaGenerator} - Kite schema files (.kite)</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * var provider = new AwsProvider();
 * var generator = new DocGenerator(provider);
 *
 * // Generate all formats
 * generator.generateHtml(Path.of("docs"), "1.0.0");
 * generator.generateMarkdown(Path.of("docs/md"));
 * generator.generateKite(Path.of("docs/schemas"));
 * }</pre>
 */
public class DocGenerator {

    private final HtmlDocGenerator htmlGenerator;
    private final MarkdownDocGenerator markdownGenerator;
    private final KiteSchemaGenerator kiteGenerator;
    private final String version;

    /**
     * Creates a documentation generator from a provider instance.
     */
    public DocGenerator(KiteProvider provider) {
        this.htmlGenerator = new HtmlDocGenerator(provider);
        this.markdownGenerator = new MarkdownDocGenerator(provider);
        this.kiteGenerator = new KiteSchemaGenerator(provider);
        this.version = provider.getVersion();
    }

    /**
     * Creates a documentation generator from explicit provider info.
     */
    public DocGenerator(String providerName, String providerVersion,
                        Map<String, ResourceTypeHandler<?>> resourceTypes) {
        this.htmlGenerator = new HtmlDocGenerator(providerName, providerVersion, resourceTypes);
        this.markdownGenerator = new MarkdownDocGenerator(providerName, providerVersion, resourceTypes);
        this.kiteGenerator = new KiteSchemaGenerator(providerName, providerVersion, resourceTypes);
        this.version = providerVersion;
    }

    /**
     * Generates versioned HTML documentation.
     * <p>
     * Structure:
     * <pre>
     * docsRoot/
     * ├── index.html      (non-versioned, dynamic)
     * ├── styles.css      (non-versioned)
     * ├── scripts.js      (non-versioned)
     * ├── versions.json   (list of all versions)
     * └── {version}/
     *     ├── manifest.json
     *     └── html/{Resource}.html
     * </pre>
     *
     * @param docsRoot the root docs directory (e.g., aws/docs/)
     * @param version  the current version being generated
     */
    public void generateHtml(Path docsRoot, String version) throws IOException {
        htmlGenerator.generateVersioned(docsRoot, version);
    }

    /**
     * Generates versioned HTML documentation using the provider's version.
     *
     * @param docsRoot the root docs directory
     */
    public void generateHtml(Path docsRoot) throws IOException {
        htmlGenerator.generateVersioned(docsRoot, version);
    }

    /**
     * Generates Markdown documentation.
     *
     * @param outputDir the directory to write files to
     */
    public void generateMarkdown(Path outputDir) throws IOException {
        markdownGenerator.generate(outputDir);
    }

    /**
     * Generates a single combined Markdown file.
     *
     * @param outputFile the file to write to
     */
    public void generateCombinedMarkdown(Path outputFile) throws IOException {
        markdownGenerator.generateCombined(outputFile);
    }

    /**
     * Generates Kite schema files (.kite) organized by domain.
     *
     * @param outputDir the directory to write files to
     */
    public void generateKite(Path outputDir) throws IOException {
        kiteGenerator.generate(outputDir);
    }

    /**
     * Generates all documentation formats to the given base directory.
     *
     * @param baseDir the base directory (HTML at root, md/ and schemas/ subdirs)
     */
    public void generateAll(Path baseDir) throws IOException {
        generateHtml(baseDir, version);
        generateMarkdown(baseDir.resolve("md"));
        generateKite(baseDir.resolve("schemas"));
    }

    /**
     * Returns the HTML generator for direct access.
     */
    public HtmlDocGenerator html() {
        return htmlGenerator;
    }

    /**
     * Returns the Markdown generator for direct access.
     */
    public MarkdownDocGenerator markdown() {
        return markdownGenerator;
    }

    /**
     * Returns the Kite schema generator for direct access.
     */
    public KiteSchemaGenerator kite() {
        return kiteGenerator;
    }
}
