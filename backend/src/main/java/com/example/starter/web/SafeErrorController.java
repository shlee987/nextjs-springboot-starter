package com.example.starter.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTML Accept와 직접 /error 호출도 진단 정보 없이 처리한다. */
@RestController
public final class SafeErrorController implements ErrorController {
    private final ProblemResponses problems;

    /** Servlet fallback에 공통 출력 경계를 연결한다. */
    public SafeErrorController(ProblemResponses problems) {
        this.problems = problems;
    }

    /** 컨테이너 상태만 읽고 원래 예외·메시지·요청 경로는 읽지 않는다. */
    @RequestMapping("/error")
    public ResponseEntity<Object> error(HttpServletRequest request) {
        Object statusAttribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = statusAttribute instanceof Integer value && value >= 400 && value <= 599
                ? value : 500;

        return problems.response(ApiError.fromStatus(status), status, new HttpHeaders(),
                List.of(), request, null);
    }
}
