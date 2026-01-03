package cloud.kitelang.provider.docgen;

import cloud.kitelang.provider.KiteProvider;

import java.nio.file.Path;

/**
 * Command-line interface for generating provider documentation.
 * Used by the Gradle plugin to generate docs during build.
 *
 * <p>Usage:</p>
 * <pre>
 * java -cp ... cloud.kitelang.provider.docgen.DocGeneratorCli \
 *     --provider com.example.MyProvider \
 *     --output docs \
 *     --version 0.1.0 \
 *     --format html,markdown,schemas
 * </pre>
 */
public class DocGeneratorCli {

    public static void main(String[] args) throws Exception {
        String providerClass = null;
        String outputDir = "docs";
        String version = null;
        String formats = "html,markdown,schemas";

        // Parse arguments
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--provider", "-p" -> providerClass = args[++i];
                case "--output", "-o" -> outputDir = args[++i];
                case "--version", "-v" -> version = args[++i];
                case "--format", "-f" -> formats = args[++i];
                case "--help", "-h" -> {
                    printHelp();
                    return;
                }
            }
        }

        if (providerClass == null) {
            System.err.println("Error: --provider is required");
            printHelp();
            System.exit(1);
        }

        // Load provider class
        var clazz = Class.forName(providerClass);
        if (!KiteProvider.class.isAssignableFrom(clazz)) {
            System.err.println("Error: " + providerClass + " does not extend KiteProvider");
            System.exit(1);
        }

        @SuppressWarnings("unchecked")
        var providerInstance = (KiteProvider) clazz.getDeclaredConstructor().newInstance();

        // Generate documentation
        var generator = new DocGenerator(providerInstance);
        var outputPath = Path.of(outputDir);

        for (var format : formats.split(",")) {
            switch (format.trim().toLowerCase()) {
                case "html" -> {
                    if (version != null) {
                        // Versioned: non-versioned index at root, resources in version subdirectory
                        generator.generateVersionedHtml(outputPath, version);
                        System.out.println("Generated versioned HTML documentation:");
                        System.out.println("  - Shared assets at " + outputPath);
                        System.out.println("  - Resource pages at " + outputPath.resolve(version));
                    } else {
                        // Legacy: all files in html/ subdirectory
                        generator.generateHtml(outputPath.resolve("html"));
                        System.out.println("Generated HTML documentation in " + outputPath.resolve("html"));
                    }
                }
                case "markdown", "md" -> {
                    var mdPath = version != null ? outputPath.resolve(version).resolve("markdown") : outputPath.resolve("markdown");
                    generator.generateMarkdown(mdPath);
                    System.out.println("Generated Markdown documentation in " + mdPath);
                }
                case "combined-markdown", "combined-md" -> {
                    var refPath = version != null ? outputPath.resolve(version).resolve("REFERENCE.md") : outputPath.resolve("REFERENCE.md");
                    generator.generateCombinedMarkdown(refPath);
                    System.out.println("Generated combined Markdown in " + refPath);
                }
                case "schemas", "kite" -> {
                    var schemaPath = version != null ? outputPath.resolve(version).resolve("schemas") : outputPath.resolve("schemas");
                    generator.generateKite(schemaPath);
                    System.out.println("Generated Kite schemas in " + schemaPath);
                }
                default -> System.err.println("Unknown format: " + format);
            }
        }

        System.out.println("Documentation generation complete!");
    }

    private static void printHelp() {
        System.out.println("""
            Kite Provider Documentation Generator

            Usage:
              java -cp ... cloud.kitelang.provider.docgen.DocGeneratorCli [options]

            Options:
              --provider, -p <class>   Provider class name (required)
              --output, -o <dir>       Output directory (default: docs)
              --version, -v <version>  Provider version for versioned docs structure
                                       When set, creates non-versioned index.html at root
                                       with resource pages in {version}/ subdirectory
              --format, -f <formats>   Comma-separated formats (default: html,markdown,schemas)
                                       - html: Interactive HTML pages
                                       - markdown: Markdown files
                                       - combined-markdown: Single REFERENCE.md
                                       - schemas: Kite schema files (.kite)
              --help, -h               Show this help message

            Example (versioned):
              java -cp app.jar cloud.kitelang.provider.docgen.DocGeneratorCli \\
                  --provider cloud.kitelang.provider.aws.AwsProvider \\
                  --output docs \\
                  --version 0.1.0 \\
                  --format html,markdown,schemas

            Example (legacy):
              java -cp app.jar cloud.kitelang.provider.docgen.DocGeneratorCli \\
                  --provider cloud.kitelang.provider.aws.AwsProvider \\
                  --output docs/0.1.0 \\
                  --format html,markdown,schemas
            """);
    }
}
