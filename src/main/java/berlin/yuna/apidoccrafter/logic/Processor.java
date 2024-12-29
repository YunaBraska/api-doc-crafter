package berlin.yuna.apidoccrafter.logic;

import berlin.yuna.typemap.logic.ArgsDecoder;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import io.swagger.v3.parser.OpenAPIV3Parser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static berlin.yuna.apidoccrafter.util.Util.*;
import static java.util.Arrays.stream;
import static java.util.Collections.emptySet;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.*;

/**
 * Orchestrates the reading, grouping, and merging of OpenAPI files.
 * Handles file operations, parsing OpenAPI specifications, and grouping
 * them based on specified patterns (tags, servers, etc.).
 *
 * <p>Main responsibilities include:</p>
 * <ul>
 *     <li>Reading and filtering OpenAPI files from directories.</li>
 *     <li>Grouping OpenAPI files by tags or servers.</li>
 *     <li>Merging grouped OpenAPI files into unified specifications.</li>
 * </ul>
 *
 * <p>This class serves as a bridge between file-level operations
 * and the logic-intensive merging processes handled by {@link Merger}.</p>
 * <p>Suppressions</p>
 * <ul>
 *     <li>S106 = System.out.println is used - That is okay as it's not a production code</li>
 *     <li>S3358 = nested ifs</li>
 * </ul>
 */
@SuppressWarnings({"java:S106", "java:S3358"})
public class Processor {

