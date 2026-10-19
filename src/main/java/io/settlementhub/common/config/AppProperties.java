package io.settlementhub.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Admin admin, Pagination pagination) {

    public record Jwt(String secret, long ttlSeconds) {}

    public record Admin(String username, String password) {}

    public record Pagination(int maxPageSize) {}
}
