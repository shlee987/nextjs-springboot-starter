package com.example.starter.audit.mapper;

import com.example.starter.audit.model.AuditEvent;
import com.example.starter.audit.model.AuditQuery;
import com.example.starter.audit.model.AuditViews;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/** 감사 runtime 경로는 INSERT와 SELECT만 제공한다. */
@Mapper
public interface AuditMapper {
    /** 접근 이벤트 한 행을 append한다. */
    int insertAccess(AuditEvent event);

    /** 보안 이벤트 한 행을 append한다. */
    int insertSecurity(AuditEvent event);

    /** 조회 자체의 감사는 상위 서비스 advice가 한 번 기록한다. */
    List<AuditViews.Access> selectAccessList(AuditQuery query);

    /** 접근 목록과 동일 조건 건수다. */
    long selectAccessCount(AuditQuery query);

    /** 보안 이력 공개 필드만 읽는다. */
    List<AuditViews.Security> selectSecurityList(AuditQuery query);

    /** 보안 목록과 동일 조건 건수다. */
    long selectSecurityCount(AuditQuery query);
}
