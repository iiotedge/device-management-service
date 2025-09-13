package com.iotmining.services.dms.controller;

import com.iotmining.common.data.devices.DeviceState;
import com.iotmining.services.dms.model.Device;
import com.iotmining.services.dms.services.DeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @PostMapping
    public ResponseEntity<Device> addDevice(@RequestBody Device device) {
        Device savedDevice = deviceService.saveDevice(device);
        return new ResponseEntity<>(savedDevice, HttpStatus.CREATED);
    }

    // ✅ Updated to support ?withProfile=true
    @GetMapping("/{id}")
    public ResponseEntity<?> getDeviceById(
            @PathVariable("id") UUID id,
            @RequestParam(name = "withProfile", required = false, defaultValue = "false") boolean withProfile) {

        Device device = deviceService.getDeviceById(id);
        if (device == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Device not found");
        }

        if (!withProfile || device.getDeviceProfileId() == null) {
            return ResponseEntity.ok(device);
        }

        try {
            Map<String, Object> resolvedProfile = deviceService.fetchResolvedProfile(device.getDeviceProfileId());
            return ResponseEntity.ok(Map.of(
                    "device", device,
                    "profile", resolvedProfile
            ));
        } catch (Exception e) {
            log.error("Failed to fetch profile for device {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("device", device, "profileError", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Device> updateDevice(@PathVariable("id") UUID id, @RequestBody Device deviceDetails) {
        Device updatedDevice = deviceService.updateDevice(id, deviceDetails);
        return updatedDevice != null ?
                new ResponseEntity<>(updatedDevice, HttpStatus.OK) :
                new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    @PutMapping("/{id}/state")
    public ResponseEntity<Device> updateDeviceState(@PathVariable("id") UUID id, @RequestParam DeviceState state) {
        Device updatedDevice = deviceService.updateDeviceState(id, state);
        return updatedDevice != null ?
                new ResponseEntity<>(updatedDevice, HttpStatus.OK) :
                new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    @PutMapping("/{id}/transition")
    public ResponseEntity<Device> transitionDeviceState(@PathVariable("id") UUID id, @RequestParam DeviceState newState) {
        Device updatedDevice = deviceService.transitionDeviceState(id, newState);
        return updatedDevice != null ?
                new ResponseEntity<>(updatedDevice, HttpStatus.OK) :
                new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDevice(@PathVariable("id") UUID id) {
        deviceService.deleteDevice(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

//    @GetMapping
//    public ResponseEntity<Page<Device>> getDevices(
//            @RequestParam(name = "page", defaultValue = "0") int page,
//            @RequestParam(name = "size", defaultValue = "10") int size,
//            @RequestParam(name = "tenantId") UUID tenantId) {
//
//        Pageable pageable = PageRequest.of(page, size);
//        Page<Device> devices = deviceService.getDevices(tenantId, pageable);
//        return new ResponseEntity<>(devices, HttpStatus.OK);
//    }

    @GetMapping
    public ResponseEntity<Page<Map<String, Object>>> getDevicesWithProfiles(
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "size", required = false, defaultValue = "10") int size,
            @RequestParam(name = "tenantId") UUID tenantId,
            @RequestParam(required = false, name="withProfile", defaultValue = "false") boolean withProfile) {

        Pageable pageable = PageRequest.of(page, size);

        if (!withProfile) {
            Page<Device> devices = deviceService.getDevices(tenantId, pageable);
            return new ResponseEntity(devices, HttpStatus.OK);
        }

        Page<Map<String, Object>> pageWithProfiles = deviceService.getDevicesWithProfiles(tenantId, pageable);
        return new ResponseEntity<>(pageWithProfiles, HttpStatus.OK);
    }
}
