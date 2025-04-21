package berlin.yuna.apidoccrafter.config;

import io.swagger.models.SecurityRequirement;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.callbacks.Callback;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.links.Link;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.XML;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.QueryParameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.servers.ServerVariable;
import io.swagger.v3.oas.models.tags.Tag;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Arrays.stream;
import static java.util.Optional.ofNullable;

/**
 * Utility class for managing and comparing unique identifiers
 * for various OpenAPI elements and data structures.
 *
 * <p>Registers identifier extraction methods for supported object types
 * (e.g., Schema, Operation, Server) and provides utilities for
 * equality checks and comparisons based on these identifiers.</p>
 *
 * <p>Primarily used to determine equivalence between objects and
 * manage object mapping or merging operations.</p>
 * <p>Suppressions</p>
 * <ul>
 *     <li>S106 = System.out.println is used - That is okay as it's not a production code</li>
 *     <li>S1874 = Use of deprecated OpenAPI methods for backwards compatibility</li>
 *     <li>deprecation = Use of deprecated OpenAPI methods for backwards compatibility</li>
 *     <li>S3358 = nested ifs</li>
 * </ul>
 */
// java:S106 - Standard outputs should not be used directly to log anything
@SuppressWarnings({"java:S106", "java:S1874", "deprecation", "java:S3358"})
public class Identifier {

    public static final Map<Class<?>, Function<Object, String[]>> ID_MAP;

    static {
        final Map<Class<?>, Function<Object, String[]>> result = new HashMap<>();
        // Not identifiable:
        //  [Object] OpenAPI
        //  [Object] Components
        //  [Map] ServerVariables
        //  [Map] ApiResponses
        //  [Map] Paths
        registerIdentifiers(result, Link.class, item -> toIds(item, Link::getOperationId, Link::getOperationRef, Link::get$ref, link -> ofNullable(link.getServer()).map(Server::getUrl).orElse(null)));
        registerIdentifiers(result, OAuthFlows.class, Identifier::toIds);
        registerIdentifiers(result, OAuthFlow.class, Identifier::toIds);
        registerIdentifiers(result, io.swagger.v3.oas.models.security.SecurityRequirement.class, item -> item.keySet().stream().map(String::toLowerCase).toArray(String[]::new));
        registerIdentifiers(result, SecurityScheme.class, item -> toIds(item, SecurityScheme::get$ref, SecurityScheme::getName));
        registerIdentifiers(result, License.class, item -> toIds(item, License::getIdentifier, License::getUrl, License::getName));
        registerIdentifiers(result, Contact.class, item -> toIds(item, Contact::getName, Contact::getUrl, Contact::getEmail));
        registerIdentifiers(result, Info.class, item -> toIds(item, Info::getTitle, Info::getDescription, Info::getSummary));
        registerIdentifiers(result, Server.class, item -> toIds(item, Server::getUrl, Server::getDescription));
        registerIdentifiers(result, ServerVariable.class, item -> toIds(item, ServerVariable::getDescription, ServerVariable::getDefault));
        registerIdentifiers(result, Tag.class, item -> toIds(item, Tag::getName, Tag::getDescription));
        registerIdentifiers(result, ExternalDocumentation.class, item -> toIds(item, ExternalDocumentation::getUrl, ExternalDocumentation::getDescription));
        registerIdentifiers(result, Parameter.class, item -> toIds(item, Parameter::getName, Parameter::get$ref, Parameter::getDescription));
        registerIdentifiers(result, QueryParameter.class, item -> toIds(item, QueryParameter::getName, QueryParameter::get$ref, QueryParameter::getDescription));
        registerIdentifiers(result, Header.class, item -> toIds(item, Header::get$ref, Header::getDescription));
        registerIdentifiers(result, ApiResponse.class, item -> toIds(item, ApiResponse::get$ref, ApiResponse::getDescription));
        registerIdentifiers(result, RequestBody.class, item -> toIds(item, RequestBody::get$ref, RequestBody::getDescription));
        registerIdentifiers(result, Callback.class, item -> toIds(item, Callback::get$ref));
        registerIdentifiers(result, SecurityRequirement.class, item -> toIds(item, SecurityRequirement::getName));
        registerIdentifiers(result, Example.class, item -> toIds(item, Example::get$ref, Example::getSummary, Example::getDescription));
        registerIdentifiers(result, Operation.class, Identifier::toIds);
        registerIdentifiers(result, PathItem.class, item -> toIds(item, PathItem::get$ref));
        registerIdentifiers(result, Schema.class, item -> toIds(item, Schema::get$id, Schema::get$ref, Schema::getName, Schema::getTitle, Schema::getDescription));
        registerIdentifiers(result, XML.class, item -> toIds(item, XML::getName, XML::getNamespace));
        registerIdentifiers(result, OffsetDateTime.class, item -> new String[]{String.valueOf(item.getNano())});
        registerIdentifiers(result, Enum.class, item -> new String[]{item.name()});
        registerIdentifiers(result, Date.class, item -> new String[]{String.valueOf(item.getTime())});
        registerIdentifiers(result, String.class, item -> new String[]{item.toLowerCase()});
        registerIdentifiers(result, Boolean.class, item -> new String[]{item.toString()});
        registerIdentifiers(result, Integer.class, item -> new String[]{item.toString()});
        registerIdentifiers(result, Long.class, item -> new String[]{item.toString()});
        registerIdentifiers(result, Float.class, item -> new String[]{item.toString()});
        registerIdentifiers(result, Double.class, item -> new String[]{item.toString()});
        registerIdentifiers(result, Short.class, item -> new String[]{item.toString()});
        ID_MAP = Collections.unmodifiableMap(result);
    }

