package berlin.yuna.apidoccrafter.logic;

import berlin.yuna.apidoccrafter.App;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static berlin.yuna.apidoccrafter.config.Config.*;
import static berlin.yuna.apidoccrafter.util.Util.safeJsonMapper;
import static berlin.yuna.apidoccrafter.util.Util.safeYamlMapper;
import static org.assertj.core.api.Assertions.assertThat;

class AppTest {

    @BeforeEach
    void resetConfigBeforeTest() {
        resetConfig();
    }

    @AfterEach
    void resetConfigAfterTest() {
        resetConfig();
    }

    @Test
    void runApp() {
        System.setProperty("TEST_KEY", "TEST_VALUE");
        final Path swaggerOutput = Path.of(org.assertj.core.util.Files.temporaryFolderPath()).resolve("swagger_output");
        final Path expectedFilesPath = Path.of("src/test/resources/files_expected");

        System.getProperties().put(FILE_DOWNLOAD_HEADER, "x-trace-id-1->1234567890||x-trace-id-2->1234567890");
        System.getProperties().put(REMOVE_PATTERNS, "Management|**Internal**");
        System.getProperties().put(SWAGGER_LOGO, "https://static1.smartbear.co/swagger/media/assets/images/swagger_logo.svg");
        System.getProperties().put(SWAGGER_LOGO_LINK, "index.html");
        System.setProperty(SORT_TAGS, "false");
        System.getProperties().put(FILE_INCLUDES, "**/files/**||**/api-doc-download/**");
        System.getProperties().put(OUTPUT_DIR, swaggerOutput.toString());
        System.setProperty(ENABLE_CUSTOM_INFO, "false");
        System.setProperty(ENABLE_OBJECT_MAPPER, "true");

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

            assertGeneratedFileEquals(swaggerOutput.resolve(fileName), expectedFilesPath.resolve(fileName));
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
        System.setProperty(SORT_TAGS, "false");
        System.getProperties().put(FILE_INCLUDES, "**/files/**||**/api-doc-download/**");
        System.getProperties().put(OUTPUT_DIR, swaggerOutput.toString());
        System.setProperty(ENABLE_OBJECT_MAPPER, "true");

        // Activate metadata enrichment
        System.setProperty(ENABLE_CUSTOM_INFO, "true");
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

    private static void assertGeneratedFileEquals(final Path actual, final Path expected) {
        try {
            if (actual.toString().endsWith(".json")) {
                assertThat(safeJsonMapper.readTree(actual.toFile())).isEqualTo(safeJsonMapper.readTree(expected.toFile()));
            } else if (actual.toString().endsWith(".yaml") || actual.toString().endsWith(".yml")) {
                assertThat(safeYamlMapper.readTree(actual.toFile())).isEqualTo(safeYamlMapper.readTree(expected.toFile()));
            } else if (actual.toString().endsWith(".html") && Files.readString(actual).contains("      spec: ")) {
                assertHtmlWithEmbeddedSpecEquals(Files.readString(actual).trim(), Files.readString(expected).trim());
            } else {
                assertThat(Files.readString(actual).trim()).isEqualTo(Files.readString(expected).trim());
            }
        } catch (IOException e) {
            try {
                assertThat(Files.readAllBytes(actual)).isEqualTo(Files.readAllBytes(expected));
            } catch (IOException ex) {
                throw new IllegalArgumentException(ex);
            }
        }
    }

    private static void assertHtmlWithEmbeddedSpecEquals(final String actual, final String expected) throws IOException {
        final String specPrefix = "      spec: ";
        final List<String> actualLines = actual.lines().toList();
        final List<String> expectedLines = expected.lines().toList();
        final int actualSpecLine = specLineIndex(actualLines, specPrefix);
        final int expectedSpecLine = specLineIndex(expectedLines, specPrefix);

        assertThat(actualSpecLine).isGreaterThanOrEqualTo(0);
        assertThat(expectedSpecLine).isGreaterThanOrEqualTo(0);
        assertThat(safeJsonMapper.readTree(specPayload(actualLines.get(actualSpecLine), specPrefix)))
            .isEqualTo(safeJsonMapper.readTree(specPayload(expectedLines.get(expectedSpecLine), specPrefix)));

        final String normalizedActual = actual.replace(actualLines.get(actualSpecLine), specPrefix + "<openapi-spec>,");
        final String normalizedExpected = expected.replace(expectedLines.get(expectedSpecLine), specPrefix + "<openapi-spec>,");
        assertThat(normalizedActual).isEqualTo(normalizedExpected);
    }

    private static int specLineIndex(final List<String> lines, final String specPrefix) {
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).startsWith(specPrefix))
                return index;
        }
        return -1;
    }

    private static String specPayload(final String line, final String specPrefix) {
        final String result = line.substring(specPrefix.length()).trim();
        return result.endsWith(",") ? result.substring(0, result.length() - 1) : result;
    }

    private static void resetConfig() {
        config().clear();
        System.getProperties().keySet().stream()
            .map(Object::toString)
            .filter(key -> key.toLowerCase().startsWith(CONFIG_PREFIX))
            .toList()
            .forEach(System.getProperties()::remove);
        System.getProperties().remove("TEST_KEY");
    }
}
