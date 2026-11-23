package cz.kso.fabric.storage.controllers;

import cz.kso.fabric.storage.api.DirectoryApi;
import cz.kso.fabric.storage.api.model.CreateDirectoryRequest;
import cz.kso.fabric.storage.api.model.DirectoryMetadata;
import cz.kso.fabric.storage.api.model.UpdateItemRequest;
import cz.kso.fabric.storage.services.DirectoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class DirectoryController implements DirectoryApi {

    private final DirectoryService directoryService;

    public DirectoryController(DirectoryService directoryService) {
        this.directoryService = directoryService;
    }

    @Override
    public ResponseEntity<DirectoryMetadata> createDirectory(CreateDirectoryRequest createDirectoryRequest) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(directoryService.createDirectory(createDirectoryRequest));
    }

    @Override
    public ResponseEntity<DirectoryMetadata> getDirectory(UUID id) {
        return ResponseEntity.ok(directoryService.getDirectory(id));
    }

    @Override
    public ResponseEntity<DirectoryMetadata> updateDirectory(UUID id, UpdateItemRequest updateFileRequest) {
        return ResponseEntity.ok(directoryService.updateDirectory(id, updateFileRequest));
    }

    @Override
    public ResponseEntity<Void> deleteDirectory(UUID id) {
        directoryService.deleteDirectory(id);
        return ResponseEntity.noContent().build();
    }
}
