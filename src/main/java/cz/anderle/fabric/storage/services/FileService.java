package cz.anderle.fabric.storage.services;

import cz.anderle.fabric.storage.api.model.FileListResponse;
import cz.anderle.fabric.storage.api.model.FileMetadata;
import cz.anderle.fabric.storage.api.model.UpdateItemRequest;
import cz.anderle.fabric.storage.config.StorageProperties;
import cz.anderle.fabric.storage.entities.FileEntity;
import cz.anderle.fabric.storage.exceptions.NotFoundException;
import cz.anderle.fabric.storage.mappers.FileMapper;
import cz.anderle.fabric.storage.repositories.FileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
public class FileService {

    private final StorageProperties storageProperties;
    private final FileRepository fileRepository;

    FileService(StorageProperties storageProperties, FileRepository fileRepository) {
        this.storageProperties = storageProperties;
        this.fileRepository = fileRepository;
    }

    @Transactional(readOnly = true)
    public FileListResponse listFiles(UUID directoryId, Integer limit, Integer offset) {
        log.info("Listing files with limit: {}, offset: {}, directoryId: {}", limit, offset, directoryId);

        Page<FileEntity> entities;
        if (directoryId == null) {
            entities = fileRepository.findByDirectoryIsNull(
                    Pageable.ofSize(limit)
                            .withPage(offset / limit));
        } else {
            entities = fileRepository.findByDirectory_Id(
                    directoryId,
                    Pageable.ofSize(limit)
                            .withPage(offset / limit));
        }

        var resp = new FileListResponse();
        resp.setTotal(entities.getTotalElements());
        for (var entity : entities) {
            resp.addFilesItem(FileMapper.toDto(entity));
        }

        return resp;
    }

    public FileMetadata updateFile(UUID id, UpdateItemRequest updateFileRequest) {
        log.info("Updating file: {}", id);

        var entity = fileRepository.findById(id);
        if (entity.isEmpty()) {
            log.error("File {} not found", id);
            throw new NotFoundException("File %s not found", id);
        }

        return new FileMetadata();
    }

}
