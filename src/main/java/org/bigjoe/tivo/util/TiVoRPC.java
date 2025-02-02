package org.bigjoe.tivo.util;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.SocketException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.bigjoe.tivo.models.RecordingFolderItem;
import org.bigjoe.tivo.models.RecordingList;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Establish an RPC connection route with a TiVo using the provided cdata files.
 * Not dependent on anything but java, javax.net.ssl, and com.tivo.kmttg.JSON.JSONObject.
 * Uses either the passed in cdata file with the default password, 
 * or else the cdata.p12 and cdata.password files in the passed in programDir folder.
 *
 */
public class TiVoRPC {
	private static Logger log = Logger.getLogger(TiVoRPC.class.getSimpleName());

   public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

   private boolean debug;
   
   private static final String SchemaVersion = "14";
   private static final String SchemaVersion_newer = "17";
   private static final int DEFAULT_PORT = 1413;
   /**
    * default read timeout in seconds.
    * @see #setSoTimeout(int)
    */
   private static final int timeout = 120;
   
   private Boolean success = true;
   
   protected final String tivoName;
   protected final String IP;
   protected final int port;
   
   private String cdata = null;
   private String programDir;
   
   protected boolean rpcOld;

   private int rpc_id = 0;
   private int session_id = 0;
   
   private SSLSocket socket = null;
   private DataInputStream in = null;
   private DataOutputStream out = null;
   private SSLSocketFactory sslSocketFactory = null;

   private int attempt = 0;

   public String bodyId;


   protected void error(String msg) {
      log.severe(msg);
   }
   protected void print(String msg) {
      log.info(msg);
   }
   protected void warn(String msg) {
      log.warning(msg);
   }
   
   public TiVoRPC(String IP, String mak) {
      this(null, IP, mak, null, -1, null, false, false);
      bodyId = fetchBodyId();
   }
   

   public String fetchBodyId() {
      JsonObject json = new JsonObject();
      json.addProperty("bodyId", "-");
      String request = this.buildRequest("bodyConfigSearch", false, json);

      this.Write(request);
      JsonObject response = this.Read();
      log.info(response.toString());

      return response.get("bodyConfig").getAsJsonArray().get(0).getAsJsonObject().get("bodyId").getAsString();
   }

   /**
    * Establish an authorized RPC connection.  Check {@link #getSuccess()} for result.
    * @param tivoName the "friendly name" of the TiVo device - not required by default implementation
    * @param IP address to which socket will be connected.
    * @param mak Media Access Key used in authentication
    * @param programDir folder containing cdata files.
    * @param port port to use in connection, 0 or negative to use default.
    * @param cdata filename of cdata file in programDir, null to use defaults.
    * @param oldSchema true if old Schema should be used (automatically gets set to true if new schema fails on first try)
    * @param debug true if debugging should be performed.
    */
   public TiVoRPC(String tivoName, String IP, String mak, String programDir, int port, String cdata, boolean oldSchema, boolean debug) {
      this.cdata = cdata;
      this.programDir = programDir;
      this.rpcOld = oldSchema;
      this.debug = debug;
      this.tivoName = tivoName;
      this.IP = IP;
      if(port <= 0) port = DEFAULT_PORT;
      this.port = port;
      RemoteInit(mak);
   }
   
   /**
    * Establish an authorized RPC connection.  Check {@link #getSuccess()} for result.
    * @param IP address to which socket will be connected.
    * @param mak Media Access Key used in authentication
    * @param programDir folder containing cdata files.
    * @param port port to use in connection, 0 or negative to use default.
    * @param cdata filename of cdata file in programDir, null to use defaults.
    */
    public TiVoRPC(String IP, String mak, String programDir, String cdata) {
      this.cdata = cdata;
      this.programDir = programDir;
      this.rpcOld = false;
      this.debug = true;
      this.tivoName = "N/A";
      this.IP = IP;
      this.port = DEFAULT_PORT;
      RemoteInit(mak);
   }
   
   public boolean isConnected() {
      return this.socket.isConnected();
   }
   
   /**
    * The result of initialization
    * @return true if the connection was established.
    */
   public boolean getSuccess() {
      return success;
   }
   
