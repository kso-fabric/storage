package cz.kso.fabric.storage.services.file;

import cz.kso.fabric.storage.TestBase;
import cz.kso.fabric.storage.api.model.*;
import cz.kso.fabric.storage.entities.*;
import cz.kso.fabric.storage.entities.enums.ItemTypeDb;
import cz.kso.fabric.storage.exceptions.BadRequestException;
import cz.kso.fabric.storage.exceptions.NotFoundException;
import cz.kso.fabric.storage.repositories.ItemRepository;
import cz.kso.fabric.storage.repositories.TagRepository;
import cz.kso.fabric.storage.services.FileService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

class FileServiceTests extends TestBase {

    @Autowired
    private FileService fileService;
    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private TagRepository tagRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private TransactionTemplate transactionTemplate;

    //region listFiles

    @Test
    void listFiles_shouldReturnOnlyRootFiles() {
        createFile("root.txt", null);
        var directory = createDirectory("photos");
        createFile("photo.raw", directory);

        var response = fileService.listFiles(null, 10, 0);

        Assertions.assertEquals(1, response.getTotal());
        Assertions.assertEquals(1, response.getItems().size());
        Assertions.assertEquals("root.txt", response.getItems().getFirst().getName());
    }

    @Test
    void listFiles_shouldFilterByDirectory() {
        var first = createDirectory("first");
        var second = createDirectory("second");

        createFile("a.txt", first);
        createFile("b.txt", first);
        createFile("c.txt", second);
        createFile("root.txt", null);

        var response = fileService.listFiles(first.getId(), 10, 0);

        Assertions.assertEquals(2, response.getTotal());
        Assertions.assertEquals(2, response.getItems().size());
        Assertions.assertTrue(response.getItems().stream()
                                      .allMatch(file -> first.getId().equals(file.getParentId())));
    }

    @Test
    void listFiles_shouldReturnEmptyResult() {
        var response = fileService.listFiles(null, 10, 0);

        Assertions.assertEquals(0, response.getTotal());
        Assertions.assertTrue(response.getItems() == null
                                      || response.getItems().isEmpty());
    }

    @Test
    void listFiles_shouldApplyPagination() {
        createFile("a.txt", null);
        createFile("b.txt", null);
        createFile("c.txt", null);

        var firstPage = fileService.listFiles(null, 2, 0);
        var secondPage = fileService.listFiles(null, 2, 1);

        Assertions.assertEquals(3, firstPage.getTotal());
        Assertions.assertEquals(3, secondPage.getTotal());
        Assertions.assertEquals(2, firstPage.getItems().size());
        Assertions.assertEquals(1, secondPage.getItems().size());

        var firstIds = firstPage.getItems().stream()
                .map(ItemDTO::getId)
                .toList();

        var secondId = secondPage.getItems().getFirst().getId();

        Assertions.assertFalse(firstIds.contains(secondId));
    }

    //endregion

    //region updateFiles

    @Test
    void updateFile_shouldRenameFile() {
        var file = createFile("old.txt", null);

        var request = new UpdateFileRequest();
        request.setName(JsonNullable.of("new.txt"));

        var result = fileService.updateFile(file.getId(), request);

        Assertions.assertEquals("new.txt", result.getName());

        var persisted = reloadFile(file.getId());
        Assertions.assertEquals("new.txt", persisted.getName());
    }

    @Test
    void updateFile_shouldMoveFileToDirectory() {
        var file = createFile("photo.raw", null);
        var directory = createDirectory("photos");

        var request = new UpdateFileRequest();
        request.setParentId(JsonNullable.of(directory.getId()));

        var result = fileService.updateFile(file.getId(), request);

        Assertions.assertEquals(directory.getId(), result.getParentId());
        Assertions.assertEquals(directory.getId(),
                                reloadFile(file.getId()).getParentId());
    }

