package com.example.starter.file;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.starter.file.storage.FileStore;
import com.example.starter.file.storage.LocalDiskFileStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 제품 root가 아닌 시험 임시 디렉터리에서만 디스크 실패/링크를 검사한다. */
class DiskFileStoreTest {
    @TempDir Path temporary;

    /** 생성 키·새 파일·동일 handle·삭제와 덮어쓰기 거부를 확인한다. */
    @Test
    void saveOpenDeleteNeverOverwritesAndCleansStagingFiles() throws Exception {
        Path root = temporary.resolve("files");
        var store = new LocalDiskFileStore(root);
        String key = UUID.randomUUID() + ".png";
        byte[] bytes = {1, 2, 3};
        store.save(key, bytes);

        try (var input = store.open(key, 3)) {
            assertArrayEquals(bytes, input.readAllBytes());
        }

        assertThrows(FileStore.WriteFailure.class, () -> store.save(key, new byte[] {4, 5, 6}));
        assertArrayEquals(bytes, Files.readAllBytes(root.resolve(key)));
        assertThrows(RuntimeException.class, () -> store.open(key, 4));

        try (var entries = Files.list(root)) {
            assertEquals(1, entries.count());
        }

        store.delete(key);
        store.delete(key);
        assertFalse(Files.exists(root.resolve(key)));

        for (String attack :
                new String[] {
                    "../outside.png",
                    "C:/outside.png",
                    "/outside.png",
                    "../" + key,
                    key + "/x",
                    "file.png"
                }) {
            assertThrows(RuntimeException.class, () -> store.save(attack, bytes));
            assertThrows(RuntimeException.class, () -> store.open(attack, 3));
            assertThrows(RuntimeException.class, () -> store.delete(attack));
        }
    }

    /** 최종 파일과 루트 자체의 symlink를 거부한다. */
    @Test
    void symlinksCannotReadOverwriteOrDeleteOutsideFiles() throws Exception {
        Path outside = temporary.resolve("outside");
        Files.createDirectory(outside);
        Path external = outside.resolve("untouched");
        Files.write(external, new byte[] {7, 8, 9});
        Path linked = temporary.resolve("linked");

        try {
            Files.createSymbolicLink(linked, outside);
        } catch (IOException | UnsupportedOperationException denied) {
            Assumptions.abort("OS does not permit symlink creation");
        }

        assertThrows(RuntimeException.class, () -> new LocalDiskFileStore(linked));
        Path root = temporary.resolve("safe");
        var store = new LocalDiskFileStore(root);
        String key = UUID.randomUUID() + ".png";
        Files.createSymbolicLink(root.resolve(key), external);
        assertThrows(RuntimeException.class, () -> store.open(key, 3));
        assertThrows(RuntimeException.class, () -> store.delete(key));
        assertThrows(RuntimeException.class, () -> store.save(key, new byte[] {1}));
        assertArrayEquals(new byte[] {7, 8, 9}, Files.readAllBytes(external));
    }

    /** 루트를 교체한 뒤에는 이전 저장소 인스턴스로 파일을 조작하지 않는다. */
    @Test
    void replacedRootAndWebDirectoriesAreRejected() throws Exception {
        Path root = temporary.resolve("safe");
        var store = new LocalDiskFileStore(root);
        Files.move(root, temporary.resolve("previous"));
        Files.createDirectory(root);
        assertThrows(
                RuntimeException.class,
                () -> store.save(UUID.randomUUID() + ".jpg", new byte[] {1}));
        assertThrows(
                RuntimeException.class, () -> new LocalDiskFileStore(temporary.resolve("public")));
    }
}
