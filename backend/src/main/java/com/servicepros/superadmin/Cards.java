package com.servicepros.superadmin;

import java.time.YearMonth;
import java.util.Map;

import com.servicepros.common.ApiException;
import org.springframework.http.HttpStatus;

/** Card number / expiry checks. Only the brand and last 4 digits leave this class. */
final class Cards {

    private Cards() {
    }

    record MaskedCard(String brand, String last4) {
    }

    /** Validates the number (length + Luhn checksum) and returns its brand and last 4 digits. */
    static MaskedCard mask(String number) {
        String d = number == null ? "" : number.replaceAll("[\\s-]", "");
        if (!d.matches("\\d{13,19}") || !luhn(d)) {
            throw field("cardNumber", "Enter a valid card number");
        }
        String brand = brand(d);
        int[] lengths = lengths(brand);
        boolean lengthOk = false;
        for (int len : lengths) lengthOk |= d.length() == len;
        if (!lengthOk) {
            throw field("cardNumber", brand + " card numbers have " + describe(lengths) + " digits");
        }
        return new MaskedCard(brand, d.substring(d.length() - 4));
    }

    /** "MM/YY" → YearMonth; must not be in the past. */
    static YearMonth expiry(String mmYy) {
        String s = mmYy == null ? "" : mmYy.trim();
        if (!s.matches("(0[1-9]|1[0-2])/\\d{2}")) {
            throw field("cardExpiry", "Enter the card expiry as MM/YY");
        }
        YearMonth ym = YearMonth.of(2000 + Integer.parseInt(s.substring(3)), Integer.parseInt(s.substring(0, 2)));
        if (ym.isBefore(YearMonth.now())) {
            throw field("cardExpiry", "This card has expired");
        }
        return ym;
    }

    /** Valid lengths per brand. Keep in sync with cardLengths() in admin/src/app/super/page.tsx. */
    static int[] lengths(String brand) {
        return switch (brand) {
            case "Amex" -> new int[]{15};
            case "Mastercard" -> new int[]{16};
            case "Visa" -> new int[]{13, 16, 19};
            case "Diners" -> new int[]{14, 16, 19};
            default -> new int[]{16, 17, 18, 19};
        };
    }

    private static String describe(int[] lengths) {
        if (lengths.length == 1) return String.valueOf(lengths[0]);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < lengths.length; i++) {
            if (i > 0) b.append(i == lengths.length - 1 ? " or " : ", ");
            b.append(lengths[i]);
        }
        return b.toString();
    }

    private static ApiException field(String field, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message, Map.of(field, message));
    }

    static boolean luhn(String digits) {
        int sum = 0;
        boolean dbl = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int n = digits.charAt(i) - '0';
            if (dbl) {
                n *= 2;
                if (n > 9) n -= 9;
            }
            sum += n;
            dbl = !dbl;
        }
        return sum % 10 == 0;
    }

    static String brand(String d) {
        if (d.startsWith("4")) return "Visa";
        if (d.matches("^(5[1-5]|2(2[2-9]|[3-6]\\d|7[01]|720)).*")) return "Mastercard";
        if (d.matches("^3[47].*")) return "Amex";
        if (d.matches("^(6011|65|64[4-9]).*")) return "Discover";
        if (d.matches("^35(2[89]|[3-8]\\d).*")) return "JCB";
        if (d.matches("^3(0[0-5]|[689]).*")) return "Diners";
        return "Card";
    }
}
