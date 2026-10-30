package com.example.starter.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.starter.common.web.ApiError;
import com.example.starter.common.web.ApiException;
import com.example.starter.file.model.FileError;
import com.example.starter.file.service.ImageFiles;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

/** JDK 실제 reader/writer로 위장·크기·픽셀·메타데이터 제거를 확인한다. */
class ImageFilesTest {
    static final String MARKER = "SYNTHETIC_IMAGE_METADATA";

    /** PNG text와 JPEG comment를 넣어도 새 바이트에는 남지 않는다. */
    @Test
    void pngAndJpegAreDecodedAndReencodedWithoutOriginalMetadata() throws Exception {
        for (String type : new String[] {"png", "jpeg"}) {
            byte[] original = image(type, 12, 9);
            byte[] marked = metadata(original, type);
            assertTrue(new String(marked, StandardCharsets.ISO_8859_1).contains(MARKER));
            var result =
                    new ImageFiles()
                            .prepare(
                                    new MockMultipartFile(
                                            "file",
                                            "정상 사진." + (type.equals("png") ? "png" : "jpg"),
                                            "image/" + type,
                                            marked));
            var decoded = ImageIO.read(new ByteArrayInputStream(result.bytes()));
            assertEquals(12, decoded.getWidth());
            assertEquals(9, decoded.getHeight());
            assertFalse(new String(result.bytes(), StandardCharsets.ISO_8859_1).contains(MARKER));
            assertEquals("image/" + type, result.contentType());
            assertFalse(result.toString().contains("정상"));
        }
    }

