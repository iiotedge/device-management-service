package com.iotmining.services.dms.dto;

import com.iotmining.common.data.devices.DeviceCategory;
import com.iotmining.common.data.devices.DeviceProtocol;
import com.iotmining.common.data.devices.DeviceType;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class DeviceResponse {
    private UUID deviceId;
    private String deviceName;
    private DeviceCategory category;
    private DeviceType deviceType;
    private DeviceProtocol protocol;
    private String accessToken;
    private UUID firmwareId;
    private UUID softwareId;
    private UUID customerId;
}
