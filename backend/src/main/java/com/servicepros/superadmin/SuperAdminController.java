package com.servicepros.superadmin;

import java.util.List;

import com.servicepros.superadmin.AdminAccountDtos.ActiveRequest;
import com.servicepros.superadmin.AdminAccountDtos.AdminRequest;
import com.servicepros.superadmin.AdminAccountDtos.AdminResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Super Admin · Admins")
@RestController
@RequestMapping("/api/super/admins")
@RequiredArgsConstructor
public class SuperAdminController {

    private final SuperAdminService service;

    @GetMapping
    public List<AdminResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public AdminResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminResponse create(@Valid @RequestBody AdminRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public AdminResponse update(@PathVariable Long id, @Valid @RequestBody AdminRequest req) {
        return service.update(id, req);
    }

    @PatchMapping("/{id}/active")
    public AdminResponse setActive(@PathVariable Long id, @Valid @RequestBody ActiveRequest req) {
        return service.setActive(id, req.active());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
