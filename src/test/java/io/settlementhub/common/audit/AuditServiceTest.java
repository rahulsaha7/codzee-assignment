package io.settlementhub.common.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.settlementhub.merchant.Merchant;
import io.settlementhub.merchant.MerchantStatus;
import io.settlementhub.security.CurrentUser;
import io.settlementhub.security.CurrentUserProvider;
import io.settlementhub.security.Role;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock private AuditLogRepository auditLogRepository;
    @Mock private CurrentUserProvider currentUserProvider;

    private AuditService auditService;

    @BeforeEach
    void setUp() {
        auditService =
                new AuditService(auditLogRepository, currentUserProvider, new ObjectMapper());
    }

    @Test
    void recordsMerchantCreationWithActorAndDetails() {
        Merchant merchant = merchant();
        when(currentUserProvider.require()).thenReturn(new CurrentUser("ops-user", Role.OPS, null));

        auditService.merchantCreated(merchant);

        AuditLog auditLog = captureSavedAuditLog();
        assertThat(auditLog.getAction()).isEqualTo("MERCHANT_CREATED");
        assertThat(auditLog.getActor()).isEqualTo("ops-user");
        assertThat(auditLog.getEntityType()).isEqualTo("merchant");
        assertThat(auditLog.getEntityId()).isEqualTo(merchant.getId().toString());
        assertThat(auditLog.getAt()).isNotNull();
        assertThat(auditLog.getDetails())
                .contains("acme@example.com")
                .contains("+1-555-0100")
                .contains("\"mdrBps\":150");
    }

    @Test
    void recordsMerchantStatusChangeWithPreviousAndCurrentStatus() {
        Merchant merchant = merchant();
        merchant.changeStatus(MerchantStatus.ACTIVE);
        when(currentUserProvider.require()).thenReturn(new CurrentUser("admin", Role.ADMIN, null));

        auditService.merchantStatusChanged(merchant, MerchantStatus.PENDING);

        AuditLog auditLog = captureSavedAuditLog();
        assertThat(auditLog.getAction()).isEqualTo("MERCHANT_STATUS_CHANGED");
        assertThat(auditLog.getActor()).isEqualTo("admin");
        assertThat(auditLog.getEntityId()).isEqualTo(merchant.getId().toString());
        assertThat(auditLog.getDetails())
                .contains("\"previousStatus\":\"PENDING\"")
                .contains("\"status\":\"ACTIVE\"");
    }

    private AuditLog captureSavedAuditLog() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        return captor.getValue();
    }

    private static Merchant merchant() {
        Merchant merchant =
                new Merchant(
                        "Acme",
                        "acme@example.com",
                        "+1-555-0100",
                        150,
                        "BANK-1",
                        MerchantStatus.PENDING);
        ReflectionTestUtils.setField(merchant, "id", UUID.randomUUID());
        return merchant;
    }
}
