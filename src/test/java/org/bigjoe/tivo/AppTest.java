package org.bigjoe.tivo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

import org.bigjoe.tivo.models.RecordingFolderItem;
import org.bigjoe.tivo.util.TiVoRPC;
import org.junit.jupiter.api.Test;

/**
 * Unit test for simple App.
 */
public class AppTest {
    private static Logger log = Logger.getLogger(AppTest.class.getSimpleName());

    App app;

    public AppTest() {
        app = new App(null, null, 5, true);
        app.ffmpegLocation = "/opt/homebrew/bin/ffmpeg";

    }

    /**
     * Rigorous Test :-)
     */
    @Test
    public void shouldAnswerWithTrue() {
        assertTrue(true);
    }

    @Test
    public void testGenerateMp4() throws Exception {
        File tsFile = new File(this.getClass().getClassLoader().getResource("output.ts").getFile());
        app.generateMp4(tsFile.getAbsolutePath(), new File("/tmp/output.12345.mp4").getAbsolutePath());
    }

    @Test
    public void testRenameFile() throws IOException {
        File tmp = File.createTempFile("prefix", "suffix");
        tmp.renameTo(new File(tmp.getAbsolutePath() + "2"));
    }

    // Set the ip and mak above to run this test
    // @Test
    public void testFetchShows() {
        TiVoRPC rpc = app.newRpc();
        List<RecordingFolderItem> recordings = rpc.fetchShows(5);
        log.info(TiVoRPC.GSON.toJson(recordings));
    }

}
