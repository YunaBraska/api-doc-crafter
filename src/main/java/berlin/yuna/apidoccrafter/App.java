package berlin.yuna.apidoccrafter;

import berlin.yuna.apidoccrafter.logic.HtmlGenerator;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.models.OpenAPI;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static berlin.yuna.apidoccrafter.config.Config.*;
import static berlin.yuna.apidoccrafter.logic.Processor.*;
import static berlin.yuna.apidoccrafter.util.Util.*;
import static berlin.yuna.typemap.logic.ArgsDecoder.hasText;
import static java.util.Optional.ofNullable;

// java:S106 - Standard outputs should not be used directly to log anything
@SuppressWarnings({"java:S106", "java:S1192"})
public class App {

    public static void main(final String[] args) {
        config().putAll(readConfigs()); // can't be done in static block because native executable will freeze it
        final Path inputDir = parseWorkDir(config().asString(WORK_DIR));
        final Path outputDir = parseOutputDir(config().asString(OUTPUT_DIR), inputDir);
        final String fileIncludes = config().asString(FILE_INCLUDES);
        final String fileExcludes = config().asString(FILE_EXCLUDES);
        final int maxDeep = config().asIntOpt(MAX_DEEP).filter(num -> num > -1).orElse(100);
        final String tagGroups = config().asString(GROUP_TAGS);
        final String serverGroups = config().asString(GROUP_SERVERS);

        downloadRemoteOpenApiFiles(inputDir, maxDeep);

        final Map<Path, OpenAPI> fileMap = readOpenApiFiles(inputDir, maxDeep, fileIncludes, fileExcludes);
        System.out.println("[INFO] Files [" + fileMap.size() + "] to process");

        // Group files
        final List<Map<Path, OpenAPI>> groupedApis = groupFiles(fileMap, tagGroups, serverGroups);

        // Merge and filter files
        final Map<Path, OpenAPI> mergedApis = mergeApis(groupedApis);

        // Save files
        mergedApis.forEach((path, openAPI) -> saveYaml(openAPI, outputDir.resolve(filenameYaml(path, openAPI))));
        mergedApis.forEach((path, openAPI) -> saveJson(openAPI, outputDir.resolve(filenameJson(path, openAPI))));

        // TODO: find non resolvable components in other API files and merge these references
        HtmlGenerator.generateHtml(sortByString(mergedApis, pathOpenAPIEntry -> displayName(pathOpenAPIEntry.getKey(), pathOpenAPIEntry.getValue())), outputDir);
    }

    private static void downloadRemoteOpenApiFiles(final Path inputDir, final int maxDeep) {
        final Path targetDir = inputDir.resolve("api-doc-download");
        System.out.println("[INFO] Downloading dir [" + targetDir + "]");
        mkdir(targetDir);
        // Download files from file_download
        config().asStringOpt(FILE_DOWNLOAD)
            .map(urls -> urls.split("\\|\\|"))
            .map(List::of)
            .orElse(List.of())
            .forEach(url -> download(url, getFileDownloadHeaders(), inputDir));

        // Download files from api-doc-links.txt
        try (final Stream<Path> files = Files.walk(inputDir, maxDeep)) {
            files
                .filter(Files::isRegularFile)
                .filter(file -> file.getFileName().toString().equals("api-doc-links.txt"))
                .map(file -> {
                    try {
                        return Files.readAllLines(file, getEncoding());
                    } catch (IOException e) {
                        System.err.println("[ERROR] Failed to read [" + file + "] cause [" + e.getClass().getSimpleName() + "] message [" + e.getMessage() + "]");
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .distinct()
                .forEach(url -> download(url, getFileDownloadHeaders(), targetDir));
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to read [" + inputDir + "] cause [" + e.getClass().getSimpleName() + "] message [" + e.getMessage() + "]");
        }
    }

    private static Path parseOutputDir(final String outputDirArg, final Path inputDir) {
        final Path result = !hasText(outputDirArg) ? ofNullable(inputDir.getParent()).orElse(inputDir).resolve("swagger_output") : Path.of(outputDirArg);
        if (Files.exists(result) && !Files.isDirectory(result)) {
            System.err.println("[ERROR] outputDir [" + result + "] is not a directory");
        } else {
            if (Files.exists(result))
                deleteFolder(result);
            mkdir(result);
        }
        System.out.println("[INFO] OutputDir [" + result + "]");
        return result;
    }

    private static Path parseWorkDir(final String workDirArg) {
        Path result = Path.of(!hasText(workDirArg) || ".".equals(workDirArg.trim()) ? System.getProperty("user.dir") : workDirArg);
        if (!Files.exists(result))
            System.err.println("[ERROR] workDir [" + result + "] does not exist");
        if (!Files.isDirectory(result))
            System.err.println("[ERROR] workDir [" + result + "] is not a directory");
        System.out.println("[INFO] WorkDir [" + result + "]");
        return result;
    }

    private static void saveYaml(final OpenAPI mergedApi, final Path outputPath) {
        try (FileWriter writer = new FileWriter(outputPath.toFile())) {
            writer.write(Yaml.mapper().writeValueAsString(mergedApi));
            writer.write(Json.mapper().writeValueAsString(mergedApi));
            System.out.println("[INFO] Generated [" + outputPath + "]");
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to save [" + outputPath + "] cause [" + e.getClass().getSimpleName() + "] message [" + e.getMessage() + "]");
        }
    }

    private static void saveJson(final OpenAPI mergedApi, final Path outputPath) {
        try (FileWriter writer = new FileWriter(outputPath.toFile())) {
            writer.write(Json.mapper().writeValueAsString(mergedApi));
            System.out.println("[INFO] Generated [" + outputPath + "]");
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to save [" + outputPath + "] cause [" + e.getClass().getSimpleName() + "] message [" + e.getMessage() + "]");
        }
    }
}
