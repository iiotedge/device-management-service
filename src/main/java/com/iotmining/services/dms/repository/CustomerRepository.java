package com.iotmining.services.dms.repository;

import com.iotmining.services.dms.model.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    // --- TENANT ISOLATION QUERIES ---

    // Fetch all customers for a specific tenant (useful for dropdowns)
    List<Customer> findByTenantId(UUID tenantId);

    // Paginated fetch for a specific tenant (useful for the Customer Data Table UI)
    Page<Customer> findByTenantId(UUID tenantId, Pageable pageable);

    // Look up a specific customer by email, ensuring they belong to the correct tenant
    Optional<Customer> findByEmailAndTenantId(String email, UUID tenantId);

    // Check if a customer exists within a tenant before doing operations
    boolean existsByIdAndTenantId(UUID id, UUID tenantId);
}