package cz.kso.fabric.storage.services;

import cz.kso.fabric.storage.api.model.CreateDirectoryRequest;
import cz.kso.fabric.storage.api.model.DirectoryMetadata;
import cz.kso.fabric.storage.api.model.UpdateItemRequest;
import cz.kso.fabric.storage.entities.DirectoryEntity;
import cz.kso.fabric.storage.entities.MetadataEntity;
import cz.kso.fabric.storage.exceptions.BadRequestException;
import cz.kso.fabric.storage.exceptions.ConflictException;
import cz.kso.fabric.storage.exceptions.NotFoundException;
import cz.kso.fabric.storage.mappers.DirectoryMapper;
import cz.kso.fabric.storage.repositories.DirectoryRepository;
import cz.kso.fabric.storage.repositories.FileRepository;
import cz.kso.fabric.storage.utils.MapperUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Service
public class DirectoryService {

    private final DirectoryRepository directoryRepository;
    private final FileRepository fileRepository;
    private final FileService fileService;
    private final MapperUtils mapperUtils;

    public DirectoryService(DirectoryRepository directoryRepository, FileRepository fileRepository, FileService fileService, MapperUtils mapperUtils) {
        this.directoryRepository = directoryRepository;
        this.fileRepository = fileRepository;
        this.fileService = fileService;
        this.mapperUtils = mapperUtils;
    }

    @Transactional
    public DirectoryMetadata createDirectory(CreateDirectoryRequest request) {
        var parentDirId = request.getParentId().orElse(null);
        log.info("Creating new directory in {}", parentDirId);

        // TODO authorization

        DirectoryEntity parentDir = null;
        if (parentDirId != null) {
            parentDir = directoryRepository.findById(parentDirId)
                    .orElseThrow(() -> new NotFoundException("Directory", parentDirId));
        }

        var dirEntity = new DirectoryEntity();
        dirEntity.setName(request.getName());
        dirEntity.setParent(parentDir);
        dirEntity.setMetadata(new MetadataEntity());

        dirEntity = directoryRepository.saveAndFlush(dirEntity);

        log.info("New directory created: {}", dirEntity.getId());
        return DirectoryMapper.toDto(dirEntity);
    }

    @Transactional(readOnly = true)
    public DirectoryMetadata getDirectory(UUID id) {
        log.info("Getting directory: {}", id);

        var dirEntity = directoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Directory", id));

        return DirectoryMapper.toDto(dirEntity);
    }

    @Transactional
    public DirectoryMetadata updateDirectory(UUID id, UpdateItemRequest request) {
        log.info("Updating directory: {}", id);
        // TODO authorization
        var dirEntity = directoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Directory", id));

        MapperUtils.applyIfPresent(request.getName(), dirEntity::setName);
        mapperUtils.updateDirectoryIfPresent(request.getDirectoryId(), dirEntity::setParent);
        mapperUtils.updateMetadataIfPresent(request, dirEntity);

        return DirectoryMapper.toDto(dirEntity);
    }

    public void deleteDirectory(UUID id, Boolean recursive) throws IOException {
        log.info("Deleting directory: {}", id);

        // TODO authorization
        // TODO locking
        if (id == null) {
            throw new BadRequestException("Cannot delete root directory");
        }

        if (!directoryRepository.existsById(id)) {
            throw new NotFoundException("Directory", id);
        }

        var hasItems = directoryRepository.existsByParent_Id(id) || fileRepository.existsByDirectory_Id(id);

        if (hasItems && !Boolean.TRUE.equals(recursive)) {
            throw new ConflictException("Directory %s is not empty and recursive is not allowed", id);
        }

        if (Boolean.TRUE.equals(recursive)) {
            deleteDirectoryFiles(id);
            deleteDirectoriesInParent(id);
        }

        directoryRepository.deleteById(id);
    }

    //region Delete directory

    private void deleteDirectoryFiles(UUID directoryId) throws IOException {
        var firstPage = PageRequest.of(0, 100);

        while (true) {
            var fileIds = fileRepository.findFileIdsByDirectory_Id(directoryId,
                                                                   firstPage);

            if (fileIds.isEmpty()) {
                break;
            }

            for (UUID fileId : fileIds) {
                fileService.deleteFile(fileId);
            }
        }
    }

    private void deleteDirectoriesInParent(UUID parentId) throws IOException {
        var firstPage = PageRequest.of(0, 100);

        while (true) {
            var dirIds = directoryRepository.findDirectoryIdsByParent_Id(parentId,
                                                                         firstPage);

            if (dirIds.isEmpty()) {
                break;
            }

            for (UUID dirId : dirIds) {
                deleteDirectory(dirId, true);
            }
        }
    }

    //endregion
}
