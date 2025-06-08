package com.iotmining.services.dms.model;

import jakarta.persistence.*;
import lombok.Data;

import java.util.UUID;

@Data
@Entity
@Table(name = "firmwares")
public class Firmware {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "version")
    private String version;

    @Column(name = "checksum")
    private String checksum;

    @Column(name = "file_url")
    private String fileUrl;
}
