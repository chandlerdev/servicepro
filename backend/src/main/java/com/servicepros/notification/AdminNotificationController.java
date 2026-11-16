package com.servicepros.notification;

import java.time.LocalDate;

import com.servicepros.common.PageResponse;
import com.servicepros.notification.NotificationDtos.NotificationResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin · Notifications")
@RestController
@RequestMapping("/api/admin/notifications")
@RequiredArgsConstructor
public class AdminNotificationController {

    private final NotificationRepository repo;
    private final NotificationRange range;

    /** Only {@code from}..{@code to} (dates, business timezone); with neither, yesterday and today. */
    @GetMapping
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(@RequestParam(required = false) Notification.Type type,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                   @RequestParam(required = false) Integer page,
                                                   @RequestParam(required = false) Integer size) {
        var pageable = PageResponse.pageable(page, size, Sort.by(Sort.Direction.DESC, "id"));
        var b = range.resolve(from, to);
        var result = type == null
                ? repo.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(b.from(), b.toExclusive(), pageable)
                : repo.findByTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(type, b.from(), b.toExclusive(), pageable);
        return PageResponse.of(result, NotificationResponse::from);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void clear() {
        repo.deleteAllInBatch();
    }
}
