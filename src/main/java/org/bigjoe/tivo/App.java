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
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bigjoe.tivo.nowplaying.Item;
import org.bigjoe.tivo.nowplaying.Page;
import org.bigjoe.tivo.util.Http;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import net.straylightlabs.tivolibre.TivoDecoder;

/**

-- This is from a kmttg run
/usr/bin/ffmpeg -y -i "/root/kmttg/release/Seinfeld - The Red Dot (12_25_2024).ts" -threads 4 -vcodec libx264 -coder 0 -level 41 -qscale 1 -subq 6 -me_range 16 -qmin 10 -qmax 50 -g 300 -bufsize 14745k -b 8000k -maxrate 16000k -me_method epzs -trellis 2 -mbd 1 -acodec copy -f mp4

    can't seem to get this working...
       "/usr/bin/ffmpeg -y -i \"" + cutFile + "\" -threads 4 -vcodec libx264 -coder 0 -level 41 -qscale 1 -subq 6 -me_range 16 -qmin 10 -qmax 50 -g 300 -bufsize 14745k -b 8000k -maxrate 16000k -me_method epzs -trellis 2 -mbd 1 -acodec copy -f mp4 " + mpegFile).waitFor();
       instead using ffmpeg -f concat -safe 0  -i 'xxx.cutfile'  -c copy -vcodec libx264  'xxx.mp4'

*/
public class App {

    String mak;
    String ip;
    String outputDir = "/tmp/";
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

        App app = new App();
        app.ip = args[0];
        app.mak = args[1];
        app.outputDir = "/downloads";

        if (args.length >= 3) {
            app.limit = Integer.valueOf(args[2]);
        }
        if (args.length >= 4) {
            app.skipDelete = !Boolean.valueOf(args[3]); // only delete from tivo if argument is "true"
        }
        
        System.out.println("IpAddress:" + app.ip + " mak:" + app.mak + " outputDir:" + app.outputDir);
        System.out.println("Gathering Now Playing Items");
        List<Item> items = app.gatherAllItems();
        System.out.println("Done gathering items:" + items.size());

