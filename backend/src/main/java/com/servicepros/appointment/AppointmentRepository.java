package com.servicepros.appointment;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface AppointmentRepository extends JpaRepository<Appointment, Long>, JpaSpecificationExecutor<Appointment> {

    @Override
    @EntityGraph(attributePaths = {"customer", "task", "route"})
    Page<Appointment> findAll(Specification<Appointment> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "task", "route"})
    List<Appointment> findByDateAndRouteIdAndStatusNot(LocalDate date, Long routeId, AppointmentStatus status);

    @EntityGraph(attributePaths = {"customer", "task", "route"})
    List<Appointment> findByDateAndStatusNot(LocalDate date, AppointmentStatus status);

    @Query("select distinct a.route.id from Appointment a where a.date = :date and a.status <> com.servicepros.appointment.AppointmentStatus.CANCELLED")
    List<Long> findRouteIdsWithAppointments(LocalDate date);

    @Query("select a from Appointment a where a.customer.id = :customerId and a.date >= :from and a.status in :statuses")
    List<Appointment> findUpcomingForCustomer(Long customerId, LocalDate from, Collection<AppointmentStatus> statuses);

    @Query("select a from Appointment a where a.task.id = :taskId and a.date >= :from and a.status in :statuses")
    List<Appointment> findUpcomingForTask(Long taskId, LocalDate from, Collection<AppointmentStatus> statuses);

    @Query("select distinct a.date from Appointment a where a.route.id = :routeId and a.date >= :from")
    List<LocalDate> findUpcomingDatesForRoute(Long routeId, LocalDate from);

    @EntityGraph(attributePaths = {"customer", "task", "route"})
    @Query("select a from Appointment a where a.customer.phoneDigits = :digits and a.date >= :from and a.status in :statuses order by a.date asc")
    List<Appointment> findUpcomingByPhone(String digits, LocalDate from, Collection<AppointmentStatus> statuses);

    boolean existsByTaskId(Long taskId);

    boolean existsByRouteId(Long routeId);

    long countByRouteIdAndStatusIn(Long routeId, Collection<AppointmentStatus> statuses);

    long countByDateAndStatusNot(LocalDate date, AppointmentStatus status);

    long countByDateAndStatus(LocalDate date, AppointmentStatus status);

    /** Records owned by an admin (unfiltered queries, used by the Super Admin). */
    long countByOwnerId(Long ownerId);
}
