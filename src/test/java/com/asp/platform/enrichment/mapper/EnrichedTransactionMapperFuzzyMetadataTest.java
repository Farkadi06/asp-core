package com.asp.platform.enrichment.mapper;

import com.asp.domain.NormalizedAccount;
import com.asp.domain.NormalizedTransaction;
import com.asp.domain.Tenant;
import com.asp.platform.enrichment.category.CategoryClassificationResult;
import com.asp.platform.enrichment.category.CategoryClassifierService;
import com.asp.platform.enrichment.domain.EnrichedTransaction;
import com.asp.platform.enrichment.merchant.MerchantFuzzyMatcher;
import com.asp.platform.enrichment.merchant.MerchantNormalizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Test suite for fuzzy matching metadata in EnrichedTransactionMapper.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EnrichedTransactionMapper Fuzzy Metadata Tests")
class EnrichedTransactionMapperFuzzyMetadataTest {

    @Mock
    private CategoryClassifierService categoryClassifierService;

    @Mock
    private MerchantNormalizationService merchantNormalizationService;

    @Mock
    private MerchantFuzzyMatcher fuzzyMatcher;

    private EnrichedTransactionMapper mapper;

    private NormalizedTransaction normalizedTransaction;
    private NormalizedAccount account;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        mapper = new EnrichedTransactionMapper(
            categoryClassifierService,
            merchantNormalizationService,
            fuzzyMatcher
        );

        // Setup test data
        tenant = new Tenant();
        tenant.setId(UUID.randomUUID());

        account = new NormalizedAccount();
        account.setId(UUID.randomUUID());

        normalizedTransaction = NormalizedTransaction.builder()
            .transactionId(UUID.randomUUID())
            .tenant(tenant)
            .account(account)
            .userRef("user-123")
            .date(LocalDate.now())
            .rawDescription("02/09 BOOST COFFE GHANDI CASA 45,00")
            .description("BOOST COFFE GHANDI")
            .amount(new BigDecimal("45.00"))
            .currency("MAD")
            .build();