        for (Item item : items) {

            app.processItem(item);
            /**
             * for deletes
             * https://github.com/lart2150/kmttg/blob/master/src/com/tivo/kmttg/util/file.java#L240
             */
        }
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
    public boolean processItem(Item item) throws Exception {
        if (!item.available) {
            System.out.println("Skipping unavailable:" + item.programId + " " + item.title + " " + item.episodeTitle);
            return true;
        }
        System.out.println("Processing:" + item.programId);
        String filePrefix = item.programId;
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
        File finalFile = new File(outputDir + "/" + item.title + "_" + item.episodeTitle + "_" + item.programId + ".mp4");

        boolean createFinalFile = false;
        boolean createCutsFile = false;
        boolean createUncutFile = false;
        boolean createEdlFile = false;
        boolean createTsFile = false;
        boolean createTivoFile = false;

        if (finalFile.exists()) {
            System.out.println("... already downloaded and converted");
            return true;
        } else if (cutsFile.exists()) {
            createFinalFile = true;
        } else if (uncutFile.exists()) {
            createCutsFile = true;
            createFinalFile = true;
        } else if (edlFile.exists()) {
            createUncutFile = true;
            createCutsFile = true;
            createFinalFile = true;
        } else if (tsFile.exists()) {
            createEdlFile = true;
            createUncutFile = true;
            createCutsFile = true;
            createFinalFile = true;
        } else if (tivoFile.exists()) {
            createTsFile = true;
            createEdlFile = true;
            createUncutFile = true;
            createCutsFile = true;
            createFinalFile = true;
        } else {
            createTivoFile = true;
            createTsFile = true;
            createEdlFile = true;
            createUncutFile = true;
            createCutsFile = true;
            createFinalFile = true;
        }
        
        if (!metaFile.exists()) {
            metaFile.createNewFile();
            BufferedWriter bw = new BufferedWriter(new FileWriter(metaFile));
            bw.write(item.raw);
            bw.close();
        }

        if (!createTivoFile) {
            System.out.println("Skipping downloading... existing file");
        } else {
            System.out.println("Creating file at:" + tivoFile.getAbsolutePath());
            try {
                tivoFile.createNewFile();
                System.out.println("Downloading from tivo");
                if (!Http.download(item.contentLink + "&Format=video/x-tivo-mpeg-ts", "tivo", mak, tivoFile.getAbsolutePath(), true, null))
                    throw new Exception("Problem downloading");
            } catch (Exception e) {
                deleteIfExists(tivoFile, e);
            }
            System.out.println("Done downloading");
        }

        if (!createTsFile) {
            System.out.println("Skipping decoding... existing file");
        } else {
            System.out.println("Decoding");
            try {
                if (!decodeFile(tivoFile.getAbsolutePath(), mak, tsFile.getAbsolutePath())) {
                    throw new Exception("Could not decode file!");
                }
            } catch (Exception e) {
                deleteIfExists(tsFile, e);
            }
            System.out.println("Decoded");
        }

        if (!createEdlFile) {
            System.out.println("Skipping comskip... existing file");
        } else {
            System.out.println("Detecting commercials");
            try {
                if (!runSynchronous(new String[] {"/Comskip/comskip", "--ini", "/Comskip/comskip.ini", tsFile.getAbsolutePath()})) {
                    throw new Exception("comskip returned bad value");
                }
            } catch (Exception e) {
                deleteIfExists(edlFile, e);
            }
            System.out.println("Detected");
        }

        if (!createUncutFile) {
            System.out.println("Skipping conversion... existing file:" + uncutFile.getAbsolutePath());
        } else {
            System.out.println("Converting to mp4");
            try {
                if (!generateMp4(tsFile.getName(), uncutFile.getAbsolutePath())) {
                    throw new Exception("Conversion failed!");
                }
            } catch (Exception e) {
                deleteIfExists(uncutFile, e);
            }
        }

        if (!createCutsFile) {
            System.out.println("Skipping cutFile creation... existing file:" + cutsFile.getAbsolutePath());
        } else {
            System.out.println("Generating cut file");
            try {
                generateConcatFile(edlFile.getAbsolutePath(), uncutFile.getName(), cutsFile.getAbsolutePath());
            } catch (Exception e) {
                deleteIfExists(cutsFile, e);
            }
            System.out.println("Generated");
        }

        if (!createFinalFile) {
            System.out.println("Skipping edited... existing file:" + finalFile.toString());
        } else {
            System.out.println("Removing cuts");
            try {
                if (!removeCommercials(item.programId + ".cutfile", finalFile.toString()))
                    throw new Exception("Failed to remove commercials");
            } catch (Exception e) {
                deleteIfExists(finalFile, e);
            }
        }

        deleteIfExists(tivoFile);
        deleteIfExists(uncutFile);
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

    /**
     * Retrieve now playing items
     * 
     * @param limit stop when limit items are reached. 0 for unlimited
     * @return
     * @throws IOException
     * @throws InterruptedException
     * @throws Exception
     */
    public List<Item> gatherAllItems() throws IOException, InterruptedException, Exception {
        List<Item> items = new ArrayList<>();
        int pageSize = 32; // default page size;
        if (pageSize > limit && limit > 0) {
            pageSize = limit;
        }

        File f = File.createTempFile("tivo_np_", ".xml");
        try {
            Page p;
            do {
                String urlPrefix = "https://" + ip + "/TiVoConnect?Command=QueryContainer&Container=/NowPlaying&Recurse=Yes&ItemCount=" + pageSize + "&AnchorOffset=";
    
                Http.download(urlPrefix + items.size(), "tivo", mak, f.getAbsolutePath(), false, null);
                p = parseNowPlaying(f);
    
                items.addAll(p.items);
                System.out.println("Retrieved items:" + p.count + " total:" + p.total + " items:" + items.size());
                if (limit > 0 && limit - items.size() > pageSize) {
                    // We don't need a full page to hit limit, so reduce the page size
                    pageSize = limit - items.size();
                }
            } while (p.count > 0 && p.total > items.size() && (limit == 0 || p.total < limit));    
        } finally {
            f.delete();
        }
        return items;
    }

    public Page parseNowPlaying(File f) throws IOException {
        Document doc = Jsoup.parse(f);
        return Page.parse(doc);
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
        System.out.println("Running:" + String.join(" ", command));
        Process process = Runtime.getRuntime().exec(command, null, new File(outputDir));

        // Consume standard output stream
        Thread stdOutThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(command[0] + ":stdout: " + line);
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
                    System.out.println(command[0] + ":stderr: " + line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        stdErrThread.start();

        // Wait for the process to finish
        int returnValue = process.waitFor();
        System.out.println(command[0] + " waitfor returned:" + returnValue);

        stdOutThread.join();
        stdErrThread.join();

        return returnValue == 0;
    }

      public void deleteProgram(String programId) throws Exception {
        // Construct the TiVo URL for deleting the program
        String tivoUrl = String.format("https://%s/TiVoConnect?Command=Delete&Item=%s", ip, programId);

        // Create a URL object
        URL url = new URL(tivoUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        // Set up the connection properties
        connection.setRequestMethod("GET");
        String auth = "tivo:" + mak;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
        connection.setRequestProperty("Authorization", "Basic " + encodedAuth);

        // Execute the request
        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            System.out.println("Program deleted successfully.");
            try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    System.out.println(inputLine);
                }
            }
        } else {
            System.out.println("Failed to delete program. HTTP Response Code: " + responseCode);
        }
    }

