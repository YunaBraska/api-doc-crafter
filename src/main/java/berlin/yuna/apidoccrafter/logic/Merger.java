package berlin.yuna.apidoccrafter.logic;

import berlin.yuna.apidoccrafter.config.Identifier;
import berlin.yuna.apidoccrafter.util.Util;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.models.SecurityRequirement;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.callbacks.Callback;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.links.Link;
import io.swagger.v3.oas.models.links.LinkParameter;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.servers.ServerVariable;
import io.swagger.v3.oas.models.servers.ServerVariables;
import io.swagger.v3.oas.models.tags.Tag;

import java.time.temporal.TemporalAccessor;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static berlin.yuna.apidoccrafter.config.Config.SORT_CONTENT;
import static berlin.yuna.apidoccrafter.config.Config.SORT_ENCODING;
import static berlin.yuna.apidoccrafter.config.Config.SORT_EXAMPLES;
import static berlin.yuna.apidoccrafter.config.Config.SORT_EXTENSIONS;
import static berlin.yuna.apidoccrafter.config.Config.SORT_HEADERS;
import static berlin.yuna.apidoccrafter.config.Config.SORT_PARAMETERS;
import static berlin.yuna.apidoccrafter.config.Config.SORT_PATHS;
import static berlin.yuna.apidoccrafter.config.Config.SORT_REQUESTS;
import static berlin.yuna.apidoccrafter.config.Config.SORT_RESPONSES;
import static berlin.yuna.apidoccrafter.config.Config.SORT_SCHEMAS;
import static berlin.yuna.apidoccrafter.config.Config.SORT_SCOPES;
import static berlin.yuna.apidoccrafter.config.Config.SORT_SECURITY;
import static berlin.yuna.apidoccrafter.config.Config.SORT_SERVERS;
import static berlin.yuna.apidoccrafter.config.Config.SORT_TAGS;
import static berlin.yuna.apidoccrafter.config.Config.SORT_WEBHOOKS;
import static berlin.yuna.apidoccrafter.config.Config.sortBy;
import static berlin.yuna.apidoccrafter.config.Identifier.isEqual;
import static berlin.yuna.apidoccrafter.util.Util.nothing;
import static java.util.Optional.ofNullable;

/**
 * Handles logic for merging fields of various types.
 * Contains custom equality logic using {@link Identifier#ID_MAP} to determine whether objects are the same.
 * If objects are new or different, they are added or merged as needed.
 * <p>Suppressions</p>
 * <ul>
 *     <li>unchecked = generics sometimes causes a bit of headache</li>
 *     <li>S106 = System.out.println is used - That is okay as it's not a production code</li>
 *     <li>S1874 && deprecation = deprecated methods should be supported until the next major release</li>
 * </ul>
 */
@SuppressWarnings({"unchecked", "java:S106", "java:S1874", "deprecation"})
public class Merger {

    /**
     * Merges two values, handling logic specific to their types.
     * Performs deep merge of objects if necessary.
     *
     * @param oldValue Supplier for the old value.
     * @param newValue Supplier for the new value.
     * @param onNew    Consumer to handle new values.
     * @param <T>      Type of the objects being merged.
     */
    public static <T> void merge(final Supplier<T> oldValue, final Supplier<T> newValue, final Consumer<T> onNew) {
        merge(oldValue, newValue, onNew, null);
    }

    /**
     * Merges two collections, comparing elements and merging or adding as needed.
     *
     * @param oldList   The original collection.
     * @param newList   The new collection to merge.
     * @param ascending Whether to sort the collection in ascending order.
     */
    public static void mergeCollection(final Collection<Object> oldList, final Collection<Object> newList, final Boolean ascending) {
        newList.stream().filter(Objects::nonNull).forEach(newItem -> oldList.stream()
            .filter(Objects::nonNull)
            .filter(oldItem -> isEqual(oldItem, newItem))
            .findFirst()
            .ifPresentOrElse(oldItem -> merge(oldItem, newItem), () -> oldList.add(newItem))
        );

        oldList.removeIf(Merger::shouldRemove);
        if (ascending != null && oldList instanceof SequencedCollection<Object>)
            sort(oldList, ascending);
    }

    /**
     * Merges two maps, comparing keys and merging or adding entries as needed.
     *
     * @param oldMap The original map.
     * @param newMap The new map to merge.
     * @param <K>    Type of keys in the map.
     * @param <V>    Type of values in the map.
     */
    public static <K, V> void mergeMap(final Map<K, V> oldMap, final Map<K, V> newMap, final Boolean ascending) {
        newMap.forEach((newKey, newValue) -> oldMap.entrySet().stream()
            .filter(oldEntity -> isEqual(oldEntity.getKey(), newKey))
            .findFirst()
            .ifPresentOrElse(oldEntity -> merge(oldEntity.getValue(), newValue), () -> oldMap.put(newKey, newValue)));

        oldMap.entrySet().removeIf(item -> shouldRemove(item.getKey()));
        if (ascending != null && oldMap instanceof SequencedMap<?, ?>)
            sort(oldMap, ascending);
    }

