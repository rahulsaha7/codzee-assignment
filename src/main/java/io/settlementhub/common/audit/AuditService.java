package io.settlementhub.common.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.settlementhub.merchant.Merchant;
import io.settlementhub.merchant.MerchantStatus;
import io.settlementhub.security.CurrentUser;
import io.settlementhub.security.CurrentUserProvider;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final String MERCHANT_ENTITY = "merchant";

    private final AuditLogRepository auditLogRepository;
    private final CurrentUserProvider currentUserProvider;
    private final ObjectMapper objectMapper;

    public AuditService(
            AuditLogRepository auditLogRepository,
            CurrentUserProvider currentUserProvider,
            ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.currentUserProvider = currentUserProvider;
        this.objectMapper = objectMapper;
    }

    public void merchantCreated(Merchant merchant) {
        log.info(
                "Merchant created id={} businessEmail={} phone={}",
                merchant.getId(),
                merchant.getBusinessEmail(),
                merchant.getPhone());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("legalName", merchant.getLegalName());
        details.put("businessEmail", merchant.getBusinessEmail());
        details.put("phone", merchant.getPhone());
        details.put("mdrBps", merchant.getMdrBps());
        record("MERCHANT_CREATED", merchant.getId(), details);
    }

    public void merchantStatusChanged(Merchant merchant, MerchantStatus previousStatus) {
        log.info(
                "Merchant status changed id={} businessEmail={} phone={} from={} to={}",
                merchant.getId(),
                merchant.getBusinessEmail(),
                merchant.getPhone(),
                previousStatus,
                merchant.getStatus());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("previousStatus", previousStatus);
        details.put("status", merchant.getStatus());
        details.put("businessEmail", merchant.getBusinessEmail());
        details.put("phone", merchant.getPhone());
        record("MERCHANT_STATUS_CHANGED", merchant.getId(), details);
    }

    private void record(String action, UUID entityId, Map<String, Object> details) {
        CurrentUser actor = currentUserProvider.require();
        String payload;
        try {
            payload = objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize audit details", exception);
        }
        auditLogRepository.save(
                new AuditLog(
                        actor.username(),
                        action,
                        MERCHANT_ENTITY,
                        entityId.toString(),
                        Instant.now(),
                        payload));
    }
}
