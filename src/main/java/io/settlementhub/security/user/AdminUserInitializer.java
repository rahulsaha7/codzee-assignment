package io.settlementhub.security.user;

import io.settlementhub.common.config.AppProperties;
import io.settlementhub.security.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Order(1)
public class AdminUserInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;

    public AdminUserInitializer(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AppProperties appProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.appProperties = appProperties;
    }

    @Override
    public void run(String... args) {
        AppProperties.Admin admin = appProperties.admin();
        String username = admin == null ? null : admin.username();
        String password = admin == null ? null : admin.password();
        if (!StringUtils.hasText(username)) {
            throw new IllegalStateException("APP_ADMIN_USERNAME must not be blank.");
        }
        if (!StringUtils.hasText(password)) {
            throw new IllegalStateException(
                    "APP_ADMIN_PASSWORD must be set so the admin account can be created.");
        }
        if (userRepository.existsByUsername(username)) {
            return;
        }
        UserAccount adminAccount =
                new UserAccount(username, passwordEncoder.encode(password), Role.ADMIN, null);
        userRepository.save(adminAccount);
        log.info("Created bootstrap admin user '{}'", username);
    }
}
