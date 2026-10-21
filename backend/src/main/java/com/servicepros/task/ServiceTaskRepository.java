package com.servicepros.task;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceTaskRepository extends JpaRepository<ServiceTask, Long> {

    List<ServiceTask> findByDeletedFalseOrderByNameAsc();

    /** Records owned by an admin (unfiltered queries, used by the Super Admin). */
    long countByOwnerId(Long ownerId);
}
