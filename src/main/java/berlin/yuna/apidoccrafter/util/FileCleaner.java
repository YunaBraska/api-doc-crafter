package berlin.yuna.apidoccrafter.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static berlin.yuna.apidoccrafter.config.Config.getEncoding;
import static berlin.yuna.apidoccrafter.util.Util.mkdir;

// java:S106 - Standard outputs should not be used directly to log anything
@SuppressWarnings({"java:S106", "java:S1192"})
public class FileCleaner {

    public static Path cleanFile(final Path path) {
        try {
            final Path tempDirectory = Path.of(System.getProperty("user.home"), "api-doc-cleaner");
            mkdir(tempDirectory);
            final Path target = tempDirectory.resolve(path.getFileName());
            List<String> content = Files.readAllLines(path, getEncoding())
                .stream()
                .filter(line -> !line.matches("^\\s*(//|#).*"))
                .filter(line -> !line.equals("---"))
                .map(line -> line.equals("swagger: \"2.0\"")? "openapi: 3.0.1" : line)
                .map(line -> line.equals("\"swagger\": \"2.0\"")? "\"openapi\": \"3.0.1\"" : line)
                .toList();
            Files.write(target, content, getEncoding());
            System.out.println("[WARN] Cleaning [" + target + "] and retry parsing");
            return target;
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to preprocess file [" + path + "] cause [" + e.getClass().getSimpleName() + "] message [" + e.getMessage() + "]");
            return path;
        }
    }

    private FileCleaner() {
        // utility class
    }
}
