package berlin.yuna.apidoccrafter.util;

import berlin.yuna.typemap.logic.ArgsDecoder;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static berlin.yuna.apidoccrafter.config.Config.getEncoding;
import static berlin.yuna.apidoccrafter.config.Config.getRemovePattern;
import static berlin.yuna.typemap.logic.ArgsDecoder.hasText;
import static java.util.Optional.ofNullable;

// java:S106 - Standard outputs should not be used directly to log anything
@SuppressWarnings({"java:S106", "java:S1192"})
public class Util {

    public static final String SPLIT_REGEX = "::|\\|";
    public static final String SPLIT_REGEX_WITH_COMMA = ",|::|\\|";

    /**
     * Matches a string against a glob pattern.
     *
     * @param input The string to match.
     * @return true if the string matches the glob pattern, false otherwise.
     */
    public static boolean matchesRemoveGlob(final String input) {
        return matchesStringGlob(input, getRemovePattern());
    }


    /**
     * Matches a string against a glob pattern.
     *
     * @param input The string to match.
     * @param glob  The glob pattern to match against.
     * @return true if the string matches the glob pattern, false otherwise.
     */
    public static boolean matchesStringGlob(final String input, final String glob) {
        if (!hasText(glob) || !hasText(input))
            return false; // Ensure input and glob are valid
        try {
            final String lowerInput = input.toLowerCase();
            return Arrays.stream(glob.split(SPLIT_REGEX))
                .filter(ArgsDecoder::hasText)
                .map(String::toLowerCase)
                .map(Util::globToRegex)
                .anyMatch(lowerInput::matches);
        } catch (final Exception ignored) {
            return false; // Handle invalid patterns gracefully
        }
    }

    /**
     * Matches a file path against a glob pattern.
     *
     * @param path The file path to match.
     * @param glob The glob pattern.
     * @return true if the path matches the pattern, false otherwise.
     */
    public static boolean matchesGlob(final Path path, final String glob) {
        try {
            return Arrays.stream(glob.split(SPLIT_REGEX))
                .filter(ArgsDecoder::hasText)
                .map(pattern -> FileSystems.getDefault().getPathMatcher("glob:" + pattern))
                .anyMatch(pathMatcher -> pathMatcher.matches(path));
        } catch (final Exception ignored) {
            return false; // ignore invalid patterns
        }
    }


    /**
     * Creates a collector that collects elements into an ArrayList.
     *
     * @param <T> The type of elements to collect.
     * @return A Collector that collects elements into an ArrayList.
     */
    public static <T> Collector<T, ?, ArrayList<T>> toArrayList() {
        return Collectors.toCollection(ArrayList::new);
    }

    /**
     * A no-operation Consumer implementation.
     *
     * @param <T> The type of input to the operation.
     * @return A Consumer that performs no action.
     */
    // This method is to avoid IntelliJ its weired formatting which is not possible to disable or override via .editorconfig
    public static <T> Consumer<T> nothing() {
        return i -> {
        };
    }

    public static String globToRegex(final String glob) {
        final StringBuilder regex = new StringBuilder();
        for (char c : glob.toCharArray()) {
            switch (c) {
                case '*':
                    regex.append(".*");
                    break;
                case '?':
                    regex.append('.');
                    break;
                case '.':
                    regex.append("\\.");
                    break;
                case '{':
                    regex.append('(');
                    break;
                case '}':
                    regex.append(')');
                    break;
                case ',':
                    regex.append('|');
                    break;
                default:
                    regex.append(c);
            }
        }
        return regex.toString();
    }

    /**
     * Deletes a folder and all its contents recursively.
     *
     * @param folder The folder to delete.
     */
    public static void deleteFolder(final Path folder) {
        if (folder == null || !Files.exists(folder)) return;
        try (Stream<Path> stream = Files.isDirectory(folder) ? Files.list(folder) : Stream.empty()) {
            stream.forEach(Util::deleteFolder);
            Files.delete(folder);
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to delete [" + folder + "] cause [" + e.getClass().getSimpleName() + "] message [" + e.getMessage() + "]");
        }
    }

    public static String displayName(final Path path, final OpenAPI openAPI) {
        return ofNullable(openAPI)
            .map(OpenAPI::getInfo)
            .map(Info::getTitle).orElseGet(() -> filename(path, openAPI, ""));
    }

    public static String filenameHtml(final Path path, final OpenAPI openAPI) {
        return filename(path, openAPI, ".html");
    }