    private static <T> void merge(final Supplier<T> oldValue, final Supplier<T> newValue, final Consumer<T> onNew, final String ascending) {
        mergeFun(
            oldValue, // Null check
            newValue, // Null check
            (oldItem, newItem) -> mergeSortFilter(oldItem, newItem, ascending), // merge & sort & filter
            //FIXME: unnecessary merge, only sort & filter
            newItem -> onNew.accept((mergeSortFilter(newItem, newItem, ascending)))  // sort & filter only
        );
    }

    @SuppressWarnings({"java:S6541"}) // "Brain Method" core complexity
    private static <T> T mergeSortFilter(final T oldItem, final T newItem, final String ascending) {
        switch (oldItem) {
            case OpenAPI oldAPI when newItem instanceof final OpenAPI newAPI -> {
                merge(oldAPI::getInfo, newAPI::getInfo, oldAPI::setInfo);
                merge(oldAPI::getExternalDocs, newAPI::getExternalDocs, oldAPI::setExternalDocs);
                merge(oldAPI::getServers, newAPI::getServers, oldAPI::setServers, SORT_SERVERS);
                merge(oldAPI::getSecurity, newAPI::getSecurity, oldAPI::setSecurity, SORT_SECURITY);
                merge(oldAPI::getTags, newAPI::getTags, oldAPI::setTags, SORT_TAGS);
                merge(oldAPI::getPaths, newAPI::getPaths, oldAPI::setPaths, SORT_PATHS);
                merge(oldAPI::getComponents, newAPI::getComponents, oldAPI::setComponents);
                merge(oldAPI::getJsonSchemaDialect, newAPI::getJsonSchemaDialect, oldAPI::setJsonSchemaDialect);
                merge(oldAPI::getWebhooks, newAPI::getWebhooks, oldAPI::setWebhooks, SORT_WEBHOOKS);
                merge(oldAPI::getExtensions, newAPI::getExtensions, oldAPI::setExtensions, SORT_EXTENSIONS);
                // Components are not merge able or identifiable
                oldAPI.setComponents(mergeComponents(oldAPI.getComponents(), newAPI.getComponents()));
            }
            case Components oldComp when newItem instanceof final Components newComp ->
                mergeComponents(oldComp, newComp);
            case Link oldLink when newItem instanceof final Link newLink -> {
                merge(oldLink::getOperationRef, newLink::getOperationRef, oldLink::setOperationRef);
                merge(oldLink::getOperationId, newLink::getOperationId, oldLink::setOperationId);
                merge(oldLink::getParameters, newLink::getParameters, oldLink::setParameters, SORT_PARAMETERS);
                merge(oldLink::getRequestBody, newLink::getRequestBody, oldLink::setRequestBody);
                merge(oldLink::getHeaders, newLink::getHeaders, oldLink::setHeaders, SORT_HEADERS);
                merge(oldLink::getDescription, newLink::getDescription, oldLink::setDescription);
                merge(oldLink::get$ref, newLink::get$ref, oldLink::set$ref);
                merge(oldLink::getServer, newLink::getServer, oldLink::setServer);
                merge(oldLink::getExtensions, newLink::getExtensions, oldLink::setExtensions, SORT_EXTENSIONS);
            }
            case OAuthFlow oldAuth when newItem instanceof final OAuthFlow newAuth -> {
                merge(oldAuth::getAuthorizationUrl, newAuth::getAuthorizationUrl, oldAuth::setAuthorizationUrl);
                merge(oldAuth::getTokenUrl, newAuth::getTokenUrl, oldAuth::setTokenUrl);
                merge(oldAuth::getRefreshUrl, newAuth::getRefreshUrl, oldAuth::setRefreshUrl);
                merge(oldAuth::getScopes, newAuth::getScopes, oldAuth::setScopes, SORT_SCOPES);
                merge(oldAuth::getExtensions, newAuth::getExtensions, oldAuth::setExtensions, SORT_EXTENSIONS);
            }
            case OAuthFlows oldAuth when newItem instanceof final OAuthFlows newAuth -> {
                merge(oldAuth::getImplicit, newAuth::getImplicit, oldAuth::setImplicit);
                merge(oldAuth::getPassword, newAuth::getPassword, oldAuth::setPassword);
                merge(oldAuth::getClientCredentials, newAuth::getClientCredentials, oldAuth::setClientCredentials);
                merge(oldAuth::getAuthorizationCode, newAuth::getAuthorizationCode, oldAuth::setAuthorizationCode);
                merge(oldAuth::getExtensions, newAuth::getExtensions, oldAuth::setExtensions, SORT_EXTENSIONS);
            }
            case SecurityScheme oldSe when newItem instanceof final SecurityScheme newSe -> {
                merge(oldSe::getType, newSe::getType, oldSe::setType);
                merge(oldSe::getDescription, newSe::getDescription, oldSe::setDescription);
                merge(oldSe::getName, newSe::getName, oldSe::setName);
                merge(oldSe::get$ref, newSe::get$ref, oldSe::set$ref);
                merge(oldSe::getIn, newSe::getIn, oldSe::setIn);
                merge(oldSe::getScheme, newSe::getScheme, oldSe::setScheme);
                merge(oldSe::getBearerFormat, newSe::getBearerFormat, oldSe::setBearerFormat);
                merge(oldSe::getFlows, newSe::getFlows, oldSe::setFlows);
                merge(oldSe::getOpenIdConnectUrl, newSe::getOpenIdConnectUrl, oldSe::setOpenIdConnectUrl);
                merge(oldSe::getExtensions, newSe::getExtensions, oldSe::setExtensions, SORT_EXTENSIONS);
            }
            case License oldLicense when newItem instanceof final License newLicense -> {
                merge(oldLicense::getName, newLicense::getName, oldLicense::setName);
                merge(oldLicense::getIdentifier, newLicense::getIdentifier, oldLicense::setIdentifier);
                merge(oldLicense::getUrl, newLicense::getUrl, oldLicense::setUrl);
                merge(oldLicense::getExtensions, newLicense::getExtensions, oldLicense::setExtensions, SORT_EXTENSIONS);
            }
            case MediaType oldMediaType when newItem instanceof final MediaType newMediaType -> {
                merge(oldMediaType::getSchema, newMediaType::getSchema, oldMediaType::setSchema);
                merge(oldMediaType::getExample, newMediaType::getExample, oldMediaType::setExample);
                merge(oldMediaType::getExamples, newMediaType::getExamples, oldMediaType::setExamples, SORT_EXAMPLES);
                merge(oldMediaType::getEncoding, newMediaType::getEncoding, oldMediaType::setEncoding, SORT_ENCODING);
                merge(oldMediaType::getExtensions, newMediaType::getExtensions, oldMediaType::setExtensions, SORT_EXTENSIONS);
            }
            case Contact oldContact when newItem instanceof final Contact newContact -> {
                merge(oldContact::getName, newContact::getName, oldContact::setName);
                merge(oldContact::getEmail, newContact::getEmail, oldContact::setEmail);
                merge(oldContact::getUrl, newContact::getUrl, oldContact::setUrl);
                merge(oldContact::getExtensions, newContact::getExtensions, oldContact::setExtensions, SORT_EXTENSIONS);
            }
            case Info oldInfo when newItem instanceof final Info newInfo -> {
                merge(oldInfo::getTitle, newInfo::getTitle, oldInfo::setTitle);
                merge(oldInfo::getDescription, newInfo::getDescription, oldInfo::setDescription);
                merge(oldInfo::getTermsOfService, newInfo::getTermsOfService, oldInfo::setTermsOfService);
                merge(oldInfo::getContact, newInfo::getContact, oldInfo::setContact);
                merge(oldInfo::getLicense, newInfo::getLicense, oldInfo::setLicense);
                merge(oldInfo::getVersion, newInfo::getVersion, oldInfo::setVersion);
                merge(oldInfo::getSummary, newInfo::getSummary, oldInfo::setSummary);
                merge(oldInfo::getExtensions, newInfo::getExtensions, oldInfo::setExtensions, SORT_EXTENSIONS);
            }
            case Server oldServer when newItem instanceof final Server newServer -> {
                merge(oldServer::getUrl, newServer::getUrl, oldServer::setUrl);
                merge(oldServer::getDescription, newServer::getDescription, oldServer::setDescription);
                merge(oldServer::getVariables, newServer::getVariables, oldServer::setVariables, SORT_SERVERS);
                merge(oldServer::getExtensions, newServer::getExtensions, oldServer::setExtensions, SORT_EXTENSIONS);
            }
            case ServerVariable oldServerV when newItem instanceof final ServerVariable newServerV -> {
                merge(oldServerV::getEnum, newServerV::getEnum, oldServerV::setEnum, SORT_SERVERS);
                merge(oldServerV::getDescription, newServerV::getDescription, oldServerV::setDescription);
                merge(oldServerV::getDefault, newServerV::getDefault, oldServerV::setDefault);
                merge(oldServerV::getExtensions, newServerV::getExtensions, oldServerV::setExtensions, SORT_EXTENSIONS);
            }
            case Tag oldTag when newItem instanceof final Tag newTag -> {
                merge(oldTag::getDescription, newTag::getDescription, oldTag::setDescription);
                merge(oldTag::getExternalDocs, newTag::getExternalDocs, oldTag::setExternalDocs);
                merge(oldTag::getExtensions, newTag::getExtensions, oldTag::setExtensions, SORT_EXTENSIONS);
            }
            case ExternalDocumentation oldDoc when newItem instanceof final ExternalDocumentation newDoc -> {
                merge(oldDoc::getDescription, newDoc::getDescription, newDoc::setDescription);
                merge(oldDoc::getUrl, newDoc::getUrl, newDoc::setUrl);
                merge(oldDoc::getExtensions, newDoc::getExtensions, newDoc::setExtensions, SORT_EXTENSIONS);
            }
            case Parameter oldParam when newItem instanceof final Parameter newParam -> {
                merge(oldParam::getName, newParam::getName, oldParam::setName);
                merge(oldParam::getIn, newParam::getIn, oldParam::setIn);
                merge(oldParam::getDescription, newParam::getDescription, oldParam::setDescription);
                merge(oldParam::getRequired, newParam::getRequired, oldParam::setRequired);
                merge(oldParam::getDeprecated, newParam::getDeprecated, oldParam::setDeprecated);
                merge(oldParam::getAllowEmptyValue, newParam::getAllowEmptyValue, oldParam::setAllowEmptyValue);
                merge(oldParam::get$ref, newParam::get$ref, oldParam::set$ref);
                merge(oldParam::getStyle, newParam::getStyle, oldParam::setStyle);
                merge(oldParam::getExplode, newParam::getExplode, oldParam::setExplode);
                merge(oldParam::getAllowReserved, newParam::getAllowReserved, oldParam::setAllowReserved);
                merge(oldParam::getSchema, newParam::getSchema, oldParam::setSchema);
                merge(oldParam::getExamples, newParam::getExamples, oldParam::setExamples, SORT_EXAMPLES);
                merge(oldParam::getExample, newParam::getExample, oldParam::setExample);
                merge(oldParam::getContent, newParam::getContent, oldParam::setContent, SORT_CONTENT);
                merge(oldParam::getExtensions, newParam::getExtensions, oldParam::setExtensions, SORT_EXTENSIONS);
            }
            case Header oldHeader when newItem instanceof final Header newHeader -> {
                merge(oldHeader::getDescription, newHeader::getDescription, oldHeader::setDescription);
                merge(oldHeader::get$ref, newHeader::get$ref, oldHeader::set$ref);
                merge(oldHeader::getRequired, newHeader::getRequired, oldHeader::setRequired);
                merge(oldHeader::getDeprecated, newHeader::getDeprecated, oldHeader::setDeprecated);
                merge(oldHeader::getStyle, newHeader::getStyle, oldHeader::setStyle);
                merge(oldHeader::getExplode, newHeader::getExplode, oldHeader::setExplode);
                merge(oldHeader::getSchema, newHeader::getSchema, oldHeader::setSchema);
                merge(oldHeader::getExamples, newHeader::getExamples, oldHeader::setExamples, SORT_EXAMPLES);
                merge(oldHeader::getContent, newHeader::getContent, oldHeader::setContent, SORT_CONTENT);
                merge(oldHeader::getExtensions, newHeader::getExtensions, oldHeader::setExtensions, SORT_EXTENSIONS);
            }
            case ApiResponse oldResp when newItem instanceof final ApiResponse newResp -> {
                merge(oldResp::getDescription, newResp::getDescription, oldResp::setDescription);
                merge(oldResp::getHeaders, newResp::getHeaders, oldResp::setHeaders, SORT_HEADERS);
                merge(oldResp::getContent, newResp::getContent, oldResp::setContent, SORT_CONTENT);
                merge(oldResp::getLinks, newResp::getLinks, oldResp::setLinks, SORT_WEBHOOKS);
                merge(oldResp::get$ref, newResp::get$ref, oldResp::set$ref);
                merge(oldResp::getExtensions, newResp::getExtensions, oldResp::setExtensions, SORT_EXTENSIONS);
            }
            case ApiResponses oldResp when newItem instanceof final ApiResponses newResp -> {
                merge(oldResp::getDefault, newResp::getDefault, oldResp::setDefault);
                merge(oldResp::getExtensions, newResp::getExtensions, oldResp::setExtensions, SORT_EXTENSIONS);
            }
            case RequestBody oldBody when newItem instanceof final RequestBody newBody -> {
                merge(oldBody::getDescription, newBody::getDescription, oldBody::setDescription, SORT_EXTENSIONS);
                merge(oldBody::getContent, newBody::getContent, oldBody::setContent, SORT_CONTENT);
                merge(oldBody::get$ref, newBody::get$ref, oldBody::set$ref);
                merge(oldBody::getExtensions, newBody::getExtensions, oldBody::setExtensions, SORT_EXTENSIONS);
            }
            case Callback oldCall when newItem instanceof final Callback newCall -> {
                merge(oldCall::get$ref, newCall::get$ref, oldCall::set$ref);
                merge(oldCall::getExtensions, newCall::getExtensions, oldCall::setExtensions, SORT_EXTENSIONS);
            }
            case SecurityRequirement oldSec when newItem instanceof final SecurityRequirement newSec -> {
                merge(oldSec::getName, newSec::getName, oldSec::setName);
                merge(oldSec::getScopes, newSec::getScopes, oldSec::setScopes, SORT_SCOPES);
                merge(oldSec::getRequirements, newSec::getRequirements, i -> i.forEach(oldSec::setRequirements));
            }
            case Example oldEx when newItem instanceof final Example newEx -> {
                merge(oldEx::getSummary, newEx::getSummary, oldEx::setSummary);
                merge(oldEx::getDescription, newEx::getDescription, oldEx::setDescription);
                merge(oldEx::getValue, newEx::getValue, oldEx::setValue);
                merge(oldEx::getExternalValue, newEx::getExternalValue, oldEx::setExternalValue);
                merge(oldEx::get$ref, newEx::get$ref, oldEx::set$ref);
                merge(oldEx::getValueSetFlag, newEx::getValueSetFlag, oldEx::setValueSetFlag);
                merge(oldEx::getExtensions, newEx::getExtensions, oldEx::setExtensions, SORT_EXTENSIONS);
            }
            case Operation oldOp when newItem instanceof final Operation newOp -> {
                final AtomicBoolean remove = new AtomicBoolean(false);
                filterAndRemoveOp(() -> oldOp, op -> remove.compareAndSet(false, op == null));
                filterAndRemoveOp(() -> newOp, op -> remove.compareAndSet(false, op == null));
                merge(oldOp::getTags, newOp::getTags, oldOp::setTags, SORT_TAGS);
                merge(oldOp::getSummary, newOp::getSummary, oldOp::setSummary);
                merge(oldOp::getDescription, newOp::getDescription, oldOp::setDescription);
                merge(oldOp::getExternalDocs, newOp::getExternalDocs, oldOp::setExternalDocs);
                merge(oldOp::getOperationId, newOp::getOperationId, oldOp::setOperationId);
                merge(oldOp::getParameters, newOp::getParameters, oldOp::setParameters, SORT_PARAMETERS);
                merge(oldOp::getRequestBody, newOp::getRequestBody, oldOp::setRequestBody);
                merge(oldOp::getResponses, newOp::getResponses, oldOp::setResponses, SORT_RESPONSES);
                merge(oldOp::getCallbacks, newOp::getCallbacks, oldOp::setCallbacks, SORT_WEBHOOKS);
                merge(oldOp::getDeprecated, newOp::getDeprecated, oldOp::setDeprecated);
                merge(oldOp::getSecurity, newOp::getSecurity, oldOp::setSecurity, SORT_SECURITY);
                merge(oldOp::getServers, newOp::getServers, oldOp::setServers, SORT_SERVERS);
                merge(oldOp::getExtensions, newOp::getExtensions, oldOp::setExtensions, SORT_EXTENSIONS);
                if (remove.get())
                    return null;
            }
            case PathItem oldPathItem when newItem instanceof final PathItem newPathItem -> {
                merge(oldPathItem::getSummary, newPathItem::getSummary, oldPathItem::setSummary);
                merge(oldPathItem::getDescription, newPathItem::getDescription, oldPathItem::setDescription);
                merge(oldPathItem::getGet, newPathItem::getGet, oldPathItem::setGet);
                merge(oldPathItem::getPut, newPathItem::getPut, oldPathItem::setPut);
                merge(oldPathItem::getPost, newPathItem::getPost, oldPathItem::setPost);
                merge(oldPathItem::getDelete, newPathItem::getDelete, oldPathItem::setDelete);
                merge(oldPathItem::getOptions, newPathItem::getOptions, oldPathItem::setOptions);
                merge(oldPathItem::getHead, newPathItem::getHead, oldPathItem::setHead);
                merge(oldPathItem::getPatch, newPathItem::getPatch, oldPathItem::setPatch);
                merge(oldPathItem::getTrace, newPathItem::getTrace, oldPathItem::setTrace);
                merge(oldPathItem::getServers, newPathItem::getServers, oldPathItem::setServers, SORT_SERVERS);
                merge(oldPathItem::getParameters, newPathItem::getParameters, oldPathItem::setParameters, SORT_PARAMETERS);
                merge(oldPathItem::get$ref, newPathItem::get$ref, oldPathItem::set$ref);
                merge(oldPathItem::getExtensions, newPathItem::getExtensions, oldPathItem::setExtensions, SORT_EXTENSIONS);
            }
            case Schema<?> oldSchemaGen when newItem instanceof final Schema<?> newSchemaGen -> {
                final Schema<Object> oldSchema = (Schema<Object>) oldSchemaGen;
                final Schema<Object> newSchema = (Schema<Object>) newSchemaGen;
                merge(oldSchema::getDefault, newSchema::getDefault, oldSchema::setDefault);
                merge(oldSchema::getName, newSchema::getName, oldSchema::setName);
                merge(oldSchema::getTitle, newSchema::getTitle, oldSchema::setTitle);
                merge(oldSchema::getMultipleOf, newSchema::getMultipleOf, oldSchema::setMultipleOf);
                merge(oldSchema::getMaximum, newSchema::getMaximum, oldSchema::setMaximum);
                merge(oldSchema::getExclusiveMaximum, newSchema::getExclusiveMaximum, oldSchema::setExclusiveMaximum);
                merge(oldSchema::getMinimum, newSchema::getMinimum, oldSchema::setMinimum);
                merge(oldSchema::getExclusiveMinimum, newSchema::getExclusiveMinimum, oldSchema::setExclusiveMinimum);
                merge(oldSchema::getMaxLength, newSchema::getMaxLength, oldSchema::setMaxLength);
                merge(oldSchema::getMinLength, newSchema::getMinLength, oldSchema::setMinLength);
                merge(oldSchema::getPattern, newSchema::getPattern, oldSchema::setPattern);
                merge(oldSchema::getMaxItems, newSchema::getMaxItems, oldSchema::setMaxItems);
                merge(oldSchema::getUniqueItems, newSchema::getUniqueItems, oldSchema::setUniqueItems);
                merge(oldSchema::getMaxProperties, newSchema::getMaxProperties, oldSchema::setMaxProperties);
                merge(oldSchema::getMinProperties, newSchema::getMinProperties, oldSchema::setMinProperties);
                merge(oldSchema::getRequired, newSchema::getRequired, oldSchema::setRequired, SORT_PARAMETERS);
                merge(oldSchema::getType, newSchema::getType, oldSchema::setType);
                merge(oldSchema::getNot, newSchema::getNot, oldSchema::setNot);
                merge(oldSchema::getProperties, newSchema::getProperties, oldSchema::setProperties, SORT_PARAMETERS);
                merge(oldSchema::getAdditionalProperties, newSchema::getAdditionalProperties, oldSchema::setAdditionalProperties);
                merge(oldSchema::getDescription, newSchema::getDescription, oldSchema::setDescription);
                merge(oldSchema::getFormat, newSchema::getFormat, oldSchema::setFormat);
                merge(oldSchema::get$ref, newSchema::get$ref, oldSchema::set$ref);
                merge(oldSchema::getNullable, newSchema::getNullable, oldSchema::setNullable);
                merge(oldSchema::getReadOnly, newSchema::getReadOnly, oldSchema::setReadOnly);
                merge(oldSchema::getWriteOnly, newSchema::getWriteOnly, oldSchema::setWriteOnly);
                merge(oldSchema::getExample, newSchema::getExample, oldSchema::setExample);
                merge(oldSchema::getExternalDocs, newSchema::getExternalDocs, oldSchema::setExternalDocs);
                merge(oldSchema::getDeprecated, newSchema::getDeprecated, oldSchema::setDeprecated);
                merge(oldSchema::getXml, newSchema::getXml, oldSchema::setXml);
                merge(oldSchema::getExtensions, newSchema::getExtensions, oldSchema::setExtensions, SORT_EXTENSIONS);
                merge(oldSchema::getEnum, newSchema::getEnum, oldSchema::setEnum, SORT_SCOPES);
                merge(oldSchema::getExamples, newSchema::getExamples, oldSchema::setExamples, SORT_EXAMPLES);
                merge(oldSchema::getDiscriminator, newSchema::getDiscriminator, oldSchema::setDiscriminator, SORT_EXTENSIONS);
                oldSchema.setExampleSetFlag(newSchema.getExampleSetFlag());
                merge(oldSchema::getPrefixItems, newSchema::getPrefixItems, oldSchema::setPrefixItems, SORT_SCOPES);
                merge(oldSchema::getAllOf, newSchema::getAllOf, oldSchema::setAllOf, SORT_SCHEMAS);
                merge(oldSchema::getAnyOf, newSchema::getAnyOf, oldSchema::setAnyOf, SORT_SCHEMAS);
                merge(oldSchema::getOneOf, newSchema::getOneOf, oldSchema::setOneOf, SORT_SCHEMAS);
                merge(oldSchema::getItems, newSchema::getItems, oldSchema::setItems, SORT_SCHEMAS);
                merge(oldSchema::getConst, newSchema::getConst, oldSchema::setConst);
                merge(oldSchema::getSpecVersion, newSchema::getSpecVersion, oldSchema::setSpecVersion);
                merge(oldSchema::getPatternProperties, newSchema::getPatternProperties, oldSchema::setPatternProperties, SORT_PARAMETERS);
                merge(oldSchema::getExclusiveMaximumValue, newSchema::getExclusiveMaximumValue, oldSchema::setExclusiveMaximumValue);
                merge(oldSchema::getExclusiveMinimumValue, newSchema::getExclusiveMinimumValue, oldSchema::setExclusiveMinimumValue);
                merge(oldSchema::getContains, newSchema::getContains, oldSchema::setContains);
                merge(oldSchema::get$id, newSchema::get$id, oldSchema::set$id);
                merge(oldSchema::get$anchor, newSchema::get$anchor, oldSchema::set$anchor);
                merge(oldSchema::get$vocabulary, newSchema::get$vocabulary, oldSchema::set$vocabulary);
                merge(oldSchema::get$dynamicAnchor, newSchema::get$dynamicAnchor, oldSchema::set$dynamicAnchor);
                merge(oldSchema::getContentEncoding, newSchema::getContentEncoding, oldSchema::setContentEncoding);
                merge(oldSchema::getContentMediaType, newSchema::getContentMediaType, oldSchema::setContentMediaType);
                merge(oldSchema::getContentSchema, newSchema::getContentSchema, oldSchema::setContentSchema);
                merge(oldSchema::getPropertyNames, newSchema::getPropertyNames, oldSchema::setPropertyNames);
                merge(oldSchema::getUnevaluatedProperties, newSchema::getUnevaluatedProperties, oldSchema::setUnevaluatedProperties);
                merge(oldSchema::getMaxContains, newSchema::getMaxContains, oldSchema::setMaxContains);
                merge(oldSchema::getMinContains, newSchema::getMinContains, oldSchema::setMinContains);
                merge(oldSchema::getAdditionalItems, newSchema::getAdditionalItems, oldSchema::setAdditionalItems);
                merge(oldSchema::getUnevaluatedItems, newSchema::getUnevaluatedItems, oldSchema::setUnevaluatedItems);
                merge(oldSchema::getIf, newSchema::getIf, oldSchema::setIf);
                merge(oldSchema::getElse, newSchema::getElse, oldSchema::setElse);
                merge(oldSchema::getThen, newSchema::getThen, oldSchema::setThen);
                merge(oldSchema::getDependentSchemas, newSchema::getDependentSchemas, oldSchema::setDependentSchemas, SORT_SCHEMAS);
                merge(oldSchema::get$comment, newSchema::get$comment, oldSchema::set$comment);
                merge(oldSchema::getBooleanSchemaValue, newSchema::getBooleanSchemaValue, oldSchema::setBooleanSchemaValue);
                merge(oldSchema::getJsonSchema, newSchema::getJsonSchema, oldSchema::setJsonSchema);
                merge(oldSchema::getJsonSchemaImpl, newSchema::getJsonSchemaImpl, oldSchema::setJsonSchemaImpl);
            }
            case XML oldXml when newItem instanceof final XML newXml -> {
                merge(oldXml::getName, newXml::getName, oldXml::setName);
                merge(oldXml::getNamespace, newXml::getNamespace, oldXml::setNamespace);
                merge(oldXml::getPrefix, newXml::getPrefix, oldXml::setPrefix);
                merge(oldXml::getAttribute, newXml::getAttribute, oldXml::setAttribute);
                merge(oldXml::getWrapped, newXml::getWrapped, oldXml::setWrapped);
                merge(oldXml::getExtensions, newXml::getExtensions, oldXml::setExtensions, SORT_EXTENSIONS);
            }
            case ServerVariables oldServerV when newItem instanceof final ServerVariables newServerV -> {
                mergeMap(oldServerV, newServerV, sortBy(ascending));
                merge(oldServerV::getExtensions, newServerV::getExtensions, oldServerV::setExtensions, SORT_EXTENSIONS);
            }
            case Paths oldPaths when newItem instanceof final Paths newPaths -> {
                // Complex path filtering
                filterAndRemoveOp(oldPaths);
                filterAndRemoveOp(newPaths);
                mergeMap(oldPaths, newPaths, sortBy(ascending));
                merge(oldPaths::getExtensions, newPaths::getExtensions, oldPaths::setExtensions, SORT_EXTENSIONS);
            }
            case Collection<?> oldList when newItem instanceof Collection<?> newList ->
                mergeCollection((Collection<Object>) oldList, (Collection<Object>) newList, sortBy(ascending));
            case Map<?, ?> oldMap when newItem instanceof Map<?, ?> newMap ->
                mergeMap((Map<Object, Object>) oldMap, (Map<Object, Object>) newMap, sortBy(ascending));
            case LinkParameter oldObj when newItem instanceof final LinkParameter newObj -> {
                merge(oldObj::getValue, newObj::getValue, oldObj::setValue);
                merge(oldObj::getExtensions, newObj::getExtensions, oldObj::setExtensions, SORT_EXTENSIONS);
            }
            case Discriminator oldObj when newItem instanceof final Discriminator newObj -> {
                merge(oldObj::getPropertyName, newObj::getPropertyName, oldObj::setPropertyName);
                merge(oldObj::getMapping, newObj::getMapping, oldObj::setMapping, SORT_WEBHOOKS);
                merge(oldObj::getExtensions, newObj::getExtensions, oldObj::setExtensions, SORT_EXTENSIONS);
            }
            case Encoding oldObj when newItem instanceof final Encoding newObj -> {
                merge(oldObj::getContentType, newObj::getContentType, oldObj::setContentType);
                merge(oldObj::getHeaders, newObj::getHeaders, oldObj::setHeaders, SORT_WEBHOOKS);
                merge(oldObj::getStyle, newObj::getStyle, oldObj::setStyle);
                merge(oldObj::getStyle, newObj::getStyle, oldObj::setStyle);
                merge(oldObj::getExplode, newObj::getExplode, oldObj::setExplode);
                merge(oldObj::getAllowReserved, newObj::getAllowReserved, oldObj::setAllowReserved);
                merge(oldObj::getExtensions, newObj::getExtensions, oldObj::setExtensions, SORT_EXTENSIONS);
            }
            case EncodingProperty oldObj when newItem instanceof final EncodingProperty newObj -> {
                merge(oldObj::getContentType, newObj::getContentType, oldObj::setContentType);
                merge(oldObj::getHeaders, newObj::getHeaders, oldObj::setHeaders, SORT_WEBHOOKS);
                merge(oldObj::getStyle, newObj::getStyle, oldObj::setStyle);
                merge(oldObj::getStyle, newObj::getStyle, oldObj::setStyle);
                merge(oldObj::getExplode, newObj::getExplode, oldObj::setExplode);
                merge(oldObj::getAllowReserved, newObj::getAllowReserved, oldObj::setAllowReserved);
                merge(oldObj::getExtensions, newObj::getExtensions, oldObj::setExtensions, SORT_EXTENSIONS);
            }
            default -> {
                if (oldItem instanceof Date
                    || oldItem instanceof Boolean
                    || oldItem instanceof Number
                    || oldItem instanceof CharSequence
                    || oldItem instanceof ArrayNode
                    || oldItem instanceof ObjectNode
                    || oldItem instanceof TemporalAccessor
                    || oldItem instanceof Enum<?>
                    || TreeNode.class.isAssignableFrom(oldItem.getClass())
                ) {
                    // ignored not merge-able
                } else {
                    System.out.println("[DEBUG] Unknown type [" + oldItem.getClass() + "]");
                }
            }
        }
        return oldItem;
    }

