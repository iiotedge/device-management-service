package com.iotmining.services.dms.dto;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DeviceRequest {

    private String deviceName;
    private String category;
    private String deviceType;
    private String protocol;
    private Map<String, Object> attributes;
    private boolean isPublic;
    private UUID deviceProfileId;
    private UUID firmwareId;
    private UUID softwareId;
    private UUID customerId;
    private Set<UUID> labelIds;
}
