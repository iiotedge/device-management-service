package com.iotmining.services.dms.services;

import com.iotmining.services.dms.model.Device;
import com.iotmining.services.dms.model.DeviceIdentity;
import com.iotmining.services.dms.repository.DeviceIdentityRepository;
import com.iotmining.services.dms.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class DeviceIdentityService {

    private final DeviceIdentityRepository identityRepository;
    private final DeviceRepository deviceRepository;

    /**
     * -------------------------------------------------------------------
     * THE HOT PATH (Used by Ingestion Pipeline / MQTT Interceptors)
     * -------------------------------------------------------------------
     * Resolves a physical hardware ID to an internal System UUID.
     * Heavily cached to prevent DB bottlenecks during high-throughput telemetry.
     */
    @Cacheable(value = "hardware_to_uuid", key = "#hardwareId", unless = "#result == null")
    @Transactional(readOnly = true)
    public Optional<UUID> resolveDeviceId(String hardwareId) {
        log.trace("[IdentityService] Cache miss. Resolving Hardware ID: {} against Database", hardwareId);
        return identityRepository.findDeviceIdByHardwareId(hardwareId);
    }

    /**
     * -------------------------------------------------------------------
     * UI READ PATH (Used by React Frontend Panel)
     * -------------------------------------------------------------------
     */
    @Transactional(readOnly = true)
    public Optional<DeviceIdentity> findByDeviceId(UUID deviceId) {
//        return identityRepository.findByDevice_DeviceId(deviceId);
        return identityRepository.findByDevice_DeviceIdAndActiveTrue(deviceId);
    }

    /**
     * -------------------------------------------------------------------
     * UI WRITE PATH (Used by Admins to map new hardware to existing digital twins)
     * -------------------------------------------------------------------
     */
//    @Transactional
//    public DeviceIdentity upsertMapping(UUID deviceId, String newHardwareId, String identityType) {
//        log.info("[IdentityService] Upserting mapping for Device [{}] -> Hardware ID [{}]", deviceId, newHardwareId);
//
//        // 1. Verify the System Device actually exists
//        Device device = deviceRepository.findById(deviceId)
//                .orElseThrow(() -> new IllegalArgumentException("Device UUID not found: " + deviceId));
//
//        // 2. Find existing mapping to see if we are Updating or Creating
//        Optional<DeviceIdentity> existingOpt = identityRepository.findByDevice_DeviceId(deviceId);
//
//        DeviceIdentity identity;
//        if (existingOpt.isPresent()) {
//            identity = existingOpt.get();
//
//            // If the hardware ID actually changed, we MUST evict the OLD id from the Redis cache
//            String oldHardwareId = identity.getHardwareId();
//            if (!oldHardwareId.equals(newHardwareId)) {
//                evictCache(oldHardwareId);
//            }
//        } else {
//            identity = new DeviceIdentity();
//            identity.setDevice(device);
//            identity.setTenantId(device.getTenantId()); // Inherit tenant from device
//        }
//
//        // 3. Apply new values
//        identity.setHardwareId(newHardwareId);
//        identity.setIdentityType(identityType != null ? identityType.toUpperCase() : "UNKNOWN");
//
//        // 4. Save (Catches unique constraint violations if another device already claimed this IMEI)
//        try {
//            DeviceIdentity saved = identityRepository.saveAndFlush(identity);
//            log.debug("[IdentityService] Successfully saved identity mapping for Device [{}]", deviceId);
//            return saved;
//        } catch (DataIntegrityViolationException e) {
//            log.error("[IdentityService] Failed to map Hardware ID [{}]. It is already claimed by another device.", newHardwareId);
//            throw new IllegalStateException("Hardware ID '" + newHardwareId + "' is already in use.");
//        }
//    }
    @Transactional
    public DeviceIdentity upsertMapping(UUID deviceId, String newHardwareId, String identityType) {
        log.info("[IdentityService] Upserting mapping for Device [{}] -> Hardware ID [{}]", deviceId, newHardwareId);

        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Device UUID not found: " + deviceId));

        // 1. Find the CURRENTLY active mapping
        Optional<DeviceIdentity> activeOpt = identityRepository.findByDevice_DeviceIdAndActiveTrue(deviceId);

        if (activeOpt.isPresent()) {
            DeviceIdentity activeIdentity = activeOpt.get();

            // If the hardware ID hasn't changed, just return it (no-op)
            if (activeIdentity.getHardwareId().equals(newHardwareId)) {
                return activeIdentity;
            }

            // 2. Soft-disable the old mapping
            activeIdentity.setActive(false);
            identityRepository.save(activeIdentity);

            // 3. Evict the old hardware ID from the Redis Cache so it stops routing!
            evictCache(activeIdentity.getHardwareId());
        }

        // 4. Create the NEW active mapping
        DeviceIdentity newIdentity = new DeviceIdentity();
        newIdentity.setDevice(device);
        newIdentity.setTenantId(device.getTenantId());
        newIdentity.setHardwareId(newHardwareId);
        newIdentity.setIdentityType(identityType != null ? identityType.toUpperCase() : "UNKNOWN");
        newIdentity.setActive(true);

        try {
            DeviceIdentity saved = identityRepository.saveAndFlush(newIdentity);
            log.debug("[IdentityService] Successfully saved identity mapping for Device [{}]", deviceId);
            return saved;
//            return identityRepository.saveAndFlush(newIdentity);
        } catch (DataIntegrityViolationException e) {
            log.error("[IdentityService] Hardware ID [{}] already claimed.", newHardwareId);
            throw new IllegalStateException("Hardware ID '" + newHardwareId + "' is already in use.");
        }
    }
    /**
     * -------------------------------------------------------------------
     * CLEANUP PATH (Used when a device is deleted from the system)
     * -------------------------------------------------------------------
     */
    @Transactional
    public void deleteMappingByDeviceId(UUID deviceId) {
        identityRepository.findByDevice_DeviceId(deviceId).ifPresent(identity -> {
            log.info("[IdentityService] Deleting identity mapping for Device [{}]", deviceId);
            evictCache(identity.getHardwareId());
            identityRepository.delete(identity);
        });
    }

    /**
     * Deletes EVERY mapping (active + soft-disabled history) for a device.
     * Used before device deletion: the rows hold an FK to the device, and
     * findByDevice_DeviceId(...) breaks once a device has swap history
     * (multiple rows -> non-unique result).
     */
    @Transactional
    public void deleteAllMappingsForDevice(UUID deviceId) {
        var mappings = identityRepository.findAllByDevice_DeviceIdOrderByCreatedAtDesc(deviceId);
        if (mappings.isEmpty()) {
            return;
        }
        log.info("[IdentityService] Deleting {} identity mapping(s) for Device [{}]", mappings.size(), deviceId);
        mappings.forEach(identity -> {
            evictCache(identity.getHardwareId());
            identityRepository.delete(identity);
        });
    }

    /**
     * Helper to manually evict Redis cache entries when hardware is swapped out in the field.
     */
    @CacheEvict(value = "hardware_to_uuid", key = "#hardwareId")
    public void evictCache(String hardwareId) {
        log.debug("[IdentityService] Evicted Hardware ID [{}] from Redis resolution cache", hardwareId);
    }
}