        // Default category classification
        when(categoryClassifierService.classify(anyString()))
            .thenReturn(CategoryClassificationResult.builder()
                .category("Food & Restaurants")
                .subcategory("Coffee Shop")
                .matchedPattern("BOOST COFFEE")
                .confidence(CategoryClassificationResult.DetectionConfidence.HIGH)
                .build());
    }

    @Test
    @DisplayName("Should store fuzzy metadata when fuzzy match is used")
    void testFuzzyMetadataStored_WhenFuzzyMatchUsed() {
        // Arrange: Intentional misspelling to trigger fuzzy match
        String rawDescription = "02/09 BOOST COFFE GHANDI CASA 45,00";
        String cleaned = "BOOST COFFE GHANDI";
        
        // Mock fuzzy match result
        MerchantFuzzyMatcher.MerchantMatch fuzzyMatch = MerchantFuzzyMatcher.MerchantMatch.builder()
            .canonical("boost_coffee")
            .score(0.95)
            .confidence(MerchantFuzzyMatcher.MatchConfidence.HIGH)
            .build();

        MerchantNormalizationService.MerchantNormalizationResult normResult =
            new MerchantNormalizationService.MerchantNormalizationResult(
                "boost_coffee",
                Optional.of(fuzzyMatch)
            );

        when(merchantNormalizationService.cleanMerchant(rawDescription))
            .thenReturn(cleaned);
        when(merchantNormalizationService.normalize(cleaned))
            .thenReturn(normResult);

        // Act
        EnrichedTransaction enriched = mapper.fromNormalizedTransaction(normalizedTransaction);

        // Assert
        assertThat(enriched).isNotNull();
        assertThat(enriched.getMerchantNormalized()).isEqualTo("boost_coffee");

        Map<String, Object> metadata = enriched.getMetadata();
        assertThat(metadata).isNotNull();
        assertThat(metadata.get("fuzzy_used")).isEqualTo(true);
        assertThat(metadata.get("fuzzy_score")).isEqualTo(0.95);
        assertThat(metadata.get("fuzzy_candidate")).isEqualTo("boost_coffee");
        assertThat(metadata.get("fuzzy_confidence")).isEqualTo("HIGH");

        // Verify category metadata is also present
        assertThat(metadata.get("category_pattern")).isNotNull();
        assertThat(metadata.get("category_confidence")).isNotNull();
    }

    @Test
    @DisplayName("Should store fuzzy_used=false when no fuzzy match")
    void testFuzzyMetadataStored_WhenNoFuzzyMatch() {
        // Arrange: Exact match via alias
        String rawDescription = "02/09 MARJANE HYPER CASA 150,00";
        String cleaned = "MARJANE HYPER";
        
        MerchantNormalizationService.MerchantNormalizationResult normResult =
            new MerchantNormalizationService.MerchantNormalizationResult(
                "marjane",
                Optional.empty()  // No fuzzy match
            );

        when(merchantNormalizationService.cleanMerchant(rawDescription))
            .thenReturn(cleaned);
        when(merchantNormalizationService.normalize(cleaned))
            .thenReturn(normResult);

        // Act
        EnrichedTransaction enriched = mapper.fromNormalizedTransaction(normalizedTransaction);

        // Assert
        assertThat(enriched).isNotNull();
        assertThat(enriched.getMerchantNormalized()).isEqualTo("marjane");

        Map<String, Object> metadata = enriched.getMetadata();
        assertThat(metadata).isNotNull();
        assertThat(metadata.get("fuzzy_used")).isEqualTo(false);
        
        // Verify fuzzy_* keys are NOT present
        assertThat(metadata).doesNotContainKey("fuzzy_score");
        assertThat(metadata).doesNotContainKey("fuzzy_candidate");
        assertThat(metadata).doesNotContainKey("fuzzy_confidence");
    }

    @Test
    @DisplayName("Should merge category and fuzzy metadata correctly")
    void testMetadataMergedCorrectly() {
        // Arrange
        String rawDescription = "CARREFUR MARKET CASA";
        String cleaned = "CARREFUR MARKET";
        
        MerchantFuzzyMatcher.MerchantMatch fuzzyMatch = MerchantFuzzyMatcher.MerchantMatch.builder()
            .canonical("carrefour")
            .score(0.92)
            .confidence(MerchantFuzzyMatcher.MatchConfidence.HIGH)
            .build();

        MerchantNormalizationService.MerchantNormalizationResult normResult =
            new MerchantNormalizationService.MerchantNormalizationResult(
                "carrefour",
                Optional.of(fuzzyMatch)
            );

        when(merchantNormalizationService.cleanMerchant(rawDescription))
            .thenReturn(cleaned);
        when(merchantNormalizationService.normalize(cleaned))
            .thenReturn(normResult);

        // Act
        EnrichedTransaction enriched = mapper.fromNormalizedTransaction(normalizedTransaction);

        // Assert
        Map<String, Object> metadata = enriched.getMetadata();
        
        // Category metadata
        assertThat(metadata.get("category_pattern")).isEqualTo("BOOST COFFEE");
        assertThat(metadata.get("category_confidence")).isEqualTo("HIGH");
        assertThat(metadata.get("merchant_normalized")).isEqualTo("carrefour");
        
        // Fuzzy metadata
        assertThat(metadata.get("fuzzy_used")).isEqualTo(true);
        assertThat(metadata.get("fuzzy_score")).isEqualTo(0.92);
        assertThat(metadata.get("fuzzy_candidate")).isEqualTo("carrefour");
        assertThat(metadata.get("fuzzy_confidence")).isEqualTo("HIGH");
        
        // Both should coexist
        assertThat(metadata.size()).isGreaterThanOrEqualTo(7);
    }

    @Test
    @DisplayName("Should ensure metadata is serializable")
    void testMetadataSerializable() {
        // Arrange
        String rawDescription = "MCDONAL CASA";
        String cleaned = "MCDONAL";
        
        MerchantFuzzyMatcher.MerchantMatch fuzzyMatch = MerchantFuzzyMatcher.MerchantMatch.builder()
            .canonical("macdonalds")
            .score(0.88)
            .confidence(MerchantFuzzyMatcher.MatchConfidence.MEDIUM)
            .build();

        MerchantNormalizationService.MerchantNormalizationResult normResult =
            new MerchantNormalizationService.MerchantNormalizationResult(
                "macdonalds",
                Optional.of(fuzzyMatch)
            );

        when(merchantNormalizationService.cleanMerchant(rawDescription))
            .thenReturn(cleaned);
        when(merchantNormalizationService.normalize(cleaned))
            .thenReturn(normResult);

        // Act
        EnrichedTransaction enriched = mapper.fromNormalizedTransaction(normalizedTransaction);

        // Assert
        Map<String, Object> metadata = enriched.getMetadata();
        assertThat(metadata).isNotNull();
        
        // Verify all values are serializable types
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            Object value = entry.getValue();
            assertThat(value).isInstanceOfAny(
                String.class,
                Boolean.class,
                Double.class,
                Integer.class,
                Long.class
            );
            
            // No Optional, no null values (except for category_pattern which can be null)
            if (!"category_pattern".equals(entry.getKey())) {
                assertThat(value).isNotNull();
            }
        }
        
        // Verify specific types
        assertThat(metadata.get("fuzzy_used")).isInstanceOf(Boolean.class);
        assertThat(metadata.get("fuzzy_score")).isInstanceOf(Double.class);
        assertThat(metadata.get("fuzzy_candidate")).isInstanceOf(String.class);
        assertThat(metadata.get("fuzzy_confidence")).isInstanceOf(String.class);
    }

    @Test
    @DisplayName("Should handle MEDIUM confidence fuzzy match")
    void testMediumConfidenceFuzzyMatch() {
        // Arrange
        String rawDescription = "MARJAN CASA";
        String cleaned = "MARJAN";
        
        MerchantFuzzyMatcher.MerchantMatch fuzzyMatch = MerchantFuzzyMatcher.MerchantMatch.builder()
            .canonical("marjane")
            .score(0.85)
            .confidence(MerchantFuzzyMatcher.MatchConfidence.MEDIUM)
            .build();

        MerchantNormalizationService.MerchantNormalizationResult normResult =
            new MerchantNormalizationService.MerchantNormalizationResult(
                "marjane",
                Optional.of(fuzzyMatch)
            );

        when(merchantNormalizationService.cleanMerchant(rawDescription))
            .thenReturn(cleaned);
        when(merchantNormalizationService.normalize(cleaned))
            .thenReturn(normResult);

        // Act
        EnrichedTransaction enriched = mapper.fromNormalizedTransaction(normalizedTransaction);

        // Assert
        Map<String, Object> metadata = enriched.getMetadata();
        assertThat(metadata.get("fuzzy_used")).isEqualTo(true);
        assertThat(metadata.get("fuzzy_score")).isEqualTo(0.85);
        assertThat(metadata.get("fuzzy_confidence")).isEqualTo("MEDIUM");
    }

    @Test
    @DisplayName("Should handle null normalized merchant gracefully")
    void testNullNormalizedMerchant() {
        // Arrange
        String rawDescription = "";
        
        when(merchantNormalizationService.cleanMerchant(rawDescription))
            .thenReturn(null);
        when(merchantNormalizationService.normalize(null))
            .thenReturn(null);

        normalizedTransaction.setRawDescription("");
        normalizedTransaction.setDescription("");

        // Act
        EnrichedTransaction enriched = mapper.fromNormalizedTransaction(normalizedTransaction);

        // Assert
        assertThat(enriched).isNotNull();
        assertThat(enriched.getMerchantNormalized()).isNull();
        
        Map<String, Object> metadata = enriched.getMetadata();
        assertThat(metadata).isNotNull();
        assertThat(metadata.get("fuzzy_used")).isEqualTo(false);
    }

    @Test
    @DisplayName("Should ensure metadata is always non-null")
    void testMetadataAlwaysNonNull() {
        // Arrange
        String rawDescription = "TEST MERCHANT";
        String cleaned = "TEST MERCHANT";
        
        MerchantNormalizationService.MerchantNormalizationResult normResult =
            new MerchantNormalizationService.MerchantNormalizationResult(
                "test_merchant",
                Optional.empty()
            );

        when(merchantNormalizationService.cleanMerchant(rawDescription))
            .thenReturn(cleaned);
        when(merchantNormalizationService.normalize(cleaned))
            .thenReturn(normResult);

        // Act
        EnrichedTransaction enriched = mapper.fromNormalizedTransaction(normalizedTransaction);

        // Assert
        assertThat(enriched.getMetadata()).isNotNull();
        assertThat(enriched.getMetadata()).isNotEmpty();
    }
}

