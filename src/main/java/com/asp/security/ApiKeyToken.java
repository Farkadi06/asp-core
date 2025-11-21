package com.asp.security;

import lombok.Getter;

public class ApiKeyToken {
    private static final String PREFIX = "asp_";
    private static final String LIVE_PREFIX = "asp_live_";
    private static final String SANDBOX_PREFIX = "asp_sandbox_";

    @Getter
    private final String prefix;
    @Getter
    private final String secret;
    @Getter
    private final boolean sandbox;

    private ApiKeyToken(String prefix, String secret, boolean sandbox) {
        this.prefix = prefix;
        this.secret = secret;
        this.sandbox = sandbox;
    }

    public static ApiKeyToken parse(String rawKey) {
        if (rawKey == null || rawKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API key cannot be null or empty");
        }

        rawKey = rawKey.trim();

        boolean isSandbox = false;
        String expectedPrefix;
        if (rawKey.startsWith(LIVE_PREFIX)) {
            expectedPrefix = LIVE_PREFIX;
            isSandbox = false;
        } else if (rawKey.startsWith(SANDBOX_PREFIX)) {
            expectedPrefix = SANDBOX_PREFIX;
            isSandbox = true;
        } else if (rawKey.startsWith(PREFIX)) {
            // For backward compatibility, treat as live if just "asp_"
            expectedPrefix = PREFIX;
            isSandbox = false;
        } else {
            throw new IllegalArgumentException("Invalid API key format. Must start with 'asp_live_' or 'asp_sandbox_'");
        }

        String keyWithoutPrefix = rawKey.substring(expectedPrefix.length());
        String[] parts = keyWithoutPrefix.split("_", 2);

        if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
            throw new IllegalArgumentException("Invalid API key format. Expected: asp_live_<prefix>_<secret>");
        }

        return new ApiKeyToken(parts[0], parts[1], isSandbox);
    }
}

