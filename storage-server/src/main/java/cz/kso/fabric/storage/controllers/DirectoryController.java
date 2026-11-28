package cz.kso.fabric.storage.controllers;

import cz.kso.fabric.storage.api.DirectoryApi;
import cz.kso.fabric.storage.api.model.*;
import cz.kso.fabric.storage.services.DirectoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

@RestController
public class DirectoryController implements DirectoryApi {

    private final DirectoryService directoryService;

    public DirectoryController(DirectoryService directoryService) {
        this.directoryService = directoryService;
    }

    @Override
    public ResponseEntity<ItemListDTO> listDirectory(@Nullable UUID directoryId,
                                                     @Nullable String name,
                                                     @Nullable Set<@Size(min = 1, max = 100) String> tags,
                                                     @Nullable Long minSize,
                                                     @Nullable Long maxSize,
                                                     @Nullable OffsetDateTime createdFrom,
                                                     @Nullable OffsetDateTime createdTo,
                                                     @Nullable OffsetDateTime updatedFrom,
                                                     @Nullable OffsetDateTime updatedTo,
                                                     ItemSort sort,
                                                     SortOrder order,
                                                     Integer limit,
                                                     Integer page) {
        return ResponseEntity.ok(directoryService.listDirectories(directoryId, limit, page));
    }

    @Override
    public ResponseEntity<ItemDTO> createDirectory(CreateDirectoryRequest createDirectoryRequest) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(directoryService.createDirectory(createDirectoryRequest));
    }

    @Override
    public ResponseEntity<ItemDTO> getDirectory(UUID id) {
        return ResponseEntity.ok(directoryService.getDirectory(id));
    }

    @Override
    public ResponseEntity<ItemDTO> updateDirectory(UUID id, UpdateDirectoryRequest updateFileRequest) {
        return ResponseEntity.ok(directoryService.updateDirectory(id, updateFileRequest));
    }

    @Override
    public ResponseEntity<Void> deleteDirectory(UUID id, Boolean recursive) {
        try {
            directoryService.deleteDirectory(id, recursive);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return ResponseEntity.noContent().build();
    }
}
