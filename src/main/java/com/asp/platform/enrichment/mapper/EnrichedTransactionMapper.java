package com.asp.platform.enrichment.mapper;

import com.asp.domain.NormalizedAccount;
import com.asp.domain.NormalizedTransaction;
import com.asp.platform.enrichment.category.CategoryClassificationResult;
import com.asp.platform.enrichment.category.CategoryClassifierService;
import com.asp.platform.enrichment.domain.EnrichedTransaction;
import com.asp.platform.enrichment.merchant.MerchantFuzzyMatcher;
import com.asp.platform.enrichment.merchant.MerchantNormalizationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Optional;

@Component
@Slf4j
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
        
        // Get normalization result with fuzzy matching information
        MerchantNormalizationService.MerchantNormalizationResult normResult = 
            merchantNormalizationService.normalize(cleanedMerchant);
        
        String normalizedMerchant = normResult != null ? normResult.canonical() : null;
        Optional<MerchantFuzzyMatcher.MerchantMatch> fuzzyMatch = 
            normResult != null ? normResult.fuzzyMatch() : Optional.empty();

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
        
        // Category metadata
        metadata.put("category_pattern", classification.getMatchedPattern());
        metadata.put("category_confidence", classification.getConfidence().name());
        metadata.put("merchant_normalized", normalizedMerchant);
        
        // Fuzzy matching metadata
        if (fuzzyMatch.isPresent()) {
            MerchantFuzzyMatcher.MerchantMatch match = fuzzyMatch.get();
            metadata.put("fuzzy_used", true);
            metadata.put("fuzzy_score", match.getScore());
            metadata.put("fuzzy_candidate", match.getCanonical());
            metadata.put("fuzzy_confidence", match.getConfidence().name());
            
            log.debug("[ENRICH][FUZZY] canonical={}, score={}, confidence={}",
                match.getCanonical(), match.getScore(), match.getConfidence());
        } else {
            metadata.put("fuzzy_used", false);
            log.debug("[ENRICH][FUZZY] No fuzzy match used.");
        }
        
        return metadata;
    }
}

