package com.iotmining.services.dms.mapper;

import com.iotmining.services.dms.dto.DeviceResponse;
import com.iotmining.services.dms.model.Device;
import com.iotmining.services.dms.support.TestDataFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceMapperTest {

    @Test
    void nullDeviceMapsToNull() {
        assertThat(DeviceMapper.toResponse(null)).isNull();
    }

    @Test
    void mapsScalarFields() {
        Device device = TestDataFactory.device();

        DeviceResponse response = DeviceMapper.toResponse(device);

        assertThat(response.getDeviceId()).isEqualTo(device.getDeviceId());
        assertThat(response.getDeviceName()).isEqualTo("pump-sensor-1");
        assertThat(response.getCategory()).isEqualTo(device.getCategory());
        assertThat(response.getDeviceType()).isEqualTo(device.getDeviceType());
        assertThat(response.getProtocol()).isEqualTo(device.getProtocol());
        assertThat(response.getAccessToken()).isEqualTo("token-abc123");
    }

    @Test
    void nullForeignKeysStayNullInsteadOfThrowing() {
        Device device = TestDataFactory.device(); // no firmware/software/customer set

        DeviceResponse response = DeviceMapper.toResponse(device);

        assertThat(response.getFirmwareId()).isNull();
        assertThat(response.getSoftwareId()).isNull();
        assertThat(response.getCustomerId()).isNull();
    }

    @Test
    void mapsCustomerIdWhenOwnerAssigned() {
        Device device = TestDataFactory.device();
        device.setCustomer(TestDataFactory.customer());

        DeviceResponse response = DeviceMapper.toResponse(device);

        assertThat(response.getCustomerId()).isEqualTo(TestDataFactory.CUSTOMER_ID);
    }
}
