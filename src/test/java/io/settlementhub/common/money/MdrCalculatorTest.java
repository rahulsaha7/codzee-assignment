package io.settlementhub.common.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MdrCalculatorTest {

    private final MdrCalculator calculator = new MdrCalculator();

    @ParameterizedTest
    @CsvSource({
        "10000, 150, 150",
        "999, 25, 2",
        "100, 25, 0",
        "200, 25, 1",
        "1, 2000, 0",
        "100, 2000, 20",
        "0, 150, 0",
        "12345, 175, 216",
        "333, 333, 11"
    })
    void calculatesMdrWithHalfUpRounding(long grossMinor, int mdrBps, long expectedMdrMinor) {
        assertThat(calculator.calculate(grossMinor, mdrBps)).isEqualTo(expectedMdrMinor);
    }

    @Test
    void roundsHalfUpAtExactlyHalfAMinorUnit() {
        assertThat(calculator.calculate(20, 250)).isEqualTo(1);
        assertThat(calculator.calculate(20, 249)).isZero();
        assertThat(calculator.calculate(20, 251)).isEqualTo(1);
        assertThat(calculator.calculate(4, 1250)).isEqualTo(1);
    }

    @Test
    void rejectsNegativeGrossAmount() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(-1, 150))
                .withMessageContaining("grossMinor");
    }

    @Test
    void rejectsMdrBelowRange() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(1000, -1))
                .withMessageContaining("mdrBps");
    }

    @Test
    void rejectsMdrAboveRange() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(1000, 2001))
                .withMessageContaining("mdrBps");
    }
}
