package com.servicepros.settings;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanySettingsRepository extends JpaRepository<CompanySettings, Long> {

    /** The current admin's settings row (queries are tenant-filtered). */
    Optional<CompanySettings> findFirstByOrderByIdAsc();

    Optional<CompanySettings> findByOwnerId(Long ownerId);
}
