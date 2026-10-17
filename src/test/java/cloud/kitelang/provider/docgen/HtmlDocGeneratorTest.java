package cloud.kitelang.provider.docgen;

import cloud.kitelang.api.annotations.Property;
import cloud.kitelang.api.annotations.TypeName;
import cloud.kitelang.provider.ResourceTypeHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HtmlDocGeneratorTest {

    @TypeName("Instance")
    static class Instance {
        @Property(optional = false)
        private String ami;

        @Property
        private String keyName;
    }

    static class InstanceHandler extends ResourceTypeHandler<Instance> {
        @Override
        public Instance create(Instance resource) {
            return resource;
        }

        @Override
        public Instance read(Instance resource) {
            return resource;
        }

        @Override
        public Instance update(Instance resource) {
            return resource;
        }

        @Override
        public boolean delete(Instance resource) {
            return true;
        }
    }

    @Test
    @DisplayName("manifest.json marks a property required exactly when @Property says optional = false")
    void manifestRequiredFollowsTheOptionalFlag(@TempDir Path docsRoot) throws Exception {
        var generator = new HtmlDocGenerator("test", "1.0.0", Map.of("Instance", new InstanceHandler()));

        generator.generateVersioned(docsRoot, "1.0.0");

        // The website reads required from this file (kitecorp/kite-provider-sdk#12)
        var manifest = new ObjectMapper().readTree(docsRoot.resolve("1.0.0/manifest.json").toFile());
        var requiredByName = new LinkedHashMap<String, Boolean>();
        manifest.path("resources").path("Instance").path("properties").properties()
                .forEach(entry -> requiredByName.put(entry.getKey(), entry.getValue().required("required").booleanValue()));
        assertEquals(Map.of("ami", true, "keyName", false), requiredByName);
    }
}
