package com.servicepros.common;

public final class Phones {

    private Phones() {
    }

    public static String digits(String phone) {
        return phone == null ? "" : phone.replaceAll("\\D", "");
    }

    public static String last10(String phone) {
        String d = digits(phone);
        return d.length() > 10 ? d.substring(d.length() - 10) : d;
    }

    /** US-centric E.164 normalisation: (281) 555-1234 → +12815551234. */
    public static String e164(String phone) {
        if (phone == null) return null;
        String trimmed = phone.trim();
        String d = digits(trimmed);
        if (trimmed.startsWith("+")) return "+" + d;
        if (d.length() == 10) return "+1" + d;
        if (d.length() == 11 && d.startsWith("1")) return "+" + d;
        return "+" + d;
    }
}
