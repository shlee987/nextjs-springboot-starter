package com.example.starter.file.mapper;

import com.example.starter.file.model.StoredFile;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 소유자 조건과 원자 용량 예약을 고정 SQL로 실행한다. */
@Mapper
public interface FileMapper {
    /** 최초 소유자 quota 행만 만들며 기존 수치를 바꾸지 않는다. */
    int ensureQuota(long ownerUserId);

    /** 동일 행 UPDATE 잠금과 조건으로 동시 예약도100MiB를 넘지 않게 한다. */
    int reserve(@Param("ownerUserId") long ownerUserId, @Param("size") long size);

    /** 삭제한 메타데이터와 같은 transaction에서만 용량을 돌려준다. */
    int release(@Param("ownerUserId") long ownerUserId, @Param("size") long size);

    /** 아직 공개하지 않는 PENDING 메타데이터를 추가한다. */
    int insert(StoredFile file);

    /** UUID만으로 접근하지 않고 소유자까지 같은 조건으로 조회한다. */
    StoredFile selectOwned(@Param("fileId") String fileId, @Param("ownerUserId") long ownerUserId);

    /** 완료된 본인 파일만 안정적인 생성시각/ID 순서로 읽는다. */
    List<StoredFile> selectList(
            @Param("ownerUserId") long ownerUserId,
            @Param("size") int size,
            @Param("offset") long offset);

    /** 목록과 같은 조건으로 센다. */
    long count(long ownerUserId);

    /** 최종 저장 후에만 공개한다. */
    int ready(@Param("fileId") String fileId, @Param("ownerUserId") long ownerUserId);

    /** 삭제하는 동안 새 다운로드/목록에서 숨긴다. */
    int deleting(@Param("fileId") String fileId, @Param("ownerUserId") long ownerUserId);

    /** PENDING 보상 또는 DELETING 완료를 상태 조건으로 한 번만 반영한다. */
    int delete(
            @Param("fileId") String fileId,
            @Param("ownerUserId") long ownerUserId,
            @Param("status") String status);
}
