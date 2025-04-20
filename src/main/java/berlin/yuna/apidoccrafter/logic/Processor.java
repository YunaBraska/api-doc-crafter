package berlin.yuna.apidoccrafter.logic;

import berlin.yuna.apidoccrafter.util.ExFunction;
import berlin.yuna.typemap.logic.ArgsDecoder;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static berlin.yuna.apidoccrafter.config.Config.CONFIG_PREFIX;
import static berlin.yuna.apidoccrafter.config.Config.ENABLE_CUSTOM_INFO;
import static berlin.yuna.apidoccrafter.config.Config.config;
import static berlin.yuna.apidoccrafter.util.FileCleaner.cleanFile;
import static berlin.yuna.apidoccrafter.util.Util.SPLIT_REGEX;
import static berlin.yuna.apidoccrafter.util.Util.SPLIT_REGEX_WITH_COMMA;
import static berlin.yuna.apidoccrafter.util.Util.matchesGlob;
import static berlin.yuna.apidoccrafter.util.Util.matchesStringGlob;
import static berlin.yuna.apidoccrafter.util.Util.nothing;
import static berlin.yuna.apidoccrafter.util.Util.safeJsonMapper;
import static berlin.yuna.apidoccrafter.util.Util.safeYamlMapper;
import static berlin.yuna.apidoccrafter.util.Util.toArrayList;
import static java.util.Arrays.stream;
import static java.util.Collections.emptySet;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Collectors.toUnmodifiableSet;

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
 *     <li>S1874 = deprecated methods - will be used for safety until they are removed</li>
 * </ul>
 */
@SuppressWarnings({"java:S106", "java:S3358", "java:S1874"})
public class Processor {

    public record ParseResult(String parserName, Path file, OpenAPI api, int jsonSize) {
    }

