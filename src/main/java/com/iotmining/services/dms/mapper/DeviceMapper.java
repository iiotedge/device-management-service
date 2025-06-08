package com.iotmining.services.dms.mapper;


import com.iotmining.services.dms.dto.DeviceResponse;
import com.iotmining.services.dms.model.Device;

public class DeviceMapper {

    public static DeviceResponse toResponse(Device device) {
        return DeviceResponse.builder()
                .deviceId(device.getDeviceId())
                .deviceName(device.getDeviceName())
                .category(device.getCategory())
                .deviceType(device.getDeviceType())
                .protocol(device.getProtocol())
                .attributes(device.getAttributes())
                .accessToken(device.getAccessToken())
                .isPublic(device.isPublic())
                .deviceProfileId(device.getDeviceProfileId())
                .firmwareId(device.getFirmwareId())
                .softwareId(device.getSoftwareId())
                .customerId(device.getCustomerId())
                .labelIds(device.getLabelIds())
                .createdAt(device.getCreatedAt())
                .updatedAt(device.getUpdatedAt())
                .build();
    }
}
