package com.servicepros.schedule;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RoutePlanRepository extends JpaRepository<RoutePlan, Long> {

    Optional<RoutePlan> findByDateAndRouteId(LocalDate date, Long routeId);

    List<RoutePlan> findByDateInAndRouteIdIn(Collection<LocalDate> dates, Collection<Long> routeIds);

    @Modifying
    @Query("update RoutePlan p set p.stale = true where p.route.id = :routeId and p.date >= :from")
    int markStaleFrom(Long routeId, LocalDate from);

    @Modifying
    @Query("delete from RoutePlan p where p.route.id = :routeId")
    int deleteByRouteId(Long routeId);
}