    /**
     * Enriches the given OpenAPI specification with custom metadata based on external configuration.
     * <p>
     * When the configuration key {@code enable_custom_info} is set to {@code true}, this method populates:
     * <ul>
     *   <li>{@code info.title}, {@code info.version}, {@code info.summary}, {@code info.description}</li>
     *   <li>{@code info.termsOfService}, {@code contact.name}, {@code contact.url}, {@code contact.email}</li>
     *   <li>{@code license.name}, {@code license.url}, {@code license.identifier}</li>
     *   <li>{@code externalDocs.url}, {@code externalDocs.description}</li>
     *   <li>{@code servers} and {@code tags}, using string-encoded lists split on {@code , ; |} and parsed via {@code ::}</li>
     * </ul>
     * Any null components in the {@code OpenAPI} object will be lazily instantiated before mutation.
     * <p>
     * This method is a no-op if the {@code api} parameter is null or the enrichment flag is disabled.
     *
     * @param api the {@link OpenAPI} object to enrich. Must not be null if enrichment is desired.
     */
    public static void enrichOpenAPI(final OpenAPI api) {
        if (api == null || !config().asBooleanOpt(ENABLE_CUSTOM_INFO).orElse(false)) return;

        final Info info = Optional.ofNullable(api.getInfo()).orElseGet(Info::new);
        final Contact contact = Optional.ofNullable(info.getContact()).orElseGet(Contact::new);
        final License license = Optional.ofNullable(info.getLicense()).orElseGet(License::new);
        final ExternalDocumentation docs = Optional.ofNullable(api.getExternalDocs()).orElseGet(ExternalDocumentation::new);

        config().asStringOpt(CONFIG_PREFIX + "info_title").ifPresent(info::setTitle);
        config().asStringOpt(CONFIG_PREFIX + "info_version").ifPresent(info::setVersion);
        config().asStringOpt(CONFIG_PREFIX + "info_summary").ifPresent(info::setSummary);
        config().asStringOpt(CONFIG_PREFIX + "info_description").ifPresent(info::setDescription);
        config().asStringOpt(CONFIG_PREFIX + "info_termsofservice").ifPresent(info::setTermsOfService);
        config().asStringOpt(CONFIG_PREFIX + "info_contact_name").ifPresent(contact::setName);
        config().asStringOpt(CONFIG_PREFIX + "info_contact_url").ifPresent(contact::setUrl);
        config().asStringOpt(CONFIG_PREFIX + "info_contact_email").ifPresent(contact::setEmail);
        config().asStringOpt(CONFIG_PREFIX + "info_license_name").ifPresent(license::setName);
        config().asStringOpt(CONFIG_PREFIX + "info_license_url").ifPresent(license::setUrl);
        config().asStringOpt(CONFIG_PREFIX + "info_license_identifier").ifPresent(license::setIdentifier);
        config().asStringOpt(CONFIG_PREFIX + "externaldocs_url").ifPresent(docs::setUrl);
        config().asStringOpt(CONFIG_PREFIX + "externaldocs_description").ifPresent(docs::setDescription);
        config().asStringOpt(CONFIG_PREFIX + "servers").map(svr -> Arrays.stream(svr.split("[,;]")).map(String::trim).filter(s -> !s.isEmpty()).map(s -> {
            final Server server = new Server();
            final String[] array = stream(s.split("::", 2)).map(String::trim).filter(str -> !str.isEmpty()).toArray(String[]::new);
            if (array.length == 1) {
                server.setUrl(array[0]);
                return server;
            } else {
                server.setUrl(array[0]);
                server.setDescription(array[1]);
                return server;
            }
        }).toList()).filter(svr -> !svr.isEmpty()).ifPresent(api::setServers);

        config().asStringOpt(CONFIG_PREFIX + "tags").map(svr -> Arrays.stream(svr.split("[,;]")).map(String::trim).filter(s -> !s.isEmpty()).map(s -> {
            final Tag tag = new Tag();
            final String[] array = stream(s.split("::", 2)).map(String::trim).filter(str -> !str.isEmpty()).toArray(String[]::new);
            if (array.length == 1) {
                tag.setName(array[0]);
                return tag;
            } else {
                tag.setName(array[0]);
                tag.setDescription(array[1]);
                return tag;
            }
        }).toList()).filter(svr -> !svr.isEmpty()).ifPresent(api::setTags);

        info.setContact(contact);
        info.setLicense(license);
        api.setInfo(info);
        api.setExternalDocs(docs);
    }

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
                .filter(Objects::nonNull)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .forEach(oaf -> result.put(oaf.getKey(), oaf.getValue()));
        } catch (final Exception e) {
            System.err.println("[FATAL] Failed to read OpenAPI files: " + e.getMessage());
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
        final Path cleanFile = cleanFile(filePath);
        return Stream.of(
                parseWith(OpenAPIV3Parser.class.getSimpleName(), p -> new OpenAPIV3Parser().readLocation(p.toString(), null, null).getOpenAPI(), filePath),
                parseWith(OpenAPIParser.class.getSimpleName(), p -> new OpenAPIParser().readLocation(p.toString(), null, null).getOpenAPI(), filePath),
                parseWith("modified." + OpenAPIV3Parser.class.getSimpleName(), p -> new OpenAPIV3Parser().readLocation(p.toString(), null, null).getOpenAPI(), cleanFile),
                parseWith("modified." + OpenAPIParser.class.getSimpleName(), p -> new OpenAPIParser().readLocation(p.toString(), null, null).getOpenAPI(), cleanFile),
                parseWith("legacy." + OpenAPIParser.class.getSimpleName(), p -> {
                    final ParseOptions options = new ParseOptions();
                    options.isLegacyYamlDeserialization();
                    options.setValidateInternalRefs(false);
                    options.setValidateExternalRefs(false);
                    return new OpenAPIParser().readLocation(p.toString(), null, options).getOpenAPI();
                }, cleanFile),
                parseWith("Json." + safeJsonMapper.getClass().getSimpleName(), p -> safeJsonMapper.readValue(Files.readString(p), OpenAPI.class), filePath),
                parseWith("Yaml." + safeYamlMapper.getClass().getSimpleName(), p -> safeYamlMapper.readValue(Files.readString(p), OpenAPI.class), filePath)
            )
            .parallel()
            .filter(Objects::nonNull)
            .filter(pr -> pr.api() != null && pr.jsonSize() > 20) // 20 == empty OpenAPI file
            .max(Comparator.comparingInt(ParseResult::jsonSize))
            .map(pr -> {
                System.out.println("[INFO] Read"
                    + " parser [" + pr.parserName() + "]"
                    + " info [" + ofNullable(pr.api())
                    .map(OpenAPI::getInfo)
                    .flatMap(info -> ofNullable(info.getTitle())
                        .or(() -> ofNullable(info.getSummary()))
                        .or(() -> ofNullable(info.getDescription())))
                    .or(() -> ofNullable(pr.api())
                        .map(OpenAPI::getTags)
                        .map(tags -> tags.stream().map(Tag::getName).filter(Objects::nonNull).distinct().collect(Collectors.joining(",")))
                    ).orElse(null) + "]"
                    + " length [" + pr.jsonSize() + "]"
                    + " file [" + filePath + "]"
                );
                return new AbstractMap.SimpleImmutableEntry<>(filePath, pr.api());
            })
            ;
    }

    public static ParseResult parseWith(final String name, final ExFunction<Path, OpenAPI> parser, final Path file) {
        try {
            final OpenAPI api = parser.apply(file);
            return new ParseResult(name, file, api, safeJsonMapper.writeValueAsString(api).length());
        } catch (Exception ignored) {
            return null;
        }
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
        // Group files by servers
        final Map<Boolean, List<Map<Path, OpenAPI>>> serverMap = groupFiles(groups.get(false).stream().flatMap(map -> map.entrySet().stream()).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)), serverGroups, true).stream().collect(Collectors.partitioningBy(map -> map.size() > 1));
        // Group files by title
        final Map<Boolean, List<Map<Path, OpenAPI>>> titleMap = groupFilesByTitle(
            serverMap.get(false)
                .stream()
                .flatMap(map -> map.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
        ).stream().collect(Collectors.partitioningBy(map -> map.size() > 1));

        // Merge Groups
        final List<Map<Path, OpenAPI>> result = new ArrayList<>();
        result.addAll(groups.get(true));
        result.addAll(serverMap.get(true));
        result.addAll(titleMap.get(true));
        // Get non-grouped files from last group list
        result.addAll(titleMap.get(false));
        return result;
    }

    private static List<Map<Path, OpenAPI>> groupFilesByTitle(final Map<Path, OpenAPI> files) {
        return files.entrySet().stream().collect(Collectors.groupingBy(
                entry -> ofNullable(entry.getValue()).map(OpenAPI::getInfo).map(Info::getTitle).orElseGet(() -> UUID.randomUUID().toString()).toLowerCase(),
                Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)
            ))
            .values().stream().map(LinkedHashMap::new).collect(toList());
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
