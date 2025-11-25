package com.asp.platform.enrichment.mapper;

import com.asp.domain.NormalizedAccount;
import com.asp.domain.NormalizedTransaction;
import com.asp.domain.NormalizedTransaction;
import com.asp.platform.enrichment.category.CategoryClassificationResult;
import com.asp.platform.enrichment.category.CategoryClassifierService;
import com.asp.platform.enrichment.domain.EnrichedTransaction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;

@Component
public class EnrichedTransactionMapper {

    private final CategoryClassifierService categoryClassifierService;

    public EnrichedTransactionMapper(CategoryClassifierService categoryClassifierService) {
        this.categoryClassifierService = categoryClassifierService;
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
            .merchantName(null)
            .merchantNormalized(null)
            .salary(false)
            .recurring(false)
            .recurringGroupId(null)
            .direction(direction)
            .amount(amount)
            .amountAbs(absoluteAmount)
            .currency(normalizedTransaction.getCurrency())
            .metadata(buildMetadata(classification))
            .build();
    }

    private HashMap<String, Object> buildMetadata(CategoryClassificationResult classification) {
        HashMap<String, Object> metadata = new HashMap<>();
        metadata.put("category_pattern", classification.getMatchedPattern());
        metadata.put("category_confidence", classification.getConfidence().name());
        return metadata;
    }
}

