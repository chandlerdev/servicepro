package com.servicepros.city;

import java.util.List;

import com.servicepros.city.CityService.CityOption;
import com.servicepros.city.CityService.CityRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** City list for admins: read it, and add a missing city. Editing / deleting is for the Super Admin ({@link SuperCityController}). */
@Tag(name = "Admin · Cities")
@RestController
@RequestMapping("/api/admin/cities")
@RequiredArgsConstructor
public class AdminCityController {

    private final CityService cityService;

    @GetMapping
    public List<CityOption> list() {
        return cityService.options();
    }

    /** Add a missing city to the shared list (or get the existing one with that name). Admins can't edit / delete. */
    @PostMapping
    public CityOption add(@Valid @RequestBody CityRequest r) {
        return cityService.addFromAdmin(r);
    }
}
