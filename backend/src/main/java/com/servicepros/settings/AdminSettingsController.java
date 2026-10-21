package com.servicepros.settings;

import com.servicepros.settings.SettingsDtos.SettingsRequest;
import com.servicepros.settings.SettingsDtos.SettingsResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin · Settings")
@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
public class AdminSettingsController {

    private final SettingsService settingsService;

    @GetMapping
    public SettingsResponse get() {
        return settingsService.read();
    }

    @PutMapping
    public SettingsResponse update(@Valid @RequestBody SettingsRequest req) {
        return settingsService.update(req);
    }
}
