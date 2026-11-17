package com.servicepros.integration;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegrationSettingsRepository extends JpaRepository<IntegrationSettings, Long> {

    Optional<IntegrationSettings> findFirstByOrderByIdAsc();
}
