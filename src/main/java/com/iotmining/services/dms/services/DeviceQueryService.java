package com.iotmining.services.dms.services;

import com.iotmining.common.data.devices.DeviceCategory;
import com.iotmining.common.data.devices.DeviceProtocol;
import com.iotmining.common.data.devices.DeviceState;
import com.iotmining.common.data.devices.DeviceType;
import com.iotmining.services.dms.model.Device;
import com.iotmining.services.dms.repository.DeviceRepository;
import com.iotmining.services.dms.utils.CsvUtil;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-side fleet operations: filtered search, statistics, CSV export.
 * Additive service — legacy list endpoints in DeviceController are untouched.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceQueryService {

    private final DeviceRepository deviceRepository;

    // =====================================================================
    // SEARCH
    // =====================================================================

    @Transactional(readOnly = true)
    public Page<Device> search(UUID tenantId,
                               String q,
                               DeviceState state,
                               DeviceCategory category,
                               DeviceType deviceType,
                               DeviceProtocol protocol,
                               UUID profileId,
                               UUID customerId,
                               Boolean isPublic,
                               Pageable pageable) {

        Specification<Device> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));

            if (q != null && !q.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("deviceName")), "%" + q.trim().toLowerCase() + "%"));
            }
            if (state != null) {
                predicates.add(cb.equal(root.get("state"), state));
            }
            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (deviceType != null) {
                predicates.add(cb.equal(root.get("deviceType"), deviceType));
            }
            if (protocol != null) {
                predicates.add(cb.equal(root.get("protocol"), protocol));
            }
            if (profileId != null) {
                predicates.add(cb.equal(root.get("deviceProfileId"), profileId));
            }
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customer").get("id"), customerId));
            }
            if (isPublic != null) {
                predicates.add(cb.equal(root.get("isPublic"), isPublic));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return deviceRepository.findAll(spec, pageable);
    }

    // =====================================================================
    // STATISTICS
    // =====================================================================

    @Transactional(readOnly = true)
    public Map<String, Object> stats(UUID tenantId) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", deviceRepository.countByTenantId(tenantId));
        stats.put("byState", toCountMap(deviceRepository.countByStateForTenant(tenantId)));
        stats.put("byCategory", toCountMap(deviceRepository.countByCategoryForTenant(tenantId)));
        stats.put("byType", toCountMap(deviceRepository.countByTypeForTenant(tenantId)));
        return stats;
    }

    private static Map<String, Long> toCountMap(List<Object[]> rows) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String key = row[0] != null ? row[0].toString() : "UNSPECIFIED";
            map.put(key, (Long) row[1]);
        }
        return map;
    }

    // =====================================================================
    // CSV EXPORT
    // =====================================================================

    /** Columns mirror what the CSV import accepts, so an export can be re-imported. */
    @Transactional(readOnly = true)
    public String exportCsv(UUID tenantId) {
        List<Device> devices = deviceRepository.findAllByTenantId(tenantId);

        StringBuilder sb = new StringBuilder();
        sb.append("deviceId,deviceName,category,deviceType,protocol,state,deviceProfileId,customerId,isPublic,accessToken,createdAt\n");
        for (Device d : devices) {
            sb.append(CsvUtil.escape(str(d.getDeviceId()))).append(',')
                    .append(CsvUtil.escape(d.getDeviceName())).append(',')
                    .append(CsvUtil.escape(str(d.getCategory()))).append(',')
                    .append(CsvUtil.escape(str(d.getDeviceType()))).append(',')
                    .append(CsvUtil.escape(str(d.getProtocol()))).append(',')
                    .append(CsvUtil.escape(str(d.getState()))).append(',')
                    .append(CsvUtil.escape(str(d.getDeviceProfileId()))).append(',')
                    .append(CsvUtil.escape(d.getCustomer() != null ? str(d.getCustomer().getId()) : "")).append(',')
                    .append(d.isPublic()).append(',')
                    .append(CsvUtil.escape(d.getAccessToken())).append(',')
                    .append(CsvUtil.escape(str(d.getCreatedAt())))
                    .append('\n');
        }
        log.info("[Export] tenant={} devices={}", tenantId, devices.size());
        return sb.toString();
    }

    private static String str(Object o) {
        return o != null ? o.toString() : "";
    }
}
