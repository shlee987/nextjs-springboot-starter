package com.example.starter.audit.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** raw sid를 감사 컬럼에 저장하지 않고 전용 HMAC 지문만 생성한다. */
public final class LoginReferences {
    private final SecretKeySpec key;

    /** 외부 관리 또는 local 용도 분리 파생 키만 받고 기본 공개 키는 제공하지 않는다. */
    public LoginReferences(byte[] bytes) {
        if (bytes.length < 32) {
            throw new IllegalArgumentException("Audit HMAC key must be at least 256 bits");
        }

        this.key = new SecretKeySpec(bytes, "HmacSHA256");
    }

    /** 확인된 sid만 지문화하고 확인할 수 없는 사건은 null을 보존한다. */
    public String forSid(String sid) {
        if (sid == null) {
            return null;
        }

        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(key);

            return HexFormat.of().formatHex(hmac.doFinal(sid.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Audit HMAC unavailable", exception);
        }
    }

    /** 여러 노드가 같은 감사 키를 사용하는지 정책 digest 안에서 비교한다. */
    public String policyFingerprint() {
        return forSid("starter-audit-policy-v1");
    }
}
