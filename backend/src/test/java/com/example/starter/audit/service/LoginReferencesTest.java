package com.example.starter.audit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** 합성 HMAC key만 사용하며 실제 운영/JWT key를 생성하지 않는다. */
class LoginReferencesTest {
    /** 원문 sid 없이 안정된 64자리 지문과 key 분리를 확인한다. */
    @Test
    void fingerprintsAreKeyedAndDoNotExposeRawSid() {
        var first = new LoginReferences(new byte[32]);
        byte[] different = new byte[32];
        different[0] = 1;
        var second = new LoginReferences(different);
        String sid = "b5cf43c1-9585-43cd-b637-e458bb487956";
        assertEquals(64, first.forSid(sid).length());
        assertEquals(first.forSid(sid), first.forSid(sid));
        assertFalse(first.forSid(sid).contains(sid));
        assertFalse(first.forSid(sid).equals(second.forSid(sid)));
        assertNull(first.forSid(null));
        assertThrows(IllegalArgumentException.class, () -> new LoginReferences(new byte[16]));
    }
}
