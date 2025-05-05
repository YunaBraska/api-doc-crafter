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

    public static final boolean FORCE_ALL_ATTRIBUTES = false;
    public static final String REFLECTION_CONFIG = "src/main/resources/META-INF/native-image/berlin.yuna/api-doc-crafter/reflect-config.json";
    public static final List<String> FORCE_REFLECTION = List.of("io.swagger.v3.oas.models", "io.swagger.parser.OpenAPIParser");
    public static final List<String> EXCLUSIONS = List.of("jdk.", "jre.", "sun.", "java.", "com.sun.");

    @Test
        // To generate the reflect-config.json file for native image builds use: `./mvnw clean package -B -q -DskipTests -Pnative && java -agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image/berlin.yuna/api-doc-crafter -Dadc_output_dir=target/generated_api -jar target/api-doc-crafter.jar && ./mvnw test`
    void generate() throws Exception {
        generateReflectConfig();
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

    public static void generateReflectConfig() throws Exception {
        final Path outputFile = Path.of(REFLECTION_CONFIG);
        final ObjectMapper mapper = new ObjectMapper();
        final List<Map<String, Object>> existingConfig = Files.exists(outputFile)
            ? mapper.readValue(Files.readString(outputFile), List.class)
            : new ArrayList<>();

        final Set<String> existingClassNames = new HashSet<>();
        for (Map<String, Object> item : existingConfig) {
            Object nameObj = item.get("name");
            if (nameObj instanceof final String name) {
                existingClassNames.add(name);
                if (FORCE_ALL_ATTRIBUTES || EXCLUSIONS.stream().noneMatch(name::contains)) {
                    addMetaData(item);
                }
            }
        }

        final String mergerClasses = Files.readString(Path.of(System.getProperty("user.dir"))
            .resolve("src/main/java/berlin/yuna/apidoccrafter/logic/Merger.java"), getEncoding());

        final List<Map<String, Object>> newConfig = new ArrayList<>();

        for (String packageName : FORCE_REFLECTION) {
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
                        addMetaData(classConfig);
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

    private static void addMetaData(final Map<String, Object> item) {
        item.putIfAbsent("allDeclaredMethods", true);
        item.putIfAbsent("allDeclaredFields", true);
        item.putIfAbsent("allDeclaredConstructors", true);
        item.putIfAbsent("allPublicMethods", true);
        item.putIfAbsent("allPublicFields", true);
        item.putIfAbsent("allPublicConstructors", true);
        item.putIfAbsent("queryAllDeclaredMethods", true);
        item.putIfAbsent("queryAllDeclaredConstructors", true);
        item.putIfAbsent("queryAllPublicMethods", true);
        item.putIfAbsent("queryAllPublicConstructors", true);
        item.putIfAbsent("unsafeAllocated", true);
        if (Boolean.TRUE.equals(item.get("allDeclaredMethods")))
            item.remove("methods");
        if (Boolean.TRUE.equals(item.get("allDeclaredConstructors")))
            item.remove("constructors");
        if (Boolean.TRUE.equals(item.get("allDeclaredFields")))
            item.remove("fields");
    }

    public static List<Class<?>> getClassesForPackage2(final String packageName) {
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
