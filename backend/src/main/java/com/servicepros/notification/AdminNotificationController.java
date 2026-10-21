package com.servicepros.notification;

import com.servicepros.common.PageResponse;
import com.servicepros.notification.NotificationDtos.NotificationResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
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

    @GetMapping
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(@RequestParam(required = false) Notification.Type type,
                                                   @RequestParam(required = false) Integer page,
                                                   @RequestParam(required = false) Integer size) {
        var pageable = PageResponse.pageable(page, size, Sort.by(Sort.Direction.DESC, "id"));
        var result = type == null ? repo.findAll(pageable) : repo.findByType(type, pageable);
        return PageResponse.of(result, NotificationResponse::from);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void clear() {
        repo.deleteAllInBatch();
    }
}
