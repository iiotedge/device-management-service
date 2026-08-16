package com.iotmining.services.dms.services;

import com.iotmining.common.data.devices.DeviceCategory;
import com.iotmining.common.data.devices.DeviceProtocol;
import com.iotmining.common.data.devices.DeviceState;
import com.iotmining.common.data.devices.DeviceType;
import com.iotmining.services.dms.dto.bulk.BulkDeviceItem;
import com.iotmining.services.dms.dto.bulk.BulkItemResult;
import com.iotmining.services.dms.dto.bulk.BulkOperationResponse;
import com.iotmining.services.dms.model.Device;
import com.iotmining.services.dms.repository.CustomerRepository;
import com.iotmining.services.dms.repository.DeviceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Fleet-scale operations: bulk create (JSON/CSV), bulk delete, bulk state.
 *
 * Every item runs in its OWN transaction (via TransactionTemplate) so a single
 * bad row reports FAILED without rolling back the rest of the batch — the
 * behavior operators expect from an import tool.
 */
@Slf4j
@Service
public class DeviceBulkService {

    /** Hard cap per request; keeps memory and transaction time bounded. */
    public static final int MAX_BATCH_SIZE = 500;

    private final DeviceService deviceService;
    private final DeviceRepository deviceRepository;
    private final CustomerRepository customerRepository;
    private final DeviceIdentityService identityService;
    private final TransactionTemplate transactionTemplate;

    public DeviceBulkService(DeviceService deviceService,
                             DeviceRepository deviceRepository,
                             CustomerRepository customerRepository,
                             DeviceIdentityService identityService,
                             PlatformTransactionManager transactionManager) {
        this.deviceService = deviceService;
        this.deviceRepository = deviceRepository;
        this.customerRepository = customerRepository;
        this.identityService = identityService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // =====================================================================
    // BULK CREATE
    // =====================================================================

    public BulkOperationResponse bulkCreate(UUID tenantId, List<BulkDeviceItem> items) {
        requireBatch(items);

        List<BulkItemResult> results = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            results.add(createOne(tenantId, i, items.get(i)));
        }
        BulkOperationResponse response = BulkOperationResponse.of(results);
        log.info("[Bulk Create] tenant={} total={} created={} skipped={} failed={}",
                tenantId, response.getTotal(), response.getSucceeded(),
                response.getSkipped(), response.getFailed());
        return response;
    }

    private BulkItemResult createOne(UUID tenantId, int index, BulkDeviceItem item) {
        String name = item.getDeviceName();
        try {
            if (name == null || name.isBlank()) {
                return failed(index, name, "deviceName is required");
            }

            // Idempotent re-import: an existing device with the same name is skipped, not duplicated.
            if (deviceRepository.existsByTenantIdAndDeviceName(tenantId, name.trim())) {
                return BulkItemResult.builder()
                        .index(index).deviceName(name)
                        .status(BulkItemResult.Status.SKIPPED)
                        .error("A device with this name already exists in the tenant")
                        .build();
            }

            if (item.getCustomerId() != null
                    && !customerRepository.existsByIdAndTenantId(item.getCustomerId(), tenantId)) {
                return failed(index, name, "customerId does not belong to this tenant: " + item.getCustomerId());
            }

            Device device = toDevice(tenantId, item);

            // Device + optional hardware identity commit or roll back TOGETHER.
            Device saved = transactionTemplate.execute(tx -> {
                Device persisted = deviceService.saveDevice(device);
                if (item.getCustomerId() != null) {
                    persisted.setCustomer(customerRepository.getReferenceById(item.getCustomerId()));
                    persisted = deviceRepository.save(persisted);
                }
                if (item.getHardwareId() != null && !item.getHardwareId().isBlank()) {
                    identityService.upsertMapping(
                            persisted.getDeviceId(),
                            item.getHardwareId().trim(),
                            item.getIdentityType());
                }
                return persisted;
            });

            return BulkItemResult.builder()
                    .index(index).deviceName(name)
                    .deviceId(saved != null ? saved.getDeviceId() : null)
                    .status(BulkItemResult.Status.CREATED)
                    .build();

        } catch (Exception e) {
            log.warn("[Bulk Create] row {} ('{}') failed: {}", index, name, e.getMessage());
            return failed(index, name, e.getMessage());
        }
    }

