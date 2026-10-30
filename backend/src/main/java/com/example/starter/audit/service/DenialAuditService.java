package com.example.starter.audit.service;

import com.example.starter.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/** 서비스 advice 이전 거부를 서버 route registry로 분류한다. */
@Service
public class DenialAuditService {
    private final AuditWriter writer;

    /** low-level writer를 사용하여 advice 재귀를 막는다. */
    public DenialAuditService(AuditWriter writer) {
        this.writer = writer;
    }

    /** raw path를 저장하지 않고 고정 resource와 승인된 숫자 target만 추출한다. */
    public void denied(HttpServletRequest request, String reason) {
        if (reason.equals("TOKEN_EXPIRED")) {
            writer.security("TOKEN_EXPIRED", null, "DENIED", reason);
        }
        String path = request.getRequestURI();
        String resource =
                switch (path) {
                    case "/api/users" -> "USER_LIST";
                    case "/api/audits/access" -> "ACCESS_AUDIT";
                    case "/api/audits/security" -> "SECURITY_AUDIT";
                    default -> null;
                };
        if (path.equals("/api/files") || path.startsWith("/api/files/")) {
            resource = "FILE";
        }
        String target = null;

        if (path.matches("/api/users/[1-9][0-9]{0,18}")) {
            resource = "USER_DETAIL";
            target = path.substring("/api/users/".length());
        }

        if (resource != null) {
            writer.access(resource, "READ", target, "DENIED", reason, null);
        } else {
            writer.security("REQUEST_DENIED", null, "DENIED", reason);
        }
    }

    /** 최외곽/업무 오류 경계의 상태 저장소·폐기 실패도 고정 사건 코드로 남긴다. */
    public void failed(ErrorCode code) {
        String event =
                switch (code.name()) {
                    case "AUTH_STATE_UNAVAILABLE" -> "AUTH_STORE_FAILURE";
                    case "TOKEN_REVOKED" -> "TOKEN_REVOKED";
                    case "RATE_LIMITED" -> "REQUEST_LIMITED";
                    default -> null;
                };

        if (event != null) {
            writer.security(event, null, "FAILURE", code.name());
        }
    }
}
