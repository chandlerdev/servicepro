package com.servicepros.route;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ServiceRouteRepository extends JpaRepository<ServiceRoute, Long> {

    @Query("select r from ServiceRoute r join r.technicians t where t.id = :technicianId and r.deleted = false order by r.name")
    List<ServiceRoute> findByTechnicianId(Long technicianId);

    Optional<ServiceRoute> findFirstByCityIgnoreCaseAndDeletedFalseOrderByIdAsc(String city);

    boolean existsByVehicleIdAndDeletedFalse(Long vehicleId);

    List<ServiceRoute> findByDeletedFalseOrderByNameAsc();

    List<ServiceRoute> findByCityIgnoreCase(String city);

    long countByCityIgnoreCaseAndDeletedFalse(String city);

    /** Records owned by an admin (unfiltered queries, used by the Super Admin). */
    long countByOwnerId(Long ownerId);
}
