package com.servicepros.customer;

import java.time.Instant;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class CustomerDtos {

    private CustomerDtos() {
    }

    public record CustomerRequest(@NotBlank @Size(max = 120) String name,
                                  @NotBlank @Size(max = 40)
                                  @Pattern(regexp = "^[+()\\d\\s.-]{7,40}$", message = "is not a valid phone number")
                                  String phone,
                                  @Email @Size(max = 160) String email,
                                  @NotBlank @Size(max = 255) String address,
                                  @NotBlank @Size(max = 80) String city,
                                  @DecimalMin("-90") @DecimalMax("90") Double lat,
                                  @DecimalMin("-180") @DecimalMax("180") Double lng,
                                  // the address picked from the suggestions (Google place); required when the address
                                  // is new or changed and address look-up is set up — address + coordinates come from it
                                  @Size(max = 300) String placeId,
                                  @Size(max = 100) String placeSession) {
    }

    public record CustomerResponse(Long id, String name, String phone, String email, String address, String city,
                                   double lat, double lng, Instant createdAt) {
        public static CustomerResponse from(Customer c) {
            return new CustomerResponse(c.getId(), c.getName(), c.getPhone(), c.getEmail(), c.getAddress(), c.getCity(),
                    c.getLat(), c.getLng(), c.getCreatedAt());
        }
    }

    public record CustomerOption(Long id, String name, String address, String city) {
        public static CustomerOption from(Customer c) {
            return new CustomerOption(c.getId(), c.getName(), c.getAddress(), c.getCity());
        }
    }
}
