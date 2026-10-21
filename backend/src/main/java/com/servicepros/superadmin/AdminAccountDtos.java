package com.servicepros.superadmin;

import java.time.Instant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AdminAccountDtos {

    private AdminAccountDtos() {
    }

    /**
     * Create / update an admin. {@code password} and {@code cardNumber} are required on create; on update a
     * blank value keeps the current one. The card number is only used to derive brand + last 4 (never stored).
     */
    public record AdminRequest(
            @NotBlank @Size(max = 120) String companyName,
            @NotBlank @Size(max = 120) String contactName,
            @NotBlank @Pattern(regexp = "^\\+?[1-9]\\d{0,2}$", message = "Enter a country code, e.g. 1 for the US")
            String contactCountryCode,
            @NotBlank @Pattern(regexp = "^\\d{4,14}$", message = "Enter digits only") String contactPhone,
            @NotBlank @Email(message = "Enter a valid email")
            @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[A-Za-z]{2,}$", message = "Enter a valid email")
            @Size(max = 160) String email,
            @Size(min = 8, max = 100) String password,
            @NotBlank @Size(max = 120) String cardHolder,
            @Pattern(regexp = "^$|^[\\d\\s-]{13,23}$", message = "Enter a valid card number") String cardNumber,
            @NotBlank @Pattern(regexp = "^(0[1-9]|1[0-2])/\\d{2}$", message = "must be MM/YY") String cardExpiry,
            Boolean active) {
    }

    public record ActiveRequest(@NotNull Boolean active) {
    }

    /** Masked card: brand, last 4 digits, expiry and holder — never the full number. */
    public record CardSummary(String holder, String brand, String last4, Integer expMonth, Integer expYear) {
    }

    public record AdminResponse(Long id, String companyName, String contactName, String contactCountryCode,
                                String contactPhone, String email,
                                boolean active, Instant createdAt, CardSummary card,
                                long technicians, long customers) {
    }
}
