package com.example.starter.auth.config;

import com.example.starter.auth.model.AuthError;
import com.example.starter.auth.model.AuthPermissions;
import com.example.starter.auth.store.AuthStateStore;
import com.example.starter.common.web.ApiException;
import java.time.Clock;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** 검증된 JWT의 sid/sub를 공유 상태와 결합하고 snapshot만 Spring 권한으로 변환한다. */
@Component
public final class AuthAuthenticationConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {
    private final AuthStateStore states;
    private final Clock clock;
    private final JsonMapper json;

    /** 표준 Spring converter 확장점에 실제 교체 가능한 상태 저장소를 주입한다. */
    public AuthAuthenticationConverter(AuthStateStore states, Clock clock, JsonMapper json) {
        this.states = states;
        this.clock = clock;
        this.json = json;
    }

    /** JWT scope나 사용자 제공 role을 authority로 사용하지 않는다. */
    @Override
    public AbstractAuthenticationToken convert(Jwt token) {
        var login = states.require(token.getClaimAsString("sid"), clock.millis());

        if (!login.sid().equals(token.getClaimAsString("sid"))
                || !Long.toString(login.userId()).equals(token.getSubject())
                || login.authenticatedAt() / 1000
                        != token.<Number>getClaim("auth_time").longValue()) {
            throw new ApiException(AuthError.TOKEN_REVOKED);
        }

        var authorities =
                AuthPermissions.parse(json, login.permissionsJson()).stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList();

        return UsernamePasswordAuthenticationToken.authenticated(login, null, authorities);
    }
}
