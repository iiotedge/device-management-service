package com.iotmining.services.dms.model;

import jakarta.persistence.*;
import lombok.Data;
import com.iotmining.common.data.devices.DeviceCategory;
import com.iotmining.common.data.devices.DeviceProtocol;
import com.iotmining.common.data.devices.DeviceState;
import com.iotmining.common.data.devices.DeviceType;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Data
@Entity
@Table(name = "devices")
public class Device {

    @Id
    @Column(name = "device_id", updatable = false, nullable = false)
    private UUID deviceId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "device_name")
    private String deviceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "category")
    private DeviceCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type")
    private DeviceType deviceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "state")
    private DeviceState state;

    @Enumerated(EnumType.STRING)
    @Column(name = "protocol")
    private DeviceProtocol protocol;

    @Column(name = "device_token")
    private String deviceToken;

    @Column(name = "location")
    private String location;

    @Column(name = "attributes", columnDefinition = "TEXT")
    @Convert(converter = com.iotmining.common.util.JsonMapConverter.class)
    private Map<String, Object> attributes;

    @Column(name = "access_token")
    private String accessToken;

    @Column(name = "is_public")
    private boolean isPublic;

    @Column(name = "device_profile_id")
    private UUID deviceProfileId;

    @Column(name = "firmware_id")
    private UUID firmwareId;

    @Column(name = "software_id")
    private UUID softwareId;

    @Column(name = "customer_id")
    private UUID customerId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "device_labels", joinColumns = @JoinColumn(name = "device_id"))
    @Column(name = "label_id")
    private Set<UUID> labelIds;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    public void assignIdIfNull() {
        if (deviceId == null) {
            this.deviceId = UUID.randomUUID(); // or custom UUID generator
        }
    }
}
