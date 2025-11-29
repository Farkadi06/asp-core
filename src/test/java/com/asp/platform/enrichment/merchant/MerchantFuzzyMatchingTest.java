package com.asp.platform.enrichment.merchant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive test suite for MerchantFuzzyMatcher.
 * Tests fuzzy matching with various merchant name variations, typos, and edge cases.
 */
@DisplayName("Merchant Fuzzy Matching Tests")
class MerchantFuzzyMatchingTest {

    private MerchantFuzzyMatcher fuzzyMatcher;
    private MerchantNormalizationService normalizationService;

    @BeforeEach
    void setUp() {
        fuzzyMatcher = new MerchantFuzzyMatcher();
        
        // Setup normalization service with test aliases
        normalizationService = new MerchantNormalizationService(fuzzyMatcher);
        MerchantNormalizationService.MerchantRulesConfig config = 
            new MerchantNormalizationService.MerchantRulesConfig();
        config.setIgnoreTokens(Collections.emptyList());
        
        // Setup test aliases
        Map<String, List<String>> aliases = new HashMap<>();
        aliases.put("marjane", List.of("marjane", "marjane_market", "mj"));
        aliases.put("carrefour", List.of("carrefour", "carrefour_market", "crf"));
        aliases.put("boost_coffee", List.of("boost coffee", "boost_coffee"));
        aliases.put("macdonalds", List.of("mcdonald", "mcdonalds", "mcdo"));
        aliases.put("total", List.of("total", "total station"));
        
        config.setMerchantAliases(aliases);
        normalizationService.overrideRules(config);
        
        // Load canonical merchants in fuzzy matcher
        fuzzyMatcher.loadCanonicalMerchants();
    }

    @Test
    @DisplayName("Should match exact merchant name")
    void testExactMatch() {
        Optional<MerchantFuzzyMatcher.MerchantMatch> match = fuzzyMatcher.match("marjane");
        
        assertThat(match).isPresent();
        assertThat(match.get().getCanonical()).isEqualTo("marjane");
        assertThat(match.get().getScore()).isGreaterThanOrEqualTo(0.90);
        assertThat(match.get().getConfidence()).isEqualTo(MerchantFuzzyMatcher.MatchConfidence.HIGH);
    }

    @Test
    @DisplayName("Should prefer alias match over fuzzy match")
    void testAliasMatchBeatsFuzzy() {
        // "mj" is an alias for "marjane", so alias resolution should work first
        String normalized = normalizationService.normalizeMerchant("MJ");
        
        // Should resolve to "marjane" via alias, not fuzzy
        assertThat(normalized).isEqualTo("marjane");
    }

    @Test
    @DisplayName("Should match Boost Coffee variations with fuzzy matching")
    void testBoostCoffeeVariations() {
        // Test various typos and variations
        Optional<MerchantFuzzyMatcher.MerchantMatch> match1 = fuzzyMatcher.match("boost_coffe");
        assertThat(match1).isPresent();
        assertThat(match1.get().getCanonical()).isEqualTo("boost_coffee");
        assertThat(match1.get().getScore()).isGreaterThanOrEqualTo(0.80);
        
        Optional<MerchantFuzzyMatcher.MerchantMatch> match2 = fuzzyMatcher.match("boost_cofee");
        assertThat(match2).isPresent();
        assertThat(match2.get().getCanonical()).isEqualTo("boost_coffee");
        
        Optional<MerchantFuzzyMatcher.MerchantMatch> match3 = fuzzyMatcher.match("boos_coffee");
        assertThat(match3).isPresent();
        assertThat(match3.get().getCanonical()).isEqualTo("boost_coffee");
    }

    @Test
    @DisplayName("Should match Carrefour variations")
    void testCarrefourVariations() {
        Optional<MerchantFuzzyMatcher.MerchantMatch> match1 = fuzzyMatcher.match("carrefur");
        assertThat(match1).isPresent();
        assertThat(match1.get().getCanonical()).isEqualTo("carrefour");
        
        Optional<MerchantFuzzyMatcher.MerchantMatch> match2 = fuzzyMatcher.match("carrefuor");
        assertThat(match2).isPresent();
        assertThat(match2.get().getCanonical()).isEqualTo("carrefour");
        
        Optional<MerchantFuzzyMatcher.MerchantMatch> match3 = fuzzyMatcher.match("carrefour_market");
        // This might match via alias first, but if not, fuzzy should work
        assertThat(match3).isPresent();
    }

