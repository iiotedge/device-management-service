package com.iotmining.services.dms.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Maps a physical hardware identifier (IMEI, MAC, serial, token) to a Device's
 * internal system UUID. A device can accumulate multiple rows over its lifetime
 * (hardware swaps) - only one is ever active at a time; the rest are history.
 */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "device_identity", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"hardware_id"})
})
public class DeviceIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "hardware_id", nullable = false, length = 100)
    private String hardwareId;

    // One of: IMEI, MAC, SN, TOKEN
    @Column(name = "identity_type", nullable = false, length = 50)
    private String identityType;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
