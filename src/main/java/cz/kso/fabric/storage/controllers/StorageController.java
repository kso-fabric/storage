package cz.kso.fabric.storage.controllers;

import cz.kso.fabric.storage.api.StorageApi;
import cz.kso.fabric.storage.api.model.StorageInfo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/storage")
public class StorageController implements StorageApi {

    @Override
    public ResponseEntity<StorageInfo> getStorageInfo() {
        return StorageApi.super.getStorageInfo();
    }
}
