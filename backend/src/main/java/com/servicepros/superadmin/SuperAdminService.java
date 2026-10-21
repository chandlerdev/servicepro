package com.servicepros.superadmin;

import java.time.Instant;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.servicepros.auth.PasswordResetCodeRepository;
import com.servicepros.auth.RefreshTokenRepository;
import com.servicepros.auth.User;
import com.servicepros.auth.UserRepository;
import com.servicepros.common.ApiException;
import com.servicepros.common.PhoneRules;
import com.servicepros.customer.CustomerRepository;
import com.servicepros.superadmin.AdminAccountDtos.AdminRequest;
import com.servicepros.superadmin.AdminAccountDtos.AdminResponse;
import com.servicepros.superadmin.AdminAccountDtos.CardSummary;
import com.servicepros.technician.TechnicianRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Super Admin: create, list, edit, disable and (soft) delete Admins. Each Admin owns a fully separate set of
 * technicians, customers, routes, tasks, vehicles, appointments and settings. Super Admin requests run
 * unfiltered (root tenant), so the per-owner counts below see every admin's data.
 */
@Service
@RequiredArgsConstructor
public class SuperAdminService {

    private final UserRepository users;
    private final AdminProfileRepository profiles;
    private final AdminProfileService profileService;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordResetCodeRepository resetCodes;
    private final PasswordEncoder passwordEncoder;
    private final TechnicianRepository technicians;
    private final CustomerRepository customers;

    @Transactional(readOnly = true)
    public List<AdminResponse> list() {
        return users.findByRoleOrderByIdAsc(User.Role.ADMIN).stream()
                .filter(u -> u.getDeletedAt() == null).map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AdminResponse get(Long id) {
        return toResponse(findAdmin(id));
    }

    @Transactional
    public AdminResponse create(AdminRequest r) {
        if (!StringUtils.hasText(r.password())) {
            throw ApiException.badRequest("Password is required");
        }
        if (!StringUtils.hasText(r.cardNumber())) {
            throw ApiException.badRequest("Card number is required");
        }
        String email = r.email().trim().toLowerCase();
        checkDuplicates(r, email, null);
        AdminProfile p = new AdminProfile();
        applyProfile(p, r); // validates everything before the user is saved
        User u = new User();
        u.setRole(User.Role.ADMIN);
        u.setName(p.getContactName());
        u.setEmail(email);
        u.setActive(r.active() == null || r.active());
        u.setPasswordHash(passwordEncoder.encode(r.password()));
        u = users.save(u);
        p.setUserId(u.getId());
        profiles.save(p);
        return toResponse(u);
    }

    @Transactional
    public AdminResponse update(Long id, AdminRequest r) {
        User u = findAdmin(id);
        String email = r.email().trim().toLowerCase();
        checkDuplicates(r, email, id);
        AdminProfile p = profiles.findByUserId(id).orElseGet(() -> {
            AdminProfile n = new AdminProfile();
            n.setUserId(id);
            return n;
        });
        if (p.getCardLast4() == null && !StringUtils.hasText(r.cardNumber())) {
            throw ApiException.badRequest("Card number is required");
        }
        applyProfile(p, r);
        profiles.save(p);
        profileService.syncSettingsCompanyName(id, p.getCompanyName());
        u.setName(p.getContactName());
        u.setEmail(email);
        u.setActive(r.active() == null || r.active());
        if (StringUtils.hasText(r.password())) {
            u.setPasswordHash(passwordEncoder.encode(r.password()));
            refreshTokens.revokeAllForUser(id);
        }
        if (!u.isActive()) {
            refreshTokens.revokeAllForUser(id); // signs the admin out everywhere
        }
        return toResponse(u);
    }

    /** Enable / disable only. Disabling signs the admin out and blocks their technicians too. */
    @Transactional
    public AdminResponse setActive(Long id, boolean active) {
        User u = findAdmin(id);
        u.setActive(active);
        if (!active) {
            refreshTokens.revokeAllForUser(id);
        }
        return toResponse(u);
    }

    /**
     * Soft delete: nothing is removed from the database. The admin disappears from the list and can't sign in,
     * their technicians can't sign in either, and the email, company name and mobile become free to reuse.
     * Their technicians, customers, routes and history stay in the database untouched.
     */
    @Transactional
    public void delete(Long id) {
        User u = findAdmin(id);
        AdminProfile p = profiles.findByUserId(id).orElseGet(() -> {
            AdminProfile n = new AdminProfile();
            n.setUserId(id);
            n.setCompanyName("(deleted)");
            n.setContactName(u.getName());
            n.setContactPhone("");
            return n;
        });
        p.setDeleted(true);
        p.setDeletedEmail(u.getEmail());
        profiles.save(p);

        String placeholder = "deleted-" + id + "-" + u.getEmail();
        u.setEmail(placeholder.length() > 160 ? placeholder.substring(0, 160) : placeholder);
        u.setActive(false);
        u.setDeletedAt(Instant.now());
        refreshTokens.revokeAllForUser(id);
        resetCodes.invalidateAllForUser(id);
    }

    /**
     * Email (any user — admins and technicians share one login namespace), company name and contact mobile
     * must be unique. All clashes are reported together, each under its own field. {@code selfId} = admin being edited.
     */
    private void checkDuplicates(AdminRequest r, String email, Long selfId) {
        Map<String, String> errors = new LinkedHashMap<>();
        boolean emailTaken = selfId == null ? users.existsByEmailIgnoreCase(email)
                : users.existsByEmailIgnoreCaseAndIdNot(email, selfId);
        if (emailTaken) {
            errors.put("email", "This email is already registered");
        }
        errors.putAll(profileService.conflicts(r.companyName(), r.contactCountryCode(), r.contactPhone(), selfId));
        if (!errors.isEmpty()) {
            String message = errors.size() == 1 ? errors.values().iterator().next()
                    : "Some details are already used by another admin";
            throw new ApiException(HttpStatus.CONFLICT, message, errors);
        }
    }

    private void applyProfile(AdminProfile p, AdminRequest r) {
        String code = PhoneRules.normalizeCode(r.contactCountryCode());
        String phone = PhoneRules.normalizeNumber(code, r.contactPhone());
        YearMonth exp = Cards.expiry(r.cardExpiry());
        if (StringUtils.hasText(r.cardNumber())) {
            Cards.MaskedCard card = Cards.mask(r.cardNumber());
            p.setCardBrand(card.brand());
            p.setCardLast4(card.last4());
        }
        p.setCompanyName(r.companyName().trim());
        p.setContactName(r.contactName().trim());
        p.setContactCountryCode(code);
        p.setContactPhone(phone);
        p.setCardHolder(r.cardHolder().trim());
        p.setCardExpMonth(exp.getMonthValue());
        p.setCardExpYear(exp.getYear());
    }

    private User findAdmin(Long id) {
        return users.findById(id).filter(u -> u.getRole() == User.Role.ADMIN && u.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("Admin", id));
    }

    private AdminResponse toResponse(User u) {
        AdminProfile p = profiles.findByUserId(u.getId()).orElse(null);
        CardSummary card = AdminProfileService.card(p);
        return new AdminResponse(u.getId(), p == null ? null : p.getCompanyName(), p == null ? u.getName() : p.getContactName(),
                p == null ? null : p.getContactCountryCode(), p == null ? null : p.getContactPhone(), u.getEmail(), u.isActive(), u.getCreatedAt(), card,
                technicians.countByOwnerId(u.getId()), customers.countByOwnerIdAndDeletedFalse(u.getId()));
    }
}
