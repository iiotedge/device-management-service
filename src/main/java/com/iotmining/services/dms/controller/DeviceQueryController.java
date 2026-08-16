package com.iotmining.services.dms.controller;

import com.iotmining.common.data.devices.DeviceCategory;
import com.iotmining.common.data.devices.DeviceProtocol;
import com.iotmining.common.data.devices.DeviceState;
import com.iotmining.common.data.devices.DeviceType;
import com.iotmining.services.dms.model.Device;
import com.iotmining.services.dms.services.DeviceQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Read-side fleet endpoints (search / stats / export). All NEW paths —
 * the legacy list endpoints in DeviceController are untouched.
 */
@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
public class DeviceQueryController {

    /** Cap page size so a single call can't dump an entire fleet. */
    private static final int MAX_PAGE_SIZE = 200;

    private final DeviceQueryService queryService;

    @GetMapping("/search")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN') or " +
            "authentication.principal['tenantId'] == #tenantId.toString()")
    public ResponseEntity<Page<Device>> search(
            @RequestParam(name = "tenantId") UUID tenantId,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "state", required = false) DeviceState state,
            @RequestParam(name = "category", required = false) DeviceCategory category,
            @RequestParam(name = "deviceType", required = false) DeviceType deviceType,
            @RequestParam(name = "protocol", required = false) DeviceProtocol protocol,
            @RequestParam(name = "profileId", required = false) UUID profileId,
            @RequestParam(name = "customerId", required = false) UUID customerId,
            @RequestParam(name = "isPublic", required = false) Boolean isPublic,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {

        int cappedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<Device> result = queryService.search(tenantId, q, state, category, deviceType,
                protocol, profileId, customerId, isPublic, PageRequest.of(Math.max(page, 0), cappedSize));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN') or " +
            "authentication.principal['tenantId'] == #tenantId.toString()")
    public ResponseEntity<Map<String, Object>> stats(@RequestParam(name = "tenantId") UUID tenantId) {
        return ResponseEntity.ok(queryService.stats(tenantId));
    }

    @GetMapping(value = "/export", produces = "text/csv")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN') or " +
            "(hasRole('ROLE_TENANT_ADMIN') and authentication.principal['tenantId'] == #tenantId.toString())")
    public ResponseEntity<String> exportCsv(@RequestParam(name = "tenantId") UUID tenantId) {
        String csv = queryService.exportCsv(tenantId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header("Content-Disposition", "attachment; filename=\"devices-" + tenantId + ".csv\"")
                .body(csv);
    }
}
