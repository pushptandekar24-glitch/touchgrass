package com.wildstep;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class WildStepApplicationTests {

    @Test
    void contextLoads() {
        // Verifies Spring application context loads clean
    }
}
