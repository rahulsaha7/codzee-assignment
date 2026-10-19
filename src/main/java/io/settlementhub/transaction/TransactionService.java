package io.settlementhub.transaction;

import io.settlementhub.common.config.AppProperties;
import io.settlementhub.common.error.ApiException;
import io.settlementhub.common.error.ErrorCode;
import io.settlementhub.common.web.PageResponse;
import io.settlementhub.common.web.Pagination;
import io.settlementhub.merchant.MerchantRepository;
import io.settlementhub.security.CurrentUser;
import io.settlementhub.security.CurrentUserProvider;
import io.settlementhub.transaction.dto.CreateTransactionRequest;
import io.settlementhub.transaction.dto.TransactionResponse;
import io.settlementhub.transaction.mapper.TransactionMapper;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final MerchantRepository merchantRepository;
    private final CurrentUserProvider currentUserProvider;
    private final int maxPageSize;

    public TransactionService(
            TransactionRepository transactionRepository,
            MerchantRepository merchantRepository,
            CurrentUserProvider currentUserProvider,
            AppProperties appProperties) {
        this.transactionRepository = transactionRepository;
        this.merchantRepository = merchantRepository;
        this.currentUserProvider = currentUserProvider;
        this.maxPageSize = appProperties.pagination().maxPageSize();
    }

    @Transactional
    public TransactionCreationResult capture(CreateTransactionRequest request) {
        if (!merchantRepository.existsById(request.merchantId())) {
            throw new ApiException(
                    ErrorCode.NOT_FOUND, "Merchant not found: " + request.merchantId());
        }
        Optional<Transaction> existing =
                transactionRepository.findByMerchantIdAndExternalRef(
                        request.merchantId(), request.externalRef());
        if (existing.isPresent()) {
            return new TransactionCreationResult(
                    TransactionMapper.toResponse(existing.get()), false);
        }
        Instant capturedAt = request.capturedAt() == null ? Instant.now() : request.capturedAt();
        Transaction transaction =
                new Transaction(
                        request.merchantId(),
                        request.externalRef(),
                        request.amountMinor(),
                        request.currency(),
                        TransactionStatus.CAPTURED,
                        capturedAt,
                        null);
        return new TransactionCreationResult(
                TransactionMapper.toResponse(transactionRepository.save(transaction)), true);
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> list(
            UUID merchantId, Instant from, Instant to, int page, int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "from must be before or equal to to");
        }
        CurrentUser currentUser = currentUserProvider.require();
        UUID effectiveMerchantId = resolveMerchantFilter(currentUser, merchantId);
        Pageable pageable =
                Pagination.of(
                        page,
                        size,
                        maxPageSize,
                        Sort.by(Sort.Order.desc("capturedAt"), Sort.Order.desc("id")));
        Page<Transaction> transactions =
                transactionRepository.findAll(matching(effectiveMerchantId, from, to), pageable);
        return PageResponse.from(transactions.map(TransactionMapper::toResponse));
    }

    private static Specification<Transaction> matching(UUID merchantId, Instant from, Instant to) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (merchantId != null) {
                predicates.add(criteriaBuilder.equal(root.get("merchantId"), merchantId));
            }
            if (from != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("capturedAt"), from));
            }
            if (to != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("capturedAt"), to));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private UUID resolveMerchantFilter(CurrentUser currentUser, UUID requestedMerchantId) {
        if (!currentUser.isMerchant()) {
            return requestedMerchantId;
        }
        if (currentUser.merchantId() == null) {
            throw new ApiException(
                    ErrorCode.FORBIDDEN, "This account is not linked to a merchant.");
        }
        if (requestedMerchantId != null && !requestedMerchantId.equals(currentUser.merchantId())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "You do not have access to this merchant.");
        }
        return currentUser.merchantId();
    }
}
