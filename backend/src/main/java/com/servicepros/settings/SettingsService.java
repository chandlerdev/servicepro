package com.servicepros.settings;

import java.util.Map;

import com.servicepros.common.ApiException;
import com.servicepros.config.AppProperties;
import com.servicepros.notification.MailGateway;
import com.servicepros.settings.SettingsDtos.SettingsRequest;
import com.servicepros.settings.SettingsDtos.SettingsResponse;
import com.servicepros.geo.PlacesService;
import com.servicepros.geo.PlacesService.PlaceDetails;
import com.servicepros.superadmin.AdminProfile;
import com.servicepros.superadmin.AdminProfileService;
import com.servicepros.tenant.TenantResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final CompanySettingsRepository repo;
    private final AppProperties props;
    private final MailGateway mailGateway;
    private final AdminProfileService profiles;
    private final TenantResolver tenants;
    private final PlacesService places;

    /**
     * The logged-in admin's settings (technicians get their admin's). Until the admin saves Settings, the
     * company name comes from what the Super Admin entered.
     */
    @Transactional(readOnly = true)
    public CompanySettings get() {
        return repo.findFirstByOrderByIdAsc().orElseGet(() -> unsaved(currentAdminId()));
    }

    /**
     * Settings of a specific admin — for code running without a logged-in admin (e.g. the SMS webhook,
     * which runs unfiltered).
     */
    @Transactional(readOnly = true)
    public CompanySettings getFor(Long ownerId) {
        return repo.findByOwnerId(ownerId).orElseGet(() -> unsaved(ownerId));
    }

    /** Settings + the admin's own details (company name falls back to what the Super Admin entered). */
    @Transactional(readOnly = true)
    public SettingsResponse read() {
        Long adminId = currentAdminId();
        AdminProfile p = profiles.find(adminId).orElse(null);
        String loginEmail = profiles.loginEmail(adminId);
        CompanySettings s = get();
        return new SettingsResponse(
                firstText(s.getCompanyName(), p == null ? null : p.getCompanyName()),
                s.getOfficeAddress(), s.getOfficeLat(), s.getOfficeLng(), props.getTimezone(),
                s.getGoogleReviewUrl(), s.getFacebookReviewUrl(),
                p == null ? null : p.getContactName(), p == null ? null : p.getContactCountryCode(),
                p == null ? null : p.getContactPhone(), loginEmail, AdminProfileService.card(p),
                props.getTwilio().isConfigured(), props.getGoogle().isConfigured(), mailGateway.isConfigured());
    }

    @Transactional
    public SettingsResponse update(SettingsRequest r) {
        Long adminId = currentAdminId();
        AdminProfile p = profiles.find(adminId).orElse(null);
        // company name + phone are re-checked against every other admin (409 with the field that clashes)
        String contactName = StringUtils.hasText(r.contactName()) ? r.contactName()
                : p != null ? p.getContactName() : profiles.userName(adminId);
        if (StringUtils.hasText(r.contactPhone())) {
            if (!StringUtils.hasText(r.contactCountryCode())) throw field("contactCountryCode", "Enter a country code");
            p = profiles.updateContact(adminId, r.companyName(), contactName, r.contactCountryCode(), r.contactPhone());
        } else if (p != null && StringUtils.hasText(p.getContactPhone())) {
            p = profiles.updateContact(adminId, r.companyName(), contactName,
                    p.getContactCountryCode() == null ? "1" : p.getContactCountryCode(), p.getContactPhone());
        }
        if (StringUtils.hasText(r.cardHolder()) || StringUtils.hasText(r.cardNumber()) || StringUtils.hasText(r.cardExpiry())) {
            p = profiles.updateCard(adminId, r.cardHolder(), r.cardNumber(), r.cardExpiry());
        }
        CompanySettings s = repo.findFirstByOrderByIdAsc().orElseGet(CompanySettings::new);
        s.setCompanyName(r.companyName().trim());
        s.setCompanyPhone(AdminProfileService.displayPhone(p)); // kept in step with the contact mobile
        s.setCompanyEmail(profiles.loginEmail(adminId));
        if (places.isConfigured() && StringUtils.hasText(r.officePlaceId())) {
            // the depot address picked from the suggestions: exact address + coordinates from Google
            PlaceDetails d = places.details(r.officePlaceId(), r.officePlaceSession());
            s.setOfficeAddress(d.address());
            s.setOfficeLat(d.lat());
            s.setOfficeLng(d.lng());
        } else if (places.isConfigured()
                && (s.getOfficeAddress() == null || !s.getOfficeAddress().equalsIgnoreCase(r.officeAddress().trim()))) {
            String msg = "Choose the address from the suggestions list";
            throw new ApiException(HttpStatus.BAD_REQUEST, msg, Map.of("officeAddress", msg));
        } else {
            // address unchanged (or no Google key): keep / accept it as typed
            s.setOfficeAddress(r.officeAddress().trim());
            s.setOfficeLat(r.officeLat());
            s.setOfficeLng(r.officeLng());
        }
        s.setGoogleReviewUrl(blankToNull(r.googleReviewUrl()));
        s.setFacebookReviewUrl(blankToNull(r.facebookReviewUrl()));
        if (s.getId() == null) repo.save(s);
        return read();
    }

    /** Not saved: only carries the company name the Super Admin entered (for customer texts). */
    private CompanySettings unsaved(Long adminId) {
        CompanySettings s = new CompanySettings();
        profiles.find(adminId).ifPresent(p -> s.setCompanyName(p.getCompanyName()));
        return s;
    }

    /** The admin whose data the current request works on (an admin, or a technician's admin). */
    private Long currentAdminId() {
        Long id = tenants.resolveCurrentTenantIdentifier();
        return tenants.isRoot(id) ? null : id;
    }

    private static String firstText(String a, String b) {
        return StringUtils.hasText(a) ? a : b;
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static ApiException field(String field, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message, Map.of(field, message));
    }
}