    @Test
    void updateFile_shouldMoveFileToRoot() {
        var directory = createDirectory("photos");
        var file = createFile("photo.raw", directory);

        var request = new UpdateFileRequest();
        request.setParentId(JsonNullable.of(null));

        var result = fileService.updateFile(file.getId(), request);

        Assertions.assertNull(result.getParentId());
        Assertions.assertNull(reloadFile(file.getId()).getParentId());
    }

    @Test
    void updateFile_shouldPreserveOmittedFields() {
        var directory = createDirectory("photos");
        var file = createFile("original.raw", directory);

        var request = new UpdateFileRequest();
        request.setName(JsonNullable.of("renamed.raw"));

        fileService.updateFile(file.getId(), request);

        var persisted = reloadFile(file.getId());

        Assertions.assertEquals("renamed.raw", persisted.getName());
        Assertions.assertEquals(directory.getId(), persisted.getParentId());
        Assertions.assertEquals(1024L, persisted.getSize());
        Assertions.assertEquals("image/x-canon-cr2", persisted.getContentType());
        Assertions.assertEquals("a".repeat(64), persisted.getChecksum());
    }

    @Test
    void updateFile_shouldThrowWhenFileDoesNotExist() {
        var request = new UpdateFileRequest();
        request.setName(JsonNullable.of("new.txt"));

        Assertions.assertThrows(NotFoundException.class,
                                () -> fileService.updateFile(UUID.randomUUID(), request));
    }

    @Test
    void updateFile_shouldThrowWhenTargetDirectoryDoesNotExist() {
        var file = createFile("test.txt", null);

        var request = new UpdateFileRequest();
        request.setParentId(JsonNullable.of(UUID.randomUUID()));

        Assertions.assertThrows(NotFoundException.class,
                                () -> fileService.updateFile(file.getId(), request));

        Assertions.assertNull(reloadFile(file.getId()).getParentId());
    }

    @Test
    void updateFile_shouldUpdateDescription() {
        var file = createFile("test.txt", null);

        var metadata = new UpdateMetadataRequest();
        metadata.setDescription(JsonNullable.of("New description"));

        var request = new UpdateFileRequest();
        request.setAdditionalData(JsonNullable.of(metadata));

        var result = fileService.updateFile(file.getId(), request);

        assert result.getAdditionalData() != null;
        Assertions.assertEquals("New description",
                                result.getAdditionalData().getDescription());

        Assertions.assertEquals(
                "New description",
                loadMetadata(file.getId()).getDescription()
        );
    }

    @Test
    void updateFile_shouldClearDescription() {
        var file = createFile("test.txt", null);

        updateDescription(file.getId(), "Original description");

        var metadata = new UpdateMetadataRequest();
        metadata.setDescription(JsonNullable.of(null));

        var request = new UpdateFileRequest();
        request.setAdditionalData(JsonNullable.of(metadata));

        fileService.updateFile(file.getId(), request);

        Assertions.assertNull(loadMetadata(file.getId()).getDescription());
    }

    @Test
    void updateFile_shouldUpdateCustomMetadata() {
        var file = createFile("test.txt", null);

        var custom = Map.<String, Object>of(
                "camera", "Canon 40D",
                "iso", 1600,
                "exposure", 15
        );

        var metadata = new UpdateMetadataRequest();
        metadata.setCustom(JsonNullable.of(custom));

        var request = new UpdateFileRequest();
        request.setAdditionalData(JsonNullable.of(metadata));

        fileService.updateFile(file.getId(), request);

        Assertions.assertEquals(custom,
                                loadMetadata(file.getId()).getCustom());
    }

    @Test
    void updateFile_shouldPreserveOmittedMetadataFields() {
        var file = createFile("test.txt", null);

        updateDescription(file.getId(), "Original description");

        var metadata = new UpdateMetadataRequest();
        metadata.setCustom(JsonNullable.of(Map.of("iso", 1600)));

        var request = new UpdateFileRequest();
        request.setAdditionalData(JsonNullable.of(metadata));

        fileService.updateFile(file.getId(), request);

        var persisted = loadMetadata(file.getId());

        Assertions.assertEquals("Original description",
                                persisted.getDescription());
        Assertions.assertEquals(Map.of("iso", 1600),
                                persisted.getCustom());
    }

