package com.example.starter;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/** DB에 연결하지 않고 commit/rollback callback과 순서를 관찰하는 시험 transaction이다. */
public class TestTransactions extends AbstractPlatformTransactionManager {
    public int commits;
    public int rollbacks;
    public int timeout;
    public boolean failCommit;
    public int active;

    /** 외부 자원 대신 호출별 표식만 생성한다. */
    @Override
    protected Object doGetTransaction() {
        return new Object();
    }

    /** 제품에서 지정한 대기 상한을 관찰한다. */
    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        timeout = definition.getTimeout();
        active++;
    }

    /** 실제 DB commit의 증거가 아닌 callback 순서만 검사한다. */
    @Override
    protected void doCommit(DefaultTransactionStatus status) {
        if (failCommit) {
            throw new IllegalStateException("SYNTHETIC_COMMIT_FAILURE");
        }

        commits++;
        active--;
    }

    /** 오류 경로에서 transaction이 rollback됨을 관찰한다. */
    @Override
    protected void doRollback(DefaultTransactionStatus status) {
        rollbacks++;
        active--;
    }
}
