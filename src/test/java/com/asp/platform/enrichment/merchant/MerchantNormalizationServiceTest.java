package com.asp.platform.enrichment.merchant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MerchantNormalizationServiceTest {

    private MerchantNormalizationService service;

    @BeforeEach
    void setUp() {
        service = new MerchantNormalizationService();
        service.overrideRules(new MerchantNormalizationService.MerchantRulesConfig(
            Collections.emptyList(),
            Collections.emptyMap()
        ));
    }

    @Test
    void shouldCleanAndNormalizeMerchant() {
        String raw = "02/0902/09 PAIEMENT PAR CARTE 0332 BOOST COFFEE GHANDI 25,00";

        String cleaned = service.cleanMerchant(raw);
        assertThat(cleaned).isEqualTo("BOOST COFFEE GHANDI");

        String normalized = service.normalizeMerchant(cleaned);
        assertThat(normalized).isEqualTo("boost_coffee_ghandi");
    }
}

