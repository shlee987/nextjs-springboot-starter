package com.example.starter.file.storage;

import java.io.InputStream;

/** 생성 키만 사용하는 저장소다. 구현이 파일 경로를 API에 반환하지 않는다. */
public interface FileStore {
    /** 실패 시 자체 부분파일을 정리하며 잔류 가능성은 WriteFailure로 알린다. */
    void save(String key, byte[] bytes);

    /** symlink를 따라가지 않는 검증된 파일 핸들을 연다. 호출자가 반드시 닫는다. */
    InputStream open(String key, long expectedSize);

    /** 없어진 파일도 삭제 완료로 취급하며 오류는 숨기지 않는다. */
    void delete(String key);

    /** 쓰기 실패 뒤 소유 부분파일이 남을 수 있는지 구분한다. */
    final class WriteFailure extends RuntimeException {
        private final boolean residue;

        /** 원문은 공개하지 않고 보상 판단만 전달한다. */
        public WriteFailure(boolean residue, Throwable cause) {
            super("File write failed", cause);
            this.residue = residue;
        }

        /** true이면 quota/PENDING을 남기고 확인해야 한다. */
        public boolean residue() {
            return residue;
        }
    }
}
