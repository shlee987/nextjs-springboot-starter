package com.example.starter.auth.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Argon2 provider와 공개 seed hash만 검사하며 JWT 키/DB를 생성하지 않는다. */
class PasswordContractTest {
    /** 확정 Argon2id 비용·식별자와 정확한 원문 비교를 확인한다. */
    @Test
    void argon2EncoderMatchesSeedAndUsesRequiredCosts() {
        var encoder = new PasswordConfiguration().passwordEncoder();
        String seed =
                "{argon2}$argon2id$v=19$m=19456,t=2,p=1$xEIADau0nITStXj1om5P2g$VtAhVuavHavD6NUrNs/l/2qmT0CK8TQuVYmO4eXzeOk";
        assertTrue(encoder.matches("LocalExample1234!", seed));
        assertFalse(encoder.matches(" LocalExample1234!", seed));
        String generated = encoder.encode("😀".repeat(15));
        assertTrue(generated.startsWith("{argon2}$argon2id$v=19$m=19456,t=2,p=1$"));
        assertTrue(encoder.matches("😀".repeat(15), generated));
    }
}
