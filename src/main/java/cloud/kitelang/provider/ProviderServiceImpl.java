package cloud.kitelang.provider;

import cloud.kitelang.api.schema.Schema;
import cloud.kitelang.proto.v1.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.msgpack.jackson.dataformat.MessagePackMapper;

import java.util.List;

/**
 * gRPC service implementation that delegates to a KiteProvider.
 */
@Slf4j
public class ProviderServiceImpl extends ProviderGrpc.ProviderImplBase {
    private final KiteProvider provider;
    private final ObjectMapper msgpackMapper;

    public ProviderServiceImpl(KiteProvider provider) {
        this.provider = provider;
        this.msgpackMapper = new MessagePackMapper();
    }

    @Override
    public void getProviderSchema(GetProviderSchema.Request request,
                                  StreamObserver<GetProviderSchema.Response> responseObserver) {
        log.debug("GetProviderSchema called for provider: {}", provider.getName());

        var responseBuilder = GetProviderSchema.Response.newBuilder();

        // Convert each resource type's schema
        for (var entry : provider.getResourceTypes().entrySet()) {
            var typeName = entry.getKey();
            var resourceType = entry.getValue();
            Schema apiSchema = resourceType.getSchema();

            var protoSchema = convertSchema(apiSchema);
            responseBuilder.putResourceSchemas(typeName, protoSchema);
        }

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void configureProvider(ConfigureProvider.Request request,
                                  StreamObserver<ConfigureProvider.Response> responseObserver) {
        log.debug("ConfigureProvider called");

        try {
            // Deserialize config if provided
            if (request.hasConfig() && !request.getConfig().getMsgpack().isEmpty()) {
                Object config = msgpackMapper.readValue(
                        request.getConfig().getMsgpack().toByteArray(),
                        Object.class);
                provider.configure(config);
            }

            responseObserver.onNext(ConfigureProvider.Response.newBuilder().build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Failed to configure provider", e);
            responseObserver.onNext(ConfigureProvider.Response.newBuilder()
                    .addDiagnostics(errorDiagnostic("Configuration failed", e.getMessage()))
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void validateResourceConfig(ValidateResourceConfig.Request request,
                                       StreamObserver<ValidateResourceConfig.Response> responseObserver) {
        log.debug("ValidateResourceConfig called for type: {}", request.getTypeName());

        var responseBuilder = ValidateResourceConfig.Response.newBuilder();

        try {
            ResourceTypeHandler<Object> resourceType = provider.getResourceType(request.getTypeName());
            if (resourceType == null) {
                responseBuilder.addDiagnostics(errorDiagnostic(
                        "Unknown resource type",
                        "Resource type '" + request.getTypeName() + "' not found"));
            } else {
                Object resource = fromResourcePayload(request.getConfig(), resourceType.getResourceClass());
                List<Diagnostic> diagnostics = resourceType.validate(resource);
                for (Diagnostic d : diagnostics) {
                    responseBuilder.addDiagnostics(convertDiagnostic(d));
                }
            }
        } catch (Exception e) {
            log.error("Validation failed", e);
            responseBuilder.addDiagnostics(errorDiagnostic("Validation error", e.getMessage()));
        }

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void createResource(CreateResource.Request request,
                               StreamObserver<CreateResource.Response> responseObserver) {
        log.debug("CreateResource called for type: {}", request.getTypeName());

        var responseBuilder = CreateResource.Response.newBuilder();

        try {
            ResourceTypeHandler<Object> resourceType = provider.getResourceType(request.getTypeName());
            if (resourceType == null) {
                responseBuilder.addDiagnostics(errorDiagnostic(
                        "Unknown resource type",
                        "Resource type '" + request.getTypeName() + "' not found"));
            } else {
                Object resource = fromResourcePayload(request.getConfig(), resourceType.getResourceClass());
                Object created = resourceType.create(resource);
                responseBuilder.setNewState(toResourcePayload(created));
            }
        } catch (Exception e) {
            log.error("Create failed", e);
            responseBuilder.addDiagnostics(errorDiagnostic("Create failed", e.getMessage()));
        }

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void readResource(ReadResource.Request request,
                             StreamObserver<ReadResource.Response> responseObserver) {
        log.debug("ReadResource called for type: {}", request.getTypeName());

        var responseBuilder = ReadResource.Response.newBuilder();

        try {
            ResourceTypeHandler<Object> resourceType = provider.getResourceType(request.getTypeName());
            if (resourceType == null) {
                responseBuilder.addDiagnostics(errorDiagnostic(
                        "Unknown resource type",
                        "Resource type '" + request.getTypeName() + "' not found"));
            } else {
                Object resource = fromResourcePayload(request.getCurrentState(), resourceType.getResourceClass());
                Object current = resourceType.read(resource);
                if (current != null) {
                    responseBuilder.setNewState(toResourcePayload(current));
                }
            }
        } catch (Exception e) {
            log.error("Read failed", e);
            responseBuilder.addDiagnostics(errorDiagnostic("Read failed", e.getMessage()));
        }

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void updateResource(UpdateResource.Request request,
                               StreamObserver<UpdateResource.Response> responseObserver) {
        log.debug("UpdateResource called for type: {}", request.getTypeName());

        var responseBuilder = UpdateResource.Response.newBuilder();

        try {
            ResourceTypeHandler<Object> resourceType = provider.getResourceType(request.getTypeName());
            if (resourceType == null) {
                responseBuilder.addDiagnostics(errorDiagnostic(
                        "Unknown resource type",
                        "Resource type '" + request.getTypeName() + "' not found"));
            } else {
                Object resource = fromResourcePayload(request.getPlannedState(), resourceType.getResourceClass());
                Object updated = resourceType.update(resource);
                responseBuilder.setNewState(toResourcePayload(updated));
            }
        } catch (Exception e) {
            log.error("Update failed", e);
            responseBuilder.addDiagnostics(errorDiagnostic("Update failed", e.getMessage()));
        }

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void deleteResource(DeleteResource.Request request,
                               StreamObserver<DeleteResource.Response> responseObserver) {
        log.debug("DeleteResource called for type: {}", request.getTypeName());

        var responseBuilder = DeleteResource.Response.newBuilder();

        try {
            ResourceTypeHandler<Object> resourceType = provider.getResourceType(request.getTypeName());
            if (resourceType == null) {
                responseBuilder.addDiagnostics(errorDiagnostic(
                        "Unknown resource type",
                        "Resource type '" + request.getTypeName() + "' not found"));
            } else {
                Object resource = fromResourcePayload(request.getPriorState(), resourceType.getResourceClass());
                boolean deleted = resourceType.delete(resource);
                if (!deleted) {
                    responseBuilder.addDiagnostics(cloud.kitelang.proto.v1.Diagnostic.newBuilder()
                            .setSeverity(cloud.kitelang.proto.v1.Diagnostic.Severity.WARNING)
                            .setSummary("Resource not found")
                            .build());
                }
            }
        } catch (Exception e) {
            log.error("Delete failed", e);
            responseBuilder.addDiagnostics(errorDiagnostic("Delete failed", e.getMessage()));
        }

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void planResourceChange(PlanResourceChange.Request request,
                                   StreamObserver<PlanResourceChange.Response> responseObserver) {
        log.debug("PlanResourceChange called for type: {}", request.getTypeName());

        var responseBuilder = PlanResourceChange.Response.newBuilder();

        try {
            ResourceTypeHandler<Object> resourceType = provider.getResourceType(request.getTypeName());
            if (resourceType == null) {
                responseBuilder.addDiagnostics(errorDiagnostic(
                        "Unknown resource type",
                        "Resource type '" + request.getTypeName() + "' not found"));
            } else {
                Object priorState = request.hasPriorState() && !request.getPriorState().getMsgpack().isEmpty()
                        ? fromResourcePayload(request.getPriorState(), resourceType.getResourceClass())
                        : null;
                Object proposedState = fromResourcePayload(request.getProposedNewState(), resourceType.getResourceClass());
                Object plannedState = resourceType.plan(priorState, proposedState);
                responseBuilder.setPlannedState(toResourcePayload(plannedState));
            }
        } catch (Exception e) {
            log.error("Plan failed", e);
            responseBuilder.addDiagnostics(errorDiagnostic("Plan failed", e.getMessage()));
        }

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void stopProvider(StopProvider.Request request,
                             StreamObserver<StopProvider.Response> responseObserver) {
        log.debug("StopProvider called");

        try {
            provider.stop();
        } catch (Exception e) {
            log.warn("Error during provider stop", e);
        }

        responseObserver.onNext(StopProvider.Response.newBuilder().build());
        responseObserver.onCompleted();

        // Schedule graceful shutdown
        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(100);
                System.exit(0);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /**
     * Convert API Schema to proto Schema.
     */
    private cloud.kitelang.proto.v1.Schema convertSchema(Schema apiSchema) {
        var schemaBuilder = cloud.kitelang.proto.v1.Schema.newBuilder()
                .setVersion(1);

        var blockBuilder = Block.newBuilder();

        for (cloud.kitelang.api.resource.Property property : apiSchema.getProperties()) {
            var propBuilder = cloud.kitelang.proto.v1.Property.newBuilder()
                    .setName(property.name())
                    .setType(ByteString.copyFromUtf8(mapType(property.type(), property.typeClass())))
                    .setRequired(true) // Default to required
                    .setComputed(property.isCloud());

            blockBuilder.addProperties(propBuilder.build());
        }

        schemaBuilder.setBlock(blockBuilder.build());
        return schemaBuilder.build();
    }

    /**
     * Map Java type to Kite type string.
     * Kite types: string, number, boolean, object (for dynamic maps), schema name (for structs)
     *
     * @param type The type name string (lowercase simple name)
     * @param typeClass The actual Java class (used to detect structs with @TypeName)
     */
    private String mapType(Object type, Class<?> typeClass) {
        if (type == null) return "any";

        // First check if it's a struct (class with @TypeName annotation)
        if (typeClass != null && typeClass.isAnnotationPresent(cloud.kitelang.api.annotations.TypeName.class)) {
            var typeName = typeClass.getAnnotation(cloud.kitelang.api.annotations.TypeName.class);
            return typeName.value();  // Return the schema name
        }

        // type is a String from Schema.toSchema() which uses field.getType().getSimpleName().toLowerCase()
        if (type instanceof String typeName) {
            return switch (typeName) {
                case "string" -> "string";
                case "int", "integer", "long", "double", "float", "number" -> "number";
                case "boolean", "bool" -> "boolean";
                case "list", "arraylist" -> "any[]";  // Kite uses array syntax
                case "map", "hashmap", "linkedhashmap" -> "object"; // Dynamic maps use object
                default -> typeName; // pass through as-is (could be a schema name)
            };
        }
        if (type instanceof Class<?> clazz) {
            // Check for @TypeName annotation (struct)
            if (clazz.isAnnotationPresent(cloud.kitelang.api.annotations.TypeName.class)) {
                var typeName = clazz.getAnnotation(cloud.kitelang.api.annotations.TypeName.class);
                return typeName.value();
            }
            if (clazz == String.class) return "string";
            if (clazz == Integer.class || clazz == int.class) return "number";
            if (clazz == Long.class || clazz == long.class) return "number";
            if (clazz == Double.class || clazz == double.class) return "number";
            if (clazz == Float.class || clazz == float.class) return "number";
            if (clazz == Boolean.class || clazz == boolean.class) return "boolean";
            if (List.class.isAssignableFrom(clazz)) return "any[]";
            if (java.util.Map.class.isAssignableFrom(clazz)) return "object";
        }
        return "any";
    }

    /**
     * Convert SDK Diagnostic to proto Diagnostic.
     */
    private cloud.kitelang.proto.v1.Diagnostic convertDiagnostic(Diagnostic d) {
        var builder = cloud.kitelang.proto.v1.Diagnostic.newBuilder()
                .setSeverity(d.getSeverity() == Diagnostic.Severity.ERROR
                        ? cloud.kitelang.proto.v1.Diagnostic.Severity.ERROR
                        : cloud.kitelang.proto.v1.Diagnostic.Severity.WARNING)
                .setSummary(d.getSummary());

        if (d.getDetail() != null) {
            builder.setDetail(d.getDetail());
        }

        if (d.getPropertyPath() != null && !d.getPropertyPath().isEmpty()) {
            var pathBuilder = PropertyPath.newBuilder();
            for (String step : d.getPropertyPath()) {
                pathBuilder.addSteps(PropertyPath.Step.newBuilder()
                        .setPropertyName(step)
                        .build());
            }
            builder.setProperty(pathBuilder.build());
        }

        return builder.build();
    }

    /**
     * Create an error diagnostic.
     */
    private cloud.kitelang.proto.v1.Diagnostic errorDiagnostic(String summary, String detail) {
        return cloud.kitelang.proto.v1.Diagnostic.newBuilder()
                .setSeverity(cloud.kitelang.proto.v1.Diagnostic.Severity.ERROR)
                .setSummary(summary)
                .setDetail(detail != null ? detail : "")
                .build();
    }

    /**
     * Convert a ResourcePayload to a Java object.
     */
    private <T> T fromResourcePayload(ResourcePayload value, Class<T> clazz) throws Exception {
        if (value == null || value.getMsgpack().isEmpty()) {
            return null;
        }
        return msgpackMapper.readValue(value.getMsgpack().toByteArray(), clazz);
    }

    /**
     * Convert a Java object to a ResourcePayload.
     */
    private ResourcePayload toResourcePayload(Object value) throws Exception {
        if (value == null) {
            return ResourcePayload.getDefaultInstance();
        }
        byte[] msgpack = msgpackMapper.writeValueAsBytes(value);
        return ResourcePayload.newBuilder()
                .setMsgpack(ByteString.copyFrom(msgpack))
                .build();
    }
}
