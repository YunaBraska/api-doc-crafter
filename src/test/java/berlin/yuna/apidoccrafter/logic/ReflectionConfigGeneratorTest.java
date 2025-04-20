package berlin.yuna.apidoccrafter.logic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static berlin.yuna.apidoccrafter.config.Config.getEncoding;

class ReflectionConfigGeneratorTest {

    public static final String REFLECTION_CONFIG = "src/main/resources/META-INF/native-image/berlin.yuna/api-doc-crafter/reflect-config.json";

    @Test
        // To generate the reflect-config.json file for native image builds use: `./mvnw clean package -B -q -DskipTests -Pnative && java -agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image/berlin.yuna/api-doc-crafter -Dadc_output_dir=target/generated_api -Dadc_work_dir=src/test/resources/files -jar target/api-doc-crafter.jar`
    void generate() throws Exception {
        generateReflectConfig(
            List.of("io.swagger.v3.oas.models", "io.swagger.parser.OpenAPIParser"),
            REFLECTION_CONFIG
        );
    }

    void prettifyReflectionConfig() throws Exception {
        final Path oarentPath = Path.of(REFLECTION_CONFIG).getParent();
        final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        try (var stream = Files.walk(oarentPath)) {
            stream.filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".json"))
                .forEach(path -> {
                    try {
                        final JsonNode tree = mapper.readTree(Files.readString(path));
                        Files.writeString(path, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(tree));
                        System.out.println("💅 Pretty-printed: " + path.toAbsolutePath());
                    } catch (Exception e) {
                        System.err.println("❌ Failed to prettify: " + path + " -> " + e.getMessage());
                    }
                });
        }

        System.out.println("💅 Pretty-printed reflect-config written to: " + oarentPath.toAbsolutePath());
    }

    public static void generateReflectConfig(final List<String> packages, final String outputPath) throws Exception {
        final Path outputFile = Path.of(outputPath);
        final ObjectMapper mapper = new ObjectMapper();
        final List<Map<String, Object>> existingConfig = Files.exists(outputFile)
            ? mapper.readValue(Files.readString(outputFile), List.class)
            : new ArrayList<>();

        final Set<String> existingClassNames = new HashSet<>();
        for (Map<String, Object> item : existingConfig) {
            Object name = item.get("name");
            if (name instanceof String) {
                existingClassNames.add((String) name);
            }
        }

        final String mergerClasses = Files.readString(Path.of(System.getProperty("user.dir"))
            .resolve("src/main/java/berlin/yuna/apidoccrafter/logic/Merger.java"), getEncoding());

        final List<Map<String, Object>> newConfig = new ArrayList<>();

        for (String packageName : packages) {
            for (Class<?> clazz : getClassesForPackage2(packageName)) {
                if (!Modifier.isAbstract(clazz.getModifiers()) && !clazz.isInterface()) {
                    if (!mergerClasses.contains("case " + clazz.getSimpleName())
                        && !clazz.isEnum()
                        && !clazz.isAnnotation()
                        && !Schema.class.isAssignableFrom(clazz)
                        && !Map.class.isAssignableFrom(clazz)
                        && !Collection.class.isAssignableFrom(clazz)
                        && !Parameter.class.isAssignableFrom(clazz)) {
                        throw new AssertionError("Class not covered by Merger: " + clazz.getCanonicalName());
                    }

                    if (!existingClassNames.contains(clazz.getName())) {
                        final Map<String, Object> classConfig = new LinkedHashMap<>();
                        classConfig.put("name", clazz.getName());
                        classConfig.put("allDeclaredFields", true);
                        classConfig.put("allDeclaredMethods", true);
                        classConfig.put("allDeclaredConstructors", true);
                        newConfig.add(classConfig);
                        existingClassNames.add(clazz.getName()); // For future-proofing
                    }
                }
            }
        }

        final List<Map<String, Object>> mergedConfig = new ArrayList<>(existingConfig);
        mergedConfig.addAll(newConfig);

        Files.createDirectories(outputFile.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile.toFile(), mergedConfig);

        System.out.println("📦 Reflect-config updated at: " + outputFile.toAbsolutePath());

        // And now, for the makeup
        new ReflectionConfigGeneratorTest().prettifyReflectionConfig();
    }

    public static List<Class<?>> getClassesForPackage2(String packageName) {
        List<Class<?>> classes = new ArrayList<>();
        try (ScanResult scanResult = new ClassGraph()
            .acceptPackages(packageName) // Scan the specific package
            .enableClassInfo() // Enable class scanning
            .scan()) {

            scanResult.getAllClasses().forEach(classInfo -> {
                try {
                    classes.add(Class.forName(classInfo.getName()));
                } catch (Exception | Error ignored) {
                    System.err.println("[ERROR] Class not found [" + classInfo + "]");
                }
            });
        }
        return classes;
    }
}