   /**
    * calls {@link SSLSocket#setSoTimeout(int)}
    */
   protected void setSoTimeout(int timeout) throws SocketException {
      socket.setSoTimeout(timeout);
   }
   
   /**
    * public method to perform a simple RpcRequest to get a single response.
    * @param type used to set the "RequestType" header, also put into data as "type"
    * @param data if this contains "bodyId" that is used as the "BodyId" header, otherwise that header is blank.
    * @return the JSON response with added IsFinal value, or null if the Write didn't succeed or RpcRequest String had an error.
    */
   public synchronized JsonObject SingleRequest(String type, JsonObject data) {
      String req = buildRequest(type, false, data);
      if(req != null && Write(req)) {
         return Read();
      } else {
         return null;
      }
   }
   
   /**
    * Define the request String (header and body) to transmit over the socket.
    * SchemaVersion header is defined based on constructor boolean or downgraded automatically on the first error response of "Unsupported schema version."
    * @param type used to set the "RequestType" header, also put into data as "type"
    * @param monitor true to set a "ResponseCount" header of "multiple"
    * @param data if this contains "bodyId" that is used as the "BodyId" header, otherwise that header is blank.
    * @return the String to pass to {@link #Write(String)}
    */
   public synchronized String buildRequest(String type, Boolean monitor, JsonObject data) {
      try {
         String ResponseCount = "single";
         if (monitor)
            ResponseCount = "multiple";
         String bodyId = "";
         if (data.has("bodyId"))
            bodyId = data.get("bodyId").getAsString();
         String schema = SchemaVersion_newer;
         if (rpcOld)
            schema = SchemaVersion;
         rpc_id++;
         String eol = "\r\n";
         String headers =
            "Type: request" + eol +
            "RpcId: " + rpc_id + eol +
            "SchemaVersion: " + schema + eol +
            "Content-Type: application/json" + eol +
            "RequestType: " + type + eol +
            "ResponseCount: " + ResponseCount + eol +
            "BodyId: " + bodyId + eol +
            "X-ApplicationName: Quicksilver" + eol +
            "X-ApplicationVersion: 1.2" + eol +
            String.format("X-ApplicationSessionId: 0x%x", session_id) + eol;
         data.addProperty("type", type);

         String body = data.toString();
         String start_line = String.format("MRPC/2 %d %d", headers.length()+2, body.length());
         return start_line + eol + headers + eol + body + "\n";
      } catch (Exception e) {
         error("RpcRequest error: " + e.getMessage());
         return null;
      }
   }
   
   public void disconnect() {
    try {
       if (out != null) out.close();
       if (in != null) in.close();
    } catch (IOException e) {
       error("rpc disconnect error - " + e.getMessage());
    }
   }

   private class NaiveTrustManager implements X509TrustManager {
      // Doesn't throw an exception, so this is how it approves a certificate.
      public void checkClientTrusted ( X509Certificate[] cert, String authType )
                  throws CertificateException {}

      // Doesn't throw an exception, so this is how it approves a certificate.
      public void checkServerTrusted ( X509Certificate[] cert, String authType ) 
         throws CertificateException {}

      public X509Certificate[] getAcceptedIssuers () {
         return new X509Certificate[0];
      }
   }
    
