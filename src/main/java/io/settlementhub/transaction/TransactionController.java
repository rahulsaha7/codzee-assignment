package io.settlementhub.transaction;

import io.settlementhub.common.config.OpenApiConfig;
import io.settlementhub.common.web.PageResponse;
import io.settlementhub.transaction.dto.CreateTransactionRequest;
import io.settlementhub.transaction.dto.TransactionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
@Tag(name = "Transactions", description = "Transaction capture and reporting")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    @Operation(
            summary = "Capture a transaction",
            description =
                    "Creates a CAPTURED transaction. The pair (merchantId, externalRef) is idempotent:")
    @ApiResponse(responseCode = "201", description = "Transaction created")
    @ApiResponse(responseCode = "200", description = "Existing transaction returned")
    @ApiResponse(
            responseCode = "404",
            description = "Merchant not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<TransactionResponse> capture(
            @Valid @RequestBody CreateTransactionRequest request) {
        TransactionCreationResult result = transactionService.capture(request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.transaction());
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS', 'MERCHANT')")
    @Operation(
            summary = "List transactions",
            description =
                    "Paginated transaction listing. MERCHANT users are restricted to their own merchant.")
    @ApiResponse(responseCode = "200", description = "Page of transactions")
    public PageResponse<TransactionResponse> list(
            @Parameter(description = "Optional merchant filter") @RequestParam(required = false)
                    UUID merchantId,
            @Parameter(description = "Inclusive lower bound for capturedAt (ISO-8601)")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant from,
            @Parameter(description = "Inclusive upper bound for capturedAt (ISO-8601)")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant to,
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0")
                    int page,
            @Parameter(description = "Page size, capped at 100") @RequestParam(defaultValue = "20")
                    int size) {
        return transactionService.list(merchantId, from, to, page, size);
    }
}
