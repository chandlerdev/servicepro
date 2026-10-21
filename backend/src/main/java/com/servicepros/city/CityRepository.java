package com.servicepros.city;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** The shared city list (not per admin). */
public interface CityRepository extends JpaRepository<City, Long> {

    List<City> findAllByOrderByNameAsc();

    Optional<City> findByNameIgnoreCase(String name);

    /** Super Admin search (name contains, any case), paged. */
    Page<City> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
