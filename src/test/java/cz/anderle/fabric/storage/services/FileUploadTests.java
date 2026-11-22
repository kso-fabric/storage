package cz.anderle.fabric.storage.services;

import cz.anderle.fabric.storage.TestBase;
import cz.anderle.fabric.storage.entities.DirectoryEntity;
import cz.anderle.fabric.storage.entities.MetadataEntity;
import cz.anderle.fabric.storage.exceptions.NotFoundException;
import cz.anderle.fabric.storage.repositories.DirectoryRepository;
import cz.anderle.fabric.storage.repositories.FileRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.UUID;

public class FileUploadTests extends TestBase {

    private static final Path STORAGE_ROOT = createStorageRoot();

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "cz.anderle.fabric.storage.disc-root",
                () -> STORAGE_ROOT.toString()
        );
    }

    @Autowired
    private FileService fileService;

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private DirectoryRepository directoryRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void uploadFile_shouldSaveFileToRoot() throws IOException {
        byte[] content = "Ahoj :)".getBytes(StandardCharsets.UTF_8);

        var response = fileService.uploadFile(
                "test.txt",
                null,
                null,
                null,
                -1,
                new ByteArrayInputStream(content)
        );

        Assertions.assertNotNull(response.getId());
        Assertions.assertEquals("test.txt", response.getName());
        Assertions.assertEquals((long) content.length, response.getSize());
        Assertions.assertEquals(sha256(content), response.getChecksum());

        var entity = fileRepository.findById(response.getId()).orElseThrow();

        Assertions.assertNull(entity.getDirectory());
        Assertions.assertNotNull(entity.getMetadata());

        var path = filePath(response.getId());

        Assertions.assertTrue(Files.exists(path));
        Assertions.assertArrayEquals(content, Files.readAllBytes(path));
    }

    @Test
    void uploadFile_shouldSaveFileToDirectory() throws IOException {
        var directory = createDirectory("Astrophotography");
        byte[] content = "M42".getBytes(StandardCharsets.UTF_8);

        var response = fileService.uploadFile(
                "M42.txt",
                directory.getId(),
                null,
                null,
                -1,
                new ByteArrayInputStream(content)
        );

        var entity = fileRepository.findById(response.getId()).orElseThrow();

        Assertions.assertNotNull(entity.getDirectory());
        Assertions.assertEquals(
                directory.getId(),
                entity.getDirectory().getId()
        );

        Assertions.assertArrayEquals(
                content,
                Files.readAllBytes(filePath(response.getId()))
        );
    }

    @Test
    void uploadFile_shouldAcceptMatchingChecksum() throws IOException {
        byte[] content = "Hello Fabric".getBytes(StandardCharsets.UTF_8);
        String checksum = sha256(content);

        var response = fileService.uploadFile(
                "checksum.txt",
                null,
                checksum.toUpperCase(),
                null,
                -1,
                new ByteArrayInputStream(content)
        );

        Assertions.assertEquals(checksum, response.getChecksum());
        Assertions.assertTrue(Files.exists(filePath(response.getId())));
    }

    @Test
    void uploadFile_shouldRejectChecksumMismatch() {
        byte[] content = "Invalid checksum".getBytes(StandardCharsets.UTF_8);
        String wrongChecksum = "0".repeat(64);

        long countBefore = fileRepository.count();
        long filesBefore = countStoredFiles();

        // Nahraď RuntimeException konkrétní ChecksumMismatchException.
        Assertions.assertThrows(RuntimeException.class, () ->
                fileService.uploadFile(
                        "invalid.txt",
                        null,
                        wrongChecksum,
                        null,
                        -1,
                        new ByteArrayInputStream(content)
                )
        );

        Assertions.assertEquals(countBefore, fileRepository.count());
        Assertions.assertEquals(filesBefore, countStoredFiles());
    }

    @Test
    void uploadFile_shouldRejectNonexistentDirectory() {
        byte[] content = "Test".getBytes(StandardCharsets.UTF_8);

        long countBefore = fileRepository.count();
        long filesBefore = countStoredFiles();

        Assertions.assertThrows(NotFoundException.class, () ->
                fileService.uploadFile(
                        "test.txt",
                        UUID.randomUUID(),
                        null,
                        null,
                        -1,
                        new ByteArrayInputStream(content)
                )
        );

        Assertions.assertEquals(countBefore, fileRepository.count());
        Assertions.assertEquals(filesBefore, countStoredFiles());
    }

    @Test
    void uploadFile_shouldRejectDuplicateNameInSameDirectory()
            throws IOException {

        byte[] content = "First file".getBytes(StandardCharsets.UTF_8);

        var first = fileService.uploadFile(
                "duplicate.txt",
                null,
                null,
                null,
                -1,
                new ByteArrayInputStream(content)
        );

        long countBefore = fileRepository.count();
        long filesBefore = countStoredFiles();

        // Nahraď RuntimeException konkrétní ConflictException.
        Assertions.assertThrows(RuntimeException.class, () ->
                fileService.uploadFile(
                        "duplicate.txt",
                        null,
                        null,
                        null,
                        -1,
                        new ByteArrayInputStream("Second file".getBytes())
                )
        );

        Assertions.assertEquals(countBefore, fileRepository.count());
        Assertions.assertEquals(filesBefore, countStoredFiles());

        Assertions.assertArrayEquals(
                content,
                Files.readAllBytes(filePath(first.getId()))
        );
    }

    private DirectoryEntity createDirectory(String name) {
        return transactionTemplate.execute(status -> {
            var directory = new DirectoryEntity();
            directory.setName(name);
            directory.setMetadata(new MetadataEntity());

            entityManager.persist(directory);
            entityManager.flush();

            return directory;
        });
    }

    private Path filePath(UUID id) {
        return STORAGE_ROOT.resolve(id.toString());
    }

    private static String sha256(byte[] content) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static long countStoredFiles() {
        try (var files = Files.list(STORAGE_ROOT)) {
            return files.count();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("fabric-storage-test-");
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @AfterAll
    static void cleanupStorage() throws IOException {
        try (var paths = Files.walk(STORAGE_ROOT)) {
            for (var path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
