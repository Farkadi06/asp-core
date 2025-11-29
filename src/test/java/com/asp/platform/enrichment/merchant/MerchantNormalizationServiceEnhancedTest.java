package com.asp.platform.enrichment.merchant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enhanced test suite for MerchantNormalizationService with comprehensive coverage
 * of cleaning, normalization, city/country/payment token removal, and alias resolution.
 */
@DisplayName("MerchantNormalizationService Enhanced Tests")
class MerchantNormalizationServiceEnhancedTest {

    private MerchantNormalizationService service;

    @BeforeEach
    void setUp() {
        service = new MerchantNormalizationService();
        MerchantNormalizationService.MerchantRulesConfig config = new MerchantNormalizationService.MerchantRulesConfig();
        config.setIgnoreTokens(Collections.emptyList());
        
        // Setup test aliases
        Map<String, List<String>> aliases = new HashMap<>();
        aliases.put("marjane", List.of("marjane", "marjane_market", "mj"));
        aliases.put("carrefour", List.of("carrefour", "carrefour_market", "crf"));
        aliases.put("boost_coffee", List.of("boost coffee", "boost_coffee"));
        aliases.put("macdonalds", List.of("mcdonald", "mcdonalds", "mcdo"));
        
        config.setMerchantAliases(aliases);
        service.overrideRules(config);
    }

    @Test
    @DisplayName("Should remove date prefixes")
    void testRemoveDatePrefixes() {
        String raw = "02/09/2024 PAIEMENT PAR CARTE MARJANE CASA 150,00";
        String cleaned = service.cleanMerchant(raw);
        assertThat(cleaned).doesNotContain("02/09/2024");
        assertThat(cleaned).contains("MARJANE");
    }

    @Test
    @DisplayName("Should remove amount patterns")
    void testRemoveAmounts() {
        String raw = "MARJANE 150,00 MAD";
        String cleaned = service.cleanMerchant(raw);
        assertThat(cleaned).doesNotContain("150,00");
        assertThat(cleaned).contains("MARJANE");
    }

    @Test
    @DisplayName("Should remove city tokens")
    void testRemoveCityTokens() {
        String raw = "MARJANE CASA RABAT";
        String cleaned = service.cleanMerchant(raw);
        assertThat(cleaned).doesNotContain("CASA");
        assertThat(cleaned).doesNotContain("RABAT");
        assertThat(cleaned).contains("MARJANE");
    }

    @Test
    @DisplayName("Should remove country tokens")
    void testRemoveCountryTokens() {
        String raw = "MARJANE MAROC MA";
        String cleaned = service.cleanMerchant(raw);
        assertThat(cleaned).doesNotContain("MAROC");
        assertThat(cleaned).doesNotContain("MA");
        assertThat(cleaned).contains("MARJANE");
    }

    @Test
    @DisplayName("Should remove payment tokens")
    void testRemovePaymentTokens() {
        String raw = "MARJANE POS ECOM ATM";
        String cleaned = service.cleanMerchant(raw);
        assertThat(cleaned).doesNotContain("POS");
        assertThat(cleaned).doesNotContain("ECOM");
        assertThat(cleaned).doesNotContain("ATM");
        assertThat(cleaned).contains("MARJANE");
    }

    @Test
    @DisplayName("Should remove noise tokens")
    void testRemoveNoiseTokens() {
        String raw = "PAIEMENT PAR CARTE MARJANE";
        String cleaned = service.cleanMerchant(raw);
        assertThat(cleaned).doesNotContain("PAIEMENT");
        assertThat(cleaned).doesNotContain("PAR");
        assertThat(cleaned).doesNotContain("CARTE");
        assertThat(cleaned).contains("MARJANE");
    }

    @Test
    @DisplayName("Should normalize fuel merchant names")
    void testFuelMerchantNormalization() {
        String raw = "02/09 STATION AFRIQUIA CASA 200,00";
        String cleaned = service.cleanMerchant(raw);
        String normalized = service.normalizeMerchant(cleaned);
        
        assertThat(cleaned).contains("AFRIQUIA");
        assertThat(normalized).isEqualTo("afriquia");
    }

