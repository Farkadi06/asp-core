package com.asp.platform.enrichment.merchant;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@Slf4j
public class MerchantNormalizationService {

    private static final List<String> DEFAULT_NOISE_TOKENS = List.of(
        "PAIEMENT", "PAR", "CARTE", "ECOM", "NATIONAL", "INTERNATIONAL",
        "CIH", "GAB", "MAROC", "T", "L", "COM", "0332", "PAY", "RECHARGE"
    );

    private static final Pattern AMOUNT_PATTERN = Pattern.compile("\\b\\d{1,3}(?:[\\s.,]\\d{3})*(?:[.,]\\d{2})\\b");
    private static final Pattern DATE_PREFIX_PATTERN = Pattern.compile("^\\d{2}/\\d{2}\\S*\\s+");
    private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s+");
    private static final Pattern NON_ALPHANUM_UNDERSCORE = Pattern.compile("[^a-z0-9_]");

    private static final String RULES_PATH = "enrichment/merchant_rules.yml";

    private final Yaml yaml = new Yaml(new Constructor(MerchantRulesConfig.class, new LoaderOptions()));

    private Set<String> noiseTokens = new HashSet<>(DEFAULT_NOISE_TOKENS);
    private Map<String, List<String>> merchantAliases = new HashMap<>();

    @PostConstruct
    public void loadRules() {
        MerchantRulesConfig config = readConfig();
        if (config != null) {
            applyConfig(config);
        } else {
            log.warn("Merchant rules file not found at {}, using default tokens only", RULES_PATH);
        }
    }

    private MerchantRulesConfig readConfig() {
        ClassPathResource resource = new ClassPathResource(RULES_PATH);
        if (!resource.exists()) {
            return null;
        }
        try (InputStream inputStream = resource.getInputStream()) {
            return yaml.load(inputStream);
        } catch (IOException ex) {
            log.error("Unable to read merchant rules from {}", RULES_PATH, ex);
            return null;
        }
    }

    void applyConfig(MerchantRulesConfig config) {
        if (config == null) {
            return;
        }
        noiseTokens = new HashSet<>(DEFAULT_NOISE_TOKENS);
        if (config.ignoreTokens != null) {
            noiseTokens.addAll(config.ignoreTokens);
        }
        merchantAliases = config.merchantAliases != null
            ? new HashMap<>(config.merchantAliases)
            : new HashMap<>();
        log.info("Loaded merchant rules: {} noise tokens, {} merchant aliases",
            noiseTokens.size(), merchantAliases.size());
    }

    public void overrideRules(MerchantRulesConfig config) {
        applyConfig(config);
    }

    public String cleanMerchant(String rawDescription) {
        if (!StringUtils.hasText(rawDescription)) {
            return null;
        }

        log.debug("[MerchantNormalization] Raw description: {}", rawDescription);

        // Remove date prefixes
        String working = Normalizer.normalize(rawDescription.toUpperCase(Locale.ROOT), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "");

        String cleaned = DATE_PREFIX_PATTERN.matcher(working).replaceFirst("");
        cleaned = AMOUNT_PATTERN.matcher(cleaned).replaceAll(" ");

        // Remove noise tokens
        for (String token : noiseTokens) {
            cleaned = cleaned.replaceAll("\\b" + Pattern.quote(token) + "\\b", " ");
        }

        // Collapse spaces and trim
        cleaned = MULTIPLE_SPACES.matcher(cleaned).replaceAll(" ").trim();

        log.debug("[MerchantNormalization] Cleaned merchant: {}", cleaned);
        return cleaned.isBlank() ? null : cleaned;
    }

    public String normalizeMerchant(String cleaned) {
        if (!StringUtils.hasText(cleaned)) {
            return null;
        }

        String lower = cleaned.toLowerCase(Locale.ROOT);
        String normalized = Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replace(' ', '_');
        normalized = NON_ALPHANUM_UNDERSCORE.matcher(normalized).replaceAll("");

        // Apply merchant alias mapping
        String canonical = resolveAlias(normalized);

        log.debug("[MerchantNormalization] Normalized merchant: {}", canonical);
        return canonical;
    }

    private String resolveAlias(String normalized) {
        if (merchantAliases.isEmpty() || !StringUtils.hasText(normalized)) {
            return normalized;
        }

        for (Map.Entry<String, List<String>> entry : merchantAliases.entrySet()) {
            for (String alias : entry.getValue()) {
                if (normalized.equalsIgnoreCase(alias)) {
                    return entry.getKey();
                }
            }
        }
        return normalized;
    }

    public record MerchantRulesConfig(
        List<String> ignoreTokens,
        Map<String, List<String>> merchantAliases
    ) {}
}

