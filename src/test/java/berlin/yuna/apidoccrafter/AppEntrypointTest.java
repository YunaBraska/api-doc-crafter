package berlin.yuna.apidoccrafter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static berlin.yuna.apidoccrafter.config.Config.FILE_INCLUDES;
import static berlin.yuna.apidoccrafter.config.Config.MAX_DEEP;
import static berlin.yuna.apidoccrafter.config.Config.OUTPUT_DIR;
import static berlin.yuna.apidoccrafter.config.Config.WORK_DIR;
import static berlin.yuna.apidoccrafter.config.Config.config;
import static org.assertj.core.api.Assertions.assertThat;

class AppEntrypointTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void resetConfigBeforeTest() {
        resetConfig();
    }

    @AfterEach
    void resetConfigAfterTest() {
        resetConfig();
    }

    private static void resetConfig() {
        config().clear();
        System.getProperties().keySet().stream()
            .map(Object::toString)
            .filter(key -> key.toLowerCase().startsWith("adc_"))
            .toList()
            .forEach(System.getProperties()::remove);
    }

    @Test
    void shouldPrintHelpFromLongOption() throws Exception {
        final String output = captureStdout(() -> App.main(new String[]{"--help"}));

        assertThat(output)
            .contains("Usage:")
            .contains("api-doc-crafter")
            .contains("--adc_work_dir")
            .contains("--adc_output_dir");
    }

    @Test
    void shouldPrintHelpFromShortOption() throws Exception {
        final String output = captureStdout(() -> App.main(new String[]{"-h"}));

        assertThat(output)
            .contains("Usage:")
            .contains("api-doc-crafter");
    }

    @Test
    void shouldUseRuntimeArgumentsForWorkAndOutputDirectories() throws IOException {
        final Path input = tempDir.resolve("input");
        final Path output = tempDir.resolve("output");
        writeOpenApi(input.resolve("smoke.json"), "Smoke API");

        App.main(new String[]{
            arg(WORK_DIR, input),
            arg(OUTPUT_DIR, output),
            arg(FILE_INCLUDES, "**/smoke.json"),
            arg(MAX_DEEP, "1")
        });

        assertThat(output.resolve("index.html")).exists();
        assertThat(output.resolve("smoke_api.html")).exists();
        assertThat(output.resolve("smoke_api.yaml")).exists();
        assertThat(output.resolve("smoke_api.json")).content().contains("\"title\" : \"Smoke API\"");
    }

    @Test
    void shouldNotReuseRuntimeArgumentsBetweenEntrypointRuns() throws IOException {
        final Path firstInput = tempDir.resolve("first-input");
        final Path firstOutput = tempDir.resolve("first-output");
        final Path secondInput = tempDir.resolve("second-input");
        final Path defaultSecondOutput = tempDir.resolve("swagger_output");
        writeOpenApi(firstInput.resolve("first.json"), "First API");
        writeOpenApi(secondInput.resolve("second.json"), "Second API");

        App.main(new String[]{
            arg(WORK_DIR, firstInput),
            arg(OUTPUT_DIR, firstOutput),
            arg(FILE_INCLUDES, "**/first.json"),
            arg(MAX_DEEP, "1")
        });
        App.main(new String[]{
            arg(WORK_DIR, secondInput),
            arg(FILE_INCLUDES, "**/second.json"),
            arg(MAX_DEEP, "1")
        });

        assertThat(firstOutput.resolve("first_api.json")).exists();
        assertThat(defaultSecondOutput.resolve("second_api.json")).exists();
        assertThat(firstOutput.resolve("second_api.json")).doesNotExist();
    }

    private static String arg(final String key, final Path value) {
        return arg(key, value.toString());
    }

    private static String arg(final String key, final String value) {
        return "--" + key + "=\"" + value + "\"";
    }

    private static String captureStdout(final ThrowingAction action) throws Exception {
        final PrintStream originalOut = System.out;
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintStream printStream = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setOut(printStream);
            action.run();
        } finally {
            System.setOut(originalOut);
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    private static void writeOpenApi(final Path file, final String title) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
            {
              "openapi": "3.0.0",
              "info": {
                "title": "%s",
                "version": "1.0.0"
              },
              "paths": {
                "/health": {
                  "get": {
                    "responses": {
                      "200": {
                        "description": "OK"
                      }
                    }
                  }
                }
              }
            }
            """.formatted(title));
    }

    private interface ThrowingAction {
        void run() throws Exception;
    }
}
