package com.iotmining.services.dms.services;

import com.iotmining.common.data.devices.DeviceState;
import com.iotmining.services.dms.client.DeviceProfileClient;
import com.iotmining.services.dms.model.Customer;
import com.iotmining.services.dms.model.Device;
import com.iotmining.services.dms.repository.CustomerRepository;
import com.iotmining.services.dms.repository.DeviceRepository;
import com.iotmining.services.dms.services.DeviceIdentityService;
import com.iotmining.services.dms.support.TestDataFactory;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock private DeviceRepository deviceRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private DeviceIdentityService identityService;
    @Mock private DeviceProfileClient deviceProfileClient;

    private DeviceService service;

    @BeforeEach
    void setUp() {
        service = new DeviceService(deviceRepository, customerRepository, identityService, deviceProfileClient);
    }

    // =====================================================================
    // saveDevice
    // =====================================================================

    @Test
    void saveDeviceAppliesDefaults() {
        Device device = TestDataFactory.device();
        device.setState(null);
        device.setAccessToken(null);
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> inv.getArgument(0));

        Device saved = service.saveDevice(device);

        assertThat(saved.getState()).isEqualTo(DeviceState.IDLE);
        assertThat(saved.getAccessToken()).hasSize(16).matches("[A-Za-z0-9]+");
    }

    @Test
    void saveDeviceKeepsExplicitValues() {
        Device device = TestDataFactory.device();
        device.setState(DeviceState.ON);
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> inv.getArgument(0));

        Device saved = service.saveDevice(device);

        assertThat(saved.getState()).isEqualTo(DeviceState.ON);
        assertThat(saved.getAccessToken()).isEqualTo("token-abc123");
    }

    @Test
    void saveDeviceValidatesProfileWhenPresent() {
        Device device = TestDataFactory.device();
        device.setDeviceProfileId(TestDataFactory.PROFILE_ID);
        when(deviceProfileClient.validateProfile(TestDataFactory.PROFILE_ID)).thenReturn(ResponseEntity.ok().build());
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> inv.getArgument(0));

        service.saveDevice(device);

        verify(deviceProfileClient).validateProfile(TestDataFactory.PROFILE_ID);
    }

    @Test
    void unknownProfileIsRejectedAsBadRequest() {
        Device device = TestDataFactory.device();
        device.setDeviceProfileId(TestDataFactory.PROFILE_ID);
        when(deviceProfileClient.validateProfile(any())).thenThrow(mock(FeignException.NotFound.class));

        assertThatThrownBy(() -> service.saveDevice(device))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void forbiddenProfileAccessBecomesSecurityException() {
        Device device = TestDataFactory.device();
        device.setDeviceProfileId(TestDataFactory.PROFILE_ID);
        when(deviceProfileClient.validateProfile(any())).thenThrow(mock(FeignException.Forbidden.class));

        assertThatThrownBy(() -> service.saveDevice(device))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void profileServiceOutageBecomesIllegalState() {
        Device device = TestDataFactory.device();
        device.setDeviceProfileId(TestDataFactory.PROFILE_ID);
        when(deviceProfileClient.validateProfile(any())).thenThrow(new RuntimeException("connection refused"));

        assertThatThrownBy(() -> service.saveDevice(device))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Device Profile Service");
    }

    // =====================================================================
    // updateDevice
    // =====================================================================

    @Test
    void updateDeviceCopiesEditableFieldsIncludingLocation() {
        Device existing = TestDataFactory.device();
        when(deviceRepository.findById(TestDataFactory.DEVICE_ID)).thenReturn(Optional.of(existing));
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> inv.getArgument(0));

        Device details = TestDataFactory.device();
        details.setDeviceName("renamed");
        details.setLocation("plant-7/hall-2"); // regression: location was silently dropped before
        details.setAttributes(Map.of("fw", "2.0"));
        details.setPublic(true);

        Device updated = service.updateDevice(TestDataFactory.DEVICE_ID, details);

        assertThat(updated.getDeviceName()).isEqualTo("renamed");
        assertThat(updated.getLocation()).isEqualTo("plant-7/hall-2");
        assertThat(updated.getAttributes()).containsEntry("fw", "2.0");
        assertThat(updated.isPublic()).isTrue();
    }

    @Test
    void updateMissingDeviceReturnsNull() {
        when(deviceRepository.findById(any())).thenReturn(Optional.empty());
        assertThat(service.updateDevice(UUID.randomUUID(), TestDataFactory.device())).isNull();
    }

    @Test
    void updateValidatesProfileOnlyWhenChanged() {
        Device existing = TestDataFactory.device();
        existing.setDeviceProfileId(TestDataFactory.PROFILE_ID);
        when(deviceRepository.findById(TestDataFactory.DEVICE_ID)).thenReturn(Optional.of(existing));
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> inv.getArgument(0));

        Device sameProfile = TestDataFactory.device();
        sameProfile.setDeviceProfileId(TestDataFactory.PROFILE_ID);
        service.updateDevice(TestDataFactory.DEVICE_ID, sameProfile);
        verify(deviceProfileClient, never()).validateProfile(any());

        UUID newProfile = UUID.randomUUID();
        Device changedProfile = TestDataFactory.device();
        changedProfile.setDeviceProfileId(newProfile);
        when(deviceProfileClient.validateProfile(newProfile)).thenReturn(ResponseEntity.ok().build());
        service.updateDevice(TestDataFactory.DEVICE_ID, changedProfile);
        verify(deviceProfileClient).validateProfile(newProfile);
    }

    // =====================================================================
    // state & delete
    // =====================================================================

    @Test
    void updateDeviceStatePersistsNewState() {
        Device device = TestDataFactory.device();
        when(deviceRepository.findById(TestDataFactory.DEVICE_ID)).thenReturn(Optional.of(device));
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> inv.getArgument(0));

        Device updated = service.updateDeviceState(TestDataFactory.DEVICE_ID, DeviceState.ON);

        assertThat(updated.getState()).isEqualTo(DeviceState.ON);
    }

    @Test
    void updateStateOnMissingDeviceReturnsNull() {
        when(deviceRepository.findById(any())).thenReturn(Optional.empty());
        assertThat(service.updateDeviceState(UUID.randomUUID(), DeviceState.OFF)).isNull();
    }

    @Test
    void deleteDeviceCleansIdentityMappingsBeforeTheRow() {
        service.deleteDevice(TestDataFactory.DEVICE_ID);

        // Order matters: identity rows hold an FK to the device
        InOrder order = inOrder(identityService, deviceRepository);
        order.verify(identityService).deleteAllMappingsForDevice(TestDataFactory.DEVICE_ID);
        order.verify(deviceRepository).deleteById(TestDataFactory.DEVICE_ID);
    }

    // =====================================================================
    // profile-joined listings
    // =====================================================================

    @Test
    void getDevicesWithProfilesMergesBatchProfileLookup() {
        Device withProfile = TestDataFactory.device();
        withProfile.setDeviceProfileId(TestDataFactory.PROFILE_ID);
        Device withoutProfile = TestDataFactory.device();
        withoutProfile.setDeviceId(UUID.randomUUID());
        withoutProfile.setDeviceProfileId(null);

        Pageable pageable = PageRequest.of(0, 10);
        when(deviceRepository.findByTenantId(TestDataFactory.TENANT_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(withProfile, withoutProfile), pageable, 2));
        when(deviceProfileClient.fetchBatchResolvedProfiles(List.of(TestDataFactory.PROFILE_ID)))
                .thenReturn(Map.of(TestDataFactory.PROFILE_ID, Map.of("name", "thermo-profile")));

        Page<Map<String, Object>> page = service.getDevicesWithProfiles(TestDataFactory.TENANT_ID, pageable);

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent().get(0)).containsKey("profile");
        assertThat(page.getContent().get(1)).doesNotContainKey("profile");
    }

    @Test
    void profileServiceFailureStillReturnsDevices() {
        Device device = TestDataFactory.device();
        device.setDeviceProfileId(TestDataFactory.PROFILE_ID);
        Pageable pageable = PageRequest.of(0, 10);
        when(deviceRepository.findByTenantId(TestDataFactory.TENANT_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(device), pageable, 1));
        when(deviceProfileClient.fetchBatchResolvedProfiles(any())).thenThrow(new RuntimeException("down"));

        Page<Map<String, Object>> page = service.getDevicesWithProfiles(TestDataFactory.TENANT_ID, pageable);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0)).containsKey("device").doesNotContainKey("profile");
    }

    // =====================================================================
    // assignment
    // =====================================================================

    @Test
    void assignPrimaryOwnerRejectsForeignTenant() {
        Device device = TestDataFactory.device();
        when(deviceRepository.findById(TestDataFactory.DEVICE_ID)).thenReturn(Optional.of(device));

        assertThatThrownBy(() -> service.assignPrimaryOwner(
                TestDataFactory.DEVICE_ID, TestDataFactory.CUSTOMER_ID, UUID.randomUUID()))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void assignPrimaryOwnerSetsCustomerReference() {
        Device device = TestDataFactory.device();
        Customer customer = TestDataFactory.customer();
        when(deviceRepository.findById(TestDataFactory.DEVICE_ID)).thenReturn(Optional.of(device));
        when(customerRepository.getReferenceById(TestDataFactory.CUSTOMER_ID)).thenReturn(customer);
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> inv.getArgument(0));

        Device updated = service.assignPrimaryOwner(
                TestDataFactory.DEVICE_ID, TestDataFactory.CUSTOMER_ID, TestDataFactory.TENANT_ID);

        assertThat(updated.getCustomer()).isSameAs(customer);
    }

    @Test
    void sharedCustomersCanBeAddedAndRemoved() {
        Device device = TestDataFactory.device();
        when(deviceRepository.findById(TestDataFactory.DEVICE_ID)).thenReturn(Optional.of(device));
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> inv.getArgument(0));

        service.addSharedCustomer(TestDataFactory.DEVICE_ID, "user-9", TestDataFactory.TENANT_ID);
        assertThat(device.getAssignedCustomers()).contains("user-9");

        service.removeSharedCustomer(TestDataFactory.DEVICE_ID, "user-9", TestDataFactory.TENANT_ID);
        assertThat(device.getAssignedCustomers()).doesNotContain("user-9");
    }

    @Test
    void assignmentOnMissingDeviceIsBadRequest() {
        when(deviceRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addSharedCustomer(UUID.randomUUID(), "u", TestDataFactory.TENANT_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