    /** 이름·MIME·실제 decoder가 모두 맞아야 저장 대상으로 인정한다. */
    @Test
    void rejectsUnsafeNamesMimeMismatchAndDisguisedOrMalformedImages() throws Exception {
        byte[] png = image("png", 2, 2);

        for (String name :
                new String[] {
                    "../a.png",
                    "a/../b.png",
                    "C:\\a.png",
                    "a.png\r\nInjected",
                    "a\u202e.png",
                    "a.svg",
                    "png",
                    ".png",
                    "a.png.",
                    "a\u0000.png"
                }) {
            assertThrows(
                    ApiException.class,
                    () ->
                            new ImageFiles()
                                    .prepare(
                                            new MockMultipartFile("file", name, "image/png", png)));
        }

        assertEquals(
                FileError.FILE_TYPE_UNSUPPORTED,
                assertThrows(
                                ApiException.class,
                                () ->
                                        new ImageFiles()
                                                .prepare(
                                                        new MockMultipartFile(
                                                                "file",
                                                                "ok.png",
                                                                "image/jpeg",
                                                                png)))
                        .error());

        for (byte[] bytes :
                new byte[][] {
                    "<svg onload='alert(1)'/>".getBytes(),
                    new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10},
                    image("jpeg", 2, 2)
                }) {
            assertEquals(
                    FileError.FILE_CONTENT_INVALID,
                    assertThrows(
                                    ApiException.class,
                                    () ->
                                            new ImageFiles()
                                                    .prepare(
                                                            new MockMultipartFile(
                                                                    "file",
                                                                    "ok.png",
                                                                    "image/png",
                                                                    bytes)))
                            .error());
        }
    }

    /** IHDR 크기 상한을 decode 전에 검사한다. */
    @Test
    void checksDimensionsBeforeDecodeAndAllows8192PixelEdge() throws Exception {
        byte[] png = image("png", 1, 1);

        for (int[] dimensions : new int[][] {{8193, 1}, {6000, 3000}, {0, 1}}) {
            byte[] changed = png.clone();
            ByteBuffer.wrap(changed, 16, 8).putInt(dimensions[0]).putInt(dimensions[1]);
            CRC32 crc = new CRC32();
            crc.update(changed, 12, 17);
            ByteBuffer.wrap(changed, 29, 4).putInt((int) crc.getValue());
            assertEquals(
                    FileError.FILE_CONTENT_INVALID,
                    assertThrows(
                                    ApiException.class,
                                    () ->
                                            new ImageFiles()
                                                    .prepare(
                                                            new MockMultipartFile(
                                                                    "file",
                                                                    "large.png",
                                                                    "image/png",
                                                                    changed)))
                            .error());
        }

        assertEquals(
                8192,
                new ImageFiles()
                        .prepare(
                                new MockMultipartFile(
                                        "file", "edge.png", "image/png", image("png", 8192, 1)))
                        .width());
    }

    /** Content-Length와 getSize를 신뢰할 수 없는 입력도5MiB+1까지만 읽는다. */
    @Test
    void unknownLengthInputIsBoundedAndClosed() throws Exception {
        AtomicInteger consumed = new AtomicInteger();
        AtomicBoolean closed = new AtomicBoolean();
        MultipartFile file =
                new MultipartFile() {
                    /** 합성 요청의 고정 part 이름을 반환한다. */
                    public String getName() {
                        return "file";
                    }

                    /** 크기 검사를 통과할 수 있는 합성 표시명을 반환한다. */
                    public String getOriginalFilename() {
                        return "large.png";
                    }

                    /** 합성 PNG MIME을 반환한다. */
                    public String getContentType() {
                        return "image/png";
                    }

                    /** 길이 위장을 검사하기 위해 비어 있지 않다고 표시한다. */
                    public boolean isEmpty() {
                        return false;
                    }

                    /** 공격 요청의 길이0 위장을 모사한다. */
                    public long getSize() {
                        return 0;
                    }

                    /** 한도 없는 배열 읽기를 사용하면 시험을 실패시킨다. */
                    public byte[] getBytes() {
                        throw new AssertionError("Unbounded getBytes");
                    }

                    /** 검증 전 원본 파일 저장을 사용하면 시험을 실패시킨다. */
                    public void transferTo(File target) {
                        throw new AssertionError("Original storage");
                    }

                    /** 표시 길이와 무관하게 상한을 넘는 시험 스트림을 연다. */
                    public InputStream getInputStream() {
                        return new InputStream() {
                            /** 무한 입력을 모사해 실제 읽기 바이트 수 제한을 검사한다. */
                            public int read() {
                                consumed.incrementAndGet();

                                return 0;
                            }

                            /** 시험에서 열린 핸들이 해제됐음을 기록한다. */
                            public void close() {
                                closed.set(true);
                            }
                        };
                    }
                };
        assertEquals(
                ApiError.PAYLOAD_TOO_LARGE,
                assertThrows(ApiException.class, () -> new ImageFiles().prepare(file)).error());
        assertEquals(ImageFiles.MAX_BYTES + 1, consumed.get());
        assertTrue(closed.get());
    }

    /** 작은 정상 이미지를 실제 JDK writer로 만든다. */
    static byte[] image(String type, int width, int height) throws IOException {
        var image =
                new BufferedImage(
                        width,
                        height,
                        type.equals("png")
                                ? BufferedImage.TYPE_INT_ARGB
                                : BufferedImage.TYPE_INT_RGB);
        var bytes = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, type, bytes));
        image.flush();

        return bytes.toByteArray();
    }

    /** 테스트용 메타데이터만 추가하며 외부 악성 파일을 가져오지 않는다. */
    private byte[] metadata(byte[] image, String type) throws IOException {
        byte[] text = ("Comment\0" + MARKER).getBytes(StandardCharsets.ISO_8859_1);
        var bytes = new ByteArrayOutputStream();
        var out = new DataOutputStream(bytes);

        if (type.equals("png")) {
            out.write(image, 0, image.length - 12);
            out.writeInt(text.length);
            out.writeBytes("tEXt");
            out.write(text);
            CRC32 crc = new CRC32();
            crc.update("tEXt".getBytes(StandardCharsets.US_ASCII));
            crc.update(text);
            out.writeInt((int) crc.getValue());
            out.write(image, image.length - 12, 12);
        } else {
            out.write(image, 0, 2);
            out.writeShort(0xfffe);
            out.writeShort(text.length + 2);
            out.write(text);
            out.write(image, 2, image.length - 2);
        }

        return bytes.toByteArray();
    }
}
