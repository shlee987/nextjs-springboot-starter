package com.example.starter.file.web;

import com.example.starter.common.web.ApiError;
import com.example.starter.common.web.ApiException;
import com.example.starter.common.web.ApiResponses;
import com.example.starter.common.web.PageData;
import com.example.starter.file.model.FileInfo;
import com.example.starter.file.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 본인 이미지의 업로드/목록/attachment 다운로드/삭제만 제공한다. */
@RestController
@RequestMapping("/api/files")
public class FileController {
    private final FileService service;

    /** 실제 서비스에만 저장과 권한 검사를 위임한다. */
    public FileController(FileService service) {
        this.service = service;
    }

    /** JSON owner/status/key를 받지 않고 file part 하나만 허용한다. */
    @PostMapping(
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            requestBody = @RequestBody(required = true),
            operationId = "uploadFile",
            summary = "본인 PNG/JPEG 저장",
            description =
                    "file part1개,5MiB.8192px/16M픽셀 검사 후 재인코딩. 소유자100MiB. 400 이름/내용,413 크기,415"
                            + " 형식,409 quota,429 처리/횟수,503 저장/DB/감사 장애.")
    @ApiResponse(
            responseCode = "201",
            description = "재인코딩 완료 메타데이터",
            headers =
                    @Header(
                            name = "Location",
                            description = "/api/files/{fileId}/content",
                            schema = @Schema(type = "string")))
    public ResponseEntity<com.example.starter.common.web.ApiResponse<FileInfo>> upload(
            @RequestPart("file") MultipartFile file, HttpServletRequest request)
            throws ServletException, IOException {
        var parts = request.getParts();

        if (parts.size() != 1
                || parts.stream()
                        .anyMatch(
                                part ->
                                        !part.getName().equals("file")
                                                || part.getSubmittedFileName() == null)) {
            throw new ApiException(ApiError.MALFORMED_REQUEST);
        }

        var result = service.upload(file);

        return ApiResponses.created(
                URI.create("/api/files/" + result.fileId() + "/content"), result);
    }

    /** 본인의 완료 파일만 반환한다. */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "searchFiles",
            summary = "내 파일 목록",
            description = "page0/size20, 최대100. 생성시각DESC+ID. READY만 반환하며 owner는 인증 주체로 결정.")
    public ResponseEntity<com.example.starter.common.web.ApiResponse<PageData<FileInfo>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponses.ok(service.list(page, size));
    }

    /** 다른 소유자와 미공개 파일에는404를 반환한다. */
    @GetMapping("/{fileId}/content")
    @Operation(
            operationId = "downloadFile",
            summary = "본인 이미지 다운로드",
            description =
                    "소유자만 접근. attachment·UTF-8 filename·nosniff·no-store. 서버 경로는 반환하지 않음. 다른 소유자/없는"
                            + " 파일404.")
    @ApiResponse(
            responseCode = "200",
            description = "재인코딩된 이미지 바이트",
            headers = {
                @Header(
                        name = "Content-Disposition",
                        description = "attachment, UTF-8 filename",
                        schema = @Schema(type = "string")),
                @Header(
                        name = "Cache-Control",
                        schema = @Schema(type = "string", allowableValues = "no-store")),
                @Header(
                        name = "X-Content-Type-Options",
                        schema = @Schema(type = "string", allowableValues = "nosniff")),
                @Header(
                        name = "Content-Length",
                        schema = @Schema(type = "integer", maximum = "5242880"))
            },
            content = {
                @Content(
                        mediaType = "image/png",
                        schema = @Schema(type = "string", format = "binary")),
                @Content(
                        mediaType = "image/jpeg",
                        schema = @Schema(type = "string", format = "binary"))
            })
    public ResponseEntity<Resource> content(@PathVariable String fileId) {
        var file = service.download(fileId);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.info().contentType()))
                .contentLength(file.info().size())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(file.info().originalName(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.ACCEPT_RANGES, "none")
                .body(new InputStreamResource(file.stream()));
    }

    /** 디스크 삭제와 용량 반환이 확인돼야204를 반환한다. */
    @DeleteMapping("/{fileId}")
    @Operation(
            operationId = "deleteFile",
            summary = "본인 파일 삭제",
            description = "성공204. 다른 소유자/없음404. 실패503에서는 삭제 완료로 표시하지 않고 DELETING을 재시도할 수 있음.")
    @ApiResponse(responseCode = "204", description = "본문 없음")
    public ResponseEntity<Void> delete(@PathVariable String fileId) {
        service.delete(fileId);

        return ApiResponses.noContent();
    }
}
