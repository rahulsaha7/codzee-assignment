package io.settlementhub.settlement;

import io.settlementhub.common.config.OpenApiConfig;
import io.settlementhub.common.web.PageResponse;
import io.settlementhub.settlement.dto.CreateSettlementBatchRequest;
import io.settlementhub.settlement.dto.SettlementBatchResponse;
import io.settlementhub.settlement.dto.SettlementResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settlements")
@Tag(name = "Settlements", description = "Asynchronous settlement batches")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class SettlementController {

    private final SettlementService settlementService;

    public SettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @PostMapping(
            path = "/batches",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    @Operation(
            summary = "Create a settlement batch",
            description =
                    "Starts an asynchronous settlement run for the business date. The "
                            + "Idempotency-Key header makes the operation safe to retry.")
    @ApiResponse(responseCode = "202", description = "Settlement batch accepted")
    @ApiResponse(
            responseCode = "400",
            description = "Validation failed",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "429",
            description = "Settlement executor is saturated",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public SettlementBatchResponse createBatch(
            @Parameter(description = "Client-generated idempotency key, up to 80 characters")
                    @RequestHeader("Idempotency-Key")
                    String idempotencyKey,
            @Valid @RequestBody CreateSettlementBatchRequest request) {
        return settlementService.createBatch(idempotencyKey, request);
    }

    @GetMapping(path = "/batches/{batchId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    @Operation(summary = "Get settlement batch progress")
    @ApiResponse(responseCode = "200", description = "Settlement batch found")
    @ApiResponse(
            responseCode = "404",
            description = "Settlement batch not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public SettlementBatchResponse getBatch(
            @Parameter(description = "Settlement batch id") @PathVariable UUID batchId) {
        return settlementService.getBatch(batchId);
    }

    @GetMapping(
            path = "/batches/{batchId}/settlements",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    @Operation(summary = "List settlements in a batch")
    @ApiResponse(responseCode = "200", description = "Page of settlements")
    @ApiResponse(
            responseCode = "404",
            description = "Settlement batch not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public PageResponse<SettlementResponse> listSettlements(
            @Parameter(description = "Settlement batch id") @PathVariable UUID batchId,
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0")
                    int page,
            @Parameter(description = "Page size, capped at 100") @RequestParam(defaultValue = "20")
                    int size) {
        return settlementService.listSettlements(batchId, page, size);
    }
}