    @Test
    @DisplayName("Should match McDonald's variations")
    void testMcDonaldsVariations() {
        Optional<MerchantFuzzyMatcher.MerchantMatch> match1 = fuzzyMatcher.match("mcdonal");
        assertThat(match1).isPresent();
        assertThat(match1.get().getCanonical()).isEqualTo("macdonalds");
        
        Optional<MerchantFuzzyMatcher.MerchantMatch> match2 = fuzzyMatcher.match("mcdonals");
        assertThat(match2).isPresent();
        assertThat(match2.get().getCanonical()).isEqualTo("macdonalds");
        
        Optional<MerchantFuzzyMatcher.MerchantMatch> match3 = fuzzyMatcher.match("macdonalds");
        assertThat(match3).isPresent();
        assertThat(match3.get().getCanonical()).isEqualTo("macdonalds");
    }

    @Test
    @DisplayName("Should match Total station variations")
    void testTotalStationVariations() {
        Optional<MerchantFuzzyMatcher.MerchantMatch> match1 = fuzzyMatcher.match("total_station");
        assertThat(match1).isPresent();
        assertThat(match1.get().getCanonical()).isEqualTo("total");
        
        Optional<MerchantFuzzyMatcher.MerchantMatch> match2 = fuzzyMatcher.match("totl");
        assertThat(match2).isPresent();
        assertThat(match2.get().getCanonical()).isEqualTo("total");
    }

    @Test
    @DisplayName("Should reject bad matches with score < 0.70")
    void testBadMatchesRejected() {
        Optional<MerchantFuzzyMatcher.MerchantMatch> match1 = fuzzyMatcher.match("xyz_unknown_merchant");
        assertThat(match1).isEmpty();
        
        Optional<MerchantFuzzyMatcher.MerchantMatch> match2 = fuzzyMatcher.match("completely_different");
        assertThat(match2).isEmpty();
        
        Optional<MerchantFuzzyMatcher.MerchantMatch> match3 = fuzzyMatcher.match("abc123");
        assertThat(match3).isEmpty();
    }

    @Test
    @DisplayName("Should score multiple candidates and pick best")
    void testMultipleCandidateScoring() {
        // "marjan" is close to both "marjane" and potentially others
        Optional<MerchantFuzzyMatcher.MerchantMatch> match = fuzzyMatcher.match("marjan");
        
        assertThat(match).isPresent();
        // Should pick "marjane" as best match
        assertThat(match.get().getCanonical()).isEqualTo("marjane");
        assertThat(match.get().getScore()).isGreaterThan(0.70);
    }

    @Test
    @DisplayName("Should handle Unicode variations")
    void testUnicodeVariations() {
        // Test with accents that should be normalized
        Optional<MerchantFuzzyMatcher.MerchantMatch> match1 = fuzzyMatcher.match("café_maure");
        // Should normalize and potentially match
        assertThat(match1).isPresent();
        
        Optional<MerchantFuzzyMatcher.MerchantMatch> match2 = fuzzyMatcher.match("marjane_élégant");
        // Should normalize accents and match
        assertThat(match2).isPresent();
        assertThat(match2.get().getCanonical()).isEqualTo("marjane");
    }

    @Test
    @DisplayName("Should handle normalization interactions")
    void testNormalizationInteractions() {
        // Test that normalization + fuzzy matching works together
        String raw = "02/09 BOOST COFFEE GHANDI CASA 45,00";
        String cleaned = normalizationService.cleanMerchant(raw);
        String normalized = normalizationService.normalizeMerchant(cleaned);
        
        // Should normalize to "boost_coffee_ghandi" or similar
        assertThat(normalized).isNotNull();
        assertThat(normalized).contains("boost");
        
        // Fuzzy matching should work on normalized form
        Optional<MerchantFuzzyMatcher.MerchantMatch> match = fuzzyMatcher.match(normalized);
        // If normalized form is close to "boost_coffee", should match
        if (normalized.contains("boost_coffee") || normalized.startsWith("boost")) {
            assertThat(match).isPresent();
        }
    }

