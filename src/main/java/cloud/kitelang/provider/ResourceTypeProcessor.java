package cloud.kitelang.provider;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.HashSet;
import java.util.Set;

/**
 * Annotation processor that discovers ResourceTypeHandler classes and provider main classes at compile time.
 * Generates:
 * <ul>
 *   <li>META-INF/kite/resource-types.txt - listing all ResourceTypeHandler subclasses</li>
 *   <li>META-INF/kite/provider-main.txt - the main ProviderServer class</li>
 * </ul>
 *
 * <p>This enables GraalVM native-image compatibility by avoiding runtime classpath scanning,
 * and allows the Gradle plugin to auto-detect the main class.</p>
 */
@SupportedAnnotationTypes("cloud.kitelang.api.annotations.TypeName")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class ResourceTypeProcessor extends AbstractProcessor {

    private static final String RESOURCE_TYPES_PATH = "META-INF/kite/resource-types.txt";
    private static final String PROVIDER_MAIN_PATH = "META-INF/kite/provider-main.txt";
    private final Set<String> resourceTypeClasses = new HashSet<>();
    private String providerMainClass = null;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (var annotation : annotations) {
            for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
                if (element instanceof TypeElement resourceClass) {
                    // Find ResourceTypeHandler<ThisClass> in the same package
                    findResourceTypeFor(resourceClass);
                    // Also look for ProviderServer subclass in the same package
                    findProviderServerIn(resourceClass);
                }
            }
        }

        // Write manifests on final round
        if (roundEnv.processingOver()) {
            if (!resourceTypeClasses.isEmpty()) {
                writeResourceTypesManifest();
            }
            if (providerMainClass != null) {
                writeProviderMainManifest();
            }
        }

        return false; // Don't claim the annotation
    }

    /**
     * Find a ResourceTypeHandler class that handles the given resource class.
     * Looks for classes that extend ResourceTypeHandler<ResourceClass>.
     */
    private void findResourceTypeFor(TypeElement resourceClass) {
        var packageElement = processingEnv.getElementUtils().getPackageOf(resourceClass);
        var resourceClassName = resourceClass.getQualifiedName().toString();

        // Look for classes in the same package that extend ResourceTypeHandler<resourceClass>
        for (Element sibling : packageElement.getEnclosedElements()) {
            if (sibling instanceof TypeElement candidate) {
                if (isResourceTypeFor(candidate, resourceClassName)) {
                    resourceTypeClasses.add(candidate.getQualifiedName().toString());
                    processingEnv.getMessager().printMessage(
                            Diagnostic.Kind.NOTE,
                            "Found ResourceTypeHandler: " + candidate.getQualifiedName() + " for " + resourceClassName);
                }
            }
        }
    }

    /**
     * Check if candidate extends ResourceTypeHandler<resourceClassName>.
     */
    private boolean isResourceTypeFor(TypeElement candidate, String resourceClassName) {
        TypeMirror superclass = candidate.getSuperclass();

        while (superclass instanceof DeclaredType declaredType) {
            var typeElement = (TypeElement) declaredType.asElement();
            var typeName = typeElement.getQualifiedName().toString();

            if (typeName.equals("cloud.kitelang.provider.ResourceTypeHandler")) {
                // Check type argument
                var typeArgs = declaredType.getTypeArguments();
                if (!typeArgs.isEmpty()) {
                    var typeArg = typeArgs.get(0);
                    if (typeArg instanceof DeclaredType argType) {
                        var argElement = (TypeElement) argType.asElement();
                        return argElement.getQualifiedName().toString().equals(resourceClassName);
                    }
                }
                return false;
            }

            // Walk up the hierarchy
            superclass = typeElement.getSuperclass();
        }

        return false;
    }

    /**
     * Find a ProviderServer subclass in the same package as the resource class.
     */
    private void findProviderServerIn(TypeElement resourceClass) {
        if (providerMainClass != null) {
            return; // Already found
        }

        var packageElement = processingEnv.getElementUtils().getPackageOf(resourceClass);

        // Look for classes in the same package that extend ProviderServer
        for (Element sibling : packageElement.getEnclosedElements()) {
            if (sibling instanceof TypeElement candidate) {
                if (extendsProviderServer(candidate)) {
                    providerMainClass = candidate.getQualifiedName().toString();
                    processingEnv.getMessager().printMessage(
                            Diagnostic.Kind.NOTE,
                            "Found ProviderServer: " + providerMainClass);
                    return;
                }
            }
        }
    }

    /**
     * Check if candidate extends ProviderServer.
     */
    private boolean extendsProviderServer(TypeElement candidate) {
        TypeMirror superclass = candidate.getSuperclass();

        while (superclass instanceof DeclaredType declaredType) {
            var typeElement = (TypeElement) declaredType.asElement();
            var typeName = typeElement.getQualifiedName().toString();

            if (typeName.equals("cloud.kitelang.provider.ProviderServer")) {
                return true;
            }

            // Walk up the hierarchy
            superclass = typeElement.getSuperclass();
        }

        return false;
    }

    /**
     * Write the manifest file with discovered ResourceTypeHandler classes.
     */
    private void writeResourceTypesManifest() {
        try {
            FileObject file = processingEnv.getFiler().createResource(
                    StandardLocation.CLASS_OUTPUT, "", RESOURCE_TYPES_PATH);

            try (Writer writer = file.openWriter()) {
                writer.write("# Generated by ResourceTypeProcessor - do not edit\n");
                for (String className : resourceTypeClasses) {
                    writer.write(className);
                    writer.write("\n");
                }
            }

            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.NOTE,
                    "Generated " + RESOURCE_TYPES_PATH + " with " + resourceTypeClasses.size() + " resource types");

        } catch (IOException e) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "Failed to write resource types manifest: " + e.getMessage());
        }
    }

    /**
     * Write the manifest file with the discovered ProviderServer main class.
     */
    private void writeProviderMainManifest() {
        try {
            FileObject file = processingEnv.getFiler().createResource(
                    StandardLocation.CLASS_OUTPUT, "", PROVIDER_MAIN_PATH);

            try (Writer writer = file.openWriter()) {
                writer.write(providerMainClass);
                writer.write("\n");
            }

            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.NOTE,
                    "Generated " + PROVIDER_MAIN_PATH + " with main class: " + providerMainClass);

        } catch (IOException e) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "Failed to write provider main manifest: " + e.getMessage());
        }
    }
}
