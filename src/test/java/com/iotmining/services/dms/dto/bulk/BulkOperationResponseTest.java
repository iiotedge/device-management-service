package com.iotmining.services.dms.dto.bulk;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BulkOperationResponseTest {

    private static BulkItemResult with(BulkItemResult.Status status) {
        return BulkItemResult.builder().status(status).build();
    }

    @Test
    void aggregatesCountsByStatus() {
        BulkOperationResponse response = BulkOperationResponse.of(List.of(
                with(BulkItemResult.Status.CREATED),
                with(BulkItemResult.Status.CREATED),
                with(BulkItemResult.Status.SKIPPED),
                with(BulkItemResult.Status.FAILED),
                with(BulkItemResult.Status.DELETED),
                with(BulkItemResult.Status.UPDATED)));

        assertThat(response.getTotal()).isEqualTo(6);
        // DELETED and UPDATED count as successes
        assertThat(response.getSucceeded()).isEqualTo(4);
        assertThat(response.getSkipped()).isEqualTo(1);
        assertThat(response.getFailed()).isEqualTo(1);
        assertThat(response.getResults()).hasSize(6);
    }

    @Test
    void emptyBatchProducesZeroes() {
        BulkOperationResponse response = BulkOperationResponse.of(List.of());
        assertThat(response.getTotal()).isZero();
        assertThat(response.getSucceeded()).isZero();
        assertThat(response.getFailed()).isZero();
    }
}
