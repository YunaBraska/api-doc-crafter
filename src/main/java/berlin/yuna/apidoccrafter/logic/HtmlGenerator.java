package berlin.yuna.apidoccrafter.logic;

import berlin.yuna.apidoccrafter.config.Config;
import berlin.yuna.typemap.logic.ArgsDecoder;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.OpenAPI;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static berlin.yuna.apidoccrafter.config.Config.LOCAL_PATTERN;
import static berlin.yuna.apidoccrafter.config.Config.SORT_PATHS;
import static berlin.yuna.apidoccrafter.config.Config.SORT_TAGS;
import static berlin.yuna.apidoccrafter.config.Config.STATIC_SWAGGER_BUNDLE_JS;
import static berlin.yuna.apidoccrafter.config.Config.STATIC_SWAGGER_CSS;
import static berlin.yuna.apidoccrafter.config.Config.STATIC_SWAGGER_FAVICON;
import static berlin.yuna.apidoccrafter.config.Config.STATIC_SWAGGER_LOGO;
import static berlin.yuna.apidoccrafter.config.Config.STATIC_SWAGGER_STANDALONE_JS;
import static berlin.yuna.apidoccrafter.config.Config.SWAGGER_BUNDLE_JS;
import static berlin.yuna.apidoccrafter.config.Config.SWAGGER_CSS;
import static berlin.yuna.apidoccrafter.config.Config.SWAGGER_FAVICON;
import static berlin.yuna.apidoccrafter.config.Config.SWAGGER_JS;
import static berlin.yuna.apidoccrafter.config.Config.SWAGGER_LINKS;
import static berlin.yuna.apidoccrafter.config.Config.SWAGGER_LOGO;
import static berlin.yuna.apidoccrafter.config.Config.SWAGGER_LOGO_LINK;
import static berlin.yuna.apidoccrafter.config.Config.SWAGGER_NAV;
import static berlin.yuna.apidoccrafter.config.Config.SWAGGER_NAV_CSS;
import static berlin.yuna.apidoccrafter.config.Config.SWAGGER_TITLE;
import static berlin.yuna.apidoccrafter.config.Config.config;
import static berlin.yuna.apidoccrafter.config.Config.sortBy;
import static berlin.yuna.apidoccrafter.util.Util.copyResourceToOutput;
import static berlin.yuna.apidoccrafter.util.Util.displayName;
import static berlin.yuna.apidoccrafter.util.Util.filenameHtml;
import static berlin.yuna.apidoccrafter.util.Util.filenameJson;
import static berlin.yuna.apidoccrafter.util.Util.filenameYaml;
import static berlin.yuna.apidoccrafter.util.Util.readResource;
import static berlin.yuna.apidoccrafter.util.Util.replaceVariables;
import static berlin.yuna.apidoccrafter.util.Util.safeJsonMapper;
import static berlin.yuna.apidoccrafter.util.Util.writeFile;
import static java.util.Optional.ofNullable;

@SuppressWarnings({"java:S106"})
public class HtmlGenerator {

    public static final String LS = System.lineSeparator();

    /**
     * Generates a dynamic HTML file with Swagger UI and navigation to switch between OpenAPI files.
     *
     * @param mergedApis The map of file paths to merged OpenAPI objects.
     * @param outputDir  The directory where the HTML file will be saved.
     */
    public static void generateHtml(final Map<Path, OpenAPI> mergedApis, final Path outputDir) {
        // HTML pages
        mergedApis.forEach((path, openApi) -> writeFile(
            outputDir.resolve(filenameHtml(path, openApi)),
            replaceVariables(getSwaggerUiTemplate(path, openApi, mergedApis))
        ));

        // Index page
        writeFile(outputDir.resolve("index.html"), replaceVariables(generateHead(config().asStringOpt(SWAGGER_TITLE).orElse("API Documentation")) + "<body>" + generateNavigation(mergedApis, null) + "</body></html>"));

        // RESOURCES
        copyResourceToOutput("bin/static/favicon.png", outputDir);
        copyResourceToOutput("bin/static/swagger.css", outputDir);
        copyResourceToOutput("bin/static/swagger_nav.css", outputDir);
        copyResourceToOutput("bin/static/swagger.js", outputDir);
        copyResourceToOutput("bin/static/swagger_bundle.js", outputDir);
        copyResourceToOutput("bin/static/logo.png", outputDir);
    }

