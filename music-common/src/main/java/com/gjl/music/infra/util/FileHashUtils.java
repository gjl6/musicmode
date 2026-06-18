package com.gjl.music.infra.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


public class FileHashUtils {

    public static String getFileHash(Object input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            if (input instanceof File file) {
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        digest.update(buffer, 0, bytesRead);
                    }
                }
            } else if (input instanceof byte[] byteArray) {
                digest.update(byteArray);
            } else if (input instanceof String string) {
                digest.update(string.getBytes(StandardCharsets.UTF_8));
            } else {
                throw new IllegalArgumentException("Input must be File, byte[] or String");
            }

            byte[] hashBytes = digest.digest();
            StringBuilder hexString = new StringBuilder(2 * hashBytes.length);
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
