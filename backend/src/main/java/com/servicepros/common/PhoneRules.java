package com.servicepros.common;

import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * Country calling code + national number checks (admin contact mobile, technician phone). Known countries have exact
 * lengths; any other code allows 4 digits up to the E.164 maximum of 15 digits including the code.
 * Keep in sync with {@code COUNTRIES} in admin/src/lib/phone.ts.
 */
public final class PhoneRules {

    private PhoneRules() {
    }

    /** calling code (digits) → allowed national-number lengths */
    private static final Map<String, int[]> LENGTHS = Map.ofEntries(
            Map.entry("1", new int[]{10}),        // US, Canada
            Map.entry("44", new int[]{10}),       // UK
            Map.entry("91", new int[]{10}),       // India
            Map.entry("61", new int[]{9}),        // Australia
            Map.entry("971", new int[]{9}),       // UAE
            Map.entry("65", new int[]{8}),        // Singapore
            Map.entry("60", new int[]{9, 10}),    // Malaysia
            Map.entry("49", new int[]{10, 11}),   // Germany
            Map.entry("33", new int[]{9}),        // France
            Map.entry("86", new int[]{11}),       // China
            Map.entry("81", new int[]{10}),       // Japan
            Map.entry("52", new int[]{10}));      // Mexico

    /** "+1", "1", " +91 " → "+1" / "+91"; 400 (field contactCountryCode) when not 1–3 digits. */
    public static String normalizeCode(String code) {
        return normalizeCode(code, "contactCountryCode");
    }

    /** Same, reporting the error under {@code fieldName}. */
    public static String normalizeCode(String code, String fieldName) {
        String d = code == null ? "" : code.trim().replaceFirst("^\\+", "");
        if (!d.matches("[1-9]\\d{0,2}")) {
            throw field(fieldName, "Enter a country code, e.g. 1 for the US");
        }
        return "+" + d;
    }

    /** Digits only, with the right length for the country; 400 (field contactPhone) otherwise. */
    public static String normalizeNumber(String normalizedCode, String number) {
        return normalizeNumber(normalizedCode, number, "contactPhone");
    }

    /** Same, reporting the error under {@code fieldName}. */
    public static String normalizeNumber(String normalizedCode, String number, String fieldName) {
        String n = number == null ? "" : number.trim();
        if (!n.matches("\\d+")) {
            throw field(fieldName, "Enter digits only");
        }
        String code = normalizedCode.substring(1);
        int[] allowed = LENGTHS.get(code);
        if (allowed != null) {
            for (int len : allowed) {
                if (n.length() == len) return n;
            }
            throw field(fieldName, "Mobile number for +" + code + " must have " + describe(allowed) + " digits");
        }
        int max = 15 - code.length();
        if (n.length() < 4 || n.length() > max) {
            throw field(fieldName, "Mobile number must have 4 to " + max + " digits");
        }
        return n;
    }

    /** "+1" + "2815550100" → "+1 (281) 555-0100"; "+91" + "7810894925" → "+91 7810894925"; no code → as stored. */
    public static String display(String code, String number) {
        if (number == null || number.isBlank()) return null;
        if ("+1".equals(code) && number.matches("\\d{10}")) {
            return "+1 (" + number.substring(0, 3) + ") " + number.substring(3, 6) + "-" + number.substring(6);
        }
        return code == null ? number : code + " " + number;
    }

    private static String describe(int[] lengths) {
        return lengths.length == 1 ? String.valueOf(lengths[0]) : lengths[0] + " or " + lengths[1];
    }

    private static ApiException field(String field, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message, Map.of(field, message));
    }
}