    private static void filterAndRemoveOp(final Paths paths) {
        if (paths != null) {
            paths.values().forEach(item -> ofNullable(item).ifPresent(pathItem -> {
                filterAndRemoveOp(pathItem::getGet, pathItem::setGet);
                filterAndRemoveOp(pathItem::getPut, pathItem::setPut);
                filterAndRemoveOp(pathItem::getHead, pathItem::setHead);
                filterAndRemoveOp(pathItem::getPost, pathItem::setPost);
                filterAndRemoveOp(pathItem::getDelete, pathItem::setDelete);
                filterAndRemoveOp(pathItem::getPatch, pathItem::setPatch);
                filterAndRemoveOp(pathItem::getOptions, pathItem::setOptions);
                filterAndRemoveOp(pathItem::getTrace, pathItem::setTrace);
            }));
            // Remove empty and glob paths
            paths.entrySet().removeIf(entry ->
                ofNullable(entry.getValue()).filter(item -> item.readOperations() == null || item.readOperations().isEmpty()).isPresent()
                    || ofNullable(entry.getValue()).map(Identifier::getKeys).filter(item -> Arrays.stream(item).anyMatch(Util::matchesRemoveGlob)).isPresent()
            );
        }
    }

    private static void filterAndRemoveOp(final Supplier<Operation> getOp, final Consumer<Operation> setOp) {
        ofNullable(getOp).map(Supplier::get).filter(op -> ofNullable(op.getTags()).map(tags -> tags.stream().anyMatch(Util::matchesRemoveGlob)).orElse(false)).ifPresent(op -> setOp.accept(null));
    }

