package io.settlementhub.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.settlementhub.common.web.PageResponse;
import io.settlementhub.security.JwtAuthFilter;
import io.settlementhub.security.JwtService;
import io.settlementhub.security.SecurityConfig;
import io.settlementhub.security.SecurityErrorResponder;
import io.settlementhub.settlement.SettlementBatchStatus;
import io.settlementhub.settlement.SettlementController;
import io.settlementhub.settlement.SettlementService;
import io.settlementhub.settlement.SettlementStatus;
import io.settlementhub.settlement.dto.SettlementBatchResponse;
import io.settlementhub.settlement.dto.SettlementResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SettlementController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, SecurityErrorResponder.class})
class SettlementControllerWebMvcTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private SettlementService settlementService;
    @MockBean private JwtService jwtService;

    @Test
    @WithMockUser(roles = "OPS")
    void acceptsBatchCreation() throws Exception {
        SettlementBatchResponse response = batchResponse(SettlementBatchStatus.CREATED);
        when(settlementService.createBatch(eq("batch-key"), any())).thenReturn(response);

        mockMvc.perform(
                        post("/api/v1/settlements/batches")
                                .header("Idempotency-Key", "batch-key")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"businessDate\":\"2026-03-10\"}"))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(jsonPath("$.id").value(response.id().toString()))
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void returnsExistingBatchForRepeatedKey() throws Exception {
        SettlementBatchResponse response = batchResponse(SettlementBatchStatus.COMPLETED);
        when(settlementService.createBatch(eq("batch-key"), any())).thenReturn(response);

        mockMvc.perform(
                        post("/api/v1/settlements/batches")
                                .header("Idempotency-Key", "batch-key")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"businessDate\":\"2026-03-10\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(response.id().toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void rejectsMissingIdempotencyKey() throws Exception {
        mockMvc.perform(
                        post("/api/v1/settlements/batches")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"businessDate\":\"2026-03-10\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("bad_request"));

        verifyNoInteractions(settlementService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void rejectsFutureBusinessDate() throws Exception {
        mockMvc.perform(
                        post("/api/v1/settlements/batches")
                                .header("Idempotency-Key", "batch-key")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"businessDate\":\"2999-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.violations").isArray());

        verifyNoInteractions(settlementService);
    }

    @Test
    @WithMockUser(roles = "MERCHANT")
    void forbidsMerchantFromCreatingBatch() throws Exception {
        mockMvc.perform(
                        post("/api/v1/settlements/batches")
                                .header("Idempotency-Key", "batch-key")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"businessDate\":\"2026-03-10\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));

        verifyNoInteractions(settlementService);
    }

    @Test
    void requiresAuthenticationToCreateBatch() throws Exception {
        mockMvc.perform(
                        post("/api/v1/settlements/batches")
                                .header("Idempotency-Key", "batch-key")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"businessDate\":\"2026-03-10\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));

        verifyNoInteractions(settlementService);
    }

    @Test
    @WithMockUser(roles = "OPS")
    void mapsExecutorRejectionToTooManyRequests() throws Exception {
        when(settlementService.createBatch(eq("batch-key"), any()))
                .thenThrow(new TaskRejectedException("executor is saturated"));

        mockMvc.perform(
                        post("/api/v1/settlements/batches")
                                .header("Idempotency-Key", "batch-key")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"businessDate\":\"2026-03-10\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("too_many_requests"));
    }

    @Test
    @WithMockUser(roles = "OPS")
    void returnsBatchProgress() throws Exception {
        SettlementBatchResponse response = batchResponse(SettlementBatchStatus.RUNNING);
        when(settlementService.getBatch(response.id())).thenReturn(response);

        mockMvc.perform(get("/api/v1/settlements/batches/{batchId}", response.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(response.id().toString()))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.totalMerchants").value(2));
    }

    @Test
    @WithMockUser(roles = "OPS")
    void listsBatchSettlements() throws Exception {
        UUID batchId = UUID.randomUUID();
        SettlementResponse settlement =
                new SettlementResponse(
                        UUID.randomUUID(),
                        batchId,
                        UUID.randomUUID(),
                        15000L,
                        225L,
                        14775L,
                        2,
                        "USD",
                        SettlementStatus.CREATED,
                        null,
                        Instant.parse("2026-03-10T12:00:00Z"));
        when(settlementService.listSettlements(batchId, 0, 20))
                .thenReturn(new PageResponse<>(List.of(settlement), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/settlements/batches/{batchId}/settlements", batchId))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.items[0].merchantId").value(settlement.merchantId().toString()))
                .andExpect(jsonPath("$.items[0].netMinor").value(14775))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    private static SettlementBatchResponse batchResponse(SettlementBatchStatus status) {
        return new SettlementBatchResponse(
                UUID.randomUUID(),
                LocalDate.parse("2026-03-10"),
                status,
                2,
                1,
                0,
                Instant.parse("2026-03-10T10:00:00Z"),
                null,
                Instant.parse("2026-03-10T09:59:00Z"));
    }
}
