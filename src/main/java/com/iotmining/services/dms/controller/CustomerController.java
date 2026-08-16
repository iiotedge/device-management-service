package com.iotmining.services.dms.controller;

import com.iotmining.services.dms.model.Customer;
import com.iotmining.services.dms.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerRepository customerRepository;

    /**
     * Tenant-scoped customer list for UI dropdowns (owner assignment, sharing).
     * Previously returned hardcoded fake customers with fresh random UUIDs —
     * assigning one as a device owner produced a dangling FK reference.
     * Response shape is unchanged: [{ "id": ..., "name": ... }, ...]
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getCustomers(@RequestParam("tenantId") UUID tenantId) {
        List<Map<String, Object>> customers = customerRepository.findByTenantId(tenantId).stream()
                .map(c -> {
                    Map<String, Object> entry = new LinkedHashMap<String, Object>();
                    entry.put("id", c.getId());
                    entry.put("name", c.getName() != null ? c.getName() : c.getEmail());
                    return entry;
                })
                .toList();

        return ResponseEntity.ok(customers);
    }

    /** Full customer record (additive endpoint). */
    @GetMapping("/{id}")
    public ResponseEntity<Customer> getCustomer(@PathVariable("id") UUID id) {
        return customerRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