    /**
     * Checks if two objects are equal using the registered identifiers in {@link Identifier#ID_MAP}.
     *
     * @param obj1 First object.
     * @param obj2 Second object.
     * @return true if objects are equal, false otherwise.
     */
    public static boolean isEqual(final Object obj1, final Object obj2) {
        return ofNullable(obj1)
            .map(Identifier::getKeys)
            .map(id1 -> ofNullable(obj2)
                .map(Identifier::getKeys)
                .map(id2 -> {
                    for (int i = 0; i < Math.min(id1.length, id2.length); i++)
                        if (Objects.equals(id1[i], id2[i]))
                            return true;
                    return false;
                })
                .orElse(Objects.equals(obj1, obj2))
            )
            .orElse(Objects.equals(obj1, obj2));
    }

    /**
     * Compares keys using their identifiers.
     *
     * @param key1 The first key to compare.
     * @param key2 The second key to compare.
     * @return A negative number, zero, or a positive number as the first key is less than, equal to, or greater than the second.
     */
    public static int compareKeys(final Object key1, final Object key2) {
        final String[] keys1 = getKeys(key1);
        final String[] keys2 = getKeys(key2);

        if (keys1 == null || keys2 == null)
            return key1.equals(key2) ? 0 : Integer.compare(key1.hashCode(), key2.hashCode());

        Integer nonEqualsComparison = null;
        for (int i = 0; i < Math.min(keys1.length, keys2.length); i++) {
            if (keys1[i] != null && keys2[i] != null) {
                int comparison = keys1[i].compareTo(keys2[i]);
                if (comparison == 0) {
                    // Identical identifier found
                    return 0;
                } else if (nonEqualsComparison == null) {
                    // Store the first meaningful difference
                    nonEqualsComparison = comparison;
                }
            }
        }
        return nonEqualsComparison != null ? nonEqualsComparison : Long.compare(stream(keys1).filter(Objects::nonNull).count(), stream(keys2).filter(Objects::nonNull).count());
    }

    /**
     * Retrieves identifier keys for an object using the registered identifiers.
     *
     * @param key The object to retrieve keys for.
     * @return An array of identifier strings for the object.
     */
    public static String[] getKeys(final Object key) {
        return ofNullable(key).flatMap(object -> ID_MAP.entrySet()
                .stream()
                .filter(item -> item.getKey().isInstance(object))
                .map(Map.Entry::getValue).findFirst())
            .map(result -> result.apply(key)).orElse(null);
    }

    /**
     * Converts an OAuthFlows object to its identifiers.
     *
     * @param item The OAuthFlows object.
     * @return An array of identifiers for the object.
     */
    private static String[] toIds(final OAuthFlows item) {
        return item == null ? null : Stream.of(item.getImplicit(), item.getPassword(), item.getClientCredentials(), item.getAuthorizationCode())
            .map(Identifier::toIds)
            .flatMap(Stream::of)
            .toArray(String[]::new);
    }

    /**
     * Converts an OAuthFlow object to its identifiers.
     *
     * @param flow The OAuthFlow object.
     * @return An array of identifiers for the object.
     */
    private static String[] toIds(final OAuthFlow flow) {
        return flow == null ? new String[3] : toIds(flow, OAuthFlow::getAuthorizationUrl, OAuthFlow::getTokenUrl, OAuthFlow::getRefreshUrl);
    }

    /**
     * Converts an Operation object to its identifiers.
     *
     * @param op The Operation object.
     * @return An array of identifiers for the object.
     */
    private static String[] toIds(final Operation op) {
        return op == null ? new String[3] : toIds(op, Operation::getOperationId, Operation::getSummary, Operation::getDescription);
    }

    @SafeVarargs
    private static <T> String[] toIds(final T item, final Function<T, String>... conversions) {
        if (conversions == null || conversions.length == 0)
            throw new IllegalArgumentException("Mighty be a bug, please report this issue, that no conversion is provided for item: " + item.getClass().getCanonicalName());
        return item == null || conversions == null || conversions.length == 0? null : stream(conversions).map(conversion -> conversion.apply(item)).map(s -> s == null ? null : s.toLowerCase()).toArray(String[]::new);
    }

    // DX function to register identifiers for deep merge

    /**
     * Registers identifiers for deep merging.
     *
     * @param result     The map to register the identifiers in.
     * @param sourceType The class type of the objects for which the identifiers are registered.
     * @param conversion The function that extracts identifier keys from the object.
     * @param <T>        The type of the objects.
     */
    @SuppressWarnings("unchecked")
    private static <T> void registerIdentifiers(final Map<Class<?>, Function<Object, String[]>> result, final Class<T> sourceType, final Function<T, String[]> conversion) {
        result.put(sourceType, (Function<Object, String[]>) conversion);
    }

    private Identifier() {
        // utility class
    }
}
