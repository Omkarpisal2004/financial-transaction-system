package com.financialtransaction.idempotency.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class HashUtil {

    private HashUtil() {
        // Utility class; prevent instantiation.
    }

    public static String sha256(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Input cannot be null");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    input.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is unavailable", e
            );
        }
    }
}

