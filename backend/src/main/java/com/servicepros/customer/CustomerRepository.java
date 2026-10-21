package com.servicepros.customer;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CustomerRepository extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {

    List<Customer> findByDeletedFalseOrderByNameAsc();

    List<Customer> findByCityIgnoreCase(String city);

    long countByCityIgnoreCaseAndDeletedFalse(String city);

    List<Customer> findByPhoneDigitsAndDeletedFalse(String phoneDigits);

    long countByDeletedFalse();

    /** Customers of an admin (unfiltered query, used by the Super Admin). */
    long countByOwnerIdAndDeletedFalse(Long ownerId);

    long countByOwnerId(Long ownerId);
}
