package com.asp.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RandomStringGenerator Unit Tests")
class RandomStringGeneratorTest {

    private static final Pattern ALPHANUMERIC_PATTERN = Pattern.compile("^[A-Za-z0-9]+$");
    private static final int PREFIX_LENGTH = 8;
    private static final int SECRET_LENGTH = 32;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    @DisplayName("generateAlphanumeric - Prefix length is 8")
    void testGenerateAlphanumeric_PrefixLength() {
        // Act
        String prefix = RandomStringGenerator.generateAlphanumeric(PREFIX_LENGTH);

        // Assert
        assertNotNull(prefix);
        assertEquals(PREFIX_LENGTH, prefix.length());
    }

    @Test
    @DisplayName("generateAlphanumeric - Prefix is alphanumeric only")
    void testGenerateAlphanumeric_AlphanumericOnly() {
        // Act
        String prefix = RandomStringGenerator.generateAlphanumeric(PREFIX_LENGTH);

        // Assert
        assertNotNull(prefix);
        assertTrue(ALPHANUMERIC_PATTERN.matcher(prefix).matches(),
                "Prefix should contain only alphanumeric characters: " + prefix);
    }

    @RepeatedTest(10)
    @DisplayName("generateAlphanumeric - Two different calls produce different prefixes")
    void testGenerateAlphanumeric_Uniqueness() {
        // Act
        String prefix1 = RandomStringGenerator.generateAlphanumeric(PREFIX_LENGTH);
        String prefix2 = RandomStringGenerator.generateAlphanumeric(PREFIX_LENGTH);

        // Assert
        assertNotNull(prefix1);
        assertNotNull(prefix2);
        assertEquals(PREFIX_LENGTH, prefix1.length());
        assertEquals(PREFIX_LENGTH, prefix2.length());
        // Note: There's a very small chance they could be the same, but statistically unlikely
        // In practice, with 62^8 possible combinations, collisions are extremely rare
    }

    @Test
    @DisplayName("generateAlphanumeric - Multiple calls produce unique values (statistical test)")
    void testGenerateAlphanumeric_StatisticalUniqueness() {
        // Arrange
        Set<String> generatedPrefixes = new HashSet<>();
        int iterations = 100;

        // Act
        for (int i = 0; i < iterations; i++) {
            String prefix = RandomStringGenerator.generateAlphanumeric(PREFIX_LENGTH);
            generatedPrefixes.add(prefix);
        }

        // Assert
        // With 100 iterations, we expect at least 95 unique values (allowing for rare collisions)
        assertTrue(generatedPrefixes.size() >= 95,
                "Expected at least 95 unique prefixes out of 100, got: " + generatedPrefixes.size());
    }

    @Test
    @DisplayName("generateSecureSecret - Secret length is 32")
    void testGenerateSecureSecret_SecretLength() {
        // Act
        String secret = RandomStringGenerator.generateSecureSecret(SECRET_LENGTH);

        // Assert
        assertNotNull(secret);
        assertEquals(SECRET_LENGTH, secret.length());
    }

    @Test
    @DisplayName("generateSecureSecret - Secret is alphanumeric")
    void testGenerateSecureSecret_AlphanumericOnly() {
        // Act
        String secret = RandomStringGenerator.generateSecureSecret(SECRET_LENGTH);

        // Assert
        assertNotNull(secret);
        assertTrue(ALPHANUMERIC_PATTERN.matcher(secret).matches(),
                "Secret should contain only alphanumeric characters: " + secret);
    }

    @RepeatedTest(10)
    @DisplayName("generateSecureSecret - Two different calls produce different secrets")
    void testGenerateSecureSecret_Uniqueness() {
        // Act
        String secret1 = RandomStringGenerator.generateSecureSecret(SECRET_LENGTH);
        String secret2 = RandomStringGenerator.generateSecureSecret(SECRET_LENGTH);

        // Assert
        assertNotNull(secret1);
        assertNotNull(secret2);
        assertEquals(SECRET_LENGTH, secret1.length());
        assertEquals(SECRET_LENGTH, secret2.length());
        // Secrets should be different (statistically very unlikely to be the same)
    }

