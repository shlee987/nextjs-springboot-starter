package com.example.starter.file.service;

import com.example.starter.common.web.ApiError;
import com.example.starter.common.web.ApiException;
import com.example.starter.file.model.FileError;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.Set;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** JDK PNG/JPEG reader로 크기를 먼저 검사하고 새 이미지로 재인코딩한다. */
@Component
public class ImageFiles {
    public static final int MAX_BYTES = 5 * 1024 * 1024;

    /** 표시용 이름만 검사하며 실제 저장 경로에는 사용하지 않는다. */
    public static String filename(String name) {
        if (name == null
                || name.isBlank()
                || name.codePointCount(0, name.length()) > 120
                || name.startsWith(".")
                || name.endsWith(".")
                || name.endsWith(" ")) {
            throw new ApiException(FileError.FILE_NAME_INVALID);
        }

        for (int point : name.codePoints().toArray()) {
            if (Character.isISOControl(point)
                    || Character.getType(point) == Character.FORMAT
                    || Character.getType(point) == Character.LINE_SEPARATOR
                    || Character.getType(point) == Character.PARAGRAPH_SEPARATOR
                    || point >= 0xd800 && point <= 0xdfff
                    || "/\\:\"<>|?*".indexOf(point) >= 0) {
                throw new ApiException(FileError.FILE_NAME_INVALID);
            }
        }

        if (name.lastIndexOf('.') <= 0) {
            throw new ApiException(FileError.FILE_NAME_INVALID);
        }

        String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);

        if (!Set.of("png", "jpg", "jpeg").contains(ext)) {
            throw new ApiException(FileError.FILE_TYPE_UNSUPPORTED);
        }

