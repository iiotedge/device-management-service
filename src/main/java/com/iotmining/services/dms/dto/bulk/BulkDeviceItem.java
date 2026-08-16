package com.iotmining.services.dms.dto.bulk;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One device row in a bulk-create request (JSON array or CSV import).
 * Only deviceName is mandatory; everything else falls back to the same
 * defaults single-device creation uses.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BulkDeviceItem {

    private String deviceName;
    private String category;
    private String deviceType;
    private String protocol;
    private String state;
    private Map<String, Object> attributes;
    private boolean isPublic;
    private UUID deviceProfileId;
    private UUID customerId;
    private Set<UUID> labelIds;

    /** Optional pre-set access token; generated when absent. */
    private String accessToken;

    /** Optional physical identity — creates the hardware-ID mapping atomically. */
    private String hardwareId;
    /** IMEI, MAC, SN or TOKEN (defaults to UNKNOWN when hardwareId is set without a type). */
    private String identityType;
}
