package org.bigjoe.tivo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.Test;

/**
 * Unit test for simple App.
 */
public class AppTest {

    App app;

    public AppTest() {
        app = new App();
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
}
