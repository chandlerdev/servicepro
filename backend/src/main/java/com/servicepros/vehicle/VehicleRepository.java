package com.servicepros.vehicle;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    /** Records owned by an admin (unfiltered queries, used by the Super Admin). */
    long countByOwnerId(Long ownerId);
}
