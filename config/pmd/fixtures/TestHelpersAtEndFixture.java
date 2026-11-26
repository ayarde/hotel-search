import org.junit.jupiter.api.Test;

// Fixture de verifyPmdTestRules: no se compila. Ver build.gradle.kts.
// Cada línea que la regla debe marcar termina con "expect: <regla>".
class TestHelpersAtEndFixture {

    @Test
    void firstTest_Scenario_Passes() {
    }

    private void helperBetweenTests() { // expect: TestHelpersAtEnd
    }

    @Test
    void secondTest_Scenario_Passes() {
    }

    private void helperAfterAllTests() {
    }
}
