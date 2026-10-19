package io.settlementhub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SettlementHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(SettlementHubApplication.class, args);
    }
}
