package berlin.yuna.apidoccrafter;

import berlin.yuna.apidoccrafter.logic.HtmlGenerator;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.models.OpenAPI;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static berlin.yuna.typemap.logic.ArgsDecoder.hasText;
import static berlin.yuna.apidoccrafter.config.Config.*;
import static berlin.yuna.apidoccrafter.logic.Processor.*;
import static berlin.yuna.apidoccrafter.util.Util.*;
import static java.util.Optional.ofNullable;

// java:S106 - Standard outputs should not be used directly to log anything
@SuppressWarnings("java:S106")
public class App {

    public static void main(String[] args) {
        final Path inputDir = parseWorkDir(config().asString(WORK_DIR));
        final Path outputDir = parseOutputDir(config().asString(OUTPUT_DIR), inputDir);
        final String fileIncludes = config().asString(FILE_INCLUDES);
        final String fileExcludes = config().asString(FILE_EXCLUDES);
        final int maxDeep = config().asIntOpt(MAX_DEEP).filter(num -> num > -1).orElse(100);
        final String tagGroups = config().asString(GROUP_TAGS);
        final String serverGroups = config().asString(GROUP_SERVERS);

        final Map<Path, OpenAPI> fileMap = readOpenApiFiles(inputDir, maxDeep, fileIncludes, fileExcludes);
        System.out.println("[INFO] Files [" + fileMap.size() + "] to process");

        // Group files
        final List<Map<Path, OpenAPI>> groupedApis = groupFiles(fileMap, tagGroups, serverGroups);

        // Merge and filter files
        final Map<Path, OpenAPI> mergedApis = mergeApis(groupedApis);

        // Save files
        mergedApis.forEach((path, openAPI) -> saveMergedApi(openAPI, outputDir.resolve(filenameYaml(path, openAPI))));

        HtmlGenerator.generateHtml(sortByString(mergedApis, pathOpenAPIEntry -> displayName(pathOpenAPIEntry.getKey(), pathOpenAPIEntry.getValue())), outputDir);
    }

    private static Path parseOutputDir(final String outputDirArg, final Path inputDir) {
        final Path result = !hasText(outputDirArg) ? ofNullable(inputDir.getParent()).orElse(inputDir).resolve("swagger_output") : Path.of(outputDirArg);
        if (Files.exists(result) && !Files.isDirectory(result)) {
            System.err.println("[ERROR] outputDir [" + result + "] is not a directory");
        } else {
            try {
                if (Files.exists(result))
                    deleteFolder(result);
                Files.createDirectories(result);
            } catch (IOException e) {
                System.err.println("[ERROR] Failed to create outputDir [" + result + "] cause [" + e.getClass().getSimpleName() + "] message [" + e.getMessage() + "]");
            }
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

    private static void saveMergedApi(final OpenAPI mergedApi, final Path outputPath) {
        try (FileWriter writer = new FileWriter(outputPath.toFile())) {
            writer.write(Yaml.mapper().writeValueAsString(mergedApi));
            System.out.println("[INFO] Generated [" + outputPath + "]");
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to save [" + outputPath + "] cause [" + e.getClass().getSimpleName() + "] message [" + e.getMessage() + "]");
        }
    }


}