   private final void createSocketFactory() {
      if ( sslSocketFactory == null ) {
        try {
           KeyStoreBuilder getKeyStore = new KeyStoreBuilder();
           KeyStore keyStore = getKeyStore.getKeyStore();
           String keyPassword = getKeyStore.getKeyPassword();

           Enumeration<String> aliases = keyStore.aliases();

           while (aliases.hasMoreElements()) {
              String alias = aliases.nextElement();
              X509Certificate crt = (X509Certificate) keyStore.getCertificate(alias);
              LocalDateTime notAfter = crt.getNotAfter().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();

              int expiresDays = (int) ChronoUnit.DAYS.between(LocalDateTime.now(), notAfter);
              if (expiresDays < 14) {
                 log.warning("RPC Certificate expires in " + expiresDays + " days.");
              } else if (expiresDays < 90) {
                 log.warning("RPC Certificate expires in " + expiresDays + " days.");
              }
           }

           KeyManagerFactory fac = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
           fac.init(keyStore, keyPassword.toCharArray());
           SSLContext context = SSLContext.getInstance("TLS");
           TrustManager[] tm = new TrustManager[] { new NaiveTrustManager() };
           context.init(fac.getKeyManagers(), tm, new SecureRandom());
           sslSocketFactory = context.getSocketFactory();
        } catch (KeyManagementException e) {
          error("KeyManagementException - " + e.getMessage()); 
        } catch (NoSuchAlgorithmException e) {
          error("NoSuchAlgorithmException - " + e.getMessage());
        } catch (KeyStoreException e) {
           error("KeyStoreException - " + e.getMessage());
        } catch (FileNotFoundException e) {
           error("FileNotFoundException - " + e.getMessage());
        } catch (CertificateException e) {
           error("CertificateException - " + e.getMessage());
        } catch (IOException e) {
           error("IOException - " + e.getMessage());
        } catch (UnrecoverableKeyException e) {
           error("UnrecoverableKeyException - " + e.getMessage());
        }
      }
    }

    /** perform a (non-web) socket setup and auth.  should be followed by If getSuccess() bodyId_get() */
    private void RemoteInit(String MAK) {
       createSocketFactory();
       //TODO this is going to produce the exact same session_id in every instance.  Should seed Random with e.g. the current time.
       session_id = new Random(0x27dc20).nextInt();
       try {
          socket = (SSLSocket) sslSocketFactory.createSocket(IP, port);
          socket.setNeedClientAuth(true);
          socket.setEnableSessionCreation(true);
          socket.setSoTimeout(timeout*1000);
          socket.startHandshake();
          in = new DataInputStream(socket.getInputStream());
          out = new DataOutputStream(socket.getOutputStream());
          
          success = Auth(MAK);
          
       } catch (Exception e) {
          if (attempt == 0 && e.getMessage() != null && e.getMessage().contains("UNKNOWN ALERT")) {
             // Try it again as this could be temporary glitch
             attempt = 1;
             warn("RemoteInit 2nd attempt...");
             RemoteInit(MAK);
             return;
          }
          error(e.toString());
          error("RemoteInit - (IP=" + IP + ", port=" + port + "): " + e.getMessage());
          error(Arrays.toString(e.getStackTrace()));
          success = false;
       }
    }


    /**
     * default implementation: perform a bodyAuthenticate RPC request to the connected device
     * @param MAK the makCredential to use in the bodyAuthenticate
     * @return true if response status equals "success"
     */
   protected boolean Auth(String MAK) {
       try {
         JsonObject credential = new JsonObject();
         JsonObject h = new JsonObject();
          credential.addProperty("type", "makCredential");
          credential.addProperty("key", MAK);
          h.add("credential", credential);
          String req = buildRequest("bodyAuthenticate", false, h);
          if (Write(req) ) {
             JsonObject result = Read();
             if (result.has("status")) {
                if (result.get("status").getAsString().equals("success"))
                   return true;
             }
          }
       } catch (Exception e) {
          error("rpc Auth error - " + e.getMessage());
       }
       return false;
    }
    
   /**
    * Write the request to the socket. 
    * @param data
    * @return true if the write succeeded
    */
   public synchronized final boolean Write(String data) {
      try {
         if (debug) {
            print("WRITE: " + data);
         }
         if (out == null)
            return false;
         out.write(data.getBytes());
         out.flush();
      } catch (IOException e) {
         error("rpc Write error - " + e.getMessage());
         return false;
      }
      return true;
   }
   
