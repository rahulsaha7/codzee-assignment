package io.settlementhub.transaction;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface TransactionRepository
        extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    Optional<Transaction> findByMerchantIdAndExternalRef(UUID merchantId, String externalRef);

    @Transactional
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select t from Transaction t
            where t.merchantId = :merchantId
              and t.status = :status
              and t.capturedAt >= :from
              and t.capturedAt < :to
            order by t.id
            """)
    List<Transaction> findForSettlement(
            @Param("merchantId") UUID merchantId,
            @Param("status") TransactionStatus status,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query(
            """
            select distinct t.merchantId from Transaction t
            where t.status = :status
              and t.capturedAt >= :from
              and t.capturedAt < :to
            order by t.merchantId
            """)
    List<UUID> findMerchantIdsForSettlement(
            @Param("status") TransactionStatus status,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
