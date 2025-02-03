package org.bigjoe.tivo;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Logger;

import org.bigjoe.tivo.models.RecordingFolderItem;
import org.bigjoe.tivo.util.Http;
import org.bigjoe.tivo.util.TiVoRPC;

import net.straylightlabs.tivolibre.TivoDecoder;

/**

-- This is from a kmttg run
/usr/bin/ffmpeg -y -i "/root/kmttg/release/Seinfeld - The Red Dot (12_25_2024).ts" -threads 4 -vcodec libx264 -coder 0 -level 41 -qscale 1 -subq 6 -me_range 16 -qmin 10 -qmax 50 -g 300 -bufsize 14745k -b 8000k -maxrate 16000k -me_method epzs -trellis 2 -mbd 1 -acodec copy -f mp4

    can't seem to get this working...
       "/usr/bin/ffmpeg -y -i \"" + cutFile + "\" -threads 4 -vcodec libx264 -coder 0 -level 41 -qscale 1 -subq 6 -me_range 16 -qmin 10 -qmax 50 -g 300 -bufsize 14745k -b 8000k -maxrate 16000k -me_method epzs -trellis 2 -mbd 1 -acodec copy -f mp4 " + mpegFile).waitFor();
       instead using ffmpeg -f concat -safe 0  -i 'xxx.cutfile'  -c copy -vcodec libx264  'xxx.mp4'

*/
public class App {
	private static Logger log = Logger.getLogger(App.class.getSimpleName());

    String mak;
    String ip;
    String outputDir = "/downloads/";
    int limit = 5;
    boolean skipDelete = true;

    String ffmpegLocation = "/usr/bin/ffmpeg";


    public static void main(String[] args) throws IOException, InterruptedException, Exception {
        if (args != null && args.length < 2) {
            System.out.println("arg count:" + args.length + ":" + String.join(" ", args));
            System.out.println("usage: ip:port MAK [limit deleteFromTivo]");
            System.out.println(" -- limit is the total number of programs to process (0 for unlimited)");
            System.out.println(" -- deleteFromTivo if \"true\", delete the programs after processing successfully");
            return;
        }

        int limit = 5;
        if (args.length >= 3) {
            limit = Integer.valueOf(args[2]);
        }
        boolean skipDelete = true;
        if (args.length >= 4) {
            skipDelete = !Boolean.valueOf(args[3]); // only delete from tivo if argument is "true"
        }

        App app = new App(args[0], args[1], limit, skipDelete);

        
        log.info("IpAddress:" + app.ip + " mak:" + app.mak + " limit:" + app.limit + " skipDelete:" + app.skipDelete);
        log.info("Gathering Now Playing Items");
        
        TiVoRPC rpc = app.newRpc();
        List<RecordingFolderItem> items = rpc.fetchShows(limit);

        log.info("Done gathering items:" + items.size());

        for (RecordingFolderItem item : items) {

            app.processItem(item);
            /**
             * for deletes
             * https://github.com/lart2150/kmttg/blob/master/src/com/tivo/kmttg/util/file.java#L240
             */
        }
    }

    //String IP, String mak, String programDir, String cdata, boolean oldSchema, boolean debug
    public App(String ip, String mak, int limit, boolean skipDelete) {
        this.ip = ip;
        this.mak = mak;
        this.limit = limit;
        this.skipDelete = skipDelete;

        // Remove any java.security restrictions which can intefere with connecting to the tivo
        java.security.Security.setProperty("jdk.tls.disabledAlgorithms", "");
        java.security.Security.setProperty("jdk.jar.disabledAlgorithms", "");
        java.security.Security.setProperty("jdk.certpath.disabledAlgorithms", "");

    }

