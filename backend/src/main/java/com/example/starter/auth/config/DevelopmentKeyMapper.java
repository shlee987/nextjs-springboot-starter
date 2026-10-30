package com.example.starter.auth.config;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** local 전용 schema 접근이다. shared 구성은 이 Mapper를 호출하지 않는다. */
@Mapper
public interface DevelopmentKeyMapper {
    /** 최초 키 생성 경쟁을 DB transaction advisory lock으로 직렬화한다. */
    String selectInitializationLock();

    /** 현재 active 키를 byte 배열로 읽는다. */
    KeyRow selectActiveKey();

    /** 회전 전 공개키도 기존 access 검증용으로 읽는다. */
    List<KeyRow> selectKeyList();

    /** local DB에 생성한 키를 한 번 기록한다. */
    int insertKey(
            @Param("keyId") String keyId,
            @Param("privateKey") byte[] privateKey,
            @Param("publicKey") byte[] publicKey,
            @Param("now") Instant now);

    /** 개인키 배열의 기본 toString을 노출하지 않는 DB 전용 모델이다. */
    record KeyRow(String keyId, byte[] privateKeyPkcs8, byte[] publicKeySpki, boolean active) {
        /** 로그에 key material을 넣지 않는다. */
        @Override
        public String toString() {
            return "KeyRow[redacted]";
        }
    }
}