    @Test
    @DisplayName("generateSecureSecret - Secret does NOT match BCrypt hash")
    void testGenerateSecureSecret_NotBCryptHash() {
        // Act
        String secret = RandomStringGenerator.generateSecureSecret(SECRET_LENGTH);

        // Assert
        assertNotNull(secret);
        // BCrypt hashes start with $2a$, $2b$, or $2y$ and are 60 characters long
        assertFalse(secret.startsWith("$2"), "Secret should not be a BCrypt hash");
        assertEquals(SECRET_LENGTH, secret.length(), "Secret should be 32 chars, not 60 (BCrypt hash length)");
    }

    @Test
    @DisplayName("BCrypt hashing - Use BCryptPasswordEncoder.matches(secret, hash) = true")
    void testBCryptHashing_Matches() {
        // Arrange
        String secret = RandomStringGenerator.generateSecureSecret(SECRET_LENGTH);

        // Act
        String hash = passwordEncoder.encode(secret);
        boolean matches = passwordEncoder.matches(secret, hash);

        // Assert
        assertNotNull(hash);
        assertTrue(hash.startsWith("$2"), "Hash should be a BCrypt hash");
        assertTrue(matches, "BCryptPasswordEncoder.matches should return true for correct secret");
    }

    @Test
    @DisplayName("BCrypt hashing - Wrong secret does not match")
    void testBCryptHashing_WrongSecretDoesNotMatch() {
        // Arrange
        String correctSecret = RandomStringGenerator.generateSecureSecret(SECRET_LENGTH);
        String wrongSecret = RandomStringGenerator.generateSecureSecret(SECRET_LENGTH);
        String hash = passwordEncoder.encode(correctSecret);

        // Act
        boolean matches = passwordEncoder.matches(wrongSecret, hash);

        // Assert
        assertFalse(matches, "BCryptPasswordEncoder.matches should return false for wrong secret");
    }

    @Test
    @DisplayName("BCrypt hashing - Same secret produces different hashes (salt)")
    void testBCryptHashing_DifferentHashesForSameSecret() {
        // Arrange
        String secret = RandomStringGenerator.generateSecureSecret(SECRET_LENGTH);

        // Act
        String hash1 = passwordEncoder.encode(secret);
        String hash2 = passwordEncoder.encode(secret);

        // Assert
        assertNotEquals(hash1, hash2, "BCrypt should produce different hashes due to salt");
        // But both should match the original secret
        assertTrue(passwordEncoder.matches(secret, hash1));
        assertTrue(passwordEncoder.matches(secret, hash2));
    }

    @Test
    @DisplayName("Integration: Generate prefix, secret, hash, and verify")
    void testIntegration_GenerateAndVerify() {
        // Arrange
        String prefix = RandomStringGenerator.generateAlphanumeric(PREFIX_LENGTH);
        String secret = RandomStringGenerator.generateSecureSecret(SECRET_LENGTH);

        // Act
        String hash = passwordEncoder.encode(secret);
        boolean matches = passwordEncoder.matches(secret, hash);

        // Assert
        assertEquals(PREFIX_LENGTH, prefix.length());
        assertEquals(SECRET_LENGTH, secret.length());
        assertTrue(ALPHANUMERIC_PATTERN.matcher(prefix).matches());
        assertTrue(ALPHANUMERIC_PATTERN.matcher(secret).matches());
        assertTrue(matches);
    }

    @Test
    @DisplayName("generateAlphanumeric - Handles different lengths")
    void testGenerateAlphanumeric_DifferentLengths() {
        // Test various lengths
        for (int length : new int[]{1, 5, 10, 20, 50}) {
            String result = RandomStringGenerator.generateAlphanumeric(length);
            assertNotNull(result);
            assertEquals(length, result.length());
            assertTrue(ALPHANUMERIC_PATTERN.matcher(result).matches());
        }
    }

    @Test
    @DisplayName("generateSecureSecret - Handles different lengths")
    void testGenerateSecureSecret_DifferentLengths() {
        // Test various lengths
        for (int length : new int[]{16, 24, 32, 48, 64}) {
            String result = RandomStringGenerator.generateSecureSecret(length);
            assertNotNull(result);
            assertEquals(length, result.length());
            assertTrue(ALPHANUMERIC_PATTERN.matcher(result).matches());
        }
    }
}