        return name;
    }

    /** 5MiB 입력·디코딩 전8192/16M검사·5MiB 출력 상한을 모두 적용한다. */
    public Prepared prepare(MultipartFile file) {
        String name = filename(file.getOriginalFilename());

        if (file.getSize() > MAX_BYTES) {
            throw new ApiException(ApiError.PAYLOAD_TOO_LARGE);
        }

        String format = name.toLowerCase(Locale.ROOT).endsWith(".png") ? "png" : "jpeg";
        String contentType = "image/" + format;

        if (!contentType.equalsIgnoreCase(file.getContentType())) {
            throw new ApiException(FileError.FILE_TYPE_UNSUPPORTED);
        }

        byte[] raw;

        try (InputStream input = file.getInputStream()) {
            raw = input.readNBytes(MAX_BYTES + 1);
        } catch (IOException failure) {
            throw new ApiException(FileError.FILE_CONTENT_INVALID, failure);
        }

        if (raw.length > MAX_BYTES) {
            throw new ApiException(ApiError.PAYLOAD_TOO_LARGE);
        }

        if (raw.length < 8
                || format.equals("png")
                        && !(raw[0] == (byte) 137
                                && raw[1] == 80
                                && raw[2] == 78
                                && raw[3] == 71
                                && raw[4] == 13
                                && raw[5] == 10
                                && raw[6] == 26
                                && raw[7] == 10)
                || format.equals("jpeg")
                        && !(raw[0] == (byte) 255
                                && raw[1] == (byte) 216
                                && raw[2] == (byte) 255)) {
            throw new ApiException(FileError.FILE_CONTENT_INVALID);
        }

        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(raw))) {
            var readers = ImageIO.getImageReaders(input);

            if (!readers.hasNext()) {
                throw new ApiException(FileError.FILE_CONTENT_INVALID);
            }

            var reader = readers.next();

            try {
                if (!reader.getFormatName().equalsIgnoreCase(format)) {
                    throw new ApiException(FileError.FILE_CONTENT_INVALID);
                }

                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);

                if (width < 1
                        || height < 1
                        || width > 8192
                        || height > 8192
                        || (long) width * height > 16000000) {
                    throw new ApiException(FileError.FILE_CONTENT_INVALID);
                }

                BufferedImage decoded = reader.read(0);

                if (decoded == null
                        || decoded.getWidth() != width
                        || decoded.getHeight() != height) {
                    throw new ApiException(FileError.FILE_CONTENT_INVALID);
                }

                var clean =
                        new BufferedImage(
                                width,
                                height,
                                format.equals("png")
                                        ? BufferedImage.TYPE_INT_ARGB
                                        : BufferedImage.TYPE_INT_RGB);
                var graphics = clean.createGraphics();

                try {
                    graphics.drawImage(decoded, 0, 0, null);
                } finally {
                    graphics.dispose();
                    decoded.flush();
                }

                var bytes = new LimitedOutput();

                try (var output = new LimitedImageOutput(bytes)) {
                    var writers = ImageIO.getImageWritersByFormatName(format);

                    if (!writers.hasNext()) {
                        throw new ApiException(FileError.FILE_STORAGE_UNAVAILABLE);
                    }

                    var writer = writers.next();

                    try {
                        writer.setOutput(output);
                        writer.write(
                                null,
                                new IIOImage(clean, null, null),
                                writer.getDefaultWriteParam());
                        output.flush();
                    } finally {
                        writer.dispose();
                        clean.flush();
                    }
                }

                return new Prepared(
                        name,
                        contentType,
                        format.equals("png") ? "png" : "jpg",
                        width,
                        height,
                        bytes.toByteArray());
            } finally {
                reader.dispose();
            }
        } catch (ApiException failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
            throw new ApiException(FileError.FILE_CONTENT_INVALID, failure);
        }
    }

    /** 검증 완료 바이트는 HTTP 응답이나 로그로 직접 직렬화하지 않는다. */
    public record Prepared(
            String originalName,
            String contentType,
            String extension,
            int width,
            int height,
            byte[] bytes) {
        /** 바이트와 원본명이 진단에 출력되지 않게 한다. */
        @Override
        public String toString() {
            return "PreparedImage[redacted]";
        }
    }

    /** ImageOutputStream 자체의 캐시도5MiB 이상 커지지 않게 한다. */
    private static final class LimitedImageOutput extends MemoryCacheImageOutputStream {
        /** 출력 스트림과 ImageIO 메모리 캐시에 같은 상한을 적용한다. */
        LimitedImageOutput(OutputStream output) {
            super(output);
        }

        /** 캐시 위치가 상한을 넘기기 전에 단일 바이트 쓰기를 거부한다. */
        @Override
        public void write(int value) throws IOException {
            if (getStreamPosition() >= MAX_BYTES) {
                throw new ApiException(ApiError.PAYLOAD_TOO_LARGE);
            }

            super.write(value);
        }

        /** 캐시가 커지기 전에 벌크 쓰기의 범위를 검사한다. */
        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            if (length > MAX_BYTES - getStreamPosition()) {
                throw new ApiException(ApiError.PAYLOAD_TOO_LARGE);
            }

            super.write(bytes, offset, length);
        }

        /** writer의 위치 이동으로 캐시 상한을 우회하지 않게 한다. */
        @Override
        public void seek(long position) throws IOException {
            if (position > MAX_BYTES) {
                throw new ApiException(ApiError.PAYLOAD_TOO_LARGE);
            }

            super.seek(position);
        }
    }

    /** writer가 큰 출력으로 메모리를 계속 늘리지 않게 한다. */
    private static final class LimitedOutput extends ByteArrayOutputStream {
        /** 단일 바이트 쓰기의 크기를 제한한다. */
        @Override
        public synchronized void write(int value) {
            if (count >= MAX_BYTES) {
                throw new ApiException(ApiError.PAYLOAD_TOO_LARGE);
            }

            super.write(value);
        }

        /** 벌크 쓰기도 복사 전에 상한을 확인한다. */
        @Override
        public synchronized void write(byte[] bytes, int offset, int length) {
            if (length > MAX_BYTES - count) {
                throw new ApiException(ApiError.PAYLOAD_TOO_LARGE);
            }

            super.write(bytes, offset, length);
        }
    }
}
