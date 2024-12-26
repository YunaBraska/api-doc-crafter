package berlin.yuna.apidoccrafter.logic;

import io.swagger.v3.oas.models.OpenAPI;

import java.nio.file.Path;
import java.util.Map;

import static berlin.yuna.apidoccrafter.config.Config.CREATE_NAV;
import static berlin.yuna.apidoccrafter.config.Config.config;
import static berlin.yuna.apidoccrafter.util.Util.*;

public class HtmlGenerator {

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
            getSwaggerUiTemplate(displayName(path, openApi), mergedApis, filenameYaml(path, openApi))
        ));

        // Index page
        writeFile(outputDir.resolve("index.html"), generateHead(config().asStringOpt("title").orElse("API Documentation")) + "<body>" + generateNavigation(mergedApis, null) + "</body></html>");
    }

    private static String getSwaggerUiTemplate(final String title, final Map<Path, OpenAPI> mergedApis, final String swaggerYamlPath) {
        //https://swagger.io/docs/open-source-tools/swagger-ui/usage/installation/
        return generateHead(title) + """
            <body>
            """ + generateNavigation(mergedApis, title) + """
            <div id="swagger-ui"></div>
            <script src="https://unpkg.com/swagger-ui-dist@5.11.0/swagger-ui-bundle.js" crossorigin></script>
            <script src="https://unpkg.com/swagger-ui-dist@5.11.0/swagger-ui-standalone-preset.js" crossorigin></script>
            <script>
                    SwaggerUIBundle({
                        url: '
            """.trim() + swaggerYamlPath + """
                        ',
                        dom_id: '#swagger-ui',
                        deepLinking: true,
                        presets: [
                            SwaggerUIBundle.presets.apis,
                            SwaggerUIStandalonePreset
                        ],
                        layout: "BaseLayout"
                    });
                </script>
            </body>
            </html>
            """.trim();
    }

    private static String generateNavigation(final Map<Path, OpenAPI> mergedApis, final String selected) {
        final StringBuilder navigation = new StringBuilder();
        if (config().asBooleanOpt(CREATE_NAV).orElse(true)) {
            navigation.append("<nav class=\".swagger-ui\"><h2>Docs</h2><ul>");
            mergedApis.forEach((path, openAPI) -> {
                final String displayName = displayName(path, openAPI);
                navigation
                    .append("<li><a href='").append(filenameHtml(path, openAPI)).append("'>")
                    .append(displayName.equals(selected) ? "<strong>" + displayName + "</strong>" : displayName)
                    .append("</a></li>");
            });
            navigation.append("</ul></nav>");
        }
        return navigation.toString();
    }

    private static String generateHead(final String title) {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>
            """.trim() + title + """
                </title>
                <link rel="icon" type="image/png" href="https://petstore.swagger.io/favicon-32x32.png" sizes="32x32" />
                <link rel="icon" type="image/png" href="https://petstore.swagger.io/favicon-16x16.png" sizes="16x16" />
                <link rel="stylesheet" href="https://unpkg.com/swagger-ui-dist@5.11.0/swagger-ui.css" />
                <style>
                    body { display: flex; font-family: Arial, sans-serif; margin: 0; height: 100vh; overflow: hidden; }
                    nav { width: 250px; background: rgba(0,0,0,.05); border-right: 1px solid #ddd; padding: 20px; overflow-y: auto; }
                    nav ul { list-style: none; padding: 0; }
                    nav li { padding: 10px; }
                    nav a { text-decoration: none; color: #3b4151; }
                    nav a:hover { text-decoration: underline; }
                    #swagger-ui { flex: 1; overflow-y: auto; }
                </style>
            </head>
            """.trim();
    }

    private HtmlGenerator() {
        // Utility class
    }
}
