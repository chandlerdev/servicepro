package com.servicepros.city;

import com.servicepros.city.CityService.CityRequest;
import com.servicepros.city.CityService.CityResponse;
import com.servicepros.common.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Super Admin: the shared city list every admin picks from. Changes apply to all admins at once. */
@Tag(name = "Super Admin · Cities")
@RestController
@RequestMapping("/api/super/cities")
@RequiredArgsConstructor
public class SuperCityController {

    private final CityService cityService;

    /** Search by name ({@code q}), paged ({@code page} from 0, {@code size} up to 200). */
    @GetMapping
    public PageResponse<CityResponse> list(@RequestParam(required = false) String q,
                                           @RequestParam(required = false) Integer page,
                                           @RequestParam(required = false) Integer size) {
        return cityService.page(q, page, size);
    }

    @GetMapping("/{id}")
    public CityResponse get(@PathVariable Long id) {
        return cityService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CityResponse create(@Valid @RequestBody CityRequest r) {
        return cityService.create(r);
    }

    @PutMapping("/{id}")
    public CityResponse update(@PathVariable Long id, @Valid @RequestBody CityRequest r) {
        return cityService.update(id, r);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        cityService.delete(id);
    }
}
