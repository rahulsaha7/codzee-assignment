package io.settlementhub.security.user;

import io.settlementhub.common.error.ApiException;
import io.settlementhub.common.error.ErrorCode;
import io.settlementhub.security.JwtService;
import io.settlementhub.security.user.dto.LoginRequest;
import io.settlementhub.security.user.dto.LoginResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    public AuthService(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.username(), request.password()));
        } catch (AuthenticationException exception) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Invalid username or password.");
        }
        UserAccount account =
                userRepository
                        .findByUsername(request.username())
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ErrorCode.UNAUTHORIZED,
                                                "Invalid username or password."));
        return new LoginResponse(jwtService.generateToken(account), jwtService.expiresInSeconds());
    }
}
