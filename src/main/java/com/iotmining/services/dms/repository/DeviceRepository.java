package com.iotmining.services.dms.repository;

import com.iotmining.services.dms.model.Device;
import com.iotmining.common.data.devices.DeviceCategory;
import com.iotmining.common.data.devices.DeviceState;
import com.iotmining.common.data.devices.DeviceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DeviceRepository extends JpaRepository<Device, UUID>, JpaSpecificationExecutor<Device> {

    // Standard Property Filters
    List<Device> findByCategory(DeviceCategory category);
    List<Device> findByDeviceType(DeviceType deviceType);
    List<Device> findByState(DeviceState state);

    // --- TENANT OWNERSHIP ---
    // @EntityGraph forces Hibernate to fetch these foreign keys in 1 SQL query instead of N+1
    @EntityGraph(attributePaths = {"customer", "firmware", "software"})
    Page<Device> findByTenantId(UUID tenantId, Pageable pageable);

    // --- ASSIGNMENT SEARCH ---
    @EntityGraph(attributePaths = {"customer", "firmware", "software"})
    Page<Device> findByAssignedCustomersContaining(String customerId, Pageable pageable);

    // --- THE "DASHBOARD" QUERY ---
    // Returns devices the user OWNS (Tenant Admin), OWNS (Customer Object),
    // or is ASSIGNED to (User/Sub-Tenant), plus public devices.
    // Note: DISTINCT is required because LEFT JOINing a collection can return duplicate device rows.
    @EntityGraph(attributePaths = {"customer", "firmware", "software"})
    @Query("SELECT DISTINCT d FROM Device d LEFT JOIN d.assignedCustomers ac " +
            "WHERE d.tenantId = :tenantId " +
            "OR d.customer.id = :customerUuid " +
            "OR ac = :customerString " +
            "OR d.isPublic = true")
    Page<Device> findVisibleDevices(@Param("tenantId") UUID tenantId,
                                    @Param("customerUuid") UUID customerUuid,
                                    @Param("customerString") String customerString,
                                    Pageable pageable);

    Page<Device> findByTenantIdAndDeviceProfileId(UUID tenantId, UUID deviceProfileId, Pageable pageable);

    // --- BULK / QUERY SUPPORT (additive; used by DeviceBulkService & DeviceQueryService) ---

    boolean existsByTenantIdAndDeviceName(UUID tenantId, String deviceName);

    @EntityGraph(attributePaths = {"customer", "firmware", "software"})
    List<Device> findAllByTenantId(UUID tenantId);

    long countByTenantId(UUID tenantId);

    @Query("SELECT d.state, COUNT(d) FROM Device d WHERE d.tenantId = :tenantId GROUP BY d.state")
    List<Object[]> countByStateForTenant(@Param("tenantId") UUID tenantId);

    @Query("SELECT d.category, COUNT(d) FROM Device d WHERE d.tenantId = :tenantId GROUP BY d.category")
    List<Object[]> countByCategoryForTenant(@Param("tenantId") UUID tenantId);

    @Query("SELECT d.deviceType, COUNT(d) FROM Device d WHERE d.tenantId = :tenantId GROUP BY d.deviceType")
    List<Object[]> countByTypeForTenant(@Param("tenantId") UUID tenantId);
}

//package com.iotmining.services.dms.repository;
//
//import com.iotmining.services.dms.model.Device;
//import com.iotmining.common.data.devices.DeviceCategory;
//import com.iotmining.common.data.devices.DeviceState;
//import com.iotmining.common.data.devices.DeviceType;
//import org.springframework.data.domain.Page;
//import org.springframework.data.domain.Pageable;
//import org.springframework.data.jpa.repository.JpaRepository;
//import org.springframework.data.jpa.repository.Query;
//import org.springframework.data.repository.query.Param;
//import org.springframework.stereotype.Repository;
//
//import java.util.List;
//import java.util.UUID;
//
//@Repository
//public interface DeviceRepository extends JpaRepository<Device, UUID> {
//
//    // Standard Property Filters
//    List<Device> findByCategory(DeviceCategory category);
//    List<Device> findByDeviceType(DeviceType deviceType);
//    List<Device> findByState(DeviceState state);
//
//    // --- TENANT OWNERSHIP ---
//    Page<Device> findByTenantId(UUID tenantId, Pageable pageable);
//
//    // --- ASSIGNMENT SEARCH ---
//    // Finds devices where the specific ID exists in the assignedCustomers Set
//    Page<Device> findByAssignedCustomersContaining(String customerId, Pageable pageable);
//
//    // --- THE "DASHBOARD" QUERY ---
//    // This is the most powerful query for your UI.
//    // It returns devices the user OWNS (Tenant Admin) OR devices ASSIGNED to them (User/Sub-Tenant).
//    @Query("SELECT d FROM Device d LEFT JOIN d.assignedCustomers c " +
//            "WHERE d.tenantId = :tenantId OR c = :customerId OR d.isPublic = true")
//    Page<Device> findVisibleDevices(@Param("tenantId") UUID tenantId,
//                                    @Param("customerId") String customerId,
//                                    Pageable pageable);
//}
//
//
////package com.iotmining.services.dms.repository;
////
////import com.iotmining.services.dms.model.Device;
////import com.iotmining.common.data.devices.DeviceCategory;
////import com.iotmining.common.data.devices.DeviceState;
////import com.iotmining.common.data.devices.DeviceType;
////import org.springframework.data.domain.Page;
////import org.springframework.data.domain.Pageable;
////import org.springframework.data.jpa.repository.JpaRepository;
////import org.springframework.stereotype.Repository;
////
////import java.util.List;
////import java.util.UUID;
////
////@Repository
////public interface DeviceRepository extends JpaRepository<Device, UUID> {
////
////    List<Device> findByCategory(DeviceCategory category);
////    List<Device> findByDeviceType(DeviceType deviceType);
////    List<Device> findByState(DeviceState state);
////    List<Device> findByTenantId(UUID tenantId); // now supported
////    Page<Device> findByTenantId(UUID tenantId, Pageable pageable);
////}
