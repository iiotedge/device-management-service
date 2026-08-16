package com.iotmining.services.dms.controller;

import com.iotmining.common.data.devices.DeviceState;
import com.iotmining.services.dms.model.Device;
import com.iotmining.services.dms.services.DeviceService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/devices") // Restored to plural per standard REST conventions
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    // =========================================================================
    // CREATION
    // Must be authorized to create a device inside the specified tenant
    // =========================================================================
    @PostMapping
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN') or " +
            "(hasRole('ROLE_TENANT_ADMIN') and authentication.principal['tenantId'] == #device.tenantId.toString())")
    public ResponseEntity<Device> addDevice(@RequestBody Device device) {
        Device savedDevice = deviceService.saveDevice(device);
        return new ResponseEntity<>(savedDevice, HttpStatus.CREATED);
    }

    // =========================================================================
    // RETRIEVAL (Specific Device)
    // Uses the custom evaluator to check Ownership OR Assignment
    // =========================================================================
    @GetMapping("/{id}")
    @PreAuthorize("@deviceSecurity.canAccessDevice(authentication, #id)")
    public ResponseEntity<?> getDeviceById(
            @PathVariable("id") UUID id,
            @RequestParam(name = "withProfile", required = false, defaultValue = "false") boolean withProfile) {

        Device device = deviceService.getDeviceById(id);
        if (device == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Device not found");
        }

        if (!withProfile || device.getDeviceProfileId() == null) {
            return ResponseEntity.ok(device);
        }

        try {
            Map<String, Object> resolvedProfile = deviceService.fetchResolvedProfile(device.getDeviceProfileId());
            return ResponseEntity.ok(Map.of(
                    "device", device,
                    "profile", resolvedProfile
            ));
        } catch (Exception e) {
            log.error("Failed to fetch profile for device {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("device", device, "profileError", e.getMessage()));
        }
    }

    // =========================================================================
    // RETRIEVAL (Paginated List)
    // Users can only query lists for their own tenant
    // =========================================================================
    @GetMapping
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN') or " +
            "authentication.principal['tenantId'] == #tenantId.toString()")
    public ResponseEntity<Page<Map<String, Object>>> getDevicesWithProfiles(
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "size", required = false, defaultValue = "10") int size,
            @RequestParam(name = "tenantId") UUID tenantId,
            @RequestParam(required = false, name="withProfile", defaultValue = "false") boolean withProfile) {

        Pageable pageable = PageRequest.of(page, size);

        if (!withProfile) {
            Page<Device> devices = deviceService.getDevices(tenantId, pageable);
            return new ResponseEntity(devices, HttpStatus.OK);
        }

        Page<Map<String, Object>> pageWithProfiles = deviceService.getDevicesWithProfiles(tenantId, pageable);
        return new ResponseEntity<>(pageWithProfiles, HttpStatus.OK);
    }

    // =========================================================================
    // UPDATING
    // Must have access to the device to update it
    // =========================================================================
    @PutMapping("/{id}")
    @PreAuthorize("@deviceSecurity.canAccessDevice(authentication, #id)")
    public ResponseEntity<Device> updateDevice(@PathVariable("id") UUID id, @RequestBody Device deviceDetails) {
        Device updatedDevice = deviceService.updateDevice(id, deviceDetails);
        return updatedDevice != null ?
                new ResponseEntity<>(updatedDevice, HttpStatus.OK) :
                new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    // =========================================================================
    // DELETION
    // Usually restricted to Tenant Admins, not standard operational users
    // =========================================================================
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN') or " +
            "(hasRole('ROLE_TENANT_ADMIN') and @deviceSecurity.canAccessDevice(authentication, #id))")
    public ResponseEntity<Void> deleteDevice(@PathVariable("id") UUID id) {
        deviceService.deleteDevice(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    // =========================================================================
    // ASSIGNMENT MANAGEMENT (New Endpoints for UI Sharing)
    // =========================================================================

    @PostMapping("/{id}/assign-owner/{customerId}")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN') or hasRole('ROLE_TENANT_ADMIN')")
    public ResponseEntity<Device> assignPrimaryOwner(
            @PathVariable("id") UUID deviceId,
            @PathVariable("customerId") UUID customerId,
            Authentication authentication) {

        UUID tenantId = UUID.fromString(extractClaim(authentication, "tenantId"));
        Device updatedDevice = deviceService.assignPrimaryOwner(deviceId, customerId, tenantId);
        return ResponseEntity.ok(updatedDevice);
    }

    @PostMapping("/{id}/share/{assignedId}")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN') or hasRole('ROLE_TENANT_ADMIN')")
    public ResponseEntity<Device> shareDeviceWithUser(
            @PathVariable("id") UUID deviceId,
            @PathVariable("assignedId") String assignedId,
            Authentication authentication) {

        UUID tenantId = UUID.fromString(extractClaim(authentication, "tenantId"));
        Device updatedDevice = deviceService.addSharedCustomer(deviceId, assignedId, tenantId);
        return ResponseEntity.ok(updatedDevice);
    }

    @DeleteMapping("/{id}/share/{assignedId}")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN') or hasRole('ROLE_TENANT_ADMIN')")
    public ResponseEntity<Device> unshareDeviceFromUser(
            @PathVariable("id") UUID deviceId,
            @PathVariable("assignedId") String assignedId,
            Authentication authentication) {

        UUID tenantId = UUID.fromString(extractClaim(authentication, "tenantId"));
        Device updatedDevice = deviceService.removeSharedCustomer(deviceId, assignedId, tenantId);
        return ResponseEntity.ok(updatedDevice);
    }

    // =========================================================================
    // INTERNAL MICROSERVICE LOOKUPS (Called by TSS / TNS)
    // Secured by X-Internal-API-Key inside the LiteJwtAuthenticationFilter
    // =========================================================================

    @GetMapping("/{id}/owner")
    @PreAuthorize("hasRole('ROLE_SYSTEM')")
    public ResponseEntity<Map<String, String>> getDeviceOwner(@PathVariable("id") UUID id) {
        Device device = deviceService.getDeviceById(id);

        if (device == null) {
            log.warn("L2 Owner Lookup Failed: Device {} not found.", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // Updated to use the new Foreign Key object mapping safely
        String ownerId = (device.getCustomer() != null && device.getCustomer().getId() != null)
                ? device.getCustomer().getId().toString()
                : null;

        // Map.of() rejects null values (throws NPE -> 500 for every unassigned
        // device); singletonMap keeps the documented {"ownerId": null} contract.
        return ResponseEntity.ok(java.util.Collections.singletonMap("ownerId", ownerId));
    }

    @GetMapping("/{id}/access-info")
    @PreAuthorize("hasRole('ROLE_SYSTEM')")
    public ResponseEntity<Map<String, String>> getDeviceAccessInfo(@PathVariable("id") UUID id) {
        Device device = deviceService.getDeviceById(id);

        if (device == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        String ownerTenantId = device.getTenantId().toString();

        // Updated to use the new Foreign Key object mapping safely
        String assignedCustomerId = (device.getCustomer() != null && device.getCustomer().getId() != null)
                ? device.getCustomer().getId().toString()
                : "UNASSIGNED";

        return ResponseEntity.ok(Map.of(
                "tenantId", ownerTenantId,
                "customerId", assignedCustomerId
        ));
    }

    // =========================================================================
    // NEW ENDPOINT: Filtered by Profile
    // =========================================================================
    @GetMapping("/profile/{profileId}")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN') or hasRole('ROLE_TENANT_ADMIN')")
    public ResponseEntity<Page<Map<String, Object>>> getDevicesByProfile(
            @PathVariable("profileId") UUID profileId,
            @RequestParam(name = "tenantId") UUID tenantId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {

        log.info("Fetching devices for profile: {} in tenant: {}", profileId, tenantId);
        Pageable pageable = PageRequest.of(page, size);

        Page<Map<String, Object>> result = deviceService.getDevicesByProfile(tenantId, profileId, pageable);
        return ResponseEntity.ok(result);
    }

    // Helper for extracting JWT claims safely
    private String extractClaim(Authentication authentication, String claimKey) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof Claims claims) return claims.get(claimKey, String.class);
        return null;
    }

    // =========================================================================
    // METADATA LOOKUPS
    // =========================================================================
    @GetMapping("/labels")
    @PreAuthorize("isAuthenticated()") // Or whatever your base auth rule is
    public ResponseEntity<List<Map<String, Object>>> getDeviceLabels(Authentication authentication) {

        // TODO: Replace this with a real call to your LabelRepository if you have a labels table!
        // For now, returning a mock list so your UI doesn't crash and the dropdown populates.
        List<Map<String, Object>> labels = List.of(
                Map.of("id", UUID.randomUUID(), "name", "High Priority"),
                Map.of("id", UUID.randomUUID(), "name", "Maintenance Needed"),
                Map.of("id", UUID.randomUUID(), "name", "Deployed")
        );

        return ResponseEntity.ok(labels);
    }
}




//package com.iotmining.services.dms.controller;
//
//import com.iotmining.common.data.devices.DeviceState;
//import com.iotmining.services.dms.model.Device;
//import com.iotmining.services.dms.services.DeviceService;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.data.domain.Page;
//import org.springframework.data.domain.Pageable;
//import org.springframework.data.domain.PageRequest;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.Map;
//import java.util.UUID;
//
//@Slf4j
//@RestController
//@RequestMapping("/api/v1/device") // CHANGED TO SINGULAR
//@RequiredArgsConstructor
//public class DeviceController {
//
//    private final DeviceService deviceService;
//
//    @PostMapping
//    public ResponseEntity<Device> addDevice(@RequestBody Device device) {
//        Device savedDevice = deviceService.saveDevice(device);
//        return new ResponseEntity<>(savedDevice, HttpStatus.CREATED);
//    }
//
//    @GetMapping("/{id}")
//    public ResponseEntity<?> getDeviceById(
//            @PathVariable("id") UUID id,
//            @RequestParam(name = "withProfile", required = false, defaultValue = "false") boolean withProfile) {
//
//        Device device = deviceService.getDeviceById(id);
//        if (device == null) {
//            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Device not found");
//        }
//
//        if (!withProfile || device.getDeviceProfileId() == null) {
//            return ResponseEntity.ok(device);
//        }
//
//        try {
//            Map<String, Object> resolvedProfile = deviceService.fetchResolvedProfile(device.getDeviceProfileId());
//            return ResponseEntity.ok(Map.of(
//                    "device", device,
//                    "profile", resolvedProfile
//            ));
//        } catch (Exception e) {
//            log.error("Failed to fetch profile for device {}: {}", id, e.getMessage());
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                    .body(Map.of("device", device, "profileError", e.getMessage()));
//        }
//    }
//
//    // =========================================================================
//    // NEW ENDPOINT: L2 CACHE LOOKUP FOR STREAM SERVICE (TSS)
//    // Returns exactly { "ownerId": "uuid-string" } or { "ownerId": null }
//    // =========================================================================
//    @GetMapping("/{id}/owner")
//    public ResponseEntity<Map<String, String>> getDeviceOwner(@PathVariable("id") UUID id) {
//        Device device = deviceService.getDeviceById(id);
//
//        if (device == null) {
//            log.warn("L2 Owner Lookup Failed: Device {} not found.", id);
//            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
//        }
//
//        String ownerId = (device.getCustomerId() != null) ? device.getCustomerId().toString() : null;
//
//        return ResponseEntity.ok(Map.of("ownerId", ownerId));
//    }
//
//    // =========================================================================
//    // L2 CACHE LOOKUP FOR STREAM SERVICE (TSS)
//    // Returns the full access hierarchy of the device
//    // =========================================================================
//    @GetMapping("/{id}/access-info")
//    public ResponseEntity<Map<String, String>> getDeviceAccessInfo(@PathVariable("id") UUID id) {
//        Device device = deviceService.getDeviceById(id);
//
//        if (device == null) {
//            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
//        }
//
//        String ownerTenantId = device.getTenantId().toString();
//        String assignedCustomerId = (device.getCustomerId() != null) ? device.getCustomerId().toString() : "UNASSIGNED";
//
//        return ResponseEntity.ok(Map.of(
//                "tenantId", ownerTenantId,
//                "customerId", assignedCustomerId
//        ));
//    }
//    // =========================================================================
//
//    @PutMapping("/{id}")
//    public ResponseEntity<Device> updateDevice(@PathVariable("id") UUID id, @RequestBody Device deviceDetails) {
//        Device updatedDevice = deviceService.updateDevice(id, deviceDetails);
//        return updatedDevice != null ?
//                new ResponseEntity<>(updatedDevice, HttpStatus.OK) :
//                new ResponseEntity<>(HttpStatus.NOT_FOUND);
//    }
//
//    @GetMapping
//    public ResponseEntity<Page<Map<String, Object>>> getDevicesWithProfiles(
//            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
//            @RequestParam(name = "size", required = false, defaultValue = "10") int size,
//            @RequestParam(name = "tenantId") UUID tenantId,
//            @RequestParam(required = false, name="withProfile", defaultValue = "false") boolean withProfile) {
//
//        Pageable pageable = PageRequest.of(page, size);
//
//        if (!withProfile) {
//            Page<Device> devices = deviceService.getDevices(tenantId, pageable);
//            return new ResponseEntity(devices, HttpStatus.OK);
//        }
//
//        Page<Map<String, Object>> pageWithProfiles = deviceService.getDevicesWithProfiles(tenantId, pageable);
//        return new ResponseEntity<>(pageWithProfiles, HttpStatus.OK);
//    }
//
//    @DeleteMapping("/{id}")
//    public ResponseEntity<Void> deleteDevice(@PathVariable("id") UUID id) {
//        deviceService.deleteDevice(id);
//        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
//    }
//}
//
////package com.iotmining.services.dms.controller;
////
////import com.iotmining.common.data.devices.DeviceState;
////import com.iotmining.services.dms.model.Device;
////import com.iotmining.services.dms.services.DeviceService;
////import lombok.RequiredArgsConstructor;
////import lombok.extern.slf4j.Slf4j;
////import org.springframework.data.domain.Page;
////import org.springframework.data.domain.PageRequest;
////import org.springframework.data.domain.Pageable;
////import org.springframework.http.HttpStatus;
////import org.springframework.http.ResponseEntity;
////import org.springframework.web.bind.annotation.*;
////
////import java.util.Map;
////import java.util.UUID;
////
////@Slf4j
////@RestController
////@RequestMapping("/api/v1/devices")
////@RequiredArgsConstructor
////public class DeviceController {
////
////    private final DeviceService deviceService;
////
////    @PostMapping
////    public ResponseEntity<Device> addDevice(@RequestBody Device device) {
////        Device savedDevice = deviceService.saveDevice(device);
////        return new ResponseEntity<>(savedDevice, HttpStatus.CREATED);
////    }
////
////    @GetMapping("/{id}")
////    public ResponseEntity<?> getDeviceById(
////            @PathVariable("id") UUID id,
////            @RequestParam(name = "withProfile", required = false, defaultValue = "false") boolean withProfile) {
////
////        Device device = deviceService.getDeviceById(id);
////        if (device == null) {
////            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Device not found");
////        }
////
////        if (!withProfile || device.getDeviceProfileId() == null) {
////            return ResponseEntity.ok(device);
////        }
////
////        try {
////            Map<String, Object> resolvedProfile = deviceService.fetchResolvedProfile(device.getDeviceProfileId());
////            return ResponseEntity.ok(Map.of(
////                    "device", device,
////                    "profile", resolvedProfile
////            ));
////        } catch (Exception e) {
////            log.error("Failed to fetch profile for device {}: {}", id, e.getMessage());
////            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
////                    .body(Map.of("device", device, "profileError", e.getMessage()));
////        }
////    }
////
////    @PutMapping("/{id}")
////    public ResponseEntity<Device> updateDevice(@PathVariable("id") UUID id, @RequestBody Device deviceDetails) {
////        Device updatedDevice = deviceService.updateDevice(id, deviceDetails);
////        return updatedDevice != null ?
////                new ResponseEntity<>(updatedDevice, HttpStatus.OK) :
////                new ResponseEntity<>(HttpStatus.NOT_FOUND);
////    }
////
////    @GetMapping
////    public ResponseEntity<Page<Map<String, Object>>> getDevicesWithProfiles(
////            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
////            @RequestParam(name = "size", required = false, defaultValue = "10") int size,
////            @RequestParam(name = "tenantId") UUID tenantId,
////            @RequestParam(required = false, name="withProfile", defaultValue = "false") boolean withProfile) {
////
////        Pageable pageable = PageRequest.of(page, size);
////
////        if (!withProfile) {
////            Page<Device> devices = deviceService.getDevices(tenantId, pageable);
////            return new ResponseEntity(devices, HttpStatus.OK);
////        }
////
////        Page<Map<String, Object>> pageWithProfiles = deviceService.getDevicesWithProfiles(tenantId, pageable);
////        return new ResponseEntity<>(pageWithProfiles, HttpStatus.OK);
////    }
////
////    @DeleteMapping("/{id}")
////    public ResponseEntity<Void> deleteDevice(@PathVariable("id") UUID id) {
////        deviceService.deleteDevice(id);
////        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
////    }
////}
//////package com.iotmining.services.dms.controller;
//////
//////import com.iotmining.common.data.devices.DeviceState;
//////import com.iotmining.services.dms.model.Device;
//////import com.iotmining.services.dms.services.DeviceService;
//////import lombok.RequiredArgsConstructor;
//////import lombok.extern.slf4j.Slf4j;
//////import org.springframework.beans.factory.annotation.Autowired;
//////import org.springframework.data.domain.Page;
//////import org.springframework.data.domain.PageImpl;
//////import org.springframework.data.domain.PageRequest;
//////import org.springframework.data.domain.Pageable;
//////import org.springframework.http.HttpStatus;
//////import org.springframework.http.ResponseEntity;
//////import org.springframework.web.bind.annotation.*;
//////
//////import java.util.HashMap;
//////import java.util.List;
//////import java.util.Map;
//////import java.util.UUID;
//////import java.util.stream.Collectors;
//////
//////@Slf4j
//////@RestController
//////@RequestMapping("/api/v1/devices")
//////@RequiredArgsConstructor
//////public class DeviceController {
//////
//////    private final DeviceService deviceService;
//////
//////    @PostMapping
//////    public ResponseEntity<Device> addDevice(@RequestBody Device device) {
//////        Device savedDevice = deviceService.saveDevice(device);
//////        return new ResponseEntity<>(savedDevice, HttpStatus.CREATED);
//////    }
//////
//////    // ✅ Updated to support ?withProfile=true
//////    @GetMapping("/{id}")
//////    public ResponseEntity<?> getDeviceById(
//////            @PathVariable("id") UUID id,
//////            @RequestParam(name = "withProfile", required = false, defaultValue = "false") boolean withProfile) {
//////
//////        Device device = deviceService.getDeviceById(id);
//////        if (device == null) {
//////            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Device not found");
//////        }
//////
//////        if (!withProfile || device.getDeviceProfileId() == null) {
//////            return ResponseEntity.ok(device);
//////        }
//////
//////        try {
//////            Map<String, Object> resolvedProfile = deviceService.fetchResolvedProfile(device.getDeviceProfileId());
//////            return ResponseEntity.ok(Map.of(
//////                    "device", device,
//////                    "profile", resolvedProfile
//////            ));
//////        } catch (Exception e) {
//////            log.error("Failed to fetch profile for device {}: {}", id, e.getMessage());
//////            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//////                    .body(Map.of("device", device, "profileError", e.getMessage()));
//////        }
//////    }
//////
//////    @PutMapping("/{id}")
//////    public ResponseEntity<Device> updateDevice(@PathVariable("id") UUID id, @RequestBody Device deviceDetails) {
//////        Device updatedDevice = deviceService.updateDevice(id, deviceDetails);
//////        return updatedDevice != null ?
//////                new ResponseEntity<>(updatedDevice, HttpStatus.OK) :
//////                new ResponseEntity<>(HttpStatus.NOT_FOUND);
//////    }
//////
//////    @PutMapping("/{id}/state")
//////    public ResponseEntity<Device> updateDeviceState(@PathVariable("id") UUID id, @RequestParam DeviceState state) {
//////        Device updatedDevice = deviceService.updateDeviceState(id, state);
//////        return updatedDevice != null ?
//////                new ResponseEntity<>(updatedDevice, HttpStatus.OK) :
//////                new ResponseEntity<>(HttpStatus.NOT_FOUND);
//////    }
//////
//////    @PutMapping("/{id}/transition")
//////    public ResponseEntity<Device> transitionDeviceState(@PathVariable("id") UUID id, @RequestParam DeviceState newState) {
//////        Device updatedDevice = deviceService.transitionDeviceState(id, newState);
//////        return updatedDevice != null ?
//////                new ResponseEntity<>(updatedDevice, HttpStatus.OK) :
//////                new ResponseEntity<>(HttpStatus.NOT_FOUND);
//////    }
//////
//////    @DeleteMapping("/{id}")
//////    public ResponseEntity<Void> deleteDevice(@PathVariable("id") UUID id) {
//////        deviceService.deleteDevice(id);
//////        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
//////    }
//////
////////    @GetMapping
////////    public ResponseEntity<Page<Device>> getDevices(
////////            @RequestParam(name = "page", defaultValue = "0") int page,
////////            @RequestParam(name = "size", defaultValue = "10") int size,
////////            @RequestParam(name = "tenantId") UUID tenantId) {
////////
////////        Pageable pageable = PageRequest.of(page, size);
////////        Page<Device> devices = deviceService.getDevices(tenantId, pageable);
////////        return new ResponseEntity<>(devices, HttpStatus.OK);
////////    }
//////
//////    @GetMapping
//////    public ResponseEntity<Page<Map<String, Object>>> getDevicesWithProfiles(
//////            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
//////            @RequestParam(name = "size", required = false, defaultValue = "10") int size,
//////            @RequestParam(name = "tenantId") UUID tenantId,
//////            @RequestParam(required = false, name="withProfile", defaultValue = "false") boolean withProfile) {
//////
//////        Pageable pageable = PageRequest.of(page, size);
//////
//////        if (!withProfile) {
//////            Page<Device> devices = deviceService.getDevices(tenantId, pageable);
//////            return new ResponseEntity(devices, HttpStatus.OK);
//////        }
//////
//////        Page<Map<String, Object>> pageWithProfiles = deviceService.getDevicesWithProfiles(tenantId, pageable);
//////        return new ResponseEntity<>(pageWithProfiles, HttpStatus.OK);
//////    }
//////}
