package berlin.yuna.apidoccrafter.logic;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class ReflectionConfigGeneratorTest {

    @Test
    void generate() throws Exception {
        generateReflectConfig(
            List.of("io.swagger.v3.oas.models"),
            "src/main/resources/META-INF/native-image/berlin.yuna/api-doc-crafter/reflect-config.json"
            );
    }

    public static void generateReflectConfig(List<String> packages, String outputPath) throws Exception {
        List<Map<String, Object>> config = new ArrayList<>();

        for (String packageName : packages) {
            List<Class<?>> classes = getClassesForPackage2(packageName);
            for (Class<?> clazz : classes) {
                // Only include concrete classes (non-abstract, non-interface)
                if (!Modifier.isAbstract(clazz.getModifiers()) && !clazz.isInterface()) {
                    Map<String, Object> classConfig = new HashMap<>();
                    classConfig.put("name", clazz.getName());
                    classConfig.put("allDeclaredFields", true);
                    classConfig.put("allDeclaredMethods", true);
                    classConfig.put("allDeclaredConstructors", true);
                    config.add(classConfig);
                }
            }
        }

        // Write to reflect-config.json
        ObjectMapper mapper = new ObjectMapper();
        File outputFile = new File(outputPath);
        outputFile.getParentFile().mkdirs(); // Ensure the directory exists
        mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, config);

        System.out.println("Reflect-config.json generated at: " + outputFile.getAbsolutePath());
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