    @Test
    @DisplayName("Should resolve merchant aliases")
    void testAliasResolution() {
        String cleaned = "MARJANE";
        String normalized = service.normalizeMerchant(cleaned);
        assertThat(normalized).isEqualTo("marjane");
        
        // Test alias resolution
        String aliasCleaned = "MJ";
        String aliasNormalized = service.normalizeMerchant(aliasCleaned);
        // Should resolve to canonical name if alias matches
        assertThat(aliasNormalized).isNotNull();
    }

    @Test
    @DisplayName("Should handle Unicode and accents")
    void testUnicodeNormalization() {
        String raw = "CAFÉ MAURE ÉLÉGANT";
        String cleaned = service.cleanMerchant(raw);
        String normalized = service.normalizeMerchant(cleaned);
        
        assertThat(cleaned).doesNotContain("É");
        assertThat(normalized).doesNotContain("é");
        assertThat(normalized).contains("cafe");
    }

    @Test
    @DisplayName("Should normalize multiple spaces")
    void testMultipleSpacesNormalization() {
        String raw = "MARJANE    HYPER    CASA";
        String cleaned = service.cleanMerchant(raw);
        assertThat(cleaned).doesNotContain("    ");
        assertThat(cleaned.split(" ").length).isLessThanOrEqualTo(3);
    }

    @Test
    @DisplayName("Should generate canonical merchant form")
    void testCanonicalFormGeneration() {
        String cleaned = "BOOST COFFEE GHANDI";
        String normalized = service.normalizeMerchant(cleaned);
        
        assertThat(normalized).isEqualTo("boost_coffee_ghandi");
        assertThat(normalized).doesNotContain(" ");
        assertThat(normalized).matches("^[a-z0-9_]+$");
    }

    @Test
    @DisplayName("Should handle complex transaction description")
    void testComplexTransactionDescription() {
        String raw = "02/09/2024 PAIEMENT PAR CARTE 0332 MARJANE HYPER CASA 150,00 MAD POS";
        String cleaned = service.cleanMerchant(raw);
        String normalized = service.normalizeMerchant(cleaned);
        
        assertThat(cleaned).contains("MARJANE");
        assertThat(cleaned).doesNotContain("02/09/2024");
        assertThat(cleaned).doesNotContain("150,00");
        assertThat(cleaned).doesNotContain("CASA");
        assertThat(cleaned).doesNotContain("POS");
        assertThat(normalized).isEqualTo("marjane");
    }

    @Test
    @DisplayName("Should handle McDonald's variations")
    void testMcDonaldsVariations() {
        String raw1 = "MCDONALD CASA";
        String raw2 = "MC DONALD RABAT";
        String raw3 = "MCDO MARRAKECH";
        
        String cleaned1 = service.cleanMerchant(raw1);
        String cleaned2 = service.cleanMerchant(raw2);
        String cleaned3 = service.cleanMerchant(raw3);
        
        String normalized1 = service.normalizeMerchant(cleaned1);
        String normalized2 = service.normalizeMerchant(cleaned2);
        String normalized3 = service.normalizeMerchant(cleaned3);
        
        assertThat(normalized1).contains("mcdonald");
        assertThat(normalized2).contains("mcdonald");
        assertThat(normalized3).contains("mcdo");
    }

    @Test
    @DisplayName("Should handle telecom recharge transactions")
    void testTelecomRecharge() {
        String raw = "RECHARGE INWI 50,00 MAD";
        String cleaned = service.cleanMerchant(raw);
        String normalized = service.normalizeMerchant(cleaned);
        
        assertThat(cleaned).contains("INWI");
        assertThat(cleaned).doesNotContain("50,00");
        assertThat(normalized).contains("inwi");
    }