    private static boolean shouldRemove(final Object item) {
        return ofNullable(Identifier.getKeys(item)).map(keys -> Arrays.stream(keys).anyMatch(Util::matchesRemoveGlob)).orElse(false);
    }

    private static void sort(final Collection<Object> list, final boolean ascending) {
        ((List<Object>) list).sort((o1, o2) -> ascending ? Identifier.compareKeys(o1, o2) : Identifier.compareKeys(o2, o1));
    }

    private static <K, V> void sort(final Map<K, V> map, final boolean ascending) {
        List<Map.Entry<K, V>> sortedEntries = new ArrayList<>(map.entrySet());
        sortedEntries.sort((entry1, entry2) -> ascending ? Identifier.compareKeys(entry1.getKey(), entry2.getKey()) : Identifier.compareKeys(entry2.getKey(), entry1.getKey()));

        map.clear();
        sortedEntries.forEach(entry -> map.put(entry.getKey(), entry.getValue()));
    }

    private static Components mergeComponents(final Components oldComp, final Components newComp) {
        if (oldComp == null || newComp == null) return newComp;
        merge(oldComp::getSchemas, newComp::getSchemas, oldComp::setSchemas, SORT_SCHEMAS);
        merge(oldComp::getResponses, newComp::getResponses, oldComp::setResponses, SORT_RESPONSES);
        merge(oldComp::getParameters, newComp::getParameters, oldComp::setParameters, SORT_PARAMETERS);
        merge(oldComp::getExamples, newComp::getExamples, oldComp::setExamples, SORT_EXAMPLES);
        merge(oldComp::getRequestBodies, newComp::getRequestBodies, oldComp::setRequestBodies, SORT_REQUESTS);
        merge(oldComp::getHeaders, newComp::getHeaders, oldComp::setHeaders, SORT_HEADERS);
        merge(oldComp::getSecuritySchemes, newComp::getSecuritySchemes, oldComp::securitySchemes, SORT_SECURITY);
        merge(oldComp::getLinks, newComp::getLinks, oldComp::setLinks, SORT_WEBHOOKS);
        merge(oldComp::getCallbacks, newComp::getCallbacks, oldComp::setCallbacks, SORT_WEBHOOKS);
        merge(oldComp::getExtensions, newComp::getExtensions, oldComp::setExtensions, SORT_EXTENSIONS);
        return oldComp;
    }

    // DX function to merge two values
    private static <T> void merge(final T oldValue, final T newValue) {
        merge(() -> oldValue, () -> newValue, nothing());
    }

    // DX function to merge two values
    private static <T> void mergeFun(final Supplier<T> oldValue, final Supplier<T> newValue, final BiConsumer<T, T> onExist, final Consumer<T> onNew) {
        ofNullable(newValue).map(Supplier::get).ifPresent(newItem -> ofNullable(oldValue).map(Supplier::get).ifPresentOrElse(oldItem -> onExist.accept(oldItem, newItem), () -> onNew.accept(newItem)));
    }

    /**
     * Private utility class constructor to prevent instantiation.
     */
    private Merger() {
        // utility class
    }
}
