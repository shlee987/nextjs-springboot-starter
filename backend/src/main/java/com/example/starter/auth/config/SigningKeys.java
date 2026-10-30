package com.example.starter.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;

/** 서명용 active 개인키와 검증용 공개키 집합이다. 진단 문자열에 키를 넣지 않는다. */
public final class SigningKeys {
    private final RSAKey active;
    private final JWKSet publicKeys;

    /** 외부 또는 local DB에서 검증한 키만 보관한다. */
    public SigningKeys(RSAKey active, JWKSet publicKeys) {
        this.active = active;
        this.publicKeys = publicKeys;
    }

    /** Nimbus encoder에만 개인키를 제공한다. */
    public RSAKey active() {
        return active;
    }

    /** decoder에는 공개키만 제공한다. */
    public JWKSet publicKeys() {
        return publicKeys;
    }
}