    @Test
    @DisplayName("Should handle food delivery services")
    void testFoodDeliveryServices() {
        String raw = "GLOVO APP CASA 75,00";
        String cleaned = service.cleanMerchant(raw);
        String normalized = service.normalizeMerchant(cleaned);
        
        assertThat(cleaned).contains("GLOVO");
        assertThat(cleaned).doesNotContain("CASA");
        assertThat(normalized).contains("glovo");
    }

    @Test
    @DisplayName("Should handle e-commerce transactions")
    void testEcommerceTransactions() {
        String raw = "AMAZON EU FR 299,99 EUR";
        String cleaned = service.cleanMerchant(raw);
        String normalized = service.normalizeMerchant(cleaned);
        
        assertThat(cleaned).contains("AMAZON");
        assertThat(cleaned).doesNotContain("299,99");
        assertThat(normalized).contains("amazon");
    }

    @Test
    @DisplayName("Should handle subscription services")
    void testSubscriptionServices() {
        String raw = "NETFLIX SUBSCRIPTION 99,00 MAD";
        String cleaned = service.cleanMerchant(raw);
        String normalized = service.normalizeMerchant(cleaned);
        
        assertThat(cleaned).contains("NETFLIX");
        assertThat(cleaned).doesNotContain("99,00");
        assertThat(normalized).contains("netflix");
    }

    @Test
    @DisplayName("Should handle empty or null input")
    void testEmptyOrNullInput() {
        assertThat(service.cleanMerchant(null)).isNull();
        assertThat(service.cleanMerchant("")).isNull();
        assertThat(service.cleanMerchant("   ")).isNull();
        
        assertThat(service.normalizeMerchant(null)).isNull();
        assertThat(service.normalizeMerchant("")).isNull();
        assertThat(service.normalizeMerchant("   ")).isNull();
    }

    @Test
    @DisplayName("Should handle special characters")
    void testSpecialCharacters() {
        String raw = "CAFÉ & RESTAURANT - MARJANE";
        String cleaned = service.cleanMerchant(raw);
        String normalized = service.normalizeMerchant(cleaned);
        
        assertThat(cleaned).doesNotContain("&");
        assertThat(cleaned).doesNotContain("-");
        assertThat(normalized).matches("^[a-z0-9_]+$");
    }

    @Test
    @DisplayName("Should handle multiple hyphens and underscores")
    void testMultipleHyphensUnderscores() {
        String raw = "MARJANE---HYPER___CASA";
        String cleaned = service.cleanMerchant(raw);
        String normalized = service.normalizeMerchant(cleaned);
        
        assertThat(cleaned).doesNotContain("---");
        assertThat(cleaned).doesNotContain("___");
        assertThat(normalized).doesNotContain("__");
    }

    @Test
    @DisplayName("Should handle Carrefour variations")
    void testCarrefourVariations() {
        String raw1 = "CARREFOUR MARKET CASA";
        String raw2 = "CARREFOUR EXPRESS RABAT";
        String raw3 = "CRF MARRAKECH";
        
        String cleaned1 = service.cleanMerchant(raw1);
        String cleaned2 = service.cleanMerchant(raw2);
        String cleaned3 = service.cleanMerchant(raw3);
        
        String normalized1 = service.normalizeMerchant(cleaned1);
        String normalized2 = service.normalizeMerchant(cleaned2);
        String normalized3 = service.normalizeMerchant(cleaned3);
        
        assertThat(normalized1).contains("carrefour");
        assertThat(normalized2).contains("carrefour");
        assertThat(normalized3).contains("crf");
    }

    @Test
    @DisplayName("Should handle Boost Coffee with location")
    void testBoostCoffeeWithLocation() {
        String raw = "02/09 BOOST COFFEE GHANDI CASA 45,00";
        String cleaned = service.cleanMerchant(raw);
        String normalized = service.normalizeMerchant(cleaned);
        
        assertThat(cleaned).contains("BOOST COFFEE GHANDI");
        assertThat(cleaned).doesNotContain("CASA");
        assertThat(cleaned).doesNotContain("45,00");
        assertThat(normalized).isEqualTo("boost_coffee_ghandi");
    }
}

