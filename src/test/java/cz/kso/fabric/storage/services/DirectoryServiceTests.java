package cz.kso.fabric.storage.services;

import cz.kso.fabric.storage.TestBase;
import cz.kso.fabric.storage.api.model.CreateDirectoryRequest;
import cz.kso.fabric.storage.api.model.DirectoryMetadata;
import cz.kso.fabric.storage.config.StorageProperties;
import cz.kso.fabric.storage.exceptions.BadRequestException;
import cz.kso.fabric.storage.exceptions.ConflictException;
import cz.kso.fabric.storage.exceptions.NotFoundException;
import cz.kso.fabric.storage.repositories.DirectoryRepository;
import cz.kso.fabric.storage.repositories.FileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class DirectoryServiceTests extends TestBase {

    @Autowired
    private DirectoryService directoryService;

    @Autowired
    private FileService fileService;

    @Autowired
    private DirectoryRepository directoryRepository;

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private StorageProperties storageProperties;

    private Path storageRoot;

    @BeforeEach
    void setUp() throws IOException {
        storageRoot = Files.createTempDirectory("fabric-directory-test-");
        storageProperties.setDiscRoot(storageRoot.toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        try (var paths = Files.walk(storageRoot)) {
            for (var path : paths.sorted(
                    Comparator.reverseOrder()
            ).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    // region Create

    @Test
    void createDirectory_InRoot_ShouldCreate() {
        var created = createDirectory("Root directory", null);

        Assertions.assertNotNull(created.getId());
        Assertions.assertEquals("Root directory", created.getName());
        Assertions.assertTrue(directoryRepository.existsById(created.getId()));
    }

    @Test
    void createDirectory_ChildDirectory_ShouldCreate() {
        var parent = createDirectory("Parent", null);
        var child = createDirectory("Child", parent.getId());

        var entity = directoryRepository.findById(child.getId())
                .orElseThrow();

        Assertions.assertEquals("Child", entity.getName());
        Assertions.assertEquals(parent.getId(), entity.getParent().getId());
    }

    @Test
    void createDirectory_ForUnknownParent_ShouldFail() {
        UUID unknownId = UUID.randomUUID();

        Assertions.assertThrows(
                NotFoundException.class,
                () -> createDirectory("Child", unknownId)
        );
    }

    // endregion

    // region Get

    @Test
    void getDirectory_ExistingDirectory_ShouldReturn() {
        var created = createDirectory("Documents", null);

        var result = directoryService.getDirectory(created.getId());

        Assertions.assertEquals(created.getId(), result.getId());
        Assertions.assertEquals("Documents", result.getName());
    }

    @Test
    void getDirectory_ForUnknownId_ShouldFail() {
        UUID unknownId = UUID.randomUUID();

        Assertions.assertThrows(
                NotFoundException.class,
                () -> directoryService.getDirectory(unknownId)
        );
    }

    // endregion

    // region Delete recursive

    @Test
    void deleteDirectory_EmptyDirectoryRecursive_ShouldDelete() throws IOException {
        var directory = createDirectory("Empty", null);

        directoryService.deleteDirectory(directory.getId(), true);

        Assertions.assertFalse(
                directoryRepository.existsById(directory.getId())
        );
    }

    @Test
    void deleteDirectory_ForUnknownIdRecursive_ShouldFail() {
        UUID unknownId = UUID.randomUUID();

        Assertions.assertThrows(
                NotFoundException.class,
                () -> directoryService.deleteDirectory(unknownId, true)
        );
    }

    @Test
    void deleteDirectory_RootDeletionRecursive_ShouldReject() {
        Assertions.assertThrows(
                BadRequestException.class,
                () -> directoryService.deleteDirectory(null, true)
        );
    }

    @Test
    void deleteDirectory_FilesFromDatabaseAndDiskRecursive_ShouldDelete() throws IOException {

        var directory = createDirectory("Files", null);

        UUID firstFile = uploadFile(directory.getId(), "first.txt");
        UUID secondFile = uploadFile(directory.getId(), "second.txt");

        directoryService.deleteDirectory(directory.getId(), true);

        assertFileDeleted(firstFile);
        assertFileDeleted(secondFile);

        Assertions.assertFalse(
                directoryRepository.existsById(directory.getId())
        );
    }

    @Test
    void deleteDirectory_NestedDirectoriesAndFilesRecursive_ShouldDelete() throws IOException {

        var root = createDirectory("Root", null);
        var child = createDirectory("Child", root.getId());
        var grandchild = createDirectory("Grandchild", child.getId());

        UUID rootFile = uploadFile(root.getId(), "root.txt");
        UUID childFile = uploadFile(child.getId(), "child.txt");
        UUID grandchildFile = uploadFile(
                grandchild.getId(),
                "grandchild.txt"
        );

        directoryService.deleteDirectory(root.getId(), true);

        for (UUID id : List.of(
                root.getId(),
                child.getId(),
                grandchild.getId()
        )) {
            Assertions.assertFalse(directoryRepository.existsById(id));
        }

        assertFileDeleted(rootFile);
        assertFileDeleted(childFile);
        assertFileDeleted(grandchildFile);
    }

    @Test
    void deleteDirectory_MoreThanOnePageOfFilesRecursive_ShouldDelete() throws IOException {

        var directory = createDirectory("Many files", null);

        List<UUID> fileIds = new ArrayList<>();

        for (int i = 0; i < 105; i++) {
            fileIds.add(uploadFile(
                    directory.getId(),
                    "file-" + i + ".txt"
            ));
        }

        directoryService.deleteDirectory(directory.getId(), true);

        Assertions.assertFalse(
                directoryRepository.existsById(directory.getId())
        );

        for (UUID fileId : fileIds) {
            assertFileDeleted(fileId);
        }
    }

    @Test
    void deleteDirectory_MoreThanOnePageOfSubdirectoriesRecursive_ShouldDelete() throws IOException {
        var parent = createDirectory("Parent", null);

        List<UUID> childIds = new ArrayList<>();

        for (int i = 0; i < 105; i++) {
            var child = createDirectory(
                    "Child-" + i,
                    parent.getId()
            );
            childIds.add(child.getId());
        }

        directoryService.deleteDirectory(parent.getId(), true);

        Assertions.assertFalse(
                directoryRepository.existsById(parent.getId())
        );

        for (UUID childId : childIds) {
            Assertions.assertFalse(
                    directoryRepository.existsById(childId)
            );
        }
    }

    @Test
    void deleteDirectory_SiblingDirectoriesRecursive_ShouldNotDelete() throws IOException {

        var parent = createDirectory("Parent", null);
        var first = createDirectory("First", parent.getId());
        var second = createDirectory("Second", parent.getId());

        UUID firstFile = uploadFile(first.getId(), "first.txt");
        UUID secondFile = uploadFile(second.getId(), "second.txt");

        directoryService.deleteDirectory(first.getId(), true);

        Assertions.assertFalse(directoryRepository.existsById(first.getId()));
        Assertions.assertTrue(directoryRepository.existsById(second.getId()));
        Assertions.assertTrue(directoryRepository.existsById(parent.getId()));

        assertFileDeleted(firstFile);

        Assertions.assertTrue(fileRepository.existsById(secondFile));
        Assertions.assertTrue(
                Files.exists(storageRoot.resolve(secondFile.toString()))
        );
    }

    // endregion

    //region Delete

    @Test
    void deleteDirectory_EmptyDirectory_ShouldDelete() throws IOException {
        var directory = createDirectory("Empty", null);

        directoryService.deleteDirectory(directory.getId(), false);

        Assertions.assertFalse(
                directoryRepository.existsById(directory.getId())
        );
    }

    @Test
    void deleteDirectory_DirectoryWithFile_ShouldReject() throws IOException {
        var fileName = "file.txt";
        var directory = createDirectory("notEmpty", null);
        var fileId = uploadFile(directory.getId(), fileName);

        Assertions.assertTrue(Files.exists(Path.of(storageProperties.getDiscRoot(), fileId.toString())));

        Assertions.assertThrows(
                ConflictException.class,
                () -> directoryService.deleteDirectory(directory.getId(), false)
        );

        Assertions.assertTrue(directoryRepository.existsById(directory.getId()));
        Assertions.assertTrue(fileRepository.existsByDirectory_IdAndName(directory.getId(), fileName));
        Assertions.assertTrue(Files.exists(Path.of(storageProperties.getDiscRoot(), fileId.toString())));
    }

    @Test
    void deleteDirectory_DirectoryWithDirectory_ShouldReject() {
        var directory = createDirectory("notEmpty", null);
        var dirIn = createDirectory("emptyIn", directory.getId());

        Assertions.assertThrows(
                ConflictException.class,
                () -> directoryService.deleteDirectory(directory.getId(), false)
        );

        Assertions.assertTrue(directoryRepository.existsById(directory.getId()));
        Assertions.assertTrue(directoryRepository.existsById(dirIn.getId()));
    }

    //endregion

    // region Helpers

    private DirectoryMetadata createDirectory(String name, UUID parentId) {
        var request = new CreateDirectoryRequest();
        request.setName(name);

        if (parentId != null) {
            request.setParentId(JsonNullable.of(parentId));
        }

        return directoryService.createDirectory(request);
    }

    private UUID uploadFile(UUID directoryId, String name) throws IOException {
        byte[] content = ("Content of " + name).getBytes(StandardCharsets.UTF_8);

        var uploaded = fileService.uploadFile(
                name,
                directoryId,
                null,
                "text/plain",
                content.length,
                new ByteArrayInputStream(content)
        );

        return uploaded.getId();
    }

    private void assertFileDeleted(UUID fileId) {
        Assertions.assertFalse(
                fileRepository.existsById(fileId),
                "File still exists in database: " + fileId
        );

        Assertions.assertFalse(
                Files.exists(storageRoot.resolve(fileId.toString())),
                "File still exists on disk: " + fileId
        );
    }

    // endregion
}
