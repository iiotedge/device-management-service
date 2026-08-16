package com.iotmining.services.dms.services;

import com.iotmining.common.base.context.TenantContext;
import com.iotmining.common.data.devices.DeviceCategory;
import com.iotmining.common.data.devices.DeviceState;
import com.iotmining.common.data.devices.DeviceType;
import com.iotmining.services.dms.client.DeviceProfileClient;
import com.iotmining.services.dms.model.Customer;
import com.iotmining.services.dms.model.Device;
import com.iotmining.services.dms.repository.CustomerRepository;
import com.iotmining.services.dms.repository.DeviceRepository;
import com.iotmining.services.dms.utils.TokenUtil;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final CustomerRepository customerRepository;
    private final DeviceIdentityService identityService;

    // Inject the Feign Client instead of RestTemplate
    private final DeviceProfileClient deviceProfileClient;

    @Transactional
    public Device saveDevice(Device device) {
        validateDeviceTypeForCategory(device.getCategory(), device.getDeviceType());

        if (device.getDeviceProfileId() != null) {
            validateDeviceProfile(device.getDeviceProfileId());
        }

        if (device.getState() == null) device.setState(DeviceState.IDLE);
        if (device.getTenantId() == null) device.setTenantId(UUID.fromString(TenantContext.getTenantId()));
        if (device.getAccessToken() == null || device.getAccessToken().isEmpty()) {
            device.setAccessToken(TokenUtil.generateDeviceToken(16));
        }

        return deviceRepository.save(device);
    }

    public Device getDeviceById(UUID deviceId) {
        return deviceRepository.findById(deviceId).orElse(null);
    }

    @Transactional
    public Device updateDevice(UUID deviceId, Device deviceDetails) {
        Device device = getDeviceById(deviceId);
        if (device == null) return null;

        validateDeviceTypeForCategory(deviceDetails.getCategory(), deviceDetails.getDeviceType());

        if (deviceDetails.getDeviceProfileId() != null && !deviceDetails.getDeviceProfileId().equals(device.getDeviceProfileId())) {
            validateDeviceProfile(deviceDetails.getDeviceProfileId());
        }

        device.setDeviceName(deviceDetails.getDeviceName());
        device.setDeviceType(deviceDetails.getDeviceType());
        device.setCategory(deviceDetails.getCategory());
        device.setProtocol(deviceDetails.getProtocol());
        device.setAttributes(deviceDetails.getAttributes());
        device.setDeviceProfileId(deviceDetails.getDeviceProfileId());
        device.setPublic(deviceDetails.isPublic());
        device.setLocation(deviceDetails.getLocation());

        return deviceRepository.save(device);
    }

    @Transactional
    public Device updateDeviceState(UUID deviceId, DeviceState newState) {
        Device device = getDeviceById(deviceId);
        if (device != null) {
            device.setState(newState);
            return deviceRepository.save(device);
        }
        return null;
    }

    @Transactional
    public void deleteDevice(UUID deviceId) {
        // Remove hardware-identity mappings first: they hold an FK to the device
        // (delete would otherwise fail) and would keep routing telemetry to a
        // deleted device via the ingestion cache.
        identityService.deleteAllMappingsForDevice(deviceId);
        deviceRepository.deleteById(deviceId);
    }

    public Page<Device> getDevices(UUID tenantId, Pageable pageable) {
        return deviceRepository.findByTenantId(tenantId, pageable);
    }

    // =========================================================================================
    // PROFILE RESOLUTION LOGIC (Cleaned up using Feign!)
    // =========================================================================================

    private void validateDeviceProfile(UUID profileId) {
        try {
            ResponseEntity<Void> response = deviceProfileClient.validateProfile(profileId);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalArgumentException("Invalid device profile ID: " + profileId);
            }
        } catch (FeignException.NotFound e) {
            // Cleanly handle a 404 from the Device Profile Service
            log.warn("Validation failed: Device Profile {} not found.", profileId);
            throw new IllegalArgumentException("The specified Device Profile does not exist.");
        } catch (FeignException.Forbidden | FeignException.Unauthorized e) {
            // Cleanly handle security errors
            log.error("Validation failed: Unauthorized access to profile {}.", profileId);
            throw new SecurityException("You do not have permission to use this Device Profile.");
        } catch (Exception e) {
            log.error("Service communication error during profile validation: {}", e.getMessage());
            throw new IllegalStateException("Error communicating with Device Profile Service. Please try again.", e);
        }
    }

    public Map<String, Object> fetchResolvedProfile(UUID profileId) {
        try {
            return deviceProfileClient.fetchResolvedProfile(profileId);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to fetch resolved profile for " + profileId, e);
        }
    }

    public Page<Map<String, Object>> getDevicesWithProfiles(UUID tenantId, Pageable pageable) {
        Page<Device> page = deviceRepository.findByTenantId(tenantId, pageable);

        List<UUID> profileIds = page.getContent().stream()
                .map(Device::getDeviceProfileId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<UUID, Map<String, Object>> profileMap = Collections.emptyMap();

        if (!profileIds.isEmpty()) {
            try {
                // One clean method call!
                Map<UUID, Map<String, Object>> result = deviceProfileClient.fetchBatchResolvedProfiles(profileIds);
                if (result != null) {
                    profileMap = result;
                }
            } catch (Exception e) {
                log.error("Error batch fetching device profiles via Feign", e);
            }
        }

        Map<UUID, Map<String, Object>> finalProfileMap = profileMap;
        List<Map<String, Object>> resultList = page.getContent().stream()
                .map(device -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("device", device);
                    UUID profileId = device.getDeviceProfileId();
                    if (profileId != null && finalProfileMap.containsKey(profileId)) {
                        map.put("profile", finalProfileMap.get(profileId));
                    }
                    return map;
                })
                .collect(Collectors.toList());

        return new org.springframework.data.domain.PageImpl<>(resultList, pageable, page.getTotalElements());
    }

    public Page<Map<String, Object>> getDevicesByProfile(UUID tenantId, UUID profileId, Pageable pageable) {
        // 1. Fetch filtered list from DB
        Page<Device> devices = deviceRepository.findByTenantIdAndDeviceProfileId(tenantId, profileId, pageable);

        // 2. Wrap with profile metadata (needed for dashboard redirects)
        return devices.map(device -> {
            Map<String, Object> map = new HashMap<>();
            map.put("device", device);
            try {
                // Attach the resolved profile so the 'Open Dashboard' button works
                map.put("profile", fetchResolvedProfile(profileId));
            } catch (Exception e) {
                log.error("Could not resolve profile for filtered list: {}", e.getMessage());
            }
            return map;
        });
    }

    // =========================================================================================
    // ASSIGNMENT LOGIC
    // =========================================================================================

    @Transactional
    public Device assignPrimaryOwner(UUID deviceId, UUID customerId, UUID currentTenantId) {
        Device device = getDeviceById(deviceId);
        if (device == null) throw new IllegalArgumentException("Device not found");
        if (!device.getTenantId().equals(currentTenantId)) throw new SecurityException("Unauthorized");

        Customer customerProxy = customerRepository.getReferenceById(customerId);
        device.setCustomer(customerProxy);
        return deviceRepository.save(device);
    }

    @Transactional
    public Device addSharedCustomer(UUID deviceId, String assignedId, UUID currentTenantId) {
        Device device = getDeviceById(deviceId);
        if (device == null) throw new IllegalArgumentException("Device not found");
        if (!device.getTenantId().equals(currentTenantId)) throw new SecurityException("Unauthorized");

        device.getAssignedCustomers().add(assignedId);
        return deviceRepository.save(device);
    }

    @Transactional
    public Device removeSharedCustomer(UUID deviceId, String assignedId, UUID currentTenantId) {
        Device device = getDeviceById(deviceId);
        if (device == null) throw new IllegalArgumentException("Device not found");
        if (!device.getTenantId().equals(currentTenantId)) throw new SecurityException("Unauthorized");

        device.getAssignedCustomers().remove(assignedId);
        return deviceRepository.save(device);
    }

    private void validateDeviceTypeForCategory(DeviceCategory category, DeviceType deviceType) {
        // Warn-only for now: existing clients send mismatched pairs today, and
        // rejecting them would break those flows. Tighten to an exception once
        // callers are audited (see docs/IMPROVEMENTS.md B5).
        if (category != null && deviceType != null && deviceType.getCategory() != category) {
            log.warn("Device type {} belongs to category {}, but {} was supplied",
                    deviceType, deviceType.getCategory(), category);
        }
    }
}


