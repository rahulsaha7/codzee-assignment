package io.settlementhub.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.settlementhub.common.config.AppProperties;
import io.settlementhub.common.error.ApiException;
import io.settlementhub.common.error.ErrorCode;
import io.settlementhub.common.web.PageResponse;
import io.settlementhub.settlement.dto.CreateSettlementBatchRequest;
import io.settlementhub.settlement.dto.SettlementBatchResponse;
import io.settlementhub.settlement.dto.SettlementResponse;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    private static final int MAX_PAGE_SIZE = 100;

    @Mock private SettlementBatchRepository settlementBatchRepository;
    @Mock private SettlementRepository settlementRepository;
    @Mock private SettlementWorker settlementWorker;
    @Mock private TaskExecutor settlementExecutor;

    private SettlementService settlementService;

    @BeforeEach
    void setUp() {
        AppProperties properties =
                new AppProperties(
                        new AppProperties.Jwt("test-secret", 3600),
                        new AppProperties.Admin("admin", "password"),
                        new AppProperties.Pagination(MAX_PAGE_SIZE),
                        new AppProperties.Settlement(4, 8, 100));
        settlementService =
                new SettlementService(
                        settlementBatchRepository,
                        settlementRepository,
                        settlementWorker,
                        settlementExecutor,
                        properties);
    }

    @Test
    void createsBatchAndSubmitsWorker() {
        UUID batchId = UUID.randomUUID();
        LocalDate businessDate = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        when(settlementBatchRepository.findByIdempotencyKey("batch-key"))
                .thenReturn(Optional.empty());
        when(settlementBatchRepository.saveAndFlush(any(SettlementBatch.class)))
                .thenAnswer(
                        invocation -> {
                            SettlementBatch batch = invocation.getArgument(0);
                            ReflectionTestUtils.setField(batch, "id", batchId);
                            return batch;
                        });

        SettlementBatchResponse response =
                settlementService.createBatch(
                        "batch-key", new CreateSettlementBatchRequest(businessDate));

        assertThat(response.id()).isEqualTo(batchId);
        assertThat(response.businessDate()).isEqualTo(businessDate);
        assertThat(response.status()).isEqualTo(SettlementBatchStatus.CREATED);
        verify(settlementExecutor).execute(any(Runnable.class));
    }

    @Test
    void returnsExistingBatchForRepeatedIdempotencyKey() {
        SettlementBatch existing =
                batch(UUID.randomUUID(), LocalDate.now(ZoneOffset.UTC), "batch-key");
        when(settlementBatchRepository.findByIdempotencyKey("batch-key"))
                .thenReturn(Optional.of(existing));

        SettlementBatchResponse response =
                settlementService.createBatch(
                        "batch-key", new CreateSettlementBatchRequest(existing.getBusinessDate()));

        assertThat(response.id()).isEqualTo(existing.getId());
        verify(settlementBatchRepository, never()).saveAndFlush(any());
        verifyNoInteractions(settlementExecutor);
    }

    @Test
    void returnsExistingBatchWhenUniqueConstraintWinsTheRace() {
        SettlementBatch existing =
                batch(UUID.randomUUID(), LocalDate.now(ZoneOffset.UTC), "batch-key");
        when(settlementBatchRepository.findByIdempotencyKey("batch-key"))
                .thenReturn(Optional.empty(), Optional.of(existing));
        when(settlementBatchRepository.saveAndFlush(any(SettlementBatch.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        SettlementBatchResponse response =
                settlementService.createBatch(
                        "batch-key", new CreateSettlementBatchRequest(existing.getBusinessDate()));

        assertThat(response.id()).isEqualTo(existing.getId());
        verifyNoInteractions(settlementExecutor);
    }

    @Test
    void rejectsFutureBusinessDate() {
        LocalDate future = LocalDate.now(ZoneOffset.UTC).plusDays(1);

        assertThatThrownBy(
                        () ->
                                settlementService.createBatch(
                                        "batch-key", new CreateSettlementBatchRequest(future)))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.BAD_REQUEST));
        verifyNoInteractions(settlementBatchRepository, settlementExecutor);
    }

    @Test
    void rejectsBlankIdempotencyKey() {
        assertThatThrownBy(
                        () ->
                                settlementService.createBatch(
                                        " ",
                                        new CreateSettlementBatchRequest(
                                                LocalDate.now(ZoneOffset.UTC))))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.BAD_REQUEST));
        verifyNoInteractions(settlementBatchRepository, settlementExecutor);
    }

    @Test
    void rejectsOversizedIdempotencyKey() {
        String key = "x".repeat(81);

        assertThatThrownBy(
                        () ->
                                settlementService.createBatch(
                                        key,
                                        new CreateSettlementBatchRequest(
                                                LocalDate.now(ZoneOffset.UTC))))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.BAD_REQUEST));
        verifyNoInteractions(settlementBatchRepository, settlementExecutor);
    }

    @Test
    void returnsBatchById() {
        SettlementBatch existing =
                batch(UUID.randomUUID(), LocalDate.now(ZoneOffset.UTC), "batch-key");
        when(settlementBatchRepository.findById(existing.getId()))
                .thenReturn(Optional.of(existing));

        SettlementBatchResponse response = settlementService.getBatch(existing.getId());

        assertThat(response.id()).isEqualTo(existing.getId());
    }

    @Test
    void rejectsUnknownBatch() {
        UUID batchId = UUID.randomUUID();
        when(settlementBatchRepository.findById(batchId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> settlementService.getBatch(batchId))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void listsSettlementsForExistingBatch() {
        SettlementBatch existing =
                batch(UUID.randomUUID(), LocalDate.now(ZoneOffset.UTC), "batch-key");
        when(settlementBatchRepository.findById(existing.getId()))
                .thenReturn(Optional.of(existing));
        when(settlementRepository.findByBatchId(eq(existing.getId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        PageResponse<SettlementResponse> response =
                settlementService.listSettlements(existing.getId(), 0, 20);

        assertThat(response.items()).isEmpty();
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(settlementRepository).findByBatchId(eq(existing.getId()), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void rejectsUnknownBatchWhenListingSettlements() {
        UUID batchId = UUID.randomUUID();
        when(settlementBatchRepository.findById(batchId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> settlementService.listSettlements(batchId, 0, 20))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.NOT_FOUND));
        verifyNoInteractions(settlementRepository);
    }

    @Test
    void rejectsPageSizeAboveConfiguredLimit() {
        SettlementBatch existing =
                batch(UUID.randomUUID(), LocalDate.now(ZoneOffset.UTC), "batch-key");
        when(settlementBatchRepository.findById(existing.getId()))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(
                        () ->
                                settlementService.listSettlements(
                                        existing.getId(), 0, MAX_PAGE_SIZE + 1))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.BAD_REQUEST));
        verifyNoInteractions(settlementRepository);
    }

    private static SettlementBatch batch(UUID id, LocalDate businessDate, String key) {
        SettlementBatch batch = new SettlementBatch(businessDate, key);
        ReflectionTestUtils.setField(batch, "id", id);
        return batch;
    }
}
