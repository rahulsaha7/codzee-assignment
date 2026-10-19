package io.settlementhub.common.web;

import io.settlementhub.common.error.ApiException;
import io.settlementhub.common.error.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class Pagination {

    private Pagination() {}

    public static Pageable of(int page, int size, int maxPageSize, Sort sort) {
        if (page < 0) {
            throw new ApiException(
                    ErrorCode.BAD_REQUEST, "page must be greater than or equal to 0");
        }
        if (size < 1) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "size must be greater than 0");
        }
        if (size > maxPageSize) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "size must not exceed " + maxPageSize);
        }
        return PageRequest.of(page, size, sort);
    }
}