    public static String filenameYaml(final Path path, final OpenAPI openAPI) {
        return filename(path, openAPI, ".yaml");
    }

    public static String filenameJson(final Path path, final OpenAPI openAPI) {
        return filename(path, openAPI, ".json");
    }

    public static String filename(final Path path, final OpenAPI openAPI, final String extension) {
        final String result = ofNullable(openAPI)
            .map(OpenAPI::getInfo)
            .map(Info::getTitle)
            .orElse(path.getFileName().toString().toLowerCase())
            .toLowerCase()
            .replaceAll("[^\\p{L}\\p{Nd}_.]+", "_").replaceAll("_+", "_").trim();
        return result.contains(".") ? result.substring(0, result.indexOf(".")) : result + extension;
    }

    public static void writeFile(final Path path, final String content) {
        try {
            Files.writeString(path, content, getEncoding());
            System.out.println("[INFO] Generated [" + path + "]");
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to write [" + path + "] cause [" + e.getClass().getSimpleName() + "] message [" + e.getMessage() + "]");
        }
    }

    /**
     * Sorts a map based on a string extracted from each entry.
     *
     * @param map       The map to sort.
     * @param extractor A function to extract a string from a map entry for comparison.
     * @param <K>       The type of the map's keys.
     * @param <V>       The type of the map's values.
     * @return A new LinkedHashMap with entries sorted by the extracted string.
     */
    public static <K, V> Map<K, V> sortByString(final Map<K, V> map, final Function<Map.Entry<K, V>, String> extractor) {
        return map.entrySet().stream()
            .sorted(Comparator.comparing(extractor))
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (existing, replacement) -> existing,
                LinkedHashMap::new
            ));
    }

    public static void copyResourceToOutput(final String resourceName, final Path outputDir) {
        try (InputStream resourceStream = Util.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (resourceStream == null) {
                System.err.println("[ERROR] Resource [" + resourceName + "] not found in the resources folder.");
                return;
            }
            Path targetPath = outputDir.resolve(resourceName.contains("/") ? resourceName.substring(resourceName.lastIndexOf("/") + 1) : resourceName);
            mkdir(targetPath.getParent());
            Files.copy(resourceStream, targetPath);
            System.out.println("[INFO] Created [" + targetPath + "]");
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to copy resource [" + resourceName + "] cause [" + e.getClass().getSimpleName() + "] message [" + e.getMessage() + "]");
        }
    }

    public static String readResource(final String resourceName) {
        try (InputStream resourceStream = Util.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (resourceStream == null) {
                System.err.println("[ERROR] Resource [" + resourceName + "] not found in the resources folder.");
                return null;
            }
            return new String(resourceStream.readAllBytes());
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to copy resource [" + resourceName + "] cause [" + e.getClass().getSimpleName() + "] message [" + e.getMessage() + "]");
            return null;
        }
    }

    public static void mkdir(final Path path) {
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
            } catch (Exception e) {
                System.err.println("[ERROR] Creating dir [" + path + "] cause [" + e.getClass().getSimpleName() + "] message [" + e.getMessage() + "]");
            }
        }
    }

    public static void download(final String url, final Map<String, String> headers, final Path target) {
        if(!hasText(url) || url.startsWith("//")  || url.startsWith("#") || url.startsWith("-") || url.startsWith(">") || url.startsWith("<")) return;
        try {
            mkdir(target);
            final Path targetFile = generateFileName(url, target);
            final HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30)) // Request timeout
                .GET();
            headers.forEach(request::header);

            try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
                final HttpResponse<Path> response = client.send(request.build(), HttpResponse.BodyHandlers.ofFile(targetFile));
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    System.out.println("[INFO] Downloaded [" + targetFile + "]");
                } else {
                    Files.deleteIfExists(targetFile); // Cleanup incomplete file
                    System.err.println("[ERROR] Download [" + url + "] Status [" + response.statusCode() + "] message [" + response.body() + "]");
                }
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("[ERROR] Download [" + url + "] cause [" + e.getClass().getSimpleName() + "] message [" + e.getMessage() + "]");
        }
    }

    private static Path generateFileName(final String url, final Path target) {
        return Optional.of(url)
            .map(path -> Integer.toHexString(path.hashCode()))
            .map(path -> path + (url.endsWith(".json") ? ".json" : ".yaml"))
            .map(target::resolve)
            .orElse(target.resolve(System.currentTimeMillis() + ".tmp"));
    }

    private Util() {
        // Utility class
    }
}
