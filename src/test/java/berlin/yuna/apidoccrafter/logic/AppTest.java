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
        final Path swaggerOutput = Path.of(org.assertj.core.util.Files.temporaryFolderPath()).resolve("swagger_output");
        final Path expectedFilesPath = Path.of("src/test/resources/files_expected");

//        System.getProperties().put(FILE_DOWNLOAD, "https://raw.githubusercontent.com/europace/unterlagen-api/refs/heads/master/docs/v2/swagger.yml||https://raw.githubusercontent.com/europace/unterlagen-api/refs/heads/master/docs/v2/swagger.yml");
        System.getProperties().put(FILE_DOWNLOAD_HEADER, "x-trace-id-1->1234567890||x-trace-id-2->1234567890");
        System.getProperties().put(REMOVE_PATTERNS, "Management|**Internal**");
        System.getProperties().put(SWAGGER_LOGO, "https://static1.smartbear.co/swagger/media/assets/images/swagger_logo.svg");
        System.getProperties().put(SWAGGER_LOGO_LINK, "index.html");
        System.getProperties().put(SORT_TAGS, false);
        System.getProperties().put(FILE_INCLUDES, "**/files/**");
        System.getProperties().put(OUTPUT_DIR, swaggerOutput.toString());

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
            "health_metrics_api.yaml"
        ).forEach(fileName -> {
            assertThat(swaggerOutput.resolve(fileName)).exists();
            assertThat(expectedFilesPath.resolve(fileName)).exists();

            try {
                assertThat(Files.readString(swaggerOutput.resolve(fileName))).isEqualTo(Files.readString(expectedFilesPath.resolve(fileName)));
            } catch (IOException e) {
                try {
                    assertThat(Files.readAllBytes(swaggerOutput.resolve(fileName))).isEqualTo(Files.readAllBytes(expectedFilesPath.resolve(fileName)));
                } catch (IOException ex) {
                    throw new IllegalArgumentException(ex);
                }
            }
        });
    }
}
