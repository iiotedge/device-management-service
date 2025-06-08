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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;

import java.util.UUID;

@Slf4j
@Service
public class DeviceService {

    private final DeviceRepository deviceRepository;

    public DeviceService(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    // Save a new device with category-based validation and token generation
    public Device saveDevice(Device device) {
        validateDeviceTypeForCategory(device.getCategory(), device.getDeviceType());

        // Set default state if new device
        if (device.getState() == null) {
            device.setState(DeviceState.IDLE);
        }

        // Set Tenant ID from context if not already set
        if (device.getTenantId() == null) {
            device.setTenantId(UUID.fromString(TenantContext.getTenantId()));
        }

        // Set access token if not already present
        if (device.getAccessToken() == null || device.getAccessToken().isEmpty()) {
            device.setAccessToken(TokenUtil.generateDeviceToken(16));
        }

        // Handle createdAt and updatedAt
        Instant now = Instant.now();
        if (device.getCreatedAt() == null) {
            device.setCreatedAt(now);
        }
        device.setUpdatedAt(now);

        return deviceRepository.save(device);
    }

    // Get device by ID
    public Device getDeviceById(UUID deviceId) {
        return deviceRepository.findById(deviceId).orElse(null);
    }

    // Update device state
    public Device updateDeviceState(UUID deviceId, DeviceState newState) {
        Device device = getDeviceById(deviceId);
        if (device != null) {
            device.setState(newState);
            return saveDevice(device);
        }
        return null;
    }

    // Delete device by ID
    public void deleteDevice(UUID deviceId) {
        deviceRepository.deleteById(deviceId);
    }

    // Update device details
    public Device updateDevice(UUID deviceId, Device deviceDetails) {
        Device device = getDeviceById(deviceId);
        if (device != null) {
            validateDeviceTypeForCategory(deviceDetails.getCategory(), deviceDetails.getDeviceType());

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

    // Validate that the device type belongs to the selected category
    private void validateDeviceTypeForCategory(DeviceCategory category, DeviceType deviceType) {
        // TODO: You need to write DeviceType.getCategory() style mapping if using String enums
        // For now assume validation done somewhere else.
    }

    // Transition device to a new state with validation
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
}
