package com.example.starter.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** 현재 명세는 scalar query만 사용하므로 값 배열의 첫 항목을 임의 선택하지 않는다. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class ScalarQueryFilter extends OncePerRequestFilter {
    /** query 원문은 기록하지 않고 이름 중복/길이/개수만 parser 전에 검사한다. */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String query = request.getQueryString();

        if (query != null) {
            if (query.length() > 8192) {
                throw new ApiException(ApiError.MALFORMED_REQUEST);
            }

            String[] pairs = query.split("&", -1);

            if (pairs.length > 64) {
                throw new ApiException(ApiError.MALFORMED_REQUEST);
            }

            var names = new HashSet<String>();

            for (String pair : pairs) {
                String name;

                try {
                    name = URLDecoder.decode(pair.split("=", 2)[0], StandardCharsets.UTF_8);
                } catch (IllegalArgumentException bad) {
                    throw new ApiException(ApiError.MALFORMED_REQUEST);
                }

                if (!name.matches("[A-Za-z][A-Za-z0-9]{0,63}") || !names.add(name)) {
                    throw new ApiException(ApiError.MALFORMED_REQUEST);
                }
            }
        }

        chain.doFilter(request, response);
    }
}
