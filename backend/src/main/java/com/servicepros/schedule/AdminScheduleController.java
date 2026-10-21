package com.servicepros.schedule;

import java.time.LocalDate;

import com.servicepros.schedule.ScheduleDtos.AllocateResult;
import com.servicepros.schedule.ScheduleDtos.DateRouteRequest;
import com.servicepros.schedule.ScheduleDtos.DaySchedule;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin · Day plan")
@RestController
@RequestMapping("/api/admin/schedule")
@RequiredArgsConstructor
public class AdminScheduleController {

    private final ScheduleService scheduleService;

    @GetMapping
    public DaySchedule get(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                           @RequestParam(required = false) Long routeId) {
        return scheduleService.daySchedule(date, routeId);
    }

    @PostMapping("/optimize")
    public DaySchedule optimize(@Valid @RequestBody DateRouteRequest req) {
        return scheduleService.optimize(req.date(), req.routeId());
    }

    @PostMapping("/allocate-notify")
    public AllocateResult allocateAndNotify(@Valid @RequestBody DateRouteRequest req) {
        return scheduleService.allocateAndNotify(req.date(), req.routeId());
    }
}
