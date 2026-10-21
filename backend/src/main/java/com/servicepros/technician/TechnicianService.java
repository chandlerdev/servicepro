package com.servicepros.technician;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.servicepros.auth.RefreshTokenRepository;
import com.servicepros.auth.User;
import com.servicepros.auth.UserRepository;
import com.servicepros.auth.UserService;
import com.servicepros.common.ApiException;
import com.servicepros.common.PhoneRules;
import com.servicepros.route.ServiceRoute;
import com.servicepros.route.ServiceRouteRepository;
import com.servicepros.superadmin.AdminProfileService;
import com.servicepros.technician.TechnicianDtos.TechnicianRequest;
import com.servicepros.technician.TechnicianDtos.TechnicianResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Technicians ("resources") of the current admin, each with an app / website login (email + password).
 * The name must be unique within the admin's team; the phone and email must be unique across the whole system
 * (every company's technicians and every admin's contact mobile / login).
 */
@Service
@RequiredArgsConstructor
public class TechnicianService {

    private final TechnicianRepository repo;
    private final ServiceRouteRepository routes;
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final UserService userService;
    private final AdminProfileService adminProfiles;

    @Transactional(readOnly = true)
    public List<TechnicianResponse> list() {
        return repo.findAll(Sort.by("name")).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TechnicianResponse get(Long id) {
        return toResponse(find(id));
    }

    public Technician find(Long id) {
        return repo.findById(id).orElseThrow(() -> ApiException.notFound("Technician", id));
    }

    public TechnicianResponse toResponse(Technician t) {
        return TechnicianResponse.from(t, users.findByTechnicianId(t.getId()).isPresent());
    }

    /** Creates the technician and their login in one step. */
    @Transactional
    public TechnicianResponse create(TechnicianRequest r) {
        if (!StringUtils.hasText(r.password())) {
            throw field(HttpStatus.BAD_REQUEST, "password", "Password is required — the technician signs in with it");
        }
        Technician t = new Technician();
        apply(t, r);
        repo.save(t);
        userService.upsertTechnicianLogin(t, r.password());
        return TechnicianResponse.from(t, true);
    }

    /** Updates the technician and their login; a blank password keeps the current one. */
    @Transactional
    public TechnicianResponse update(Long id, TechnicianRequest r) {
        Technician t = find(id);
        boolean hasLogin = users.findByTechnicianId(id).isPresent();
        if (!hasLogin && !StringUtils.hasText(r.password())) {
            throw field(HttpStatus.BAD_REQUEST, "password", "Set a password to create this technician's login");
        }
        apply(t, r);
        userService.upsertTechnicianLogin(t, StringUtils.hasText(r.password()) ? r.password() : null);
        return TechnicianResponse.from(t, true);
    }

    @Transactional
    public void delete(Long id) {
        Technician t = find(id);
        List<ServiceRoute> assigned = routes.findByTechnicianId(id);
        for (ServiceRoute r : assigned) {
            if (r.getTechnicians().size() <= 1) {
                throw ApiException.conflict("'" + r.getName() + "' would be left without a technician — reassign it first");
            }
        }
        assigned.forEach(r -> r.getTechnicians().removeIf(x -> x.getId().equals(id)));
        users.findByTechnicianId(id).ifPresent(u -> {
            refreshTokens.deleteByUserId(u.getId());
            users.delete(u);
        });
        repo.delete(t);
    }

    /** Validates and normalises the fields, then checks duplicates (all clashes reported together, 409). */
    private void apply(Technician t, TechnicianRequest r) {
        String name = r.name().trim();
        String code = PhoneRules.normalizeCode(r.phoneCountryCode(), "phoneCountryCode");
        String phone = PhoneRules.normalizeNumber(code, r.phone(), "phone");
        String email = r.email().trim().toLowerCase();
        Long selfId = t.getId();

        Map<String, String> errors = new LinkedHashMap<>();
        if (repo.findByNameIgnoreCase(name).stream().anyMatch(o -> !o.getId().equals(selfId))) {
            errors.put("name", "A resource with this name already exists");
        }
        if (repo.countWithPhoneInAnyCompany(code, phone, selfId == null ? -1L : selfId) > 0
                || adminProfiles.isAdminMobile(code + phone)) {
            errors.put("phone", "This mobile number is already registered");
        }
        // the email is a login, so it must be unique across admins and technicians of every company
        Long ownUserId = selfId == null ? null : users.findByTechnicianId(selfId).map(User::getId).orElse(null);
        boolean emailTaken = users.findByEmailIgnoreCase(email).filter(u -> !u.getId().equals(ownUserId)).isPresent()
                || repo.findByEmailIgnoreCase(email).stream().anyMatch(o -> !o.getId().equals(selfId));
        if (emailTaken) {
            errors.put("email", "This email is already registered");
        }
        if (!errors.isEmpty()) {
            String message = errors.size() == 1 ? errors.values().iterator().next() : "Some details are already in use";
            throw new ApiException(HttpStatus.CONFLICT, message, errors);
        }

        t.setName(name);
        t.setPhoneCountryCode(code);
        t.setPhone(phone);
        t.setEmail(email);
        t.setSkills(StringUtils.hasText(r.skills()) ? r.skills().trim() : null);
    }

    private static ApiException field(HttpStatus status, String field, String message) {
        return new ApiException(status, message, Map.of(field, message));
    }
}
