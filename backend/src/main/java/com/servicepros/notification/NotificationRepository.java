package com.servicepros.notification;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByType(Notification.Type type, Pageable pageable);

    Page<Notification> findByRouteIdIn(Collection<Long> routeIds, Pageable pageable);

    /** Created in [from, to) - the date window of the notification screens. */
    Page<Notification> findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(Instant from, Instant to, Pageable pageable);

    Page<Notification> findByTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Notification.Type type, Instant from, Instant to, Pageable pageable);

    Page<Notification> findByRouteIdInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Collection<Long> routeIds, Instant from, Instant to, Pageable pageable);

    List<Notification> findTop5ByOrderByIdDesc();
}
