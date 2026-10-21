package com.servicepros.technician;

import com.servicepros.common.PhoneRules;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class TechnicianDtos {

    private TechnicianDtos() {
    }

    /**
     * Create / update a technician together with their app login (email + password).
     * {@code password} is required on create; on update a blank value keeps the current one.
     */
    public record TechnicianRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Pattern(regexp = "^\\+?[1-9]\\d{0,2}$", message = "Enter a country code, e.g. 1 for the US")
            String phoneCountryCode,
            @NotBlank @Pattern(regexp = "^\\d{4,14}$", message = "Enter digits only") String phone,
            @NotBlank @Email(message = "Enter a valid email")
            @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[A-Za-z]{2,}$", message = "Enter a valid email")
            @Size(max = 160) String email,
            @Size(max = 200) String skills,
            @Size(min = 8, max = 100, message = "must be at least 8 characters") String password) {
    }

    /**
     * {@code phone} is ready to show ("+1 (281) 555-0100"); the edit form uses {@code phoneCountryCode} +
     * {@code phoneNumber} (digits).
     */
    public record TechnicianResponse(Long id, String name, String phone, String phoneCountryCode, String phoneNumber,
                                     String email, String skills, boolean hasLogin) {
        public static TechnicianResponse from(Technician t, boolean hasLogin) {
            return new TechnicianResponse(t.getId(), t.getName(), PhoneRules.display(t.getPhoneCountryCode(), t.getPhone()),
                    t.getPhoneCountryCode(), t.getPhone(), t.getEmail(), t.getSkills(), hasLogin);
        }
    }

    public record TechnicianRef(Long id, String name) {
        public static TechnicianRef from(Technician t) {
            return new TechnicianRef(t.getId(), t.getName());
        }
    }
}