    @Test
    void updateFile_shouldRejectNullAdditionalData() {
        var file = createFile("test.txt", null);

        var request = new UpdateFileRequest();
        request.setAdditionalData(JsonNullable.of(null));

        Assertions.assertThrows(BadRequestException.class,
                                () -> fileService.updateFile(file.getId(), request));
    }

    @Test
    void updateFile_shouldAcceptEmptyAdditionalData() {
        var file = createFile("test.txt", null);

        updateDescription(file.getId(), "Original");

        var request = new UpdateFileRequest();
        request.setAdditionalData(
                JsonNullable.of(new UpdateMetadataRequest())
        );

        Assertions.assertDoesNotThrow(
                () -> fileService.updateFile(file.getId(), request)
        );

        Assertions.assertEquals(
                "Original",
                loadMetadata(file.getId()).getDescription()
        );
    }

    @Test
    void updateFile_shouldAddTag() {
        var file = createFile("test.txt", null);

        updateTags(file.getId(), tag("astronomy", UpdateTagItem.ActionEnum.ADD));

        Assertions.assertEquals(Set.of("astronomy"),
                                loadTagValues(file.getId()));

        Assertions.assertEquals(1, tagRepository.count());
    }

    @Test
    void updateFile_shouldReuseExistingTag() {
        var first = createFile("first.txt", null);
        var second = createFile("second.txt", null);

        updateTags(first.getId(), tag("astronomy", UpdateTagItem.ActionEnum.ADD));
        updateTags(second.getId(), tag("astronomy", UpdateTagItem.ActionEnum.ADD));

        Assertions.assertEquals(1, tagRepository.count());
        Assertions.assertEquals(Set.of("astronomy"), loadTagValues(first.getId()));
        Assertions.assertEquals(Set.of("astronomy"), loadTagValues(second.getId()));
    }

    @Test
    void updateFile_shouldDeleteTag() {
        var file = createFile("test.txt", null);

        updateTags(file.getId(), tag("astronomy", UpdateTagItem.ActionEnum.ADD));
        updateTags(file.getId(), tag("astronomy", UpdateTagItem.ActionEnum.DELETE));

        Assertions.assertTrue(loadTagValues(file.getId()).isEmpty());

        // Globální tag nesmí být smazán.
        Assertions.assertEquals(1, tagRepository.count());
    }

    @Test
    void updateFile_shouldNotDuplicateAlreadyAssignedTag() {
        var file = createFile("test.txt", null);

        updateTags(file.getId(), tag("astronomy", UpdateTagItem.ActionEnum.ADD));
        updateTags(file.getId(), tag("astronomy", UpdateTagItem.ActionEnum.ADD));

        Assertions.assertEquals(Set.of("astronomy"),
                                loadTagValues(file.getId()));

        Assertions.assertEquals(1, tagRepository.count());
    }

    @Test
    void updateFile_shouldIgnoreDeletingNonexistentTag() {
        var file = createFile("test.txt", null);

        updateTags(file.getId(), tag("missing", UpdateTagItem.ActionEnum.DELETE));

        Assertions.assertTrue(loadTagValues(file.getId()).isEmpty());
        Assertions.assertEquals(0, tagRepository.count());
    }

    @Test
    void updateFile_shouldApplyMultipleTagChanges() {
        var file = createFile("test.txt", null);

        updateTags(file.getId(),
                   tag("old", UpdateTagItem.ActionEnum.ADD),
                   tag("keep", UpdateTagItem.ActionEnum.ADD));

        updateTags(file.getId(),
                   tag("old", UpdateTagItem.ActionEnum.DELETE),
                   tag("new", UpdateTagItem.ActionEnum.ADD));

        Assertions.assertEquals(Set.of("keep", "new"),
                                loadTagValues(file.getId()));

        Assertions.assertEquals(3, tagRepository.count());
    }

