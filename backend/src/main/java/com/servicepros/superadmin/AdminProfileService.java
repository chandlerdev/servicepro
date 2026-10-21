package com.servicepros.superadmin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.servicepros.auth.UserRepository;
import com.servicepros.common.ApiException;
import com.servicepros.common.PhoneRules;
import com.servicepros.common.Phones;
import com.servicepros.settings.CompanySettingsRepository;
import com.servicepros.superadmin.AdminAccountDtos.CardSummary;
import com.servicepros.technician.TechnicianRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * An admin's company / contact details, shared by the Super Admin's Admins page and the admin's own Settings page.
 * The company name is kept in sync between {@link AdminProfile} and the admin's company settings.
 */
@Service
@RequiredArgsConstructor
public class AdminProfileService {

    private final AdminProfileRepository profiles;
    private final UserRepository users;
    private final CompanySettingsRepository settings;
    private final TechnicianRepository technicians;

    @Transactional(readOnly = true)
    public Optional<AdminProfile> find(Long adminId) {
        return adminId == null ? Optional.empty() : profiles.findByUserId(adminId);
    }

    /** The admin's login email (read-only on the Settings page). */
    @Transactional(readOnly = true)
    public String loginEmail(Long adminId) {
        return adminId == null ? null : users.findById(adminId).map(u -> u.getEmail()).orElse(null);
    }

    /** The admin user's name (used as contact person when no profile exists yet). */
    @Transactional(readOnly = true)
    public String userName(Long adminId) {
        return adminId == null ? null : users.findById(adminId).map(u -> u.getName()).orElse(null);
    }

    public static CardSummary card(AdminProfile p) {
        return p == null || p.getCardLast4() == null ? null
                : new CardSummary(p.getCardHolder(), p.getCardBrand(), p.getCardLast4(), p.getCardExpMonth(), p.getCardExpYear());
    }

    /** "+1" + "2815550100" → "+1 (281) 555-0100"; others "+91 7810894925". */
    public static String displayPhone(AdminProfile p) {
        if (p == null || p.getContactPhone() == null || p.getContactPhone().isBlank()) return null;
        String code = p.getContactCountryCode();
        String n = p.getContactPhone();
        if ("+1".equals(code) && n.matches("\\d{10}")) {
            return "+1 (" + n.substring(0, 3) + ") " + n.substring(3, 6) + "-" + n.substring(6);
        }
        return code == null ? n : code + " " + n;
    }

    /** Company name / contact mobile already used by another (not deleted) admin → field errors. */
    @Transactional(readOnly = true)
    public Map<String, String> conflicts(String companyName, String countryCode, String phone, Long selfId) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (profiles.findByCompanyNameIgnoreCase(companyName.trim()).stream()
                .anyMatch(p -> !p.isDeleted() && !p.getUserId().equals(selfId))) {
            errors.put("companyName", "An admin with this company name already exists");
        }
        String code = PhoneRules.normalizeCode(countryCode);
        String digits = Phones.digits(phone);
        if (profiles.findAll().stream()
                .anyMatch(p -> !p.isDeleted() && !p.getUserId().equals(selfId) && (code + digits).equals(fullPhone(p)))) {
            errors.put("contactPhone", "This mobile number is already used by another admin");
        } else if (technicians.countWithPhoneInAnyCompany(code, digits, -1L) > 0) {
            errors.put("contactPhone", "This mobile number is already registered to a technician");
        }
        return errors;
    }

    /** True when "+1" + "2815550100" is the contact mobile of an active (not deleted) admin. */
    @Transactional(readOnly = true)
    public boolean isAdminMobile(String fullPhone) {
        return profiles.findAll().stream().anyMatch(p -> !p.isDeleted() && fullPhone.equals(fullPhone(p)));
    }

    /** Called from the admin's Settings page: company name, contact person and mobile (name + mobile duplicate-checked). */
    @Transactional
    public AdminProfile updateContact(Long adminId, String companyName, String contactName, String countryCode, String phone) {
        String code = PhoneRules.normalizeCode(countryCode);
        String number = PhoneRules.normalizeNumber(code, phone);
        Map<String, String> errors = conflicts(companyName, code, number, adminId);
        if (!errors.isEmpty()) {
            String message = errors.size() == 1 ? errors.values().iterator().next() : "Some details are already used by another admin";
            throw new ApiException(HttpStatus.CONFLICT, message, errors);
        }
        AdminProfile p = profiles.findByUserId(adminId).orElseGet(() -> {
            AdminProfile n = new AdminProfile();
            n.setUserId(adminId);
            return n;
        });
        p.setCompanyName(companyName.trim());
        p.setContactName(contactName.trim());
        p.setContactCountryCode(code);
        p.setContactPhone(number);
        users.findById(adminId).ifPresent(u -> u.setName(p.getContactName())); // "Signed in as" in the sidebar
        return profiles.save(p);
    }

    /**
     * Called from the admin's Settings page: name on card + expiry, and a new card number when given
     * (blank keeps the saved card). Only brand, last 4, expiry and holder are stored.
     */
    @Transactional
    public AdminProfile updateCard(Long adminId, String holder, String number, String expiry) {
        if (holder == null || holder.isBlank()) throw field("cardHolder", "Name on card is required");
        if (expiry == null || expiry.isBlank()) throw field("cardExpiry", "Enter the expiry as MM/YY");
        AdminProfile p = profiles.findByUserId(adminId)
                .orElseThrow(() -> field("cardHolder", "Save the company details first"));
        if ((number == null || number.isBlank()) && p.getCardLast4() == null) {
            throw field("cardNumber", "Card number is required");
        }
        java.time.YearMonth exp = Cards.expiry(expiry);
        if (number != null && !number.isBlank()) {
            Cards.MaskedCard card = Cards.mask(number);
            p.setCardBrand(card.brand());
            p.setCardLast4(card.last4());
        }
        p.setCardHolder(holder.trim());
        p.setCardExpMonth(exp.getMonthValue());
        p.setCardExpYear(exp.getYear());
        return p;
    }

    private static ApiException field(String field, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message, Map.of(field, message));
    }

    /** Called when the Super Admin edits an admin: keep the admin's company settings showing the same name. */
    @Transactional
    public void syncSettingsCompanyName(Long adminId, String companyName) {
        settings.findByOwnerId(adminId).ifPresent(s -> s.setCompanyName(companyName.trim()));
    }

    static String fullPhone(AdminProfile p) {
        String code = p.getContactCountryCode() == null ? "" : p.getContactCountryCode();
        return code + Phones.digits(p.getContactPhone());
    }
}
