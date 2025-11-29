package com.asp.platform.enrichment.merchant;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.Yaml;

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
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class MerchantNormalizationService {

    private final MerchantFuzzyMatcher fuzzyMatcher;

    private static final List<String> DEFAULT_NOISE_TOKENS = List.of(
        "PAIEMENT", "PAR", "CARTE", "ECOM", "NATIONAL", "INTERNATIONAL",
        "CIH", "GAB", "MAROC", "T", "L", "COM", "0332", "PAY", "RECHARGE"
    );

    private static final List<String> CITY_TOKENS = List.of(
        "CASA", "CASABLANCA", "RABAT", "MARRAKECH", "MARRAKESH", "AGADIR", "TANGER", "TANGIER",
        "OUJDA", "FEZ", "FES", "MEKNES", "TETOUAN", "KENITRA", "SAFI", "EL_JADIDA", "BENI_MELLAL",
        "NADOR", "KHOURIBGA", "SETTAT", "LARACHE", "TAZA", "ERRACHIDIA", "BERKANE", "SIDI_SLIMANE",
        "KHEMISSET", "TAOUNATE", "TAROUDANT", "OUARZAZATE", "ESSAOUIRA", "KHENIFRA", "BERRECHID",
        "FNIDEQ", "MARTIL", "ASILAH", "CHEFCHAOUEN", "IFRANE", "AZROU", "MIDELT", "FIGUIG",
        "BOUDNIB", "RISSANI", "TINGHIR", "ZAGORA", "TATA", "GUELMIM", "SMARA", "LAAYOUNE",
        "DAKHLA", "AKHFENIR", "TARFAYA", "TAN_TAN", "SIDI_IFNI", "TIZNIT", "MOHAMMEDIA"
    );

    private static final List<String> COUNTRY_TOKENS = List.of(
        "MA", "MAROC", "MOROCCO", "MAR"
    );

    private static final List<String> PAYMENT_TOKENS = List.of(
        "POS", "ECOM", "ATM", "GAB", "TPE", "CARD", "DEBIT", "CREDIT"
    );

    private static final Pattern AMOUNT_PATTERN = Pattern.compile("\\b\\d{1,3}(?:[\\s.,]\\d{3})*(?:[.,]\\d{2})\\b");
    private static final Pattern DATE_PREFIX_PATTERN = Pattern.compile("^\\d{2}/\\d{2}\\S*\\s+");
    private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s+");
    private static final Pattern MULTIPLE_HYPHENS = Pattern.compile("-{2,}");
    private static final Pattern MULTIPLE_UNDERSCORES = Pattern.compile("_{2,}");
    private static final Pattern NON_ALPHANUM_UNDERSCORE = Pattern.compile("[^a-z0-9_]");

    private static final String RULES_PATH = "enrichment/merchant_rules.yml";

    private final Yaml yaml = new Yaml();

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

    @SuppressWarnings("unchecked")
    private MerchantRulesConfig readConfig() {
        ClassPathResource resource = new ClassPathResource(RULES_PATH);
        if (!resource.exists()) {
            return null;
        }
        try (InputStream inputStream = resource.getInputStream()) {
            Object loaded = yaml.load(inputStream);
            if (loaded == null) {
                return null;
            }

            MerchantRulesConfig config = new MerchantRulesConfig();
            if (loaded instanceof Map<?, ?> map) {
                Object tokens = map.get("ignore_tokens");
                if (tokens instanceof List<?> list) {
                    List<String> cleaned = new ArrayList<>();
                    for (Object item : list) {
                        if (item != null) {
                            cleaned.add(item.toString().toUpperCase(Locale.ROOT));
                        }
                    }
                    config.setIgnoreTokens(cleaned);
                }

                Object aliasesObj = map.get("merchant_aliases");
                if (aliasesObj instanceof Map<?, ?> aliasMap) {
                    Map<String, List<String>> parsed = new HashMap<>();
                    for (Map.Entry<?, ?> entry : aliasMap.entrySet()) {
                        String key = entry.getKey() != null ? entry.getKey().toString() : null;
                        if (!StringUtils.hasText(key)) {
                            continue;
                        }
                        Object value = entry.getValue();
                        List<String> aliasList = new ArrayList<>();
                        if (value instanceof List<?> values) {
                            for (Object alias : values) {
                                if (alias != null) {
                                    aliasList.add(alias.toString());
                                }
                            }
                        }
                        parsed.put(key, aliasList);
                    }
                    config.setMerchantAliases(parsed);
                }
            }
            return config;
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
        if (config.getIgnoreTokens() != null) {
            noiseTokens.addAll(config.getIgnoreTokens());
        }
        merchantAliases = config.getMerchantAliases() != null
            ? new HashMap<>(config.getMerchantAliases())
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

        log.debug("[MERCHANT] raw=\"{}\"", rawDescription);

        // Normalize Unicode and uppercase
        String working = normalizeUnicode(rawDescription.toUpperCase(Locale.ROOT));

        // Remove date prefixes
        String cleaned = DATE_PREFIX_PATTERN.matcher(working).replaceFirst("");

        // Remove amounts
        cleaned = AMOUNT_PATTERN.matcher(cleaned).replaceAll(" ");

        // Remove city tokens
        cleaned = removeCityTokens(cleaned);

        // Remove country tokens
        cleaned = removeCountryTokens(cleaned);

        // Remove payment tokens
        cleaned = removePaymentTokens(cleaned);

        // Remove noise tokens
        for (String token : noiseTokens) {
            cleaned = cleaned.replaceAll("\\b" + Pattern.quote(token) + "\\b", " ");
        }

        // Normalize multiple hyphens and underscores
        cleaned = MULTIPLE_HYPHENS.matcher(cleaned).replaceAll("-");
        cleaned = MULTIPLE_UNDERSCORES.matcher(cleaned).replaceAll("_");

        // Collapse spaces and trim
        cleaned = squeezeSpaces(cleaned);

        log.debug("[MERCHANT] cleaned=\"{}\"", cleaned);
        return cleaned.isBlank() ? null : cleaned;
    }

    public String normalizeMerchant(String cleaned) {
        if (!StringUtils.hasText(cleaned)) {
            return null;
        }

        // Convert to lowercase and normalize Unicode
        String lower = cleaned.toLowerCase(Locale.ROOT);
        String normalized = normalizeUnicode(lower);

        // Replace spaces with underscores
        normalized = normalized.replace(' ', '_');

        // Remove non-alphanumeric characters (keep underscores)
        normalized = NON_ALPHANUM_UNDERSCORE.matcher(normalized).replaceAll("");

        // Normalize multiple underscores
        normalized = MULTIPLE_UNDERSCORES.matcher(normalized).replaceAll("_");

        // Apply merchant alias mapping
        String canonical = resolveAlias(normalized);

        // If alias resolution didn't find a match, try fuzzy matching
        if (canonical.equals(normalized)) {
            Optional<MerchantFuzzyMatcher.MerchantMatch> fuzzyMatch = fuzzyMatcher.match(normalized);
            if (fuzzyMatch.isPresent()) {
                MerchantFuzzyMatcher.MerchantMatch match = fuzzyMatch.get();
                log.debug("[MERCHANT] fuzzy match: \"{}\" -> \"{}\" (score={}, confidence={})",
                    normalized, match.getCanonical(), 
                    String.format("%.3f", match.getScore()), match.getConfidence());
                canonical = match.getCanonical();
            }
        }

        log.debug("[MERCHANT] normalized=\"{}\"", canonical);
        return canonical;
    }

    private String resolveAlias(String normalized) {
        if (merchantAliases.isEmpty() || !StringUtils.hasText(normalized)) {
            return normalized;
        }

        for (Map.Entry<String, List<String>> entry : merchantAliases.entrySet()) {
            for (String alias : entry.getValue()) {
                // Normalize alias for comparison
                String normalizedAlias = normalizeUnicode(alias.toLowerCase(Locale.ROOT))
                    .replace(' ', '_');
                normalizedAlias = NON_ALPHANUM_UNDERSCORE.matcher(normalizedAlias).replaceAll("");
                
                if (normalized.equalsIgnoreCase(normalizedAlias)) {
                    return entry.getKey();
                }
            }
        }
        return normalized;
    }

    /**
     * Remove city tokens from merchant description.
     */
    private String removeCityTokens(String text) {
        String result = text;
        for (String city : CITY_TOKENS) {
            result = result.replaceAll("\\b" + Pattern.quote(city) + "\\b", " ");
        }
        return result;
    }

    /**
     * Remove country tokens from merchant description.
     */
    private String removeCountryTokens(String text) {
        String result = text;
        for (String country : COUNTRY_TOKENS) {
            result = result.replaceAll("\\b" + Pattern.quote(country) + "\\b", " ");
        }
        return result;
    }

    /**
     * Remove payment-related tokens from merchant description.
     */
    private String removePaymentTokens(String text) {
        String result = text;
        for (String payment : PAYMENT_TOKENS) {
            result = result.replaceAll("\\b" + Pattern.quote(payment) + "\\b", " ");
        }
        return result;
    }

    /**
     * Collapse multiple spaces into single space.
     */
    private String squeezeSpaces(String text) {
        return MULTIPLE_SPACES.matcher(text).replaceAll(" ").trim();
    }

    /**
     * Normalize Unicode characters (remove accents and diacritics).
     */
    private String normalizeUnicode(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "");
    }

    @Data
    public static class MerchantRulesConfig {
        private List<String> ignoreTokens = new ArrayList<>();
        private Map<String, List<String>> merchantAliases = new HashMap<>();
    }
}

