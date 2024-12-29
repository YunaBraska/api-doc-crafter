package berlin.yuna.apidoccrafter.logic;

import berlin.yuna.apidoccrafter.App;
import org.assertj.core.util.Files;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static berlin.yuna.apidoccrafter.config.Config.*;

class AppTest {

    @Test
    void runApp() {
        System.getProperties().put(SORT_TAGS, false);
        System.getProperties().put(FILE_INCLUDES, "**/files/**");
        System.getProperties().put(OUTPUT_DIR, Path.of(Files.temporaryFolderPath()).resolve("swagger_output").toString());
        App.main(new String[0]);
    }
}