    public static void TivoWebPlusDelete(String download_url) {
      if (download_url == null) return;
      int port = 8080;
      Pattern p = Pattern.compile("http://(\\S+):.+&id=(.+)$");
      Matcher m = p.matcher(download_url);
      if (m.matches()) {
         String ip = m.group(1);
         final String id = m.group(2);
         final String urlString = "http://" + ip + ":" + port + "/confirm/del/" + id;
         System.out.println(">> Issuing TivoWebPlus show delete request: " + urlString);
         try {
            // Run the http request in separate thread so as not to hang up the main program
            final URL url = new URI(urlString).toURL();
            class AutoThread implements Runnable {
               AutoThread() {}       
               public void run () {
                  int timeout = 10;
                  try {
                     String data = "u2=bnowshowing";
                     data += "&sub=Delete";
                     data += "&" + URLEncoder.encode("fsida(" + id + ")", "UTF-8")  + "=on";
                     data += "&submit=Confirm_Delete";
                     HttpURLConnection c = (HttpURLConnection) url.openConnection();
                     c.setRequestMethod("POST");
                     c.setReadTimeout(timeout*1000);
                     c.setDoOutput(true);
                     /* If authentication needed
                     final String login ="oztivo";
                     final String password ="moyekj";
                     Authenticator.setDefault(new Authenticator() {
                         protected PasswordAuthentication getPasswordAuthentication() {
                             return new PasswordAuthentication (login, password.toCharArray());
                         }
                     });
                     */
                     c.connect();
                     BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(c.getOutputStream()));
                     bw.write(data);
                     bw.flush();
                     bw.close();
                     String response = c.getResponseMessage();
                     if (response.equals("OK")) {
                        System.out.println(">> TivoWebPlus delete succeeded.");
                     } else {
                        System.out.println("TWP Delete: Received unexpected response for: " + urlString);
                        System.out.println(response);
                     }
                  }
                  catch (Exception e) {
                    System.out.println("TWP Delete: connection failed: " + urlString);
                    System.out.println(e.toString());
                  }
               }
            }
            AutoThread t = new AutoThread();
            Thread thread = new Thread(t);
            thread.start();
         }
         catch (Exception e) {
            System.out.println("TWP Delete: connection failed: " + urlString);
            System.out.println(e.toString());
         }
      }
   }
}
