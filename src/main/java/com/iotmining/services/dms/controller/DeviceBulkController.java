package com.iotmining.services.dms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iotmining.common.data.devices.DeviceState;
import com.iotmining.services.dms.dto.bulk.BulkDeviceItem;
import com.iotmining.services.dms.dto.bulk.BulkOperationResponse;
import com.iotmining.services.dms.model.Device;
import com.iotmining.services.dms.services.DeviceBulkService;
import com.iotmining.services.dms.services.DeviceService;
import com.iotmining.services.dms.utils.CsvUtil;
import com.iotmining.services.dms.utils.TokenUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fleet-scale write operations. All endpoints are NEW — nothing here changes
 * the existing single-device API used by other IIoTEdge services.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
public class DeviceBulkController {

    private final DeviceBulkService bulkService;
    private final DeviceService deviceService;
    private final ObjectMapper objectMapper;

    // =====================================================================
    // BULK CREATE (JSON)
    // =====================================================================
    @PostMapping("/bulk")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN') or " +
            "(hasRole('ROLE_TENANT_ADMIN') and authentication.principal['tenantId'] == #tenantId.toString())")
    public ResponseEntity<BulkOperationResponse> bulkCreate(
            @RequestParam(name = "tenantId") UUID tenantId,
            @RequestBody List<BulkDeviceItem> items) {

        BulkOperationResponse response = bulkService.bulkCreate(tenantId, items);
        // 207-style semantics: 201 only when every row succeeded
        HttpStatus status = response.getFailed() == 0 ? HttpStatus.CREATED : HttpStatus.MULTI_STATUS;
        return new ResponseEntity<>(response, status);
    }

    // =====================================================================
    // CSV IMPORT
    // Header row required. Recognized columns:
    // deviceName,category,deviceType,protocol,state,deviceProfileId,customerId,
    // isPublic,accessToken,hardwareId,identityType,attributes(JSON)
    // =====================================================================
    @PostMapping(value = "/import", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN') or " +
            "(hasRole('ROLE_TENANT_ADMIN') and authentication.principal['tenantId'] == #tenantId.toString())")
    public ResponseEntity<BulkOperationResponse> importCsv(
            @RequestParam(name = "tenantId") UUID tenantId,
            @RequestParam(name = "file") MultipartFile file) throws IOException {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Upload a non-empty CSV file in the 'file' part");
        }

        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        List<Map<String, String>> rows = CsvUtil.parseWithHeader(content);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("CSV contains a header but no data rows");
        }

        List<BulkDeviceItem> items = new ArrayList<>(rows.size());
        for (Map<String, String> row : rows) {
            items.add(toItem(row));
        }

        BulkOperationResponse response = bulkService.bulkCreate(tenantId, items);
        HttpStatus status = response.getFailed() == 0 ? HttpStatus.CREATED : HttpStatus.MULTI_STATUS;
        return new ResponseEntity<>(response, status);
    }

    private BulkDeviceItem toItem(Map<String, String> row) {
        Map<String, Object> attributes = null;
        String rawAttributes = blankToNull(row.get("attributes"));
        if (rawAttributes != null) {
            try {
                attributes = objectMapper.readValue(rawAttributes, Map.class);
            } catch (Exception e) {
                // surfaced per-row by the bulk engine as a FAILED result
                throw new IllegalArgumentException("attributes column is not valid JSON: " + e.getMessage());
            }
        }

        return BulkDeviceItem.builder()
                .deviceName(blankToNull(row.get("devicename")))
                .category(blankToNull(row.get("category")))
                .deviceType(blankToNull(row.get("devicetype")))
                .protocol(blankToNull(row.get("protocol")))
                .state(blankToNull(row.get("state")))
                .deviceProfileId(parseUuid(row.get("deviceprofileid")))
                .customerId(parseUuid(row.get("customerid")))
                .isPublic(Boolean.parseBoolean(row.getOrDefault("ispublic", "false")))
                .accessToken(blankToNull(row.get("accesstoken")))
                .hardwareId(blankToNull(row.get("hardwareid")))
                .identityType(blankToNull(row.get("identitytype")))
                .attributes(attributes)
                .build();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static UUID parseUuid(String s) {
        String v = blankToNull(s);
        return v != null ? UUID.fromString(v) : null;
    }

    // =====================================================================
    // BULK DELETE
    // =====================================================================
    @PostMapping("/bulk-delete")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN') or hasRole('ROLE_TENANT_ADMIN')")
    public ResponseEntity<BulkOperationResponse> bulkDelete(@RequestBody List<UUID> deviceIds) {
        return ResponseEntity.ok(bulkService.bulkDelete(deviceIds));
    }

    // =====================================================================
    // STATE MANAGEMENT
    // =====================================================================
    @PostMapping("/bulk-state")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN') or hasRole('ROLE_TENANT_ADMIN')")
    public ResponseEntity<BulkOperationResponse> bulkState(
            @RequestParam(name = "state") DeviceState state,
            @RequestBody List<UUID> deviceIds) {
        return ResponseEntity.ok(bulkService.bulkUpdateState(deviceIds, state));
    }

    @PutMapping("/{id}/state")
    @PreAuthorize("@deviceSecurity.canAccessDevice(authentication, #id)")
    public ResponseEntity<Device> updateState(
            @PathVariable("id") UUID id,
            @RequestParam(name = "state") DeviceState state) {
        Device updated = deviceService.updateDeviceState(id, state);
        return updated != null ? ResponseEntity.ok(updated) : ResponseEntity.notFound().build();
    }

    // =====================================================================
    // CREDENTIAL ROTATION
    // NOTE: ingestion caches auth:token:* in Redis — the old token remains
    // valid until that cache entry expires/is evicted (see IMPROVEMENTS.md F7).
    // =====================================================================
    @PostMapping("/{id}/credentials/rotate")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN') or " +
            "(hasRole('ROLE_TENANT_ADMIN') and @deviceSecurity.canAccessDevice(authentication, #id))")
    public ResponseEntity<Map<String, String>> rotateCredentials(@PathVariable("id") UUID id) {
        Device device = deviceService.getDeviceById(id);
        if (device == null) {
            return ResponseEntity.notFound().build();
        }
        String newToken = TokenUtil.generateDeviceToken(16);
        device.setAccessToken(newToken);
        deviceService.saveDevice(device);

        Map<String, String> body = new HashMap<>();
        body.put("deviceId", id.toString());
        body.put("accessToken", newToken);
        log.info("[Credentials] Rotated access token for device {}", id);
        return ResponseEntity.ok(body);
    }
}
