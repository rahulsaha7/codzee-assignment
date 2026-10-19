package io.settlementhub.merchant;

import io.settlementhub.common.config.OpenApiConfig;
import io.settlementhub.common.web.PageResponse;
import io.settlementhub.merchant.dto.CreateMerchantRequest;
import io.settlementhub.merchant.dto.MerchantResponse;
import io.settlementhub.merchant.dto.MerchantSummaryResponse;
import io.settlementhub.merchant.dto.UpdateMerchantStatusRequest;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/merchants")
@Tag(name = "Merchants", description = "Merchant onboarding and lifecycle management")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class MerchantController {

    private final MerchantService merchantService;

    public MerchantController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    @Operation(summary = "Create a merchant", description = "Creates a merchant in PENDING status.")
    @ApiResponse(responseCode = "201", description = "Merchant created")
    @ApiResponse(
            responseCode = "400",
            description = "Validation failed",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "409",
            description = "Business email already exists",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public MerchantResponse create(@Valid @RequestBody CreateMerchantRequest request) {
        return merchantService.create(request);
    }

    @GetMapping(path = "/{merchantId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS', 'MERCHANT')")
    @Operation(
            summary = "Get a merchant",
            description = "ADMIN and OPS can read any merchant; MERCHANT can read only their own.")
    @ApiResponse(responseCode = "200", description = "Merchant found")
    @ApiResponse(
            responseCode = "404",
            description = "Merchant not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public MerchantResponse get(
            @Parameter(description = "Merchant id") @PathVariable UUID merchantId) {
        return merchantService.get(merchantId);
    }

    @GetMapping(path = "/me/summary", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('MERCHANT')")
    @Operation(
            summary = "Get the current merchant's summary",
            description = "Returns the linked merchant profile and transaction counts.")
    @ApiResponse(responseCode = "200", description = "Merchant summary")
    @ApiResponse(
            responseCode = "403",
            description = "Account is not linked to a merchant",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public MerchantSummaryResponse mySummary() {
        return merchantService.mySummary();
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    @Operation(
            summary = "Search merchants",
            description = "Paginated merchant search with optional status and free-text filters.")
    @ApiResponse(responseCode = "200", description = "Page of merchants")
    public PageResponse<MerchantResponse> list(
            @Parameter(description = "Optional merchant status filter")
                    @RequestParam(required = false)
                    MerchantStatus status,
            @Parameter(description = "Optional legal name or business email search term")
                    @RequestParam(required = false)
                    String q,
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0")
                    int page,
            @Parameter(description = "Page size, capped at 100") @RequestParam(defaultValue = "20")
                    int size) {
        return merchantService.search(status, q, page, size);
    }

    @PatchMapping(
            path = "/{merchantId}/status",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Change merchant status",
            description = "Activates or suspends a merchant.")
    @ApiResponse(responseCode = "200", description = "Status updated")
    @ApiResponse(
            responseCode = "404",
            description = "Merchant not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public MerchantResponse updateStatus(
            @Parameter(description = "Merchant id") @PathVariable UUID merchantId,
            @Valid @RequestBody UpdateMerchantStatusRequest request) {
        return merchantService.updateStatus(merchantId, request);
    }
}
