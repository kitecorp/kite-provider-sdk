package cloud.kitelang.provider.docgen;

import cloud.kitelang.provider.KiteProvider;
import cloud.kitelang.provider.ResourceTypeHandler;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Base class for documentation generators with shared resource extraction logic.
 */
@Getter
public abstract class DocGeneratorBase {

    protected final ProviderInfo providerInfo;
    protected final List<ResourceInfo> resources;

    /**
     * Creates a documentation generator from a provider instance.
     */
    protected DocGeneratorBase(KiteProvider provider) {
        this.providerInfo = ProviderInfo.builder()
                .name(provider.getName())
                .version(provider.getVersion())
                .logoUrl(provider.getLogoUrl())
                .build();

        this.resources = provider.getResourceTypes().entrySet().stream()
                .map(entry -> extractResourceInfo(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(ResourceInfo::getName))
                .toList();
    }

    /**
     * Creates a documentation generator from explicit provider info.
     */
    protected DocGeneratorBase(String providerName, String providerVersion,
                               Map<String, ResourceTypeHandler<?>> resourceTypes) {
        this.providerInfo = ProviderInfo.builder()
                .name(providerName)
                .version(providerVersion)
                .build();

        this.resources = resourceTypes.entrySet().stream()
                .map(entry -> extractResourceInfo(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(ResourceInfo::getName))
                .toList();
    }

    private ResourceInfo extractResourceInfo(String typeName, ResourceTypeHandler<?> handler) {
        var schema = handler.getSchema();
        var properties = new ArrayList<PropertyInfo>();

        for (var prop : schema.getProperties()) {
            if (prop.hidden()) continue;

            properties.add(PropertyInfo.builder()
                    .name(prop.name())
                    .type(formatType(prop.type(), prop.typeClass()))
                    .description(prop.description())
                    .required(!isOptionalType(prop.typeClass()))
                    .cloudManaged(prop.cloud())
                    .importable(prop.importable())
                    .deprecated(prop.deprecationMessage() != null && !prop.deprecationMessage().isEmpty())
                    .deprecationMessage(prop.deprecationMessage())
                    .defaultValue(prop.defaultValue())
                    .validValues(prop.validValues())
                    .build());
        }

        return ResourceInfo.builder()
                .name(typeName)
                .domain(extractDomain(handler.getClass()))
                .description(schema.getDescription())
                .properties(properties)
                .build();
    }

    /**
     * Extracts the domain from a handler's package name.
     * e.g., "cloud.kitelang.provider.aws.networking.VpcResourceType" -> "networking"
     */
    protected String extractDomain(Class<?> handlerClass) {
        var packageName = handlerClass.getPackageName();
        var parts = packageName.split("\\.");
        if (parts.length > 0) {
            var lastPart = parts[parts.length - 1];
            if (isKnownDomain(lastPart)) {
                return lastPart;
            }
        }
        return null;
    }

    protected boolean isKnownDomain(String name) {
        return switch (name) {
            case "networking", "compute", "storage", "dns", "loadbalancing",
                 "database", "security", "iam", "monitoring", "core", "container", "files" -> true;
            default -> false;
        };
    }

    protected String formatType(Object type, Class<?> typeClass) {
        if (typeClass == null) {
            return type != null ? type.toString() : "any";
        }

        if (typeClass == String.class) return "string";
        if (typeClass == Integer.class || typeClass == int.class) return "integer";
        if (typeClass == Long.class || typeClass == long.class) return "integer";
        if (typeClass == Boolean.class || typeClass == boolean.class) return "boolean";
        if (typeClass == Double.class || typeClass == double.class) return "number";
        if (typeClass == Float.class || typeClass == float.class) return "number";
        if (List.class.isAssignableFrom(typeClass)) return "list";
        if (Map.class.isAssignableFrom(typeClass)) return "map";
        if (Set.class.isAssignableFrom(typeClass)) return "set";

        return typeClass.getSimpleName().toLowerCase();
    }

    protected boolean isOptionalType(Class<?> typeClass) {
        return typeClass != null && !typeClass.isPrimitive();
    }

    protected int getDomainOrder(String domain) {
        return switch (domain) {
            case "networking" -> 1;
            case "compute" -> 2;
            case "storage" -> 3;
            case "database" -> 4;
            case "dns" -> 5;
            case "loadbalancing" -> 6;
            case "security" -> 7;
            case "iam" -> 8;
            case "monitoring" -> 9;
            case "container" -> 10;
            case "files" -> 11;
            case "core" -> 12;
            default -> 99;
        };
    }

    protected String getDomainIcon(String domain) {
        return switch (domain) {
            case "networking" -> "🌐";
            case "compute" -> "💻";
            case "storage" -> "💾";
            case "database" -> "🗄️";
            case "dns" -> "📍";
            case "loadbalancing" -> "🚦";
            case "security" -> "🔒";
            case "iam" -> "🪪";
            case "monitoring" -> "📊";
            case "container" -> "📦";
            case "files" -> "📁";
            case "core" -> "⚙️";
            default -> "📄";
        };
    }

    protected String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    protected String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /**
     * Groups resources by domain, sorted by domain order.
     */
    protected Map<String, List<ResourceInfo>> groupByDomain() {
        var byDomain = resources.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getDomain() != null ? r.getDomain() : "general",
                        LinkedHashMap::new,
                        Collectors.toList()));

        var sortedDomains = byDomain.keySet().stream()
                .sorted((a, b) -> {
                    int aOrder = getDomainOrder(a);
                    int bOrder = getDomainOrder(b);
                    if (aOrder != bOrder) return aOrder - bOrder;
                    return a.compareTo(b);
                })
                .toList();

        var result = new LinkedHashMap<String, List<ResourceInfo>>();
        for (var domain : sortedDomains) {
            result.put(domain, byDomain.get(domain));
        }
        return result;
    }

    // Data classes
    @Data
    @Builder
    public static class ProviderInfo {
        private String name;
        private String version;
        private String logoUrl;
    }

    @Data
    @Builder
    public static class ResourceInfo {
        private String name;
        private String domain;
        private String description;
        private List<PropertyInfo> properties;
    }

    @Data
    @Builder
    public static class PropertyInfo {
        private String name;
        private String type;
        private String description;
        private boolean required;
        private boolean cloudManaged;
        private boolean importable;
        private boolean deprecated;
        private String deprecationMessage;
        private String defaultValue;
        private List<String> validValues;
    }
}