    @Test
    void updateFile_shouldRejectDuplicateTagChanges() {
        var file = createFile("test.txt", null);

        Assertions.assertThrows(
                BadRequestException.class,
                () -> updateTags(file.getId(),
                                 tag("duplicate", UpdateTagItem.ActionEnum.ADD),
                                 tag("duplicate", UpdateTagItem.ActionEnum.DELETE))
        );

        Assertions.assertTrue(loadTagValues(file.getId()).isEmpty());
    }

    @Test
    void updateFile_shouldRejectInvalidTags() {
        var file = createFile("test.txt", null);

        var invalidTags = List.of(
                tag(null, UpdateTagItem.ActionEnum.ADD),
                tag("", UpdateTagItem.ActionEnum.ADD),
                tag("   ", UpdateTagItem.ActionEnum.ADD),
                tag("a".repeat(101), UpdateTagItem.ActionEnum.ADD),
                tag("test", null)
        );

        for (var invalidTag : invalidTags) {
            Assertions.assertThrows(BadRequestException.class,
                                    () -> updateTags(file.getId(), invalidTag));
        }

        Assertions.assertEquals(0, tagRepository.count());
    }

    //end region

    //region Private methods

    private ItemEntity createDirectory(String name) {
        return transactionTemplate.execute(_ -> {
            var directory = new ItemEntity();
            directory.setId(UUID.randomUUID());
            directory.setName(name);
            directory.setType(ItemTypeDb.DIRECTORY);
            directory.setMetadata(new MetadataEntity());

            return itemRepository.saveAndFlush(directory);
        });
    }

    private ItemEntity createFile(String name, ItemEntity directory) {
        return transactionTemplate.execute(_ -> {
            var file = new ItemEntity();
            file.setId(UUID.randomUUID());
            file.setName(name);
            file.setType(ItemTypeDb.FILE);
            file.setParentId(directory == null ? null : directory.getId());
            file.setSize(1024L);
            file.setContentType("image/x-canon-cr2");
            file.setChecksum("a".repeat(64));
            file.setMetadata(new MetadataEntity());

            entityManager.persist(file);
            entityManager.flush();

            return file;
        });
    }

    private ItemEntity reloadFile(UUID id) {
        return transactionTemplate.execute(_ -> {
            entityManager.clear();
            return itemRepository.findFileById(id).orElseThrow();
        });
    }

    private MetadataEntity loadMetadata(UUID fileId) {
        return transactionTemplate.execute(_ -> {
            entityManager.clear();

            var file = itemRepository.findFileById(fileId).orElseThrow();
            var metadata = file.getMetadata();

            // Inicializace lazy kolekce.
            metadata.getTags().size();

            return metadata;
        });
    }

    private Set<String> loadTagValues(UUID fileId) {
        return transactionTemplate.execute(_ -> {
            entityManager.clear();

            return itemRepository.findFileById(fileId)
                    .orElseThrow()
                    .getMetadata()
                    .getTags()
                    .stream()
                    .map(TagEntity::getValue)
                    .collect(java.util.stream.Collectors.toSet());
        });
    }

    private void updateDescription(UUID fileId, String description) {
        var metadata = new UpdateMetadataRequest();
        metadata.setDescription(JsonNullable.of(description));

        var request = new UpdateFileRequest();
        request.setAdditionalData(JsonNullable.of(metadata));

        fileService.updateFile(fileId, request);
    }

    private UpdateTagItem tag(String value, UpdateTagItem.ActionEnum action) {
        var item = new UpdateTagItem();
        item.setValue(value);
        item.setAction(action);
        return item;
    }

    private void updateTags(UUID fileId, UpdateTagItem... tags) {
        var metadata = new UpdateMetadataRequest();
        metadata.setTags(List.of(tags));

        var request = new UpdateFileRequest();
        request.setAdditionalData(JsonNullable.of(metadata));

        fileService.updateFile(fileId, request);
    }

    //endregion
}