    /**
     * Reads OpenAPI files from the specified directory, filtering by patterns and depth.
     *
     * @param inputDir       The root directory to search for files.
     * @param maxDeep        The maximum directory depth to search.
     * @param includePattern Pattern for files to include.
     * @param excludePattern Pattern for files to exclude.
     * @return A map of file paths to OpenAPI objects.
     */
    public static Map<Path, OpenAPI> readOpenApiFiles(final Path inputDir, final int maxDeep, final String includePattern, final String excludePattern) {
        final Map<Path, OpenAPI> result = new HashMap<>();
        try (final Stream<Path> files = Files.walk(inputDir, maxDeep)) {
            files.filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".yml") || path.toString().endsWith(".yaml") || path.toString().endsWith(".json"))
                .filter(path -> includePattern == null || matchesGlob(path, includePattern))
                .filter(path -> excludePattern == null || !matchesGlob(path, excludePattern))
                .map(Processor::toOpenAPIFile)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .forEach(oaf -> result.put(oaf.getKey(), oaf.getValue()));
        } catch (final Exception e) {
            System.err.println("[FATAL] Failed to read OpenAPI files: " + e.getMessage());
            e.printStackTrace();
        }
        return result;
    }

    /**
     * Converts a file path to an OpenAPI object if the file is a valid OpenAPI definition.
     *
     * @param filePath The path to the file.
     * @return An optional containing the file path and OpenAPI object.
     */
    public static Optional<Map.Entry<Path, OpenAPI>> toOpenAPIFile(final Path filePath) {
        return parse(filePath, path -> new OpenAPIV3Parser().readLocation(path.toString(), null, null).getOpenAPI())
            .or(() -> parse(filePath, path -> new OpenAPIParser().readLocation(path.toString(), null, null).getOpenAPI()))
            .map(api -> new AbstractMap.SimpleImmutableEntry<>(filePath, api));
    }

    public static Optional<OpenAPI> parse(final Path filePath, final Function<Path, OpenAPI> parser) {
        try {
            return ofNullable(parser.apply(filePath));
        } catch (final Exception e) {
            System.err.println("[ERROR] Failed to parse OpenAPI file [" + filePath + "]: " + e.getMessage());
            e.printStackTrace();
            return Optional.empty();
        }
    }

    /**
     * Groups OpenAPI files based on specified grouping patterns for tags or servers.
     *
     * @param fileMap      Map of file paths to OpenAPI objects.
     * @param groupPattern Pattern for grouping files.
     * @param serverGroups Additional server grouping patterns.
     * @return A list of grouped OpenAPI maps.
     */
    public static List<Map<Path, OpenAPI>> groupFiles(final Map<Path, OpenAPI> fileMap, final String groupPattern, final String serverGroups) {
        // Group files by tags
        final Map<Boolean, List<Map<Path, OpenAPI>>> groups = groupFiles(fileMap, groupPattern, false).stream().collect(Collectors.partitioningBy(map -> map.size() > 1));

        // Group rest files by servers
        final List<Map<Path, OpenAPI>> serverMap = groupFiles(groups.get(false).stream().flatMap(map -> map.entrySet().stream()).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)), serverGroups, true);

        // Merge Tag and Server Groups
        serverMap.addAll(groups.get(true));
        return serverMap;
    }

    public static List<Map<Path, OpenAPI>> groupFiles(final Map<Path, OpenAPI> files, final String groupPattern, final boolean isServer) {
        return groupFiles(files, groupPattern == null ? emptySet() : stream(groupPattern.split(isServer ? SPLIT_REGEX : SPLIT_REGEX_WITH_COMMA)).filter(ArgsDecoder::hasText).map(String::trim).map(String::toLowerCase).collect(toUnmodifiableSet()), isServer);
    }

    public static List<Map<Path, OpenAPI>> groupFiles(final Map<Path, OpenAPI> files, final Collection<String> groupPattern, final boolean isServer) {
        final Set<Path> assignedFiles = new HashSet<>();
        final Set<Map<Path, OpenAPI>> groups = files.entrySet().stream()
            .flatMap(entry -> (isServer ? extractServers(entry.getValue(), groupPattern) : extractTags(entry.getValue(), groupPattern)).stream().map(tag -> Map.entry(tag, entry.getKey()))) // Flatten tags
            .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, toSet()))) // Group by tag
            .entrySet().stream()
            .sorted((e1, e2) -> Integer.compare(e2.getValue().size(), e1.getValue().size())) // Sort by Set size in descending order
            .map(entry -> Map.entry(entry.getKey(), entry.getValue().stream().filter(assignedFiles::add).collect(toSet()))) // Remove already assigned paths
            .filter(entry -> !entry.getValue().isEmpty()) // Exclude empty groups
            .map(entry -> entry.getValue().stream().collect(Collectors.toMap(path -> path, files::get, (v1, v2) -> v1, TreeMap::new))) // Convert back to sorted TreeMap<Path, OpenAPI>
            .collect(toSet());
        // add remaining files as separate groups
        files.entrySet().stream().filter(entry -> !assignedFiles.contains(entry.getKey())).forEach(entry -> groups.add(Map.of(entry.getKey(), entry.getValue()))); // Add remaining files as separate groups
        return groups.stream().sorted(Comparator.comparing(map -> map.keySet().iterator().next())).collect(toArrayList());
    }

    /**
     * Merges multiple OpenAPI files into a single OpenAPI object for each group.
     *
     * @param groupedApis Collection of grouped OpenAPI maps.
     * @return A map of merged OpenAPI objects, keyed by their representative file paths.
     */
    public static Map<Path, OpenAPI> mergeApis(final Collection<Map<Path, OpenAPI>> groupedApis) {
        return groupedApis.stream().map(Processor::mergeApis).sorted(Map.Entry.comparingByKey()) // Sort entries by Path keys
            .collect(toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (existing, replacement) -> existing,
                LinkedHashMap::new // Maintain sorted order
            ));
    }

    /**
     * Merges APIs within a collection into a single API object.
     *
     * @param mergeApis The map of APIs to merge.
     * @return A Map.Entry containing the path and the merged OpenAPI object.
     */
    public static Map.Entry<Path, OpenAPI> mergeApis(final Map<Path, OpenAPI> mergeApis) {
        System.out.println("[INFO] Process " + (mergeApis.size() > 1 ? "group" : "file") + " " + mergeApis.keySet().stream().map(Path::getFileName).toList());
        final OpenAPI result = new OpenAPI();
        mergeApis.forEach((path, api) -> Merger.merge(() -> result, () -> api, nothing()));
        return Map.entry(mergeApis.keySet().iterator().next(), result);
    }

    /**
     * Extracts the tags from an OpenAPI object, filtered by specified tags.
     *
     * @param openAPI     The OpenAPI object to extract tags from.
     * @param includeTags The tags to include.
     * @return A set of extracted tag names.
     */
    public static Set<String> extractTags(final OpenAPI openAPI, final Collection<String> includeTags) {
        return openAPI == null || openAPI.getTags() == null ? emptySet() : openAPI.getTags().stream()
            .filter(Objects::nonNull)
            .map(Tag::getName)
            .filter(ArgsDecoder::hasText)
            .map(String::toLowerCase)
            .map(String::trim)
            .filter(tag -> includeTags == null || includeTags.isEmpty() || includeTags.contains(tag))
            .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Extracts the servers from an OpenAPI object, filtered by specified patterns.
     *
     * @param openAPI        The OpenAPI object to extract servers from.
     * @param includeServers Patterns for servers to include.
     * @return A set of extracted server URLs.
     */
    public static Set<String> extractServers(final OpenAPI openAPI, final Collection<String> includeServers) {
        return openAPI == null || openAPI.getServers() == null ? emptySet() : openAPI.getServers().stream()
            .filter(Objects::nonNull)
            .map(Server::getUrl)
            .filter(ArgsDecoder::hasText)
            .map(String::toLowerCase)
            .map(String::trim)
            .map(url -> {
                if (includeServers == null || includeServers.isEmpty()) {
                    return url;
                }
                return (includeServers.contains(url)) ? url : includeServers.stream().filter(glob -> matchesStringGlob(url, glob)).findFirst().orElse(null);
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toUnmodifiableSet());
    }

    private Processor() {
        // Utility class
    }
}
