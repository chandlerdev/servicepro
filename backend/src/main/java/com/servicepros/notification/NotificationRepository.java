package com.servicepros.notification;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByType(Notification.Type type, Pageable pageable);

    Page<Notification> findByRouteIdIn(Collection<Long> routeIds, Pageable pageable);

    List<Notification> findTop5ByOrderByIdDesc();
}
