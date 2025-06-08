package com.iotmining.services.dms.repository;

import com.iotmining.services.dms.model.Device;
import com.iotmining.common.data.devices.DeviceCategory;
import com.iotmining.common.data.devices.DeviceState;
import com.iotmining.common.data.devices.DeviceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DeviceRepository extends JpaRepository<Device, UUID> {

    List<Device> findByCategory(DeviceCategory category);
    List<Device> findByDeviceType(DeviceType deviceType);
    List<Device> findByState(DeviceState state);
    List<Device> findByTenantId(UUID tenantId); // now supported
    Page<Device> findByTenantId(UUID tenantId, Pageable pageable);
}
