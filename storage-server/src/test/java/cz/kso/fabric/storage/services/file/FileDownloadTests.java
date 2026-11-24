package cz.kso.fabric.storage.services.file;

import cz.kso.fabric.storage.TestBase;
import cz.kso.fabric.storage.api.model.FileMetadata;
import cz.kso.fabric.storage.config.StorageProperties;
import cz.kso.fabric.storage.exceptions.NotFoundException;
import cz.kso.fabric.storage.repositories.FileRepository;
import cz.kso.fabric.storage.services.FileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

public class FileDownloadTests extends TestBase {

    @Autowired
    private FileService fileService;

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private StorageProperties storageProperties;

    private Path storageRoot;

    @BeforeEach
    void setUp() throws IOException {
        storageRoot = Files.createTempDirectory("fabric-download-test-");
        storageProperties.setDiscRoot(storageRoot.toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        try (var paths = Files.walk(storageRoot)) {
            for (var path : paths.sorted(
                    java.util.Comparator.reverseOrder()
            ).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Test
    void prepareDownload_shouldReturnCorrectContent() throws IOException {
        byte[] content = "Ahoj :)".getBytes(StandardCharsets.UTF_8);

        var uploaded = upload("test.txt", "text/plain", content);

        var download = fileService.prepareDownload(uploaded.getId());

        Assertions.assertArrayEquals(
                content,
                download.resource().getInputStream().readAllBytes()
        );

        Assertions.assertEquals("test.txt", download.name());
        Assertions.assertEquals(content.length, download.size());
        Assertions.assertEquals(MediaType.TEXT_PLAIN, download.contentType());
    }

    @Test
    void prepareDownload_shouldReturnCorrectContentDigest() throws Exception {
        byte[] content = "Hello Fabric".getBytes(StandardCharsets.UTF_8);

        var uploaded = upload("checksum.txt", "text/plain", content);

        var download = fileService.prepareDownload(uploaded.getId());

        byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);

        String expected = "sha-256=:"
                + Base64.getEncoder().encodeToString(digest)
                + ":";

        Assertions.assertEquals(expected, download.contentDigest());
    }

    @Test
    void prepareDownload_shouldUseOctetStreamWhenContentTypeUnknown()
            throws IOException {

        byte[] content = {1, 2, 3, 4, 5};

        var uploaded = upload("binary.dat", null, content);

        var download = fileService.prepareDownload(uploaded.getId());

        Assertions.assertEquals(
                MediaType.APPLICATION_OCTET_STREAM,
                download.contentType()
        );
    }

    @Test
    void prepareDownload_shouldReturnNotFoundForUnknownId() {
        UUID id = UUID.randomUUID();

        Assertions.assertThrows(
                NotFoundException.class,
                () -> fileService.prepareDownload(id)
        );
    }

    @Test
    void prepareDownload_shouldFailWhenPhysicalFileMissing()
            throws IOException {

        byte[] content = "Missing file".getBytes(StandardCharsets.UTF_8);

        var uploaded = upload("missing.txt", "text/plain", content);

        Files.delete(storageRoot.resolve(uploaded.getId().toString()));

        Assertions.assertThrows(
                IllegalStateException.class,
                () -> fileService.prepareDownload(uploaded.getId())
        );

        Assertions.assertTrue(
                fileRepository.existsById(uploaded.getId())
        );
    }

    @Test
    void prepareDownload_shouldNotModifyStoredFile() throws IOException {
        byte[] content = "Original content".getBytes(StandardCharsets.UTF_8);

        var uploaded = upload("original.txt", "text/plain", content);

        var download = fileService.prepareDownload(uploaded.getId());

        try (var input = download.resource().getInputStream()) {
            Assertions.assertArrayEquals(content, input.readAllBytes());
        }

        Assertions.assertArrayEquals(
                content,
                Files.readAllBytes(
                        storageRoot.resolve(uploaded.getId().toString())
                )
        );
    }

    private FileMetadata upload(
            String name,
            String contentType,
            byte[] content
    ) throws IOException {
        return fileService.uploadFile(
                name,
                null,
                sha256(content),
                contentType,
                content.length,
                new ByteArrayInputStream(content)
        );
    }

    private String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content);

            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
