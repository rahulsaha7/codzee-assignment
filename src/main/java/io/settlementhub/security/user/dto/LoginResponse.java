package io.settlementhub.security.user.dto;

public record LoginResponse(String accessToken, long expiresInSeconds) {}
