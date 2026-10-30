package com.example.starter.common.message;

import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/** 서버 전용 한국어 메시지는 API detail로 복사하지 않으며 누락 시 고정 fallback을 쓴다. */
@Component
public class ServerMessages {
    private final MessageSource messages;

    /** Spring 메시지 자원을 주입한다. */
    public ServerMessages(MessageSource messages) {
        this.messages = messages;
    }

    /** 키·파라미터를 외부 문구로 그대로 반환하지 않는다. */
    public String resolve(String key) {
        return messages.getMessage(key, null, "요청을 처리하지 못했습니다.", Locale.KOREAN);
    }
}
