package io.settlementhub.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.settlementhub.common.web.PageResponse;
import io.settlementhub.security.JwtAuthFilter;
import io.settlementhub.security.JwtService;
import io.settlementhub.security.SecurityConfig;
import io.settlementhub.security.SecurityErrorResponder;
import io.settlementhub.transaction.TransactionController;
import io.settlementhub.transaction.TransactionCreationResult;
import io.settlementhub.transaction.TransactionService;
import io.settlementhub.transaction.TransactionStatus;
import io.settlementhub.transaction.dto.TransactionResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TransactionController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, SecurityErrorResponder.class})
class TransactionControllerWebMvcTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private TransactionService transactionService;
    @MockBean private JwtService jwtService;

    @Test
    @WithMockUser(roles = "OPS")
    void capturesNewTransactionWithCreatedStatus() throws Exception {
        UUID transactionId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        when(transactionService.capture(any()))
                .thenReturn(
                        new TransactionCreationResult(
                                transaction(transactionId, merchantId), true));

        mockMvc.perform(
                        post("/api/v1/transactions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody(merchantId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(transactionId.toString()))
                .andExpect(jsonPath("$.status").value("CAPTURED"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void returnsExistingTransactionWithOkStatus() throws Exception {
        UUID transactionId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        when(transactionService.capture(any()))
                .thenReturn(
                        new TransactionCreationResult(
                                transaction(transactionId, merchantId), false));

        mockMvc.perform(
                        post("/api/v1/transactions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody(merchantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(transactionId.toString()));
    }

    @Test
    @WithMockUser(roles = "OPS")
    void rejectsInvalidTransactionPayload() throws Exception {
        mockMvc.perform(
                        post("/api/v1/transactions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "amountMinor": 0,
                                          "currency": "usd"
                                        }
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.violations").isArray());

        verifyNoInteractions(transactionService);
    }

    @Test
    @WithMockUser(roles = "MERCHANT")
    void allowsMerchantToListTransactions() throws Exception {
        when(transactionService.list(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void requiresAuthenticationToListTransactions() throws Exception {
        mockMvc.perform(get("/api/v1/transactions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));
    }

    private static TransactionResponse transaction(UUID transactionId, UUID merchantId) {
        return new TransactionResponse(
                transactionId,
                merchantId,
                "order-1",
                12500L,
                "USD",
                TransactionStatus.CAPTURED,
                Instant.parse("2026-01-10T10:00:00Z"),
                null);
    }

    private static String requestBody(UUID merchantId) {
        return """
                {
                  "merchantId": "%s",
                  "externalRef": "order-1",
                  "amountMinor": 12500,
                  "currency": "USD"
                }
                """
                .formatted(merchantId);
    }
}