//package com.iotmining.services.dms.services;
//
//import com.iotmining.common.base.context.TenantContext;
//import com.iotmining.common.data.devices.DeviceCategory;
//import com.iotmining.common.data.devices.DeviceState;
//
//
//import com.iotmining.common.data.devices.DeviceType;
//import com.iotmining.services.dms.model.Device;
//import com.iotmining.services.dms.repository.DeviceRepository;
//import com.iotmining.services.dms.utils.TokenUtil;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.data.domain.Page;
//import org.springframework.data.domain.Pageable;
//import org.springframework.stereotype.Service;
//
//import java.time.Instant;
//
//import java.util.UUID;
//
//@Slf4j
//@Service
//public class DeviceService {
//
//    private final DeviceRepository deviceRepository;
//
//    public DeviceService(DeviceRepository deviceRepository) {
//        this.deviceRepository = deviceRepository;
//    }
//
//    // Save a new device with category-based validation and token generation
//    public Device saveDevice(Device device) {
//        validateDeviceTypeForCategory(device.getCategory(), device.getDeviceType());
//
//        // Set default state if new device
//        if (device.getState() == null) {
//            device.setState(DeviceState.IDLE);
//        }
//
//        // Set Tenant ID from context if not already set
//        if (device.getTenantId() == null) {
//            device.setTenantId(UUID.fromString(TenantContext.getTenantId()));
//        }
//
//        // Set access token if not already present
//        if (device.getAccessToken() == null || device.getAccessToken().isEmpty()) {
//            device.setAccessToken(TokenUtil.generateDeviceToken(16));
//        }
//
//        // Handle createdAt and updatedAt
//        Instant now = Instant.now();
//        if (device.getCreatedAt() == null) {
//            device.setCreatedAt(now);
//        }
//        device.setUpdatedAt(now);
//
//        return deviceRepository.save(device);
//    }
//
//    // Get device by ID
//    public Device getDeviceById(UUID deviceId) {
//        return deviceRepository.findById(deviceId).orElse(null);
//    }
//
//    // Update device state
//    public Device updateDeviceState(UUID deviceId, DeviceState newState) {
//        Device device = getDeviceById(deviceId);
//        if (device != null) {
//            device.setState(newState);
//            return saveDevice(device);
//        }
//        return null;
//    }
//
//    // Delete device by ID
//    public void deleteDevice(UUID deviceId) {
//        deviceRepository.deleteById(deviceId);
//    }
//
//    // Update device details
//    public Device updateDevice(UUID deviceId, Device deviceDetails) {
//        Device device = getDeviceById(deviceId);
//        if (device != null) {
//            validateDeviceTypeForCategory(deviceDetails.getCategory(), deviceDetails.getDeviceType());
//
//            device.setDeviceName(deviceDetails.getDeviceName());
//            device.setDeviceType(deviceDetails.getDeviceType());
//            device.setState(deviceDetails.getState());
//            device.setProtocol(deviceDetails.getProtocol());
//            device.setCategory(deviceDetails.getCategory());
//            device.setAttributes(deviceDetails.getAttributes());
//
//            return saveDevice(device);
//        }
//        return null;
//    }
//
//    // Validate that the device type belongs to the selected category
//    private void validateDeviceTypeForCategory(DeviceCategory category, DeviceType deviceType) {
//        // TODO: You need to write DeviceType.getCategory() style mapping if using String enums
//        // For now assume validation done somewhere else.
//    }
//
//    // Transition device to a new state with validation
//    public Device transitionDeviceState(UUID deviceId, DeviceState newState) {
//        Device device = getDeviceById(deviceId);
//        if (device != null) {
//            switch (newState) {
//                case ON:
//                    if (device.getState() == DeviceState.OFF || device.getState() == DeviceState.IDLE) {
//                        device.setState(DeviceState.ON);
//                    } else {
//                        throw new IllegalStateException("Invalid state transition to ON");
//                    }
//                    break;
//                case OFF:
//                    if (device.getState() == DeviceState.ON) {
//                        device.setState(DeviceState.OFF);
//                    } else {
//                        throw new IllegalStateException("Invalid state transition to OFF");
//                    }
//                    break;
//                case ERROR:
//                case IDLE:
//                    device.setState(newState);
//                    break;
//                default:
//                    throw new IllegalArgumentException("Invalid device state");
//            }
//            return saveDevice(device);
//        }
//        return null;
//    }
//
//    public Page<Device> getDevices(UUID tenantId, Pageable pageable) {
//        return deviceRepository.findByTenantId(tenantId, pageable);
//    }
//}