    private Device toDevice(UUID tenantId, BulkDeviceItem item) {
        Device device = new Device();
        device.setTenantId(tenantId);
        device.setDeviceName(item.getDeviceName().trim());
        device.setCategory(parseEnum(DeviceCategory.class, item.getCategory(), "category"));
        device.setDeviceType(parseEnum(DeviceType.class, item.getDeviceType(), "deviceType"));
        device.setProtocol(parseEnum(DeviceProtocol.class, item.getProtocol(), "protocol"));
        DeviceState state = parseEnum(DeviceState.class, item.getState(), "state");
        device.setState(state); // saveDevice defaults null -> IDLE
        device.setAttributes(item.getAttributes());
        device.setPublic(item.isPublic());
        device.setDeviceProfileId(item.getDeviceProfileId());
        device.setLabelIds(item.getLabelIds());
        if (item.getAccessToken() != null && !item.getAccessToken().isBlank()) {
            device.setAccessToken(item.getAccessToken().trim());
        }
        return device;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw, String field) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid " + field + " '" + raw + "'");
        }
    }

    // =====================================================================
    // BULK DELETE
    // =====================================================================

    public BulkOperationResponse bulkDelete(List<UUID> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            throw new IllegalArgumentException("Request body must be a non-empty array of device IDs");
        }
        if (deviceIds.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("Batch too large: " + deviceIds.size() + " (max " + MAX_BATCH_SIZE + ")");
        }

        List<BulkItemResult> results = new ArrayList<>(deviceIds.size());
        for (int i = 0; i < deviceIds.size(); i++) {
            UUID id = deviceIds.get(i);
            final int index = i;
            try {
                if (!deviceRepository.existsById(id)) {
                    results.add(BulkItemResult.builder()
                            .index(index).deviceId(id)
                            .status(BulkItemResult.Status.SKIPPED)
                            .error("Device not found").build());
                    continue;
                }
                transactionTemplate.executeWithoutResult(tx -> deviceService.deleteDevice(id));
                results.add(BulkItemResult.builder()
                        .index(index).deviceId(id)
                        .status(BulkItemResult.Status.DELETED).build());
            } catch (Exception e) {
                log.warn("[Bulk Delete] {} failed: {}", id, e.getMessage());
                results.add(BulkItemResult.builder()
                        .index(index).deviceId(id)
                        .status(BulkItemResult.Status.FAILED)
                        .error(e.getMessage()).build());
            }
        }
        return BulkOperationResponse.of(results);
    }

    // =====================================================================
    // BULK STATE
    // =====================================================================

    public BulkOperationResponse bulkUpdateState(List<UUID> deviceIds, DeviceState state) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            throw new IllegalArgumentException("Request body must be a non-empty array of device IDs");
        }
        if (deviceIds.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("Batch too large: " + deviceIds.size() + " (max " + MAX_BATCH_SIZE + ")");
        }

        List<BulkItemResult> results = new ArrayList<>(deviceIds.size());
        for (int i = 0; i < deviceIds.size(); i++) {
            UUID id = deviceIds.get(i);
            final int index = i;
            try {
                Device updated = transactionTemplate.execute(tx -> deviceService.updateDeviceState(id, state));
                if (updated == null) {
                    results.add(BulkItemResult.builder()
                            .index(index).deviceId(id)
                            .status(BulkItemResult.Status.SKIPPED)
                            .error("Device not found").build());
                } else {
                    results.add(BulkItemResult.builder()
                            .index(index).deviceId(id).deviceName(updated.getDeviceName())
                            .status(BulkItemResult.Status.UPDATED).build());
                }
            } catch (Exception e) {
                results.add(BulkItemResult.builder()
                        .index(index).deviceId(id)
                        .status(BulkItemResult.Status.FAILED)
                        .error(e.getMessage()).build());
            }
        }
        return BulkOperationResponse.of(results);
    }

    private static void requireBatch(List<BulkDeviceItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Request body must be a non-empty array of devices");
        }
        if (items.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("Batch too large: " + items.size() + " (max " + MAX_BATCH_SIZE + ")");
        }
    }

    private static BulkItemResult failed(int index, String name, String reason) {
        return BulkItemResult.builder()
                .index(index).deviceName(name)
                .status(BulkItemResult.Status.FAILED)
                .error(reason)
                .build();
    }
}
