package com.asp.parsing.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;

/**
 * Registry for managing bank parsers.
 */
@Component
@Slf4j
public class ParserRegistry {

    private final Map<String, ParserStrategy> parsers = new HashMap<>();
    private final List<ParserStrategy> parserStrategies;

    public ParserRegistry(List<ParserStrategy> parserStrategies) {
        this.parserStrategies = parserStrategies != null ? parserStrategies : new ArrayList<>();
    }

    @PostConstruct
    public void initialize() {
        log.info("Initializing parser registry with {} parsers", parserStrategies.size());
        for (ParserStrategy parser : parserStrategies) {
            // Hard-register CIH parser if found
            if (parser.getClass().getSimpleName().toLowerCase().contains("cih")) {
                parsers.put("cih", parser);
                log.info("Hard-registered CIH parser: {}", parser.getParserName());
            }
            
            // Register parser for each bank it can handle
            // Check common bank identifiers
            String[] bankIdentifiers = {"cih", "attijari", "bmce", "bp"};
            for (String bankId : bankIdentifiers) {
                if (parser.canHandle(bankId)) {
                    parsers.put(bankId, parser);
                    log.info("Registered parser: {} for bank: {}", parser.getParserName(), bankId);
                }
            }
        }
        log.info("Parser registry initialized with {} registered parsers", parsers.size());
    }

    /**
     * Gets the parser for the given bank identifier.
     *
     * @param bankIdentifier the bank identifier (slug)
     * @return the parser strategy, or null if not found
     */
    public ParserStrategy getParser(String bankIdentifier) {
        if (bankIdentifier == null) {
            return null;
        }
        return parsers.get(bankIdentifier.toLowerCase());
    }

    /**
     * Registers a parser for a bank identifier.
     *
     * @param bankIdentifier the bank identifier
     * @param parser the parser strategy
     */
    public void registerParser(String bankIdentifier, ParserStrategy parser) {
        parsers.put(bankIdentifier.toLowerCase(), parser);
        log.info("Registered parser {} for bank: {}", parser.getParserName(), bankIdentifier);
    }

    /**
     * Gets list of supported bank identifiers.
     *
     * @return list of bank slugs
     */
    public List<String> getSupportedBanks() {
        return new ArrayList<>(parsers.keySet());
    }

    /**
     * Checks if a bank is supported.
     *
     * @param bankIdentifier the bank identifier
     * @return true if supported
     */
    public boolean isSupported(String bankIdentifier) {
        return bankIdentifier != null && parsers.containsKey(bankIdentifier.toLowerCase());
    }
}

