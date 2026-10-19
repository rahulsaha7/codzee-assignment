package io.settlementhub.merchant;

import io.settlementhub.common.audit.AuditService;
import io.settlementhub.common.config.AppProperties;
import io.settlementhub.common.error.ApiException;
import io.settlementhub.common.error.ErrorCode;
import io.settlementhub.common.web.PageResponse;
import io.settlementhub.common.web.Pagination;
import io.settlementhub.merchant.dto.CreateMerchantRequest;
import io.settlementhub.merchant.dto.MerchantResponse;
import io.settlementhub.merchant.dto.MerchantSummaryResponse;
import io.settlementhub.merchant.dto.UpdateMerchantStatusRequest;
import io.settlementhub.merchant.mapper.MerchantMapper;
import io.settlementhub.transaction.TransactionRepository;
import io.settlementhub.transaction.TransactionStatus;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final MerchantSearchRepository merchantSearchRepository;
    private final TransactionRepository transactionRepository;
    private final MerchantAccess merchantAccess;
    private final AuditService auditService;
    private final int maxPageSize;

    public MerchantService(
            MerchantRepository merchantRepository,
            MerchantSearchRepository merchantSearchRepository,
            TransactionRepository transactionRepository,
            MerchantAccess merchantAccess,
            AuditService auditService,
            AppProperties appProperties) {
        this.merchantRepository = merchantRepository;
        this.merchantSearchRepository = merchantSearchRepository;
        this.transactionRepository = transactionRepository;
        this.merchantAccess = merchantAccess;
        this.auditService = auditService;
        this.maxPageSize = appProperties.pagination().maxPageSize();
    }

    @Transactional
    public MerchantResponse create(CreateMerchantRequest request) {
        if (merchantRepository.existsByBusinessEmail(request.businessEmail())) {
            throw new ApiException(
                    ErrorCode.CONFLICT, "A merchant with this business email already exists.");
        }
        Merchant merchant =
                new Merchant(
                        request.legalName(),
                        request.businessEmail(),
                        request.phone(),
                        request.mdrBps(),
                        request.bankAccountRef(),
                        MerchantStatus.PENDING);
        Merchant saved = merchantRepository.save(merchant);
        auditService.merchantCreated(saved);
        return MerchantMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public MerchantResponse get(UUID merchantId) {
        merchantAccess.requireAccess(merchantId);
        return MerchantMapper.toResponse(findMerchant(merchantId));
    }

    @Transactional(readOnly = true)
    public PageResponse<MerchantResponse> search(
            MerchantStatus status, String q, int page, int size) {
        Pagination.of(page, size, maxPageSize, Sort.unsorted());
        return merchantSearchRepository.search(status, q, page, size);
    }

    @Transactional(readOnly = true)
    public MerchantSummaryResponse mySummary() {
        UUID merchantId = merchantAccess.requireLinkedMerchantId();
        Merchant merchant = findMerchant(merchantId);
        return new MerchantSummaryResponse(
                MerchantMapper.toResponse(merchant),
                transactionRepository.countByMerchantIdAndStatus(
                        merchantId, TransactionStatus.CAPTURED),
                transactionRepository.countByMerchantIdAndStatus(
                        merchantId, TransactionStatus.SETTLED));
    }

    @Transactional
    public MerchantResponse updateStatus(UUID merchantId, UpdateMerchantStatusRequest request) {
        Merchant merchant = findMerchant(merchantId);
        MerchantStatus previousStatus = merchant.getStatus();
        merchant.changeStatus(request.status());
        auditService.merchantStatusChanged(merchant, previousStatus);
        return MerchantMapper.toResponse(merchant);
    }

    private Merchant findMerchant(UUID merchantId) {
        return merchantRepository
                .findById(merchantId)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        ErrorCode.NOT_FOUND, "Merchant not found: " + merchantId));
    }
}
