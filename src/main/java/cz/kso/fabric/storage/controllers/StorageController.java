package cz.kso.fabric.storage.controllers;

import cz.kso.fabric.storage.api.StorageApi;
import cz.kso.fabric.storage.api.model.StorageInfo;
import cz.kso.fabric.storage.api.model.StorageIntegrity;
import cz.kso.fabric.storage.services.StorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.UncheckedIOException;

@RestController
public class StorageController implements StorageApi {

    private final StorageService storageService;

    public StorageController(StorageService storageService) {
        this.storageService = storageService;
    }

    @Override
    public ResponseEntity<StorageInfo> getStorageInfo() {
        try {
            return ResponseEntity.ok(storageService.getStorageInfo());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public ResponseEntity<StorageIntegrity> getStorageIntegrity() {
        try {
            return ResponseEntity.ok(storageService.getStorageIntegrity());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
