package berlin.yuna.apidoccrafter.logic;

import berlin.yuna.apidoccrafter.App;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static berlin.yuna.apidoccrafter.config.Config.*;
import static org.assertj.core.api.Assertions.assertThat;

class AppTest {

    @Test
    void runApp() {
        System.setProperty("TEST_KEY", "TEST_VALUE");
        final Path swaggerOutput = Path.of(org.assertj.core.util.Files.temporaryFolderPath()).resolve("swagger_output");
        final Path expectedFilesPath = Path.of("src/test/resources/files_expected");

        System.getProperties().put(FILE_DOWNLOAD_HEADER, "x-trace-id-1->1234567890||x-trace-id-2->1234567890");
        System.getProperties().put(REMOVE_PATTERNS, "Management|**Internal**");
        System.getProperties().put(SWAGGER_LOGO, "https://static1.smartbear.co/swagger/media/assets/images/swagger_logo.svg");
        System.getProperties().put(SWAGGER_LOGO_LINK, "index.html");
        System.getProperties().put(SORT_TAGS, false);
        System.getProperties().put(FILE_INCLUDES, "**/files/**||**/api-doc-download/**");
        System.getProperties().put(OUTPUT_DIR, swaggerOutput.toString());
        System.getProperties().put(ENABLE_CUSTOM_INFO, false);

        App.main(new String[0]);

        // Verify that the files exist
        List.of(
            "index.html",
            "logo.png",
            "favicon.png",
            "swagger.js",
            "swagger.css",
            "swagger_bundle.js",
            "swagger_nav.css",
            "books_api.html",
            "books_api.json",
            "books_api.yaml",
            "games_api.html",
            "games_api.json",
            "games_api.yaml",
            "health_metrics_api.html",
            "health_metrics_api.json",
            "health_metrics_api.yaml",
            "swagger_petstore.html",
            "swagger_petstore.json",
            "swagger_petstore.yaml"
        ).forEach(fileName -> {
            assertThat(swaggerOutput.resolve(fileName)).exists();
            assertThat(expectedFilesPath.resolve(fileName)).exists();

            try {
                assertThat(Files.readString(swaggerOutput.resolve(fileName)).trim()).isEqualTo(Files.readString(expectedFilesPath.resolve(fileName)).trim());
            } catch (IOException e) {
                try {
                    assertThat(Files.readAllBytes(swaggerOutput.resolve(fileName))).isEqualTo(Files.readAllBytes(expectedFilesPath.resolve(fileName)));
                } catch (IOException ex) {
                    throw new IllegalArgumentException(ex);
                }
            }
        });
    }

    @Test
    void runAppWithCustomization() throws IOException {
        System.setProperty("TEST_KEY", "TEST_VALUE");
        final Path swaggerOutput = Path.of(org.assertj.core.util.Files.temporaryFolderPath()).resolve("swagger_output");

        System.getProperties().put(FILE_DOWNLOAD_HEADER, "x-trace-id-1->1234567890||x-trace-id-2->1234567890");
        System.getProperties().put(REMOVE_PATTERNS, "Management|**Internal**");
        System.getProperties().put(SWAGGER_LOGO, "https://static1.smartbear.co/swagger/media/assets/images/swagger_logo.svg");
        System.getProperties().put(SWAGGER_LOGO_LINK, "index.html");
        System.getProperties().put(SORT_TAGS, false);
        System.getProperties().put(FILE_INCLUDES, "**/files/**||**/api-doc-download/**");
        System.getProperties().put(OUTPUT_DIR, swaggerOutput.toString());

        // Activate metadata enrichment
        System.getProperties().put(ENABLE_CUSTOM_INFO, true);
        System.getProperties().put(CONFIG_PREFIX + "info_title", "Custom Title");
        System.getProperties().put(CONFIG_PREFIX + "info_version", "9.9.9");
        System.getProperties().put(CONFIG_PREFIX + "info_summary", "Custom Summary");
        System.getProperties().put(CONFIG_PREFIX + "info_description", "Custom Description");
        System.getProperties().put(CONFIG_PREFIX + "info_termsofservice", "https://tos.example.com");
        System.getProperties().put(CONFIG_PREFIX + "info_contact_name", "Custom Contact");
        System.getProperties().put(CONFIG_PREFIX + "info_contact_url", "https://contact.example.com");
        System.getProperties().put(CONFIG_PREFIX + "info_contact_email", "contact@example.com");
        System.getProperties().put(CONFIG_PREFIX + "info_license_name", "MIT");
        System.getProperties().put(CONFIG_PREFIX + "info_license_url", "https://license.example.com");
        System.getProperties().put(CONFIG_PREFIX + "info_license_identifier", "MIT-0");
        System.getProperties().put(CONFIG_PREFIX + "externaldocs_url", "https://external.example.com");
        System.getProperties().put(CONFIG_PREFIX + "externaldocs_description", "External Docs Desc");
        System.getProperties().put(CONFIG_PREFIX + "servers", "AA::BB,CC");
        System.getProperties().put(CONFIG_PREFIX + "tags", "DD::EE,FF");

        App.main(new String[0]);

        final Path file = swaggerOutput.resolve("custom_title.json");
        assertThat(file).exists();
        final String content = Files.readString(file).trim();

        assertThat(content)
            .contains("\"title\" : \"Custom Title\"")
            .contains("\"version\" : \"9.9.9\"")
//            .contains("\"summary\" : \"Custom Summary\"")
            .contains("\"description\" : \"Custom Description\"")
            .contains("\"termsOfService\" : \"https://tos.example.com\"")
            .contains("\"name\" : \"Custom Contact\"")
            .contains("\"url\" : \"https://contact.example.com\"")
            .contains("\"email\" : \"contact@example.com\"")
            .contains("\"name\" : \"MIT\"")
            .contains("\"url\" : \"https://license.example.com\"")
//            .contains("\"identifier\" : \"MIT-0\"")
            .contains("\"url\" : \"https://external.example.com\"")
            .contains("\"description\" : \"External Docs Desc\"")
            .contains("\"url\" : \"AA\"")
            .contains("\"description\" : \"BB\"")
            .contains("\"url\" : \"CC\"")
            .contains("\"name\" : \"DD\"")
            .contains("\"description\" : \"EE\"")
            .contains("\"name\" : \"FF\"");

    }
}
