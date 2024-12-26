package berlin.yuna.typemap.logic;

import berlin.yuna.apidoccrafter.App;
import org.junit.jupiter.api.Test;

import static berlin.yuna.apidoccrafter.config.Config.WORK_DIR;

class AppTest {

    @Test
    void runApp() {
        System.getProperties().put(WORK_DIR, "/Users/yuna/projects/open-api-merger/src/main/resources/files");
        App.main(new String[0]);
    }
}
