package com.iotmining.services.dms.services;

import com.iotmining.common.base.context.TenantContext;
import com.iotmining.common.data.devices.DeviceCategory;
import com.iotmining.common.data.devices.DeviceState;
import com.iotmining.common.data.devices.DeviceType;
import com.iotmining.services.dms.model.Device;
import com.iotmining.services.dms.repository.DeviceRepository;
import com.iotmining.services.dms.utils.TokenUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final RestTemplate restTemplate;

    private static final String PROFILE_BASE_URL = "http://localhost:8090/api/device-profiles/";


    // Save a new device with profile validation
    public Device saveDevice(Device device) {
        validateDeviceTypeForCategory(device.getCategory(), device.getDeviceType());

        if (device.getDeviceProfileId() != null) {
            validateDeviceProfile(device.getDeviceProfileId());
        }

        if (device.getState() == null) {
            device.setState(DeviceState.IDLE);
        }

        if (device.getTenantId() == null) {
            device.setTenantId(UUID.fromString(TenantContext.getTenantId()));
        }

        if (device.getAccessToken() == null || device.getAccessToken().isEmpty()) {
            device.setAccessToken(TokenUtil.generateDeviceToken(16));
        }

        Instant now = Instant.now();
        if (device.getCreatedAt() == null) {
            device.setCreatedAt(now);
        }
        device.setUpdatedAt(now);

        return deviceRepository.save(device);
    }

    public Device getDeviceById(UUID deviceId) {
        return deviceRepository.findById(deviceId).orElse(null);
    }

    public Device updateDevice(UUID deviceId, Device deviceDetails) {
        Device device = getDeviceById(deviceId);
        if (device != null) {
            validateDeviceTypeForCategory(deviceDetails.getCategory(), deviceDetails.getDeviceType());

            if (deviceDetails.getDeviceProfileId() != null) {
                validateDeviceProfile(deviceDetails.getDeviceProfileId());
                device.setDeviceProfileId(deviceDetails.getDeviceProfileId());
            }

            device.setDeviceName(deviceDetails.getDeviceName());
            device.setDeviceType(deviceDetails.getDeviceType());
            device.setState(deviceDetails.getState());
            device.setProtocol(deviceDetails.getProtocol());
            device.setCategory(deviceDetails.getCategory());
            device.setAttributes(deviceDetails.getAttributes());

            return saveDevice(device);
        }
        return null;
    }

    public Device updateDeviceState(UUID deviceId, DeviceState newState) {
        Device device = getDeviceById(deviceId);
        if (device != null) {
            device.setState(newState);
            return saveDevice(device);
        }
        return null;
    }

    public void deleteDevice(UUID deviceId) {
        deviceRepository.deleteById(deviceId);
    }

    public Device transitionDeviceState(UUID deviceId, DeviceState newState) {
        Device device = getDeviceById(deviceId);
        if (device != null) {
            switch (newState) {
                case ON:
                    if (device.getState() == DeviceState.OFF || device.getState() == DeviceState.IDLE) {
                        device.setState(DeviceState.ON);
                    } else {
                        throw new IllegalStateException("Invalid state transition to ON");
                    }
                    break;
                case OFF:
                    if (device.getState() == DeviceState.ON) {
                        device.setState(DeviceState.OFF);
                    } else {
                        throw new IllegalStateException("Invalid state transition to OFF");
                    }
                    break;
                case ERROR:
                case IDLE:
                    device.setState(newState);
                    break;
                default:
                    throw new IllegalArgumentException("Invalid device state");
            }
            return saveDevice(device);
        }
        return null;
    }

    public Page<Device> getDevices(UUID tenantId, Pageable pageable) {
        return deviceRepository.findByTenantId(tenantId, pageable);
    }

    private void validateDeviceProfile(UUID profileId) {
        try {
            ResponseEntity<Void> response = restTemplate.getForEntity(PROFILE_BASE_URL + profileId, Void.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalArgumentException("Invalid device profile ID: " + profileId);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Error validating device profile: " + profileId, e);
        }
    }

    public Map<String, Object> fetchResolvedProfile(UUID profileId) {
        try {
            return restTemplate.exchange(
                    PROFILE_BASE_URL + profileId + "/resolved",
                    org.springframework.http.HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    }).getBody();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to fetch resolved profile for " + profileId, e);
        }
    }

    // (Stub for category-based mapping)
    private void validateDeviceTypeForCategory(DeviceCategory category, DeviceType deviceType) {
        // TODO: enforce deviceType belongs to category
    }

    public Page<Map<String, Object>> getDevicesWithProfiles(UUID tenantId, Pageable pageable) {
        // 1. Fetch devices
        Page<Device> page = deviceRepository.findByTenantId(tenantId, pageable);

        // 2. Collect all unique profile IDs
        List<UUID> profileIds = page.getContent().stream()
                .map(Device::getDeviceProfileId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        // 3. Prepare the profile map
        Map<UUID, Map<String, Object>> profileMap = Collections.emptyMap();

        if (!profileIds.isEmpty()) {
            try {
                HttpEntity<List<UUID>> request = new HttpEntity<>(profileIds);
                ResponseEntity<Map<UUID, Map<String, Object>>> response = restTemplate.exchange(
                        PROFILE_BASE_URL + "batch-resolved",
                        HttpMethod.POST,
                        request,
                        new ParameterizedTypeReference<Map<UUID, Map<String, Object>>>() {}
                );
                if (response.getBody() != null) {
                    profileMap = response.getBody();
                }
            } catch (Exception e) {
                log.error("Error batch fetching device profiles", e);
                profileMap = Collections.emptyMap();
            }
        }

        // 4. Compose final result list
        Map<UUID, Map<String, Object>> finalProfileMap = profileMap; // For lambda capture
        List<Map<String, Object>> result = page.getContent().stream()
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

        // 5. Return paged result
        return new org.springframework.data.domain.PageImpl<>(result, pageable, page.getTotalElements());
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