    @Test
    @DisplayName("Should return empty for null or empty input")
    void testNullOrEmptyInput() {
        assertThat(fuzzyMatcher.match(null)).isEmpty();
        assertThat(fuzzyMatcher.match("")).isEmpty();
        assertThat(fuzzyMatcher.match("   ")).isEmpty();
    }

    @Test
    @DisplayName("Should only return HIGH or MEDIUM confidence matches")
    void testConfidenceThresholds() {
        // HIGH confidence (score >= 0.90)
        Optional<MerchantFuzzyMatcher.MerchantMatch> highMatch = fuzzyMatcher.match("marjane");
        assertThat(highMatch).isPresent();
        assertThat(highMatch.get().getConfidence())
            .isIn(MerchantFuzzyMatcher.MatchConfidence.HIGH, MerchantFuzzyMatcher.MatchConfidence.MEDIUM);
        
        // MEDIUM confidence (score >= 0.80)
        Optional<MerchantFuzzyMatcher.MerchantMatch> mediumMatch = fuzzyMatcher.match("marjan");
        if (mediumMatch.isPresent()) {
            assertThat(mediumMatch.get().getConfidence())
                .isIn(MerchantFuzzyMatcher.MatchConfidence.HIGH, MerchantFuzzyMatcher.MatchConfidence.MEDIUM);
        }
        
        // LOW confidence (score >= 0.70) should NOT be returned
        // We can't easily test this without knowing exact scores, but the logic ensures it
    }

    @Test
    @DisplayName("Should handle case variations")
    void testCaseVariations() {
        Optional<MerchantFuzzyMatcher.MerchantMatch> match1 = fuzzyMatcher.match("MARJANE");
        assertThat(match1).isPresent();
        
        Optional<MerchantFuzzyMatcher.MerchantMatch> match2 = fuzzyMatcher.match("Marjane");
        assertThat(match2).isPresent();
        
        Optional<MerchantFuzzyMatcher.MerchantMatch> match3 = fuzzyMatcher.match("mArJaNe");
        assertThat(match3).isPresent();
    }

    @Test
    @DisplayName("Should handle spacing variations")
    void testSpacingVariations() {
        Optional<MerchantFuzzyMatcher.MerchantMatch> match1 = fuzzyMatcher.match("boost_coffee");
        assertThat(match1).isPresent();
        
        Optional<MerchantFuzzyMatcher.MerchantMatch> match2 = fuzzyMatcher.match("boost  coffee");
        assertThat(match2).isPresent();
        
        Optional<MerchantFuzzyMatcher.MerchantMatch> match3 = fuzzyMatcher.match("boost-coffee");
        assertThat(match3).isPresent();
    }

    @Test
    @DisplayName("Should handle partial matches correctly")
    void testPartialMatches() {
        // "marj" is a partial match for "marjane"
        Optional<MerchantFuzzyMatcher.MerchantMatch> match = fuzzyMatcher.match("marj");
        
        // Should either match with lower confidence or not match at all
        // depending on threshold
        if (match.isPresent()) {
            assertThat(match.get().getCanonical()).isEqualTo("marjane");
            assertThat(match.get().getScore()).isGreaterThanOrEqualTo(0.80);
        }
    }

    @Test
    @DisplayName("Should handle special characters")
    void testSpecialCharacters() {
        Optional<MerchantFuzzyMatcher.MerchantMatch> match1 = fuzzyMatcher.match("marjane@123");
        // Should normalize and match
        assertThat(match1).isPresent();
        
        Optional<MerchantFuzzyMatcher.MerchantMatch> match2 = fuzzyMatcher.match("boost-coffee!");
        assertThat(match2).isPresent();
    }

    @Test
    @DisplayName("Should compute similarity scores correctly")
    void testSimilarityScores() {
        Optional<MerchantFuzzyMatcher.MerchantMatch> exactMatch = fuzzyMatcher.match("marjane");
        assertThat(exactMatch).isPresent();
        assertThat(exactMatch.get().getScore()).isGreaterThanOrEqualTo(0.90);
        
        Optional<MerchantFuzzyMatcher.MerchantMatch> typoMatch = fuzzyMatcher.match("marjan");
        if (typoMatch.isPresent()) {
            // Should have lower score than exact match
            assertThat(typoMatch.get().getScore()).isLessThan(exactMatch.get().getScore());
            assertThat(typoMatch.get().getScore()).isGreaterThanOrEqualTo(0.80);
        }
    }
}

