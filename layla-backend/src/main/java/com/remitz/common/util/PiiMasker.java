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

    /** Masks a person's name to its first initial: "John Smith" → "J***". */
    public static String maskName(String name) {
        if (name == null || name.isBlank()) return "***";
        return name.trim().charAt(0) + "***";
    }

    /** Non-reversible fingerprint for a secret/token — logs only its length, never the value. */
    public static String maskToken(String token) {
        if (token == null) return "null";
        return "len=" + token.length();
    }

    /** Never log a date of birth value. */
    public static String maskDob(Object dob) {
        return dob == null ? "null" : "[redacted]";
    }
}
