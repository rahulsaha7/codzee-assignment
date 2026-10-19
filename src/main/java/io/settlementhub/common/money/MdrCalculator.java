package io.settlementhub.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
public class MdrCalculator {

    private static final BigDecimal BASIS_POINTS_DIVISOR = BigDecimal.valueOf(10_000);
    private static final int MAX_MDR_BPS = 2000;

    public long calculate(long grossMinor, int mdrBps) {
        if (grossMinor < 0) {
            throw new IllegalArgumentException("grossMinor must not be negative");
        }
        if (mdrBps < 0 || mdrBps > MAX_MDR_BPS) {
            throw new IllegalArgumentException("mdrBps must be between 0 and " + MAX_MDR_BPS);
        }
        return BigDecimal.valueOf(grossMinor)
                .multiply(BigDecimal.valueOf(mdrBps))
                .divide(BASIS_POINTS_DIVISOR, 0, RoundingMode.HALF_UP)
                .longValueExact();
    }
}
