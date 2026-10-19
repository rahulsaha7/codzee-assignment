package io.settlementhub.merchant;

import io.settlementhub.common.config.AppProperties;
import io.settlementhub.common.error.ApiException;
import io.settlementhub.common.error.ErrorCode;
import io.settlementhub.common.web.PageResponse;
import io.settlementhub.common.web.Pagination;
import io.settlementhub.merchant.dto.CreateMerchantRequest;
import io.settlementhub.merchant.dto.MerchantResponse;
import io.settlementhub.merchant.dto.UpdateMerchantStatusRequest;
import io.settlementhub.merchant.mapper.MerchantMapper;
import io.settlementhub.security.CurrentUser;
import io.settlementhub.security.CurrentUserProvider;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final CurrentUserProvider currentUserProvider;
    private final int maxPageSize;

    public MerchantService(
            MerchantRepository merchantRepository,
            CurrentUserProvider currentUserProvider,
            AppProperties appProperties) {
        this.merchantRepository = merchantRepository;
        this.currentUserProvider = currentUserProvider;
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
        return MerchantMapper.toResponse(merchantRepository.save(merchant));
    }

    @Transactional(readOnly = true)
    public MerchantResponse get(UUID merchantId) {
        CurrentUser currentUser = currentUserProvider.require();
        if (!currentUser.canAccessMerchant(merchantId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "You do not have access to this merchant.");
        }
        return MerchantMapper.toResponse(findMerchant(merchantId));
    }

    @Transactional(readOnly = true)
    public PageResponse<MerchantResponse> list(MerchantStatus status, int page, int size) {
        Pageable pageable =
                Pagination.of(
                        page,
                        size,
                        maxPageSize,
                        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("legalName")));
        Page<Merchant> merchants =
                status == null
                        ? merchantRepository.findAll(pageable)
                        : merchantRepository.findByStatus(status, pageable);
        return PageResponse.from(merchants.map(MerchantMapper::toResponse));
    }

    @Transactional
    public MerchantResponse updateStatus(UUID merchantId, UpdateMerchantStatusRequest request) {
        Merchant merchant = findMerchant(merchantId);
        merchant.changeStatus(request.status());
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
