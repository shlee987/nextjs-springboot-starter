package com.example.starter.auth.store;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** 불투명 갱신 자격과 단방향 locator를 만든다. 원문을 로그에 남기지 않는다. */
public final class TokenSecrets {
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 정적 난수 도구의 인스턴스 생성을 막는다. */
    private TokenSecrets() {}

    /** 256bit CSPRNG를 padding 없는 URL 안전 문자열로 만든다. */
    public static String refresh() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 제한된 서버 입력의 SHA-256만 저장소 키로 사용한다. */
    public static String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
