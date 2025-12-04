package com.asp.auth.util;

import java.util.Locale;

/**
 * Utility class for generating URL-friendly slugs.
 */
public class SlugUtils {

    /**
     * Generates a slug from a string (e.g., email or name).
     * Converts to lowercase, replaces spaces and special chars with hyphens.
     */
    public static String generateSlug(String input) {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("Input cannot be null or empty");
        }

        // Extract local part from email if it's an email
        String base = input;
        if (input.contains("@")) {
            base = input.split("@")[0];
        }

        // Convert to lowercase
        base = base.toLowerCase(Locale.ROOT);

        // Replace spaces and special characters with hyphens
        base = base.replaceAll("[^a-z0-9]+", "-");

        // Remove leading/trailing hyphens
        base = base.replaceAll("^-+|-+$", "");

        // Ensure it's not empty
        if (base.isEmpty()) {
            base = "user";
        }

        return base;
    }

    /**
     * Generates a unique slug by appending a number if needed.
     * This is a simple version - in production, you'd check against the database.
     */
    public static String generateUniqueSlug(String baseSlug, java.util.function.Function<String, Boolean> existsChecker) {
        String slug = baseSlug;
        int counter = 1;

        while (existsChecker.apply(slug)) {
            slug = baseSlug + "-" + counter;
            counter++;
            if (counter > 1000) {
                throw new RuntimeException("Failed to generate unique slug after 1000 attempts");
            }
        }

        return slug;
    }
}

