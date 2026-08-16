package com.iotmining.services.dms.security;

import com.iotmining.services.dms.model.Device;
import com.iotmining.services.dms.repository.DeviceRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component("deviceSecurity")
@RequiredArgsConstructor
@Slf4j
public class DeviceSecurityEvaluator {

    private final DeviceRepository deviceRepository;

    public boolean canAccessDevice(Authentication authentication, UUID deviceId) {
        if (authentication == null || !authentication.isAuthenticated() || deviceId == null) return false;

        // 1. Internal & Super Admin Bypasses
        if ("SYSTEM_SERVICE".equals(authentication.getPrincipal())) return true;
        if (isSuperAdmin(authentication)) return true;

        String userTenantIdStr = extractClaim(authentication, "tenantId");
        String userIdStr = extractClaim(authentication, "userId");

        if (userTenantIdStr == null) return false;

        Device device = deviceRepository.findById(deviceId).orElse(null);
        if (device == null) return false;

        // Rule 1: Direct Tenant Ownership (The Enterprise)
        boolean isTenantOwner = userTenantIdStr.equals(device.getTenantId().toString());

        // Rule 2: Customer Ownership (Navigating the new Foreign Key safely)
        boolean isCustomerOwner = device.getCustomer() != null &&
                device.getCustomer().getId() != null &&
                device.getCustomer().getId().toString().equals(userIdStr);

        // Rule 3: Explicit Assignment (Sub-tenants / specific users)
        boolean isAssigned = device.getAssignedCustomers() != null &&
                (device.getAssignedCustomers().contains(userIdStr) ||
                        device.getAssignedCustomers().contains(userTenantIdStr));

        if (isTenantOwner || isCustomerOwner || isAssigned || device.isPublic()) {
            return true;
        }

        log.warn("Security Alert: Access denied for user {} to device {}", userIdStr, deviceId);
        return false;
    }

    private String extractClaim(Authentication authentication, String claimKey) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof Claims claims) return claims.get(claimKey, String.class);
        return null;
    }

    private boolean isSuperAdmin(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof Claims claims) return Boolean.TRUE.equals(claims.get("superAdmin", Boolean.class));
        return false;
    }
}

//package com.iotmining.services.dms.security;
//
//import com.iotmining.services.dms.model.Device;
//import com.iotmining.services.dms.repository.DeviceRepository;
//import io.jsonwebtoken.Claims;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.security.core.Authentication;
//import org.springframework.stereotype.Component;
//import java.util.UUID;
//
//@Component("deviceSecurity")
//@RequiredArgsConstructor
//@Slf4j
//public class DeviceSecurityEvaluator {
//
//    private final DeviceRepository deviceRepository;
//
//    public boolean canAccessDevice(Authentication authentication, UUID deviceId) {
//        if (authentication == null || !authentication.isAuthenticated() || deviceId == null) return false;
//
//        if ("SYSTEM_SERVICE".equals(authentication.getPrincipal())) return true;
//        if (isSuperAdmin(authentication)) return true;
//
//        String userTenantIdStr = extractClaim(authentication, "tenantId");
//        String userIdStr = extractClaim(authentication, "userId");
//
//        if (userTenantIdStr == null) return false;
//
//        Device device = deviceRepository.findById(deviceId).orElse(null);
//        if (device == null) return false;
//
//        // Rule 1: Direct Ownership
//        boolean isTenantOwner = userTenantIdStr.equals(device.getTenantId().toString());
//
//        // Rule 2: Explicit Assignment (Check if Set contains UserID or TenantID)
//        boolean isAssigned = device.getAssignedCustomers() != null &&
//                (device.getAssignedCustomers().contains(userIdStr) ||
//                        device.getAssignedCustomers().contains(userTenantIdStr));
//
//        if (isTenantOwner || isAssigned || device.isPublic()) {
//            return true;
//        }
//
//        log.warn("Security Alert: Access denied for user {} to device {}", userIdStr, deviceId);
//        return false;
//    }
//
//    private String extractClaim(Authentication authentication, String claimKey) {
//        Object principal = authentication.getPrincipal();
//        if (principal instanceof Claims claims) return claims.get(claimKey, String.class);
//        return null;
//    }
//
//    private boolean isSuperAdmin(Authentication authentication) {
//        Object principal = authentication.getPrincipal();
//        if (principal instanceof Claims claims) return Boolean.TRUE.equals(claims.get("superAdmin", Boolean.class));
//        return false;
//    }
//}
//
////package com.iotmining.services.dms.security;
////
////import com.iotmining.services.dms.model.Device;
////import com.iotmining.services.dms.repository.DeviceRepository;
////import io.jsonwebtoken.Claims;
////import lombok.RequiredArgsConstructor;
////import lombok.extern.slf4j.Slf4j;
////import org.springframework.security.core.Authentication;
////import org.springframework.stereotype.Component;
////
////import java.util.UUID;
////
////@Component("deviceSecurity")
////@RequiredArgsConstructor
////@Slf4j
////public class DeviceSecurityEvaluator {
////
////    private final DeviceRepository deviceRepository;
////
////    public boolean canAccessDevice(Authentication authentication, UUID deviceId) {
////        if (authentication == null || !authentication.isAuthenticated() || deviceId == null) {
////            return false;
////        }
////
////        // 1. Internal System Bypass
////        if ("SYSTEM_SERVICE".equals(authentication.getPrincipal())) {
////            return true;
////        }
////
////        // 2. Super Admin Bypass
////        if (isSuperAdmin(authentication)) {
////            return true;
////        }
////
////        // 3. Extract IDs from JWT Claims
////        String userTenantIdStr = extractClaim(authentication, "tenantId");
////        String userIdStr = extractClaim(authentication, "userId");
////
////        if (userTenantIdStr == null) {
////            return false;
////        }
////
////        UUID userTenantId = UUID.fromString(userTenantIdStr);
////
////        // 4. Fetch the Device
////        Device device = deviceRepository.findById(deviceId).orElse(null);
////        if (device == null) {
////            return false;
////        }
////
////        // 5. Evaluate Permissions
////        boolean isTenantOwner = userTenantId.equals(device.getTenantId());
////
////        // Check if the device is assigned to the specific user (customer)
////        boolean isAssignedCustomer = device.getCustomerId() != null &&
////                device.getCustomerId().toString().equals(userIdStr);
////
////        if (isTenantOwner || isAssignedCustomer || device.isPublic()) {
////            return true;
////        }
////
////        log.warn("Security Alert: Access denied for user {} to device {}", userIdStr, deviceId);
////        return false;
////    }
////
////    private String extractClaim(Authentication authentication, String claimKey) {
////        Object principal = authentication.getPrincipal();
////        if (principal instanceof Claims claims) {
////            return claims.get(claimKey, String.class);
////        }
////        return null;
////    }
////
////    private boolean isSuperAdmin(Authentication authentication) {
////        Object principal = authentication.getPrincipal();
////        if (principal instanceof Claims claims) {
////            return Boolean.TRUE.equals(claims.get("superAdmin", Boolean.class));
////        }
////        return false;
////    }
////}