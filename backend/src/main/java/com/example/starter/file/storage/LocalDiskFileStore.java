package com.example.starter.file.storage;

import com.example.starter.common.web.ApiException;
import com.example.starter.file.model.FileError;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** 웹 루트 밖의 전용 디렉터리에 생성 키만 저장하고 링크/덮어쓰기를 거부한다. */
public final class LocalDiskFileStore implements FileStore {
    private final Path root;
    private final Object rootKey;

    /** 전용 루트를 만들고 소유자 전용 권한·링크·경로를 확인한 뒤 고정한다. */
    public LocalDiskFileStore(Path directory) {
        root = directory.toAbsolutePath().normalize();

        if (root.getParent() == null
                || root.toString()
                        .replace('\\', '/')
                        .matches("(?i).*/(public|static|resources)(/.*)?")) {
            throw new IllegalStateException("Private non-web file root required");
        }

        try {
            noLinks(root);
            boolean created = !Files.exists(root, LinkOption.NOFOLLOW_LINKS);
            Path ancestor = root;

            while (!Files.exists(ancestor, LinkOption.NOFOLLOW_LINKS)) {
                ancestor = ancestor.getParent();
            }

            if (Files.getFileStore(ancestor).supportsFileAttributeView("posix")) {
                Files.createDirectories(
                        root,
                        PosixFilePermissions.asFileAttribute(
                                PosixFilePermissions.fromString("rwx------")));
            } else {
                Files.createDirectories(root);
            }

            noLinks(root);

            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Private directory required");
            }

            if (created) {
                privatePermissions(root);
            }

            verifyPermissions(root);

            if (!Files.isWritable(root)) {
                throw new IOException("File root is not writable");
            }

            rootKey =
                    identity(
                            Files.readAttributes(
                                    root, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS));

            if (rootKey == null) {
                throw new IOException("Stable directory identity required");
            }
        } catch (IOException failure) {
            throw new IllegalStateException("File root unavailable", failure);
        }
    }

    /** 검증한 바이트만 임시 파일에 쓰고 CREATE_NEW 최종 파일을 완료한 뒤 임시 파일을 지운다. */
    @Override
    public void save(String key, byte[] bytes) {
        if (bytes == null || bytes.length < 1 || bytes.length > 5242880) {
            throw new WriteFailure(false, new IllegalArgumentException("Bounded bytes required"));
        }

        Path finalPath = path(key);
        Path temporary = root.resolve(UUID.randomUUID() + ".part");
        Object temporaryKey = null;
        Object finalKey = null;
        boolean temporaryCreated = false;
        boolean finalCreated = false;

        try {
            checkRoot();

            try (var output =
                    FileChannel.open(
                            temporary,
                            StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.WRITE,
                            LinkOption.NOFOLLOW_LINKS)) {
                temporaryCreated = true;
                temporaryKey = identity(attributes(temporary));
                ByteBuffer buffer = ByteBuffer.wrap(bytes);

                while (buffer.hasRemaining()) {
                    output.write(buffer);
                }

                output.force(true);
            }

            checkRoot();

            try (var input =
                            FileChannel.open(
                                    temporary, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
                    var output =
                            FileChannel.open(
                                    finalPath,
                                    StandardOpenOption.CREATE_NEW,
                                    StandardOpenOption.WRITE,
                                    LinkOption.NOFOLLOW_LINKS)) {
                finalCreated = true;
                finalKey = identity(attributes(finalPath));
                long position = 0;

                while (position < bytes.length) {
                    long amount = input.transferTo(position, bytes.length - position, output);

                    if (amount <= 0) {
                        throw new IOException("File copy incomplete");
                    }

                    position += amount;
                }

                output.force(true);
            }

            checkRoot();
            removeOwned(temporary, temporaryKey);
            temporaryKey = null;
        } catch (IOException | RuntimeException failure) {
            // 생성 직후 identity 조회가 실패해도 알 수 없는 잔류를 없다고 판단하지 않는다.
            boolean residue =
                    temporaryCreated && temporaryKey == null || finalCreated && finalKey == null;

            try {
                if (finalKey != null) {
                    removeOwned(finalPath, finalKey);
                }
            } catch (IOException | RuntimeException cleanup) {
                failure.addSuppressed(cleanup);
                residue = true;
            }

            try {
                if (temporaryKey != null) {
                    removeOwned(temporary, temporaryKey);
                }
            } catch (IOException | RuntimeException cleanup) {
                failure.addSuppressed(cleanup);
                residue = true;
            }

            throw new WriteFailure(residue, failure);
        }
    }

    /** NOFOLLOW로 연 핸들을 그대로 전달하며 나중에 경로를 다시 해석하지 않는다. */
    @Override
    public InputStream open(String key, long expectedSize) {
        Path file = path(key);
        FileChannel channel = null;

        try {
            checkRoot();
            var before = attributes(file);
            channel = FileChannel.open(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
            var after = attributes(file);
            checkRoot();

            if (!Objects.equals(identity(before), identity(after))
                    || channel.size() != expectedSize
                    || expectedSize < 1
                    || expectedSize > 5242880) {
                throw new IOException("File identity or size changed");
            }

            return Channels.newInputStream(channel);
        } catch (IOException | RuntimeException failure) {
            if (channel != null) {
                try {
                    channel.close();
                } catch (IOException close) {
                    failure.addSuppressed(close);
                }
            }

            throw new ApiException(FileError.FILE_STORAGE_UNAVAILABLE, failure);
        }
    }

    /** 재귀 삭제 없이 검증된 생성 키 하나만 삭제한다. */
    @Override
    public void delete(String key) {
        Path file = path(key);

        try {
            checkRoot();

            try {
                var entry = attributes(file);
                removeOwned(file, identity(entry));
            } catch (NoSuchFileException absent) {
                /* 같은 키의 삭제 재시도에서 이미 없는 경우만 허용한다. */
            }

            checkRoot();
        } catch (IOException | RuntimeException failure) {
            throw new ApiException(FileError.FILE_STORAGE_UNAVAILABLE, failure);
        }
    }

    /** 원본 이름·절대경로·구분자를 받지 않는다. */
    private Path path(String key) {
        if (key == null
                || !key.matches(
                        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(png|jpg)")) {
            throw new ApiException(FileError.FILE_STORAGE_UNAVAILABLE);
        }

        Path file = root.resolve(key);

        if (!file.getParent().equals(root)) {
            throw new ApiException(FileError.FILE_STORAGE_UNAVAILABLE);
        }

        return file;
    }

    /** 저장 루트가 교체되거나 링크가 되면 중단한다. */
    private void checkRoot() throws IOException {
        noLinks(root);
        var entry =
                Files.readAttributes(root, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);

        if (!entry.isDirectory() || !Objects.equals(rootKey, identity(entry))) {
            throw new IOException("File root identity changed");
        }

        verifyPermissions(root);
    }

    /** 일반 파일만 열고 symlink는 거부한다. */
    private BasicFileAttributes attributes(Path file) throws IOException {
        var value =
                Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);

        if (!value.isRegularFile() || value.isSymbolicLink() || value.isOther()) {
            throw new IOException("Regular owned file required");
        }

        return value;
    }

    /** 이 작업에서 만들거나 확인한 파일 identity만 삭제한다. */
    private void removeOwned(Path path, Object expected) throws IOException {
        checkRoot();

        if (!Objects.equals(identity(attributes(path)), expected)) {
            throw new IOException("Refusing changed file");
        }

        Files.delete(path);
    }

    /** 각 상위 경로의 symlink를 확인한다. 상위 디렉터리도 관리자가 보호해야 한다. */
    private static void noLinks(Path path) throws IOException {
        for (Path current = path; current != null; current = current.getParent()) {
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                var entry =
                        Files.readAttributes(
                                current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);

                if (entry.isSymbolicLink() || entry.isOther()) {
                    throw new IOException("Symbolic root path forbidden");
                }
            }
        }
    }

    /**
     * Unix fileKey를 우선하고 Windows 기본 provider에서는 보호된 ACL과 생성시각을 함께 사용한다. 생성시각은 관리자에 대한 불변 식별자가 아니며
     * 상위 경로도 신뢰된 OS 계정이 관리해야 한다.
     */
    private Object identity(BasicFileAttributes value) throws IOException {
        if (value.fileKey() != null) {
            return value.fileKey();
        }

        if (Files.getFileAttributeView(root, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS)
                        != null
                && value.creationTime().toMillis() > 0) {
            return value.creationTime();
        }

        throw new IOException("File identity unavailable");
    }

    /** 새 루트만 소유자 전용으로 만들고 기존 외부 루트의 ACL은 바꾸지 않는다. */
    private static void privatePermissions(Path directory) throws IOException {
        var posix =
                Files.getFileAttributeView(
                        directory, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);

        if (posix != null) {
            posix.setPermissions(PosixFilePermissions.fromString("rwx------"));

            return;
        }

        var acl =
                Files.getFileAttributeView(
                        directory, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);

        if (acl == null) {
            throw new IOException("Protected file permissions unavailable");
        }

        var entry =
                AclEntry.newBuilder()
                        .setType(AclEntryType.ALLOW)
                        .setPrincipal(acl.getOwner())
                        .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                        .setFlags(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT)
                        .build();
        acl.setAcl(List.of(entry));
    }

    /** 다른 일반 계정이 루트를 바꾸지 못하도록 쓰기 권한을 확인한다. */
    private static void verifyPermissions(Path directory) throws IOException {
        var posix =
                Files.getFileAttributeView(
                        directory, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);

        if (posix != null) {
            var permissions = posix.readAttributes().permissions();

            if (permissions.contains(PosixFilePermission.GROUP_WRITE)
                    || permissions.contains(PosixFilePermission.OTHERS_WRITE)) {
                throw new IOException("Private file root permissions required");
            }

            return;
        }

        var acl =
                Files.getFileAttributeView(
                        directory, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);

        if (acl == null) {
            throw new IOException("Protected file permissions unavailable");
        }

        var writes =
                Set.of(
                        AclEntryPermission.WRITE_DATA,
                        AclEntryPermission.APPEND_DATA,
                        AclEntryPermission.DELETE_CHILD,
                        AclEntryPermission.DELETE,
                        AclEntryPermission.WRITE_ACL,
                        AclEntryPermission.WRITE_OWNER);

        for (var entry : acl.getAcl()) {
            if (entry.type() == AclEntryType.ALLOW
                    && !entry.principal().equals(acl.getOwner())
                    && entry.permissions().stream().anyMatch(writes::contains)) {
                throw new IOException("Private file root ACL required");
            }
        }
    }
}
