package berlin.yuna.apidoccrafter.config;

import berlin.yuna.typemap.logic.ArgsDecoder;
import berlin.yuna.typemap.model.TypeMap;

import static berlin.yuna.typemap.logic.ArgsDecoder.hasText;
import static berlin.yuna.typemap.logic.TypeConverter.convertObj;

/**
 * Manages configurations for sorting, removing, or transforming data
 * used in processing OpenAPI elements.
 * Reads configurations from environment variables, system properties,
 * and runtime arguments.
 *
 * <p>Key functionality includes:</p>
 * <ul>
 *     <li>Providing sort preferences for various operations.</li>
 *     <li>Managing configuration keys and values in a standardized manner.</li>
 *     <li>Handling optional or absent configurations gracefully.</li>
 * </ul>
 * <p>Suppressions</p>
 * <ul>
 *     <li>S106 = System.out.println is used - That is okay as it's not a production code</li>
 * </ul>
 */
@SuppressWarnings("java:S106")
public class Config {


    public static final String CONFIG_PREFIX = "adc_";
    public static final String SORT_EXTENSIONS = CONFIG_PREFIX + "adc_sort_extensions";
    public static final String SORT_SERVERS = CONFIG_PREFIX + "sort_servers";
    public static final String SORT_SECURITY = CONFIG_PREFIX + "sort_security";
    public static final String SORT_TAGS = CONFIG_PREFIX + "sort_tags";
    public static final String SORT_PATHS = CONFIG_PREFIX + "sort_paths";
    public static final String SORT_SCHEMAS = CONFIG_PREFIX + "sort_schemas";
    public static final String SORT_PARAMETERS = CONFIG_PREFIX + "sort_parameters";
    public static final String SORT_RESPONSES = CONFIG_PREFIX + "sort_responses";
    public static final String SORT_EXAMPLES = CONFIG_PREFIX + "sort_examples";
    public static final String SORT_WEBHOOKS = CONFIG_PREFIX + "sort_webhooks";
    public static final String SORT_HEADERS = CONFIG_PREFIX + "sort_headers";
    public static final String SORT_SCOPES = CONFIG_PREFIX + "sort_scopes";
    public static final String SORT_REQUESTS = CONFIG_PREFIX + "sort_requests";
    public static final String SORT_CONTENT = CONFIG_PREFIX + "sort_content";
    public static final String SORT_ENCODING = CONFIG_PREFIX + "sort_encoding";
    public static final String REMOVE_KEY = CONFIG_PREFIX + "remove";
    // GLOB patterns separated by "::" or "|"
    public static final String FILE_INCLUDES = CONFIG_PREFIX + "file_includes";
    // GLOB patterns separated by "::" or "|"
    public static final String FILE_EXCLUDES = CONFIG_PREFIX + "file_excludes";
    // optionally merge swagger files by tags. null/empty = automatically. Disable automatic with non-existing tags like '#'. separated by "::" or "|" or ","
    public static final String GROUP_TAGS = CONFIG_PREFIX + "group_tags";
    // optionally merge swagger files by tags. null/empty = automatically. Disable automatic with non-existing tags like '#'. separated by "::" or "|"
    public static final String GROUP_SERVERS = CONFIG_PREFIX + "group_servers";
    public static final String OUTPUT_DIR = CONFIG_PREFIX + "output_dir";
    public static final String WORK_DIR = CONFIG_PREFIX + "work_dir";
    public static final String MAX_DEEP = CONFIG_PREFIX + "max_deep";
    public static final String CREATE_NAV = CONFIG_PREFIX + "create_nav";
    private static final TypeMap CONFIG_ITEMS = readConfigs();

    public static Boolean sortBy(final String key) {
        return CONFIG_ITEMS.asStringOpt(key).map(String::toLowerCase).map(cfg -> ("none".equals(cfg) || "false".equals(cfg) || "null".equals(cfg)) ? null : "asc".equals(cfg)).orElse(true);
    }

    public static String getRemovePattern() {
        return CONFIG_ITEMS.asStringOpt(REMOVE_KEY).map(String::trim).map(String::toLowerCase).orElse(null);
    }

    public static TypeMap config() {
        return CONFIG_ITEMS;
    }

    /**
     * Reads configurations from environment variables, system properties, and arguments.
     *
     * @param args Optional arguments to parse.
     * @return A TypeMap containing the configuration.
     */
    private static TypeMap readConfigs(final String... args) {
        final TypeMap result = new TypeMap();
        System.getenv().forEach((key, value) -> addConfig(result, key, value));
        System.getProperties().forEach((key, value) -> addConfig(result, key, value));
        if (args != null)
            ArgsDecoder.argsOf(String.join(" ", args)).forEach((key, value) -> addConfig(result, key, value));
        return result;
    }

    /**
     * Adds a configuration entry to the context map.
     *
     * @param context The context map to update.
     * @param key     The key of the configuration.
     * @param value   The value of the configuration.
     */
    private static void addConfig(final TypeMap context, final Object key, final Object value) {
        if (value == null || "null".equals(value) || "".equals(value)) {
            context.remove(standardiseKey(key));
        } else if (value instanceof final String valueStr && hasText(valueStr)) {
            context.put(standardiseKey(key), valueStr.trim());
        } else {
            context.put(standardiseKey(key), value);
        }
    }

    /**
     * Standardizes a key by replacing certain characters and converting it to lowercase.
     *
     * @param key The key to standardize.
     * @return The standardized key.
     */
    private static String standardiseKey(final Object key) {
        return key == null ? null : convertObj(key, String.class)
            .replace('.', '_')
            .replace('-', '_')
            .replace('+', '_')
            .replace(':', '_')
            .trim()
            .toLowerCase();
    }

    private Config() {
        // Utility class
    }
}
