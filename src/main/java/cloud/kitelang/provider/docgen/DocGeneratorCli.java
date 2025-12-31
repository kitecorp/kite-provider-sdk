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
 *     --output build/docs \
 *     --format html,markdown
 * </pre>
 */
public class DocGeneratorCli {

    public static void main(String[] args) throws Exception {
        String providerClass = null;
        String outputDir = "build/docs/provider";
        String formats = "html,markdown,kite";

        // Parse arguments
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--provider", "-p" -> providerClass = args[++i];
                case "--output", "-o" -> outputDir = args[++i];
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
                    generator.generateHtml(outputPath.resolve("html"));
                    System.out.println("Generated HTML documentation in " + outputPath.resolve("html"));
                }
                case "markdown", "md" -> {
                    generator.generateMarkdown(outputPath.resolve("markdown"));
                    System.out.println("Generated Markdown documentation in " + outputPath.resolve("markdown"));
                }
                case "combined-markdown", "combined-md" -> {
                    generator.generateCombinedMarkdown(outputPath.resolve("REFERENCE.md"));
                    System.out.println("Generated combined Markdown in " + outputPath.resolve("REFERENCE.md"));
                }
                case "kite" -> {
                    generator.generateKite(outputPath.resolve("kite"));
                    System.out.println("Generated Kite schemas in " + outputPath.resolve("kite"));
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
              --output, -o <dir>       Output directory (default: build/docs/provider)
              --format, -f <formats>   Comma-separated formats (default: html,markdown)
                                       - html: Interactive HTML pages
                                       - markdown: Markdown files
                                       - combined-markdown: Single REFERENCE.md
                                       - kite: Kite schema files (.kite)
              --help, -h               Show this help message

            Example:
              java -cp app.jar cloud.kitelang.provider.docgen.DocGeneratorCli \\
                  --provider cloud.kitelang.provider.aws.AwsProvider \\
                  --output docs \\
                  --format html,markdown,kite
            """);
    }
}
