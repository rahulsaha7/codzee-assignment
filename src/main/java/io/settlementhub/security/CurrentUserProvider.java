package io.settlementhub.security;

import io.settlementhub.common.error.ApiException;
import io.settlementhub.common.error.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserProvider {

    public CurrentUser require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof CurrentUser currentUser)) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Authentication is required.");
        }
        return currentUser;
    }
}
