package cz.kso.fabric.storage.services.file;

import cz.kso.fabric.storage.TestBase;
import cz.kso.fabric.storage.config.StorageProperties;
import cz.kso.fabric.storage.exceptions.ChecksumMismatchException;
import cz.kso.fabric.storage.exceptions.NotFoundException;
import cz.kso.fabric.storage.repositories.ItemRepository;
import cz.kso.fabric.storage.services.FileService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

public class FileServiceContentUpdateTests extends TestBase {

    @Autowired
    private FileService fileService;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private StorageProperties storageProperties;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        storageProperties.setDiscRoot(tempDir.toString());
    }

    @Test
    void shouldReplaceFileContent() throws IOException {
        UUID id = uploadFile("test.txt", "Original content");

        byte[] newContent = bytes("Updated content");

        fileService.updateFileContent(
                id,
                checksum(newContent),
                "text/markdown",
                newContent.length,
                new ByteArrayInputStream(newContent)
        );

        var metadata = fileService.getFile(id);
        var physicalContent = Files.readAllBytes(filePath(id));

        Assertions.assertEquals("Updated content", new String(physicalContent, StandardCharsets.UTF_8));
        Assertions.assertEquals(newContent.length, metadata.getSize());
        Assertions.assertEquals(checksum(newContent), metadata.getChecksum());
        Assertions.assertEquals("text/markdown", metadata.getContentType());

        Assertions.assertEquals(1, itemRepository.count());
        assertNoTemporaryFiles(id);
    }

    @Test
    void shouldPreserveOriginalContentOnChecksumMismatch() throws IOException {
        UUID id = uploadFile("test.txt", "Original content");

        var originalMetadata = fileService.getFile(id);
        byte[] originalContent = Files.readAllBytes(filePath(id));

        byte[] newContent = bytes("Corrupted content");
        String incorrectChecksum = "0".repeat(64);

        Assertions.assertThrows(
                ChecksumMismatchException.class,
                () -> fileService.updateFileContent(
                        id,
                        incorrectChecksum,
                        "text/markdown",
                        newContent.length,
                        new ByteArrayInputStream(newContent)
                )
        );

        var metadata = fileService.getFile(id);

        Assertions.assertArrayEquals(originalContent, Files.readAllBytes(filePath(id)));
        Assertions.assertEquals(originalMetadata.getSize(), metadata.getSize());
        Assertions.assertEquals(originalMetadata.getChecksum(), metadata.getChecksum());
        Assertions.assertEquals(originalMetadata.getContentType(), metadata.getContentType());

        assertNoTemporaryFiles(id);
    }

    @Test
    void shouldRejectUpdateOfNonexistentFile() {
        UUID id = UUID.randomUUID();
        byte[] content = bytes("New content");

        Assertions.assertThrows(
                NotFoundException.class,
                () -> fileService.updateFileContent(
                        id,
                        null,
                        "text/plain",
                        content.length,
                        new ByteArrayInputStream(content)
                )
        );

        Assertions.assertEquals(0, itemRepository.count());
        Assertions.assertEquals(false, Files.exists(filePath(id)));
    }

    @Test
    void shouldAllowReplacingContentWithEmptyFile() throws IOException {
        UUID id = uploadFile("test.txt", "Original content");
        byte[] emptyContent = new byte[0];

        fileService.updateFileContent(
                id,
                checksum(emptyContent),
                "text/plain",
                0,
                new ByteArrayInputStream(emptyContent)
        );

        var metadata = fileService.getFile(id);

        Assertions.assertEquals(0L, metadata.getSize());
        Assertions.assertEquals(checksum(emptyContent), metadata.getChecksum());
        Assertions.assertEquals(0L, Files.size(filePath(id)));

        assertNoTemporaryFiles(id);
    }

    @Test
    void shouldPreserveFileIdentityAndName() throws IOException {
        UUID id = uploadFile("original-name.txt", "Original content");

        byte[] content = bytes("Updated content");

        fileService.updateFileContent(
                id,
                null,
                "text/plain",
                content.length,
                new ByteArrayInputStream(content)
        );

        var metadata = fileService.getFile(id);

        Assertions.assertEquals(id, metadata.getId());
        Assertions.assertEquals("original-name.txt", metadata.getName());
        Assertions.assertEquals(1, itemRepository.count());
    }

    // Helpers

    private UUID uploadFile(String name, String content) throws IOException {
        byte[] data = bytes(content);

        var uploaded = fileService.uploadFile(
                name,
                null,
                checksum(data),
                "text/plain",
                data.length,
                new ByteArrayInputStream(data)
        );

        return uploaded.getId();
    }

    private Path filePath(UUID id) {
        return tempDir.resolve(id.toString());
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private String checksum(byte[] content) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void assertNoTemporaryFiles(UUID id) throws IOException {
        try (var files = Files.list(tempDir)) {
            var leftovers = files
                    .filter(path -> path.getFileName().toString().startsWith(id + "-"))
                    .toList();

            Assertions.assertEquals(0, leftovers.size(),
                         "Temporary or backup files were not cleaned up: " + leftovers);
        }
    }
}