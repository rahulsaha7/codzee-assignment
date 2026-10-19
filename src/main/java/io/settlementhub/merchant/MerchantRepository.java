package io.settlementhub.merchant;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantRepository extends JpaRepository<Merchant, UUID> {

    boolean existsByBusinessEmail(String businessEmail);

    Page<Merchant> findByStatus(MerchantStatus status, Pageable pageable);
}
