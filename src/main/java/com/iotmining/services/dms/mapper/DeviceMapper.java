package com.iotmining.services.dms.mapper;

import com.iotmining.services.dms.dto.DeviceResponse;
import com.iotmining.services.dms.model.Device;

public final class DeviceMapper {

    private DeviceMapper() {
    }

    public static DeviceResponse toResponse(Device device) {
        if (device == null) {
            return null;
        }

        return DeviceResponse.builder()
                .deviceId(device.getDeviceId())
                .deviceName(device.getDeviceName())
                .category(device.getCategory())
                .deviceType(device.getDeviceType())
                .protocol(device.getProtocol())
                .accessToken(device.getAccessToken())
                .firmwareId(device.getFirmware() != null ? device.getFirmware().getId() : null)
                .softwareId(device.getSoftware() != null ? device.getSoftware().getId() : null)
                .customerId(device.getCustomer() != null ? device.getCustomer().getId() : null)
                .build();
    }
}
