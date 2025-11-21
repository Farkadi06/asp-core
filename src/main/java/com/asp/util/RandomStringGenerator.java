package com.asp.util;

import java.security.SecureRandom;

public class RandomStringGenerator {

    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Generate a random alphanumeric string of specified length
     */
    public static String generateAlphanumeric(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = SECURE_RANDOM.nextInt(ALPHANUMERIC.length());
            sb.append(ALPHANUMERIC.charAt(index));
        }
        return sb.toString();
    }

    /**
     * Generate a secure random string for API key secret (uses alphanumeric for readability)
     */
    public static String generateSecureSecret(int length) {
        return generateAlphanumeric(length);
    }
}

