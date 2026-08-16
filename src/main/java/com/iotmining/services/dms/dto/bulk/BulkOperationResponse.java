package com.iotmining.services.dms.dto.bulk;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Aggregate report for a bulk operation. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BulkOperationResponse {

    private int total;
    private int succeeded;
    private int skipped;
    private int failed;
    private List<BulkItemResult> results;

    public static BulkOperationResponse of(List<BulkItemResult> results) {
        int ok = 0, skip = 0, fail = 0;
        for (BulkItemResult r : results) {
            switch (r.getStatus()) {
                case FAILED -> fail++;
                case SKIPPED -> skip++;
                default -> ok++;
            }
        }
        return BulkOperationResponse.builder()
                .total(results.size())
                .succeeded(ok)
                .skipped(skip)
                .failed(fail)
                .results(results)
                .build();
    }
}
