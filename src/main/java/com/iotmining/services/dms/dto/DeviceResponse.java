package com.iotmining.services.dms.dto;

import com.iotmining.common.data.devices.DeviceCategory;
import com.iotmining.common.data.devices.DeviceProtocol;
import com.iotmining.common.data.devices.DeviceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;


@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DeviceResponse {

    private UUID deviceId;
    private String deviceName;

    private DeviceCategory category;   // ✅ Enum
    private DeviceType deviceType;      // ✅ Enum
    private DeviceProtocol protocol;    // ✅ Enum

    private Map<String, Object> attributes;
    private String accessToken;
    private boolean isPublic;

    private UUID deviceProfileId;
    private UUID firmwareId;
    private UUID softwareId;
    private UUID customerId;

    private Set<UUID> labelIds;

    private Instant createdAt;
    private Instant updatedAt;
}