    private static String getSwaggerUiTemplate(final Path path, final OpenAPI openApi, final Map<Path, OpenAPI> mergedApis) {
        //https://swagger.io/docs/open-source-tools/swagger-ui/usage/installation/
        return generateHead(displayName(path, openApi))
            + "<body>" + LS
            // ########## NAVIGATION     ##########
            + generateNavigation(mergedApis, displayName(path, openApi))
            // ########## DOWNLOAD LINKS ##########
            + generateSourceLinks(path, openApi)
            // ########## SWAGGER        ##########
            + "<div id=\"swagger-ui\"></div>" + LS
            + "<script src=\"" + getLocation(SWAGGER_JS, "swagger.js", STATIC_SWAGGER_STANDALONE_JS) + "\" crossorigin></script>" + LS
            + "<script src=\"" + getLocation(SWAGGER_BUNDLE_JS, "swagger_bundle.js", STATIC_SWAGGER_BUNDLE_JS) + "\" crossorigin></script>" + LS
            + "<script>" + LS
            + "  SwaggerUIBundle(" + LS
            + "    {" + LS
            + "      spec: " + openApiToJson(openApi) + "," + LS
            + ofNullable(Config.sortBy(SORT_TAGS)).map(asc -> "      tagsSorter: " + (asc ? "'alpha'" : "(a, b) => a.toLowerCase() < b.toLowerCase() ? 1 : -1") + "," + LS).orElse("")
            + ofNullable(Config.sortBy(SORT_PATHS)).map(asc -> "      operationsSorter: " + (asc ? "'alpha'" : "(a, b) => a.toLowerCase() < b.toLowerCase() ? 1 : -1") + "," + LS).orElse("")
            + "      dom_id:'#swagger-ui'," + LS
            + "      deepLinking:true," + LS
            + "      presets:[SwaggerUIBundle.presets.apis,SwaggerUIStandalonePreset]," + LS
            + "      layout:'BaseLayout'" + LS
            + "    }" + LS
            + "  );" + LS
            + "</script>" + LS
            + "</body></html>" + LS
            ;
    }

    private static String generateSourceLinks(final Path path, final OpenAPI openApi) {
        final Boolean enableLinks = sortBy(SWAGGER_LINKS);
        return enableLinks == null || !enableLinks ? "" : "<div class=\"swagger-ui source-links\">" + LS
            + "  <a href='" + filenameJson(path, openApi) + "' rel=\"noopener noreferrer\" class=\"link\" target=\"" + filenameJson(path, openApi) + "\">JSON</a>" + LS
            + "  <a href='" + filenameYaml(path, openApi) + "' rel=\"noopener noreferrer\" class=\"link\" target=\"" + filenameYaml(path, openApi) + "\">YAML</a>" + LS
            + "</div>" + LS;
    }

    private static String generateNavigation(final Map<Path, OpenAPI> mergedApis, final String selected) {
        final StringBuilder navigation = new StringBuilder();
        if (config().asBooleanOpt(SWAGGER_NAV).orElse(true)) {
            navigation.append("  <nav class=\"swagger-ui sidebar\">").append(LS);
            config().asStringOpt(SWAGGER_LOGO).filter(ArgsDecoder::hasText).ifPresent(logo -> {
                navigation.append("    <div>").append(LS);
                config().asStringOpt(SWAGGER_LOGO_LINK).filter(ArgsDecoder::hasText).ifPresentOrElse(
                    logoLink -> navigation.append("    <a class=\"link\" href='").append(logoLink).append("'><img src=\"").append(getLocation(SWAGGER_LOGO, "logo.png", STATIC_SWAGGER_LOGO)).append("\" alt=\"Logo\"/></a>").append(LS),
                    () -> navigation.append("    <img src=\"").append(getLocation(SWAGGER_LOGO, "logo.png", STATIC_SWAGGER_LOGO)).append("\" alt=\"Logo\"/>").append(LS)
                );
                navigation.append("    </div>").append(LS);
            });
            navigation.append("    <h2>").append(config().asStringOpt(SWAGGER_TITLE).orElse("API Documentation")).append("</h2>").append(LS).append("    <ul>").append(LS);
            mergedApis.forEach((path, openAPI) -> {
                final String displayName = displayName(path, openAPI);
                navigation.append("      <li><a class=\"link\" href='").append(filenameHtml(path, openAPI)).append("'>").append(displayName.equals(selected) ? "<strong>" + displayName + "</strong>" : displayName).append("</a></li>").append(LS);
            });
            navigation.append("    </ul>").append(LS)
                .append("  </nav>").append(LS);
        }
        return navigation.toString();
    }

    private static String getLocation(final String key, final String local, final String defaultLocation) {
        return config().asStringOpt(key).filter(ArgsDecoder::hasText).map(s -> s.equals(LOCAL_PATTERN) ? local : s).orElse(defaultLocation);
    }

    private static String generateHead(final String title) {
        return "<!DOCTYPE html>" + LS
            + "<html lang=\"en\">" + LS
            + "<head>" + LS
            + "  <meta charset=\"UTF-8\">" + LS
            + "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" + LS
            + "  <title>" + title + "</title>" + LS
            + "  <link href=\"" + getLocation(SWAGGER_FAVICON, "favicon.png", STATIC_SWAGGER_FAVICON) + "\" type=\"image/png\" rel=\"shortcut icon\">" + LS
            + "  <link href=\"" + getLocation(SWAGGER_FAVICON, "favicon.png", STATIC_SWAGGER_FAVICON) + "\" type=\"image/png\" rel=\"icon\">" + LS
            + "  <link rel=\"stylesheet\" href=\"" + getLocation(SWAGGER_CSS, "swagger.css", STATIC_SWAGGER_CSS) + "\" />" + LS
            + ofNullable(getLocation(SWAGGER_NAV_CSS, "swagger_nav.css", null)).map(s -> "  <link rel=\"stylesheet\" href=\"" + s + "\" />").orElseGet(() -> "<style>" + readResource("bin/static/swagger_nav.css") + "</style>") + LS
            + "</head>" + LS;
    }

    private static String openApiToJson(final OpenAPI openApi) {
        try {
            return safeJsonMapper.writeValueAsString(openApi);
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to write json cause [" + e.getClass().getSimpleName() + "] message [" + e.getMessage() + "]");
            return "{}";
        }
    }

    private HtmlGenerator() {
        // Utility class
    }
}
