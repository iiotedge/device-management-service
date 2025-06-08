package com.iotmining.services.dms.model;

import jakarta.persistence.*;
import lombok.Data;

import java.util.UUID;

@Data
@Entity
@Table(name = "softwares")
public class Software {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "version")
    private String version;

    @Column(name = "file_url")
    private String fileUrl;

    @Column(name = "release_notes", columnDefinition = "TEXT")
    private String releaseNotes;
}
