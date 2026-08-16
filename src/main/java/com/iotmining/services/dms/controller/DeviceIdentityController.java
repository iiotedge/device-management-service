package com.iotmining.services.dms.controller;

import com.iotmining.services.dms.dto.IdentityRequest;
import com.iotmining.services.dms.model.DeviceIdentity;
import com.iotmining.services.dms.services.DeviceIdentityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/devices/{deviceId}/identity")
@RequiredArgsConstructor
public class DeviceIdentityController {

    private final DeviceIdentityService identityService;

    // GET the mapping for the React Panel
    @GetMapping
    public ResponseEntity<DeviceIdentity> getIdentity(@PathVariable("deviceId") UUID deviceId) {
        return identityService.findByDeviceId(deviceId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // PUT to update/create the mapping from the React Panel
    @PutMapping
    public ResponseEntity<DeviceIdentity> upsertIdentity(
            @PathVariable("deviceId") UUID deviceId,
            @Valid @RequestBody IdentityRequest payload) {

        DeviceIdentity saved = identityService.upsertMapping(
                deviceId,
                payload.getHardwareId(),
                payload.getIdentityType()
        );
        return ResponseEntity.ok(saved);
    }
}