package com.facecheck.face.service;

import java.security.MessageDigest;
import org.springframework.stereotype.Service;

@Service
public class ImageHashService {

    public String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to hash image content", exception);
        }
    }
}
