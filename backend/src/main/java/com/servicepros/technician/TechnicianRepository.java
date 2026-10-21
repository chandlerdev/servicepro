package com.servicepros.technician;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Queries run inside the current admin's space (tenant filter), except {@link #countByOwnerId} and the native query. */
public interface TechnicianRepository extends JpaRepository<Technician, Long> {

    List<Technician> findByNameIgnoreCase(String name);

    List<Technician> findByEmailIgnoreCase(String email);

    /**
     * Technicians of ANY admin with this phone (native SQL, so the per-admin filter does not apply):
     * a mobile number may belong to only one person in the whole system. {@code exceptId} = the technician being edited (-1 = none).
     */
    @Query(value = "select count(*) from technicians where phone_country_code = :code and phone = :phone and id <> :exceptId",
            nativeQuery = true)
    long countWithPhoneInAnyCompany(@Param("code") String code, @Param("phone") String phone, @Param("exceptId") long exceptId);

    /** Records owned by an admin (unfiltered queries, used by the Super Admin). */
    long countByOwnerId(Long ownerId);
}