    /**
     * Once this is done we want to have the following files left over:
     * 
     * 1. mp4 containing the final program without commercials
     * 2. ts file containing the decoded program with commercials
     * 3. edl file which is comskip's analysis
     * 4. meta file which contains all program metadata
     * 
     * Even though the ts takes up a lot of space, it might be worthwhile to store it for later deletion. This is just 
     * in case we spot errors and want to fix them.
     * 
     * If we process successfully, we'll delete the tivo and uncut mp4 file to save space
     * 
     * @param item
          * @return
          * @throws Exception
          */
         public boolean processItem(RecordingFolderItem item) throws Exception {
            log.info("Processing:" + item.title + " " + item.getIdentifier());
            if (item.recordingTransferProhibited) {
                 log.info("Skipping prohibited");
                 return true;
             }
             if ("inProgressRecording".equals(item.recordingStatusType)) {
                log.info("Skipping in progress recoding");
                return true;
            }
             String filePrefix = item.getIdentifier();
             String fqFilePrefix = outputDir + "/" + filePrefix;
     
             // contains show metadata
             File metaFile = new File(fqFilePrefix + ".meta");
             // raw file downloaded from TiVo
             File tivoFile = new File(fqFilePrefix + ".tivo");
             // decoded file
             File tsFile = new File(fqFilePrefix + ".ts");
             // comskip's analysis of commercial breaks
             File edlFile = new File(fqFilePrefix + ".edl");
             // an mp4 of the decoded file
             File uncutFile = new File(fqFilePrefix + ".uncut.mp4");
             // the cuts formatted so they are usable by ffmpeg
             File cutsFile = new File(fqFilePrefix + ".cutfile");
             // the final commercial free mp4 of the program
             File finalFile = new File(outputDir + "/" + item.title + "_" + item.getIdentifier() + ".mp4");
     
             boolean createFinalFile = false;
             boolean createCutsFile = false;
             boolean createUncutFile = false;
             boolean createEdlFile = false;
             boolean createTsFile = false;
             boolean createTivoFile = false;
     
             if (finalFile.exists()) {
                 log.info("... already downloaded and converted");
                 return true;
             } else if (cutsFile.exists()) {
                 createFinalFile = true;
             } else if (edlFile.exists()) {
                createUncutFile = !uncutFile.exists(); // we switched this, so only create uncut file if it doesn't exist
                createCutsFile = true;
                createFinalFile = true;
             } else if (uncutFile.exists()) {
                createEdlFile = !edlFile.exists();
                createCutsFile = true;
                createFinalFile = true;
             } else if (tsFile.exists()) {
                 createUncutFile = true;
                 createEdlFile = true;
                 createCutsFile = true;
                 createFinalFile = true;
             } else if (tivoFile.exists()) {
                 createTsFile = true;
                 createUncutFile = true;
                 createEdlFile = true;
                 createCutsFile = true;
                 createFinalFile = true;
             } else {
                 createTivoFile = true;
                 createTsFile = true;
                 createUncutFile = true;
                 createEdlFile = true;
                 createCutsFile = true;
                 createFinalFile = true;
             }
             
             if (!metaFile.exists()) {
                 metaFile.createNewFile();
                 BufferedWriter bw = new BufferedWriter(new FileWriter(metaFile));
                 bw.write(TiVoRPC.GSON.toJson(item));
                 bw.close();
             }
     
             if (!createTivoFile) {
                 log.info("Skipping downloading... existing file");
             } else {
                 log.info("Creating file at:" + tivoFile.getAbsolutePath());
                 try {
                     tivoFile.createNewFile();
                     String url = item.getDownloadUrl(ip) + "&Format=video/x-tivo-mpeg-ts";
                     log.info("Downloading from tivo:" + url);
                     if (!Http.download(url, "tivo", mak, tivoFile.getAbsolutePath(), true, null))
                         throw new Exception("Problem downloading");
                 } catch (Exception e) {
                     deleteIfExists(tivoFile, e);
                 }
                 log.info("Done downloading");
             }
     
             if (!createTsFile) {
                 log.info("Skipping decoding... existing file");
             } else {
                 log.info("Decoding");
                 try {
                     if (!decodeFile(tivoFile.getAbsolutePath(), mak, tsFile.getAbsolutePath())) {
                         throw new Exception("Could not decode file!");
                     }
                 } catch (Exception e) {
                     deleteIfExists(tsFile, e);
                 }
                 log.info("Decoded");
             }
     
             if (!createUncutFile) {
                 log.info("Skipping conversion... existing file:" + uncutFile.getAbsolutePath());
             } else {
                 log.info("Converting to mp4");
                 try {
                     if (!generateMp4(tsFile.getName(), uncutFile.getAbsolutePath())) {
                         throw new Exception("Conversion failed!");
                     }
                 } catch (Exception e) {
                     deleteIfExists(uncutFile, e);
                 }
             }
     
             if (!createEdlFile) {
                log.info("Skipping comskip... existing file");
            } else {
                log.info("Detecting commercials");
                try {
                   String filePath = uncutFile.exists() ? uncutFile.getAbsolutePath() : tsFile.getAbsolutePath();
                   String edlPrefix = edlFile.getAbsolutePath();
                   edlPrefix = edlPrefix.substring(0, edlPrefix.length() - 4); //remove .edl from end of filename
                    if (!runSynchronous(new String[] {"/Comskip/comskip", "--ini", "/Comskip/comskip.ini", "--output=/downloads", "--output-filename=" + item.getIdentifier(), filePath})) {
                        throw new Exception("comskip returned bad value");
                    }
                } catch (Exception e) {
                    deleteIfExists(edlFile, e);
                }
                log.info("Detected");
            }
    
             if (!createCutsFile) {
                 log.info("Skipping cutFile creation... existing file:" + cutsFile.getAbsolutePath());
             } else {
                 log.info("Generating cut file");
                 try {
                     generateConcatFile(edlFile.getAbsolutePath(), uncutFile.getName(), cutsFile.getAbsolutePath());
                 } catch (Exception e) {
                     deleteIfExists(cutsFile, e);
                 }
                 log.info("Generated");
             }
     
             if (!createFinalFile) {
                 log.info("Skipping edited... existing file:" + finalFile.toString());
             } else {
                 log.info("Removing cuts");
                 try {
                     if (!removeCommercials(item.getIdentifier() + ".cutfile", finalFile.toString()))
                    throw new Exception("Failed to remove commercials");
            } catch (Exception e) {
                deleteIfExists(finalFile, e);
            }
        }

        deleteIfExists(tivoFile);
        if (uncutFile.exists()) {
            deleteIfExists(tsFile);
        }
        if (skipDelete) {
            log.info("Deleting show from TiVo");
            try {
                newRpc().deleteShow(item.recordingId);
            } catch (Exception e) {
                log.warning("Error deleting show from TiVo:" + e.getMessage());
            }
        }
        return true;
    }

