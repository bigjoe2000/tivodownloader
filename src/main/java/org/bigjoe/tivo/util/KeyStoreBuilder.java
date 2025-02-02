package org.bigjoe.tivo.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

public class KeyStoreBuilder {
   private String keyPassword;
   private KeyStore keyStore;

   public KeyStoreBuilder()
         throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
      keyStore = KeyStore.getInstance("PKCS12");
      // This is default USA password
      keyPassword = "piUYKNH7Sb"; // expires 11/17/2026
      // keyPassword = "KllX3KygL9"; // expires 1/24/2026

      // Installation dir cdata.p12 file takes priority if it exists
      File certFile = new File(getClass().getClassLoader().getResource("cdata.p12").getFile());
      InputStream keyInput = new FileInputStream(certFile);
      //       cdata = programDir + "/cdata.password";
      //       if (new File(cdata).isFile()) {
      //          Scanner s = new Scanner(new File(cdata));
      //          keyPassword = s.useDelimiter("\\A").next();
      //          s.close();
      //       } else {
      //          System.out.println("cdata.p12 file present, but cdata.password is not");
      //       }
      //    } else {
      //       // Read default USA cdata.p12 from kmttg.jar
      //       keyInput = getClass().getResourceAsStream("/cdata.p12");
      //    }
      // } else
      //    keyInput = new FileInputStream(cdata);
      keyStore.load(keyInput, keyPassword.toCharArray());
      keyInput.close();
   }

   public KeyStore getKeyStore() {
      return keyStore;
   }

   public String getKeyPassword() {
      return keyPassword;
   }

}