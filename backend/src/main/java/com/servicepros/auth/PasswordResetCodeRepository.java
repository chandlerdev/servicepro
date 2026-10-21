package com.servicepros.auth;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, Long> {

    Optional<PasswordResetCode> findFirstByUserIdAndUsedFalseOrderByIdDesc(Long userId);

    @Modifying
    @Query("update PasswordResetCode c set c.used = true where c.userId = :userId and c.used = false")
    void invalidateAllForUser(@Param("userId") Long userId);

    @Modifying
    @Query("delete from PasswordResetCode c where c.used = true or c.expiresAt < :now")
    void purge(@Param("now") Instant now);

    void deleteByUserId(Long userId);
}