    public void deleteIfExists(File f) throws Exception {
        deleteIfExists(f, null);
    }

    public void deleteIfExists(File f, Exception e) throws Exception {
        if (f.exists()) {
            Files.delete(Paths.get(f.getAbsolutePath()));
        }
        if (e != null) {
            throw e;
        }
    }

    public boolean decodeFile(String inputFile, String mak, String outputFile) throws FileNotFoundException {
        TivoDecoder decoder = new TivoDecoder.Builder()
                .input(new FileInputStream(inputFile))
                .output(new FileOutputStream(outputFile))
                .mak(mak)
                .build();
        return decoder.decode();
    }

    /**
     * @param edlFile The comskip output file.
     * @param inputFile The uncut mp4 file. This MUST be a local filename
     * @param outputFile Where to write the results. This can be a fq filename
     * @throws IOException
     */
    public void generateConcatFile(String edlFile, String inputFile, String outputFile) throws IOException {
        File concat = new File(outputFile);
        concat.createNewFile();
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(concat))) {
            try (BufferedReader br = new BufferedReader(new FileReader(edlFile))) {
                String line;
                String lastCut = "0";
                while ((line = br.readLine()) != null) {
                    String[] parts = line.split("\t");
                    if (parts.length == 3) {
                        if ("0".equals(lastCut) && "0.00".equals(parts[0])) {
                            // Doesn't start with an ad, we need a line for start of video to parts[0]
                            lastCut = parts[1];
                            continue;
                        }
                        bw.write("file " + inputFile + "\n");
                        bw.write("inpoint " + lastCut + "\n");
                        bw.write("outpoint " + parts[0] + "\n");
                        lastCut = parts[1];
                    }
                }
                bw.write("file " + inputFile + "\n");
                bw.write("inpoint " + lastCut + "\n");
            }
        }   
    }

    /**
     * This accepts fully qualified filenames
     * 
     * @param inputFile
     * @param outFile
     * @return
     * @throws Exception
     */
    public boolean generateMp4(String inputFile, String outFile) throws Exception {
        return runSynchronous(new String[] {
            ffmpegLocation,
            "-y",
            "-i",
            inputFile,
            "-vcodec",
            "libx264",
            outFile
        });
    }


    public boolean removeCommercials(String inputFile, String outputFile) throws Exception {
        return runSynchronous(new String[] {
            ffmpegLocation,
            "-y",
            "-f",
            "concat",
            "-safe",
            "0",
            "-i",
            inputFile,
            "-c",
            "copy",
            outputFile
        });
    }

    public boolean runSynchronous(String[] command) throws Exception {
        log.info("Running:" + String.join(" ", command));
        Process process = Runtime.getRuntime().exec(command, null, new File(outputDir));

        // Consume standard output stream
        Thread stdOutThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info(command[0] + ":stdout: " + line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        stdOutThread.start();

        // Consume standard error stream
        Thread stdErrThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.warning(command[0] + ":stderr: " + line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        stdErrThread.start();

        // Wait for the process to finish
        int returnValue = process.waitFor();
        log.info(command[0] + " waitfor returned:" + returnValue);

        stdOutThread.join();
        stdErrThread.join();

        return returnValue == 0;
    }

   public TiVoRPC newRpc() {
       return new TiVoRPC(ip, mak);
   }

}
