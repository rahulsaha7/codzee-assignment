package io.settlementhub.settlement;

import io.settlementhub.common.config.AppProperties;
import io.settlementhub.common.error.ApiException;
import io.settlementhub.common.error.ErrorCode;
import io.settlementhub.common.web.PageResponse;
import io.settlementhub.common.web.Pagination;
import io.settlementhub.settlement.dto.CreateSettlementBatchRequest;
import io.settlementhub.settlement.dto.SettlementBatchResponse;
import io.settlementhub.settlement.dto.SettlementResponse;
import io.settlementhub.settlement.mapper.SettlementMapper;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SettlementService {

    private final SettlementBatchRepository settlementBatchRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementWorker settlementWorker;
    private final TaskExecutor settlementExecutor;
    private final int maxPageSize;

    public SettlementService(
            SettlementBatchRepository settlementBatchRepository,
            SettlementRepository settlementRepository,
            SettlementWorker settlementWorker,
            @Qualifier("settlementExecutor") TaskExecutor settlementExecutor,
            AppProperties appProperties) {
        this.settlementBatchRepository = settlementBatchRepository;
        this.settlementRepository = settlementRepository;
        this.settlementWorker = settlementWorker;
        this.settlementExecutor = settlementExecutor;
        this.maxPageSize = appProperties.pagination().maxPageSize();
    }

    public SettlementBatchResponse createBatch(
            String idempotencyKey, CreateSettlementBatchRequest request) {
        validateIdempotencyKey(idempotencyKey);
        if (request.businessDate() == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "businessDate must not be null");
        }
        if (request.businessDate().isAfter(LocalDate.now(ZoneOffset.UTC))) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "businessDate must not be in the future");
        }
        Optional<SettlementBatch> existing =
                settlementBatchRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return SettlementMapper.toResponse(existing.get());
        }
        SettlementBatch batch = new SettlementBatch(request.businessDate(), idempotencyKey);
        SettlementBatch saved;
        try {
            saved = settlementBatchRepository.saveAndFlush(batch);
        } catch (DataIntegrityViolationException exception) {
            return settlementBatchRepository
                    .findByIdempotencyKey(idempotencyKey)
                    .map(SettlementMapper::toResponse)
                    .orElseThrow(() -> exception);
        }
        settlementExecutor.execute(() -> settlementWorker.run(saved.getId()));
        return SettlementMapper.toResponse(saved);
    }

    public SettlementBatchResponse getBatch(UUID batchId) {
        return SettlementMapper.toResponse(findBatch(batchId));
    }

    public PageResponse<SettlementResponse> listSettlements(UUID batchId, int page, int size) {
        findBatch(batchId);
        Pageable pageable =
                Pagination.of(
                        page,
                        size,
                        maxPageSize,
                        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
        Page<Settlement> settlements = settlementRepository.findByBatchId(batchId, pageable);
        return PageResponse.from(settlements.map(SettlementMapper::toResponse));
    }

    private SettlementBatch findBatch(UUID batchId) {
        return settlementBatchRepository
                .findById(batchId)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        ErrorCode.NOT_FOUND,
                                        "Settlement batch not found: " + batchId));
    }

    private static void validateIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Idempotency-Key header is required.");
        }
        if (idempotencyKey.length() > 80) {
            throw new ApiException(
                    ErrorCode.BAD_REQUEST, "Idempotency-Key must not exceed 80 characters.");
        }
    }
}
