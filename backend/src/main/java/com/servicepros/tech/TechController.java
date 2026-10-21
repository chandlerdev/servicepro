package com.servicepros.tech;

import java.time.LocalDate;

import com.servicepros.common.PageResponse;
import com.servicepros.common.TimeService;
import com.servicepros.notification.NotificationDtos.NotificationResponse;
import com.servicepros.schedule.ScheduleDtos.StopDto;
import com.servicepros.security.CurrentUser;
import com.servicepros.tech.TechDtos.CompleteRequest;
import com.servicepros.tech.TechDtos.CompleteResponse;
import com.servicepros.tech.TechDtos.DelayRequest;
import com.servicepros.tech.TechDtos.DelayResponse;
import com.servicepros.tech.TechDtos.MeResponse;
import com.servicepros.tech.TechDtos.StopsResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Technician app")
@RestController
@RequestMapping("/api/tech")
@RequiredArgsConstructor
public class TechController {

    private final TechService service;
    private final TimeService time;

    @GetMapping("/me")
    public MeResponse me(@RequestParam(required = false) Long technicianId) {
        return service.me(CurrentUser.get(), technicianId);
    }

    @GetMapping("/stops")
    public StopsResponse stops(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                               @RequestParam(required = false) Long technicianId) {
        return service.stops(CurrentUser.get(), technicianId, date == null ? time.today() : date);
    }

    @PostMapping("/appointments/{id}/on-my-way")
    public StopDto onMyWay(@PathVariable Long id) {
        return service.onMyWay(CurrentUser.get(), id);
    }

    @PostMapping("/appointments/{id}/delay")
    public DelayResponse delay(@PathVariable Long id, @Valid @RequestBody DelayRequest req) {
        return service.delay(CurrentUser.get(), id, req.minutes(), req.reason());
    }

    @PostMapping("/appointments/{id}/complete")
    public CompleteResponse complete(@PathVariable Long id, @Valid @RequestBody(required = false) CompleteRequest req) {
        return service.complete(CurrentUser.get(), id, req == null ? null : req.notes());
    }

    @GetMapping("/notifications")
    public PageResponse<NotificationResponse> notifications(@RequestParam(required = false) Long technicianId,
                                                            @RequestParam(required = false) Integer page,
                                                            @RequestParam(required = false) Integer size) {
        return service.notifications(CurrentUser.get(), technicianId, page, size);
    }
}
