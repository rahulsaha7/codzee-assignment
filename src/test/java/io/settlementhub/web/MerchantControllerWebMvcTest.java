package io.settlementhub.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.settlementhub.common.web.PageResponse;
import io.settlementhub.merchant.MerchantController;
import io.settlementhub.merchant.MerchantService;
import io.settlementhub.merchant.MerchantStatus;
import io.settlementhub.merchant.dto.MerchantResponse;
import io.settlementhub.merchant.dto.UpdateMerchantStatusRequest;
import io.settlementhub.security.JwtAuthFilter;
import io.settlementhub.security.JwtService;
import io.settlementhub.security.SecurityConfig;
import io.settlementhub.security.SecurityErrorResponder;
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

@WebMvcTest(MerchantController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, SecurityErrorResponder.class})
class MerchantControllerWebMvcTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private MerchantService merchantService;
    @MockBean private JwtService jwtService;

    @Test
    @WithMockUser(roles = "OPS")
    void createsMerchant() throws Exception {
        UUID merchantId = UUID.randomUUID();
        MerchantResponse response =
                new MerchantResponse(
                        merchantId,
                        "Acme",
                        "acme@example.com",
                        "+1-555-0100",
                        MerchantStatus.PENDING,
                        150,
                        "BANK-1",
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-01-01T00:00:00Z"),
                        0);
        when(merchantService.create(any())).thenReturn(response);

        mockMvc.perform(
                        post("/api/v1/merchants")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "legalName": "Acme",
                                          "businessEmail": "acme@example.com",
                                          "phone": "+1-555-0100",
                                          "mdrBps": 150,
                                          "bankAccountRef": "BANK-1"
                                        }
                                        """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(jsonPath("$.id").value(merchantId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void returnsValidationViolations() throws Exception {
        mockMvc.perform(
                        post("/api/v1/merchants")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "legalName": "",
                                          "businessEmail": "not-an-email",
                                          "mdrBps": 2500
                                        }
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.violations").isArray())
                .andExpect(jsonPath("$.violations[0].field").exists());

        verifyNoInteractions(merchantService);
    }

    @Test
    @WithMockUser(roles = "MERCHANT")
    void forbidsMerchantFromCreatingMerchants() throws Exception {
        mockMvc.perform(
                        post("/api/v1/merchants")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "legalName": "Acme",
                                          "businessEmail": "acme@example.com",
                                          "mdrBps": 150
                                        }
                                        """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));

        verifyNoInteractions(merchantService);
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/merchants"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listsMerchants() throws Exception {
        MerchantResponse merchant =
                new MerchantResponse(
                        UUID.randomUUID(),
                        "Acme",
                        "acme@example.com",
                        null,
                        MerchantStatus.ACTIVE,
                        150,
                        null,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-01-01T00:00:00Z"),
                        0);
        when(merchantService.list(eq(MerchantStatus.ACTIVE), eq(0), eq(20)))
                .thenReturn(new PageResponse<>(List.of(merchant), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/merchants").param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(merchant.id().toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void changesMerchantStatus() throws Exception {
        UUID merchantId = UUID.randomUUID();
        MerchantResponse response =
                new MerchantResponse(
                        merchantId,
                        "Acme",
                        "acme@example.com",
                        null,
                        MerchantStatus.ACTIVE,
                        150,
                        null,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-01-02T00:00:00Z"),
                        1);
        when(merchantService.updateStatus(
                        eq(merchantId), eq(new UpdateMerchantStatusRequest(MerchantStatus.ACTIVE))))
                .thenReturn(response);

        mockMvc.perform(
                        patch("/api/v1/merchants/{merchantId}/status", merchantId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new UpdateMerchantStatusRequest(
                                                        MerchantStatus.ACTIVE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }
}
