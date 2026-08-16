package com.iotmining.services.dms.dto.bulk;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Outcome of a single row of a bulk operation. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BulkItemResult {

    public enum Status { CREATED, SKIPPED, FAILED, DELETED, UPDATED }

    /** Zero-based position in the submitted batch (CSV: data row number). */
    private int index;
    private String deviceName;
    private UUID deviceId;
    private Status status;
    /** Human-readable reason when status is SKIPPED or FAILED. */
    private String error;
}
