package com.servicepros.settings;

import com.servicepros.superadmin.AdminAccountDtos.CardSummary;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class SettingsDtos {

    private SettingsDtos() {
    }

    /**
     * The admin's company settings plus the details the Super Admin entered when creating the admin.
     * Everything is editable here except the login email; the card is returned masked.
     */
    public record SettingsResponse(
            String companyName, String officeAddress,
            Double officeLat, Double officeLng, String timezone,
            String googleReviewUrl, String facebookReviewUrl,
            String contactName, String contactCountryCode, String contactPhone, String loginEmail, CardSummary card,
            boolean smsConfigured, boolean googleMapsConfigured, boolean mailConfigured) {
    }

    /**
     * What an admin may change (everything except the login email). The contact / card fields are optional so
     * older clients keep working; the Settings page always sends them. A blank card number keeps the saved card.
     */
    public record SettingsRequest(
            @NotBlank @Size(max = 120) String companyName,
            @NotBlank @Size(max = 255) String officeAddress,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double officeLat,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double officeLng,
            String timezone,
            @Size(max = 255) String googleReviewUrl,
            @Size(max = 255) String facebookReviewUrl,
            @Pattern(regexp = "^$|^\\+?[1-9]\\d{0,2}$", message = "Enter a country code, e.g. 1 for the US")
            String contactCountryCode,
            @Pattern(regexp = "^$|^\\d{4,14}$", message = "Enter digits only") String contactPhone,
            @Size(max = 120) String contactName,
            @Size(max = 120) String cardHolder,
            @Pattern(regexp = "^$|^[\\d\\s-]{13,23}$", message = "Enter a valid card number") String cardNumber,
            @Pattern(regexp = "^$|^(0[1-9]|1[0-2])/\\d{2}$", message = "Enter the expiry as MM/YY") String cardExpiry,
            // the depot address picked from the suggestions (Google place); required when the address changes
            // and address look-up is set up — address + coordinates then come from Google
            @Size(max = 300) String officePlaceId,
            @Size(max = 100) String officePlaceSession) {
    }
}
