package com.example.starter.auth.config;

import com.example.starter.common.config.AppProperties;
import com.example.starter.common.web.UtcTimes;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyOperation;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** local DB 키와 외부 파일 키를 구성 시점에 분리한다. 토큰 URL로 원격 조회하지 않는다. */
@Configuration(proxyBeanMethods = false)
public class SigningKeyConfiguration {
    /** 최초 승인된 local 시작에서만 DB 잠금 안에서 RSA3072를 생성하거나 복원한다. */
    @Bean
    @DependsOn("foundationMigrations")
    @ConditionalOnProperty(name = "app.bootstrap.local-data-enabled", havingValue = "true")
    SigningKeys localSigningKeys(
            DevelopmentKeyMapper mapper, PlatformTransactionManager manager, Clock clock) {
        return new TransactionTemplate(manager)
                .execute(
                        status -> {
                            try {
                                mapper.selectInitializationLock();
                                var active = mapper.selectActiveKey();

                                if (active == null) {
                                    var generator = KeyPairGenerator.getInstance("RSA");
                                    generator.initialize(3072);
                                    var pair = generator.generateKeyPair();
                                    mapper.insertKey(
                                            "local-" + UUID.randomUUID(),
                                            pair.getPrivate().getEncoded(),
                                            pair.getPublic().getEncoded(),
                                            UtcTimes.millis(clock.instant()));
                                    active = mapper.selectActiveKey();
                                }

                                KeyFactory factory = KeyFactory.getInstance("RSA");
                                var publicKey =
                                        (RSAPublicKey)
                                                factory.generatePublic(
                                                        new X509EncodedKeySpec(
                                                                active.publicKeySpki()));
                                var privateKey =
                                        (RSAPrivateKey)
                                                factory.generatePrivate(
                                                        new PKCS8EncodedKeySpec(
                                                                active.privateKeyPkcs8()));
                                RSAKey signing =
                                        new RSAKey.Builder(publicKey)
                                                .privateKey(privateKey)
                                                .keyID(active.keyId())
                                                .keyUse(KeyUse.SIGNATURE)
                                                .algorithm(JWSAlgorithm.RS256)
                                                .build();
                                var publicKeys = new ArrayList<JWK>();

                                for (var row : mapper.selectKeyList()) {
                                    var key =
                                            (RSAPublicKey)
                                                    factory.generatePublic(
                                                            new X509EncodedKeySpec(
                                                                    row.publicKeySpki()));
                                    publicKeys.add(
                                            new RSAKey.Builder(key)
                                                    .keyID(row.keyId())
                                                    .keyUse(KeyUse.SIGNATURE)
                                                    .algorithm(JWSAlgorithm.RS256)
                                                    .build());
                                }

                                return validate(signing, new JWKSet(publicKeys));
                            } catch (Exception exception) {
                                throw new IllegalStateException(
                                        "Local signing key initialization failed");
                            }
                        });
    }

    /** 공유 환경은 운영자가 주입한 private JWK와 public JWKS 파일만 읽는다. */
    @Bean
    @ConditionalOnProperty(
            name = "app.bootstrap.local-data-enabled",
            havingValue = "false",
            matchIfMissing = true)
    SigningKeys externalSigningKeys(AppProperties properties) {
        try {
            var jwt = properties.auth().jwt();
            RSAKey privateKey =
                    RSAKey.parse(
                            Files.readString(
                                    Path.of(jwt.privateKeyLocation()), StandardCharsets.UTF_8));
            JWKSet publicKeys =
                    JWKSet.parse(
                            Files.readString(
                                    Path.of(jwt.publicKeysLocation()), StandardCharsets.UTF_8));

            if (!jwt.activeKeyId().equals(privateKey.getKeyID())) {
                throw new IllegalArgumentException("Active key mismatch");
            }

            return validate(privateKey, publicKeys);
        } catch (Exception exception) {
            throw new IllegalStateException("External signing key configuration failed");
        }
    }

    /** 키 크기·용도·kid 중복·active 공개키 일치와 private/public 분리를 검증한다. */
    private static SigningKeys validate(RSAKey active, JWKSet keys) throws Exception {
        if (!active.isPrivate()
                || active.size() < 3072
                || active.getKeyID() == null
                || active.getKeyID().isBlank()
                || active.getAlgorithm() != null
                        && !JWSAlgorithm.RS256.equals(active.getAlgorithm())
                || active.getKeyUse() != null && !KeyUse.SIGNATURE.equals(active.getKeyUse())
                || active.getKeyOperations() != null
                        && !active.getKeyOperations().contains(KeyOperation.SIGN)) {
            throw new IllegalArgumentException("Invalid active key");
        }

        var ids = new HashSet<String>();

        for (JWK key : keys.getKeys()) {
            if (!(key instanceof RSAKey rsa)
                    || rsa.isPrivate()
                    || rsa.size() < 3072
                    || key.getKeyID() == null
                    || key.getKeyID().isBlank()
                    || !ids.add(key.getKeyID())
                    || key.getAlgorithm() != null && !JWSAlgorithm.RS256.equals(key.getAlgorithm())
                    || key.getKeyUse() != null && !KeyUse.SIGNATURE.equals(key.getKeyUse())
                    || key.getKeyOperations() != null
                            && !key.getKeyOperations().contains(KeyOperation.VERIFY)) {
                throw new IllegalArgumentException("Invalid public key set");
            }
        }

        JWK match = keys.getKeyByKeyId(active.getKeyID());

        if (match == null || !match.computeThumbprint().equals(active.computeThumbprint())) {
            throw new IllegalArgumentException("Public/private key mismatch");
        }

        // 신뢰 파일의 공개/개인 material이 실제로 짝인지 확인한다. 새 키를 생성하지 않는다.
        byte[] challenge = "starter-signing-key-readiness-v1".getBytes(StandardCharsets.UTF_8);
        var signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(active.toRSAPrivateKey());
        signature.update(challenge);
        byte[] proof = signature.sign();
        signature.initVerify(active.toRSAPublicKey());
        signature.update(challenge);

        if (!signature.verify(proof)) {
            throw new IllegalArgumentException("Signing key proof failed");
        }

        return new SigningKeys(active, keys);
    }
}
