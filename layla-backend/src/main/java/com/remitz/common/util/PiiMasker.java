package com.remitz.common.util;

public final class PiiMasker {
    private PiiMasker() {}

    /**
     * Masks an email: "user@example.com" → "u***@example.com"
     */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        int at = email.indexOf('@');
        if (at <= 1) return email.charAt(0) + "***" + email.substring(at);
        return email.charAt(0) + "***" + email.substring(at);
    }
}
