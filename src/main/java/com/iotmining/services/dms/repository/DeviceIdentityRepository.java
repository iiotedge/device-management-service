package com.iotmining.services.dms.repository;

import com.iotmining.services.dms.model.DeviceIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceIdentityRepository extends JpaRepository<DeviceIdentity, UUID> {

    @Query("SELECT di.device.deviceId FROM DeviceIdentity di WHERE di.hardwareId = :hardwareId AND di.active = true")
    Optional<UUID> findDeviceIdByHardwareId(@Param("hardwareId") String hardwareId);

    Optional<DeviceIdentity> findByDevice_DeviceIdAndActiveTrue(UUID deviceId);

    Optional<DeviceIdentity> findByDevice_DeviceId(UUID deviceId);

    List<DeviceIdentity> findAllByDevice_DeviceIdOrderByCreatedAtDesc(UUID deviceId);
}
