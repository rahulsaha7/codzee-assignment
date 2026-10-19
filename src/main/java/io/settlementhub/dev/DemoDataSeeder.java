package io.settlementhub.dev;

import io.settlementhub.merchant.Merchant;
import io.settlementhub.merchant.MerchantRepository;
import io.settlementhub.merchant.MerchantStatus;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
@Order(2)
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final MerchantRepository merchantRepository;

    public DemoDataSeeder(MerchantRepository merchantRepository) {
        this.merchantRepository = merchantRepository;
    }

    @Override
    public void run(String... args) {
        if (merchantRepository.count() > 0) {
            return;
        }
        List<Merchant> merchants =
                List.of(
                        new Merchant(
                                "Northwind Traders",
                                "accounts@northwind.example",
                                "+1-555-0100",
                                150,
                                "BANK-NW-001",
                                MerchantStatus.ACTIVE),
                        new Merchant(
                                "Acme Retail",
                                "finance@acme.example",
                                "+1-555-0101",
                                200,
                                "BANK-ACME-002",
                                MerchantStatus.ACTIVE),
                        new Merchant(
                                "Bluebird Cafe",
                                "owner@bluebird.example",
                                "+1-555-0102",
                                100,
                                "BANK-BB-003",
                                MerchantStatus.PENDING),
                        new Merchant(
                                "Contoso Markets",
                                "billing@contoso.example",
                                "+1-555-0103",
                                250,
                                "BANK-CON-004",
                                MerchantStatus.SUSPENDED),
                        new Merchant(
                                "Fabrikam Studio",
                                "hello@fabrikam.example",
                                "+1-555-0104",
                                175,
                                "BANK-FAB-005",
                                MerchantStatus.ACTIVE));
        merchantRepository.saveAll(merchants);
        log.info("Seeded {} demo merchants", merchants.size());
    }
}
