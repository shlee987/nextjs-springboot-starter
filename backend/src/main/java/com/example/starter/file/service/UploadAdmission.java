package com.example.starter.file.service;

import com.example.starter.common.web.ApiException;
import com.example.starter.file.model.FileError;
import java.util.concurrent.Semaphore;

/** 노드당 두 요청만 파싱/이미지 처리하며 무한 대기열을 만들지 않는다. */
public final class UploadAdmission {
    private final Semaphore slots = new Semaphore(2);
    private final ThreadLocal<Integer> depth = new ThreadLocal<>();

    /** 필터와 서비스의 같은 스레드 중첩은 슬롯 하나만 사용한다. */
    public Lease enter() {
        Integer current = depth.get();

        if (current == null) {
            if (!slots.tryAcquire()) {
                throw new ApiException(FileError.FILE_PROCESSING_BUSY, 1);
            }

            depth.set(1);
        } else {
            depth.set(current + 1);
        }

        return new Lease();
    }

    /** 각 요청의 finally에서만 허가를 돌려준다. */
    public final class Lease implements AutoCloseable {
        private boolean closed;

        /** 중복 close와 재사용 스레드의 잔여 문맥을 막는다. */
        @Override
        public void close() {
            if (!closed) {
                closed = true;
                int current = depth.get();

                if (current == 1) {
                    depth.remove();
                    slots.release();
                } else {
                    depth.set(current - 1);
                }
            }
        }
    }
}