   /**
    * Read the response after a Write.
    * If the response type was "error" and the error was a "Unsupported schema version", sets rpcOld. 
    * Adds the boolean header "IsFinal" as a value in the response.
    * @return the JSON response.
    */
   @SuppressWarnings("deprecation")
   public synchronized final JsonObject Read() {
      String buf = "";
      Integer head_len;
      Integer body_len;
      
      try {
         // Expect line of format: MRPC/2 76 1870
         // 1st number is header length, 2nd number body length
         buf = in.readLine();
         if (debug) {
            print("READ: " + buf);
         }
         if (buf != null && buf.matches("^.*MRPC/2.+$")) {
            String[] split = buf.split(" ");
            head_len = Integer.parseInt(split[1]);
            body_len = Integer.parseInt(split[2]);
            
            byte[] headers = new byte[head_len];
            readBytes(headers, head_len);
   
            byte[] body = new byte[body_len];
            readBytes(body, body_len);
            
            if (debug) {
               print("READ: " + new String(headers) + new String(body));
            }
            
            // Pull out IsFinal value from header
            Boolean IsFinal;
            buf = new String(headers, "UTF8");
            if (buf.contains("IsFinal: true"))
               IsFinal = true;
            else
               IsFinal = false;
            
            // Return json contents with IsFinal flag added

            JsonObject j = JsonParser.parseString(new String(body, "UTF8")).getAsJsonObject();
            if (j.has("type") && j.get("type").getAsString().equals("error")) {
               error("RPC error response:\n" + j.toString());
               if (j.has("text") && j.get("text").getAsString().equals("Unsupported schema version")) {
                  // Revert to older schema version for older TiVo software versions
                  warn("Reverting to older RPC schema version - try command again.");
                  rpcOld = true;
               }
               // not returning null.  subclasses can make that choice.
            }
            j.addProperty("IsFinal", IsFinal);
            return j;

         }
      } catch (Exception e) {
         error("rpc Read error - " + e.getMessage());
         return null;
      }
      return null;
   }

   private void readBytes(byte[] body, int len) throws IOException {
      int bytesRead = 0;
      while (bytesRead < len) {
         bytesRead += in.read(body, bytesRead, len - bytesRead);
      }
   }

   public List<RecordingFolderItem> fetchShows(int maxFetch) {
        JsonObject json = new JsonObject();
        json.addProperty("flatten", true);
        json.addProperty("bodyId", bodyId);
        List<RecordingFolderItem> items = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int offset = 0;

        outer: while (true) {
            json.addProperty("offset", offset);
            json.addProperty("count", 25);

            Write(buildRequest("recordingFolderItemSearch", false, json));
            JsonObject result = Read();

            if (result == null || !result.has("recordingFolderItem")) {
                log.info("no recordingFolderItem returned");
                break;
            }

            JsonArray a = result.get("recordingFolderItem").getAsJsonArray();
            if (a.isEmpty()) {
                log.info("recordingFolderItem was empty");
                break;
            }
                
            for (JsonElement el : a) {
                offset++;
                log.info(el.toString());
                RecordingFolderItem item = GSON.fromJson(el, RecordingFolderItem.class);
                
                if (seen.add(item.childRecordingId)) {

                  item.recordingId = item.childRecordingId;

                    items.add(item);
                    if (maxFetch > 0 && items.size() >= maxFetch) {
                        break outer;
                    }
                }
            }
        }

        for (RecordingFolderItem item : items) {
            JsonObject jsonDetail = new JsonObject();
            jsonDetail.addProperty("bodyId", bodyId);
            jsonDetail.addProperty("levelOfDetail", "high");
            jsonDetail.addProperty("recordingId", item.recordingId);
            Write(buildRequest("recordingSearch", false, jsonDetail));
            item.recordingList = GSON.fromJson(Read(), RecordingList.class);

            JsonObject jsonId = new JsonObject();
            jsonId.addProperty("bodyId", bodyId);
            jsonId.addProperty("namespace", "mfs");
            jsonId.addProperty("objectId", item.recordingId);
    
            Write(buildRequest("idSearch", false, jsonId));
            String mfsId = Read().get("objectId").getAsJsonArray().get(0).getAsString();
            item.mfsId = mfsId.substring(mfsId.lastIndexOf('.') + 1);
        }
        return items;
   }

   public void deleteShow(String recordingId) {
      JsonObject json = new JsonObject();
      json.addProperty("bodyId", bodyId);
      json.addProperty("state", "deleted");

      JsonArray recordingIds = new JsonArray();
      recordingIds.add(recordingId);
      json.add("recordingId", recordingIds);

      Write(buildRequest("recordingUpdate", false, json));
      log.info("" + Read());

   }
}
