package com.iotmining.services.dms.controller;

import com.iotmining.common.data.devices.DeviceState;

import com.iotmining.services.dms.model.Device;
import com.iotmining.services.dms.services.DeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {


    private final DeviceService deviceService;

    @Autowired
    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PostMapping
    public ResponseEntity<Device> addDevice(@RequestBody Device device) {
        Device savedDevice = deviceService.saveDevice(device);
        return new ResponseEntity<>(savedDevice, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Device> getDeviceById(@PathVariable("id") UUID id) {
        Device device = deviceService.getDeviceById(id);
        return device != null ?
                new ResponseEntity<>(device, HttpStatus.OK) :
                new ResponseEntity<>(HttpStatus.NOT_FOUND);
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

    @GetMapping
    public ResponseEntity<Page<Device>> getDevices(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "tenantId") UUID tenantId) {

        Pageable pageable = PageRequest.of(page, size);
        Page<Device> devices = deviceService.getDevices(tenantId, pageable);
        return new ResponseEntity<>(devices, HttpStatus.OK);
    }
}
