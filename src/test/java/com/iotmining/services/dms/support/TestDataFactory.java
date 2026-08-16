package com.iotmining.services.dms.support;

import com.iotmining.common.data.devices.DeviceCategory;
import com.iotmining.common.data.devices.DeviceProtocol;
import com.iotmining.common.data.devices.DeviceState;
import com.iotmining.common.data.devices.DeviceType;
import com.iotmining.services.dms.model.Customer;
import com.iotmining.services.dms.model.Device;
import com.iotmining.services.dms.model.DeviceIdentity;
import io.jsonwebtoken.Claims;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Central factory for test fixtures so individual tests stay focused on
 * behavior instead of object wiring. Mirrors the auth-service convention.
 */
public final class TestDataFactory {

    public static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID DEVICE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final UUID CUSTOMER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    public static final UUID PROFILE_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private TestDataFactory() {
    }

    public static Device device() {
        Device device = new Device();
        device.setDeviceId(DEVICE_ID);
        device.setTenantId(TENANT_ID);
        device.setDeviceName("pump-sensor-1");
        device.setCategory(DeviceCategory.CLIMATE_CONTROL);
        device.setDeviceType(DeviceType.THERMOSTAT);
        device.setState(DeviceState.IDLE);
        device.setProtocol(DeviceProtocol.MQTT);
        device.setAccessToken("token-abc123");
        device.setAssignedCustomers(new HashSet<>());
        return device;
    }

    public static Customer customer() {
        Customer customer = new Customer();
        customer.setId(CUSTOMER_ID);
        customer.setTenantId(TENANT_ID);
        customer.setName("Acme Corp");
        customer.setEmail("ops@acme.example");
        return customer;
    }

    public static DeviceIdentity identity(Device device, String hardwareId) {
        DeviceIdentity identity = new DeviceIdentity();
        identity.setId(UUID.randomUUID());
        identity.setDevice(device);
        identity.setTenantId(device.getTenantId());
        identity.setHardwareId(hardwareId);
        identity.setIdentityType("IMEI");
        identity.setActive(true);
        return identity;
    }

    /** JWT-style Authentication whose principal carries the given claims. */
    public static UsernamePasswordAuthenticationToken jwtAuth(String tenantId, String userId, boolean superAdmin) {
        Claims claims = mock(Claims.class);
        when(claims.get("tenantId", String.class)).thenReturn(tenantId);
        when(claims.get("userId", String.class)).thenReturn(userId);
        when(claims.get("superAdmin", Boolean.class)).thenReturn(superAdmin ? Boolean.TRUE : null);
        return new UsernamePasswordAuthenticationToken(claims, null,
                List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN")));
    }
}
