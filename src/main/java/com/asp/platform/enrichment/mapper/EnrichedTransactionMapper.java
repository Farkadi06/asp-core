package com.asp.platform.enrichment.mapper;

import com.asp.domain.NormalizedAccount;
import com.asp.domain.NormalizedTransaction;
import com.asp.platform.enrichment.category.CategoryClassificationResult;
import com.asp.platform.enrichment.category.CategoryClassifierService;
import com.asp.platform.enrichment.domain.EnrichedTransaction;
import com.asp.platform.enrichment.merchant.MerchantFuzzyMatcher;
import com.asp.platform.enrichment.merchant.MerchantNormalizationService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Optional;

@Component
public class EnrichedTransactionMapper {

    private final CategoryClassifierService categoryClassifierService;
    private final MerchantNormalizationService merchantNormalizationService;
    private final MerchantFuzzyMatcher fuzzyMatcher;

    public EnrichedTransactionMapper(CategoryClassifierService categoryClassifierService,
                                     MerchantNormalizationService merchantNormalizationService,
                                     MerchantFuzzyMatcher fuzzyMatcher) {
        this.categoryClassifierService = categoryClassifierService;
        this.merchantNormalizationService = merchantNormalizationService;
        this.fuzzyMatcher = fuzzyMatcher;
    }

    public EnrichedTransaction fromNormalizedTransaction(NormalizedTransaction normalizedTransaction) {
        if (normalizedTransaction == null) {
            throw new IllegalArgumentException("NormalizedTransaction cannot be null");
        }

        NormalizedAccount account = normalizedTransaction.getAccount();
        String direction = normalizedTransaction.getAmount().signum() >= 0 ? "CREDIT" : "DEBIT";

        String rawDescription = normalizedTransaction.getRawDescription();
        if (rawDescription == null || rawDescription.isBlank()) {
            rawDescription = normalizedTransaction.getDescription();
        }
        if (rawDescription == null) {
            rawDescription = "";
        }

        BigDecimal amount = normalizedTransaction.getAmount();
        BigDecimal absoluteAmount = amount != null ? amount.abs() : BigDecimal.ZERO;

        CategoryClassificationResult classification = categoryClassifierService.classify(rawDescription);
        String cleanedMerchant = merchantNormalizationService.cleanMerchant(rawDescription);
        String normalizedMerchant = merchantNormalizationService.normalizeMerchant(cleanedMerchant);

        // Check if fuzzy matching was used (normalizeMerchant already applies fuzzy matching if needed)
        // We check if the normalized merchant would match via fuzzy matching to determine if it was used
        Optional<MerchantFuzzyMatcher.MerchantMatch> fuzzyMatch = Optional.empty();
        if (normalizedMerchant != null) {
            fuzzyMatch = fuzzyMatcher.match(normalizedMerchant);
            // Only include if it's a fuzzy match (not exact match to canonical)
            if (fuzzyMatch.isPresent() && !fuzzyMatch.get().getCanonical().equals(normalizedMerchant)) {
                // This indicates fuzzy matching was likely used
            } else if (fuzzyMatch.isPresent() && fuzzyMatch.get().getCanonical().equals(normalizedMerchant)) {
                // Exact match - fuzzy matching may not have been used, clear it
                fuzzyMatch = Optional.empty();
            }
        }

        return EnrichedTransaction.builder()
            .normalizedTransaction(normalizedTransaction)
            .tenantId(normalizedTransaction.getTenant().getId())
            .bankConnectionId(account != null ? account.getBankConnection().getId() : null)
            .userRef(normalizedTransaction.getUserRef())
            .txnDate(normalizedTransaction.getDate())
            .descriptionRaw(rawDescription)
            .descriptionClean(null)
            .category(classification.getCategory())
            .subcategory(classification.getSubcategory())
            .merchantName(cleanedMerchant)
            .merchantNormalized(normalizedMerchant)
            .salary(false)
            .recurring(false)
            .recurringGroupId(null)
            .direction(direction)
            .amount(amount)
            .amountAbs(absoluteAmount)
            .currency(normalizedTransaction.getCurrency())
            .metadata(buildMetadata(classification, normalizedMerchant, fuzzyMatch))
            .build();
    }

    private HashMap<String, Object> buildMetadata(CategoryClassificationResult classification,
                                                  String normalizedMerchant,
                                                  Optional<MerchantFuzzyMatcher.MerchantMatch> fuzzyMatch) {
        HashMap<String, Object> metadata = new HashMap<>();
        metadata.put("category_pattern", classification.getMatchedPattern());
        metadata.put("category_confidence", classification.getConfidence().name());
        metadata.put("merchant_normalized", normalizedMerchant);
        
        // Add fuzzy matching metadata if available
        if (fuzzyMatch.isPresent()) {
            MerchantFuzzyMatcher.MerchantMatch match = fuzzyMatch.get();
            metadata.put("fuzzy_score", match.getScore());
            metadata.put("fuzzy_candidate", match.getCanonical());
            metadata.put("fuzzy_confidence", match.getConfidence().name());
        }
        
        return metadata;
    }
}

