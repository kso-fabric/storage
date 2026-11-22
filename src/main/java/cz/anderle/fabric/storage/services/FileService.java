package cz.anderle.fabric.storage.services;

import cz.anderle.fabric.storage.api.model.FileListResponse;
import cz.anderle.fabric.storage.api.model.FileMetadata;
import cz.anderle.fabric.storage.api.model.UpdateItemRequest;
import cz.anderle.fabric.storage.api.model.UpdateTagItem;
import cz.anderle.fabric.storage.config.StorageProperties;
import cz.anderle.fabric.storage.entities.DirectoryEntity;
import cz.anderle.fabric.storage.entities.FileEntity;
import cz.anderle.fabric.storage.entities.MetadataEntity;
import cz.anderle.fabric.storage.entities.TagEntity;
import cz.anderle.fabric.storage.exceptions.BadRequestException;
import cz.anderle.fabric.storage.exceptions.NotFoundException;
import cz.anderle.fabric.storage.mappers.FileMapper;
import cz.anderle.fabric.storage.repositories.DirectoryRepository;
import cz.anderle.fabric.storage.repositories.FileRepository;
import cz.anderle.fabric.storage.repositories.TagRepository;
import cz.anderle.fabric.storage.utils.MapperUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class FileService {

    private final StorageProperties storageProperties;
    private final FileRepository fileRepository;
    private final DirectoryRepository directoryRepository;
    private final TagRepository tagRepository;

    FileService(StorageProperties storageProperties, FileRepository fileRepository, DirectoryRepository directoryRepository, TagRepository tagRepository) {
        this.storageProperties = storageProperties;
        this.fileRepository = fileRepository;
        this.directoryRepository = directoryRepository;
        this.tagRepository = tagRepository;
    }

    @Transactional(readOnly = true)
    public FileListResponse listFiles(UUID directoryId, Integer limit, Integer offset) {
        log.info("Listing files with limit: {}, offset: {}, directoryId: {}", limit, offset, directoryId);

        var pageable = PageRequest.of(
                offset / limit,
                limit,
                Sort.by(Sort.Order.asc("name"),
                        Sort.Order.asc("id"))
        );

        Page<FileEntity> entities;
        if (directoryId == null) {
            entities = fileRepository.findByDirectoryIsNull(pageable);
        } else {
            entities = fileRepository.findByDirectory_Id(directoryId, pageable);
        }

        var resp = new FileListResponse();
        resp.setTotal(entities.getTotalElements());
        for (var entity : entities) {
            resp.addFilesItem(FileMapper.toDto(entity));
        }

        return resp;
    }

    @Transactional
    public FileMetadata updateFile(UUID id, UpdateItemRequest updateFileRequest) {
        log.info("Updating file: {}", id);

        var entOpt = fileRepository.findById(id);
        if (entOpt.isEmpty()) {
            log.error("File {} not found", id);
            throw new NotFoundException("File", id);
        }
        var entity = entOpt.get();

        MapperUtils.applyIfPresent(updateFileRequest.getName(), entity::setName);

        if (updateFileRequest.getDirectoryId().isPresent()) {
            var dirId = updateFileRequest.getDirectoryId().get();
            DirectoryEntity dirEntity = null;
            if (dirId != null) {
                dirEntity = directoryRepository.findById(dirId)
                        .orElseThrow(() -> new NotFoundException("Directory", dirId));
            }
            entity.setDirectory(dirEntity);
        }

        updateMetadata(updateFileRequest, entity);

        return FileMapper.toDto(entity);
    }

    public FileMetadata uploadFile(String fileName, UUID directoryId, String sha256) {
        return new FileMetadata();
    }

    //region Update file

    private void updateMetadata(UpdateItemRequest updateFileRequest, FileEntity entity) {
        if (updateFileRequest.getAdditionalData().isPresent()) {
            entity.setUpdatedAt(Instant.now());

            var updateMetadataRequest = updateFileRequest.getAdditionalData().get();
            if (updateMetadataRequest == null) {
                log.error("Metadata update object cannot be null");
                throw new BadRequestException();
            }

            var metadataEntity = entity.getMetadata();
            MapperUtils.applyIfPresent(updateMetadataRequest.getDescription(), metadataEntity::setDescription);
            MapperUtils.applyIfPresent(updateMetadataRequest.getCustom(), metadataEntity::setCustom);

            if (updateMetadataRequest.getTags() != null) {
                validateUpdateTags(updateMetadataRequest.getTags());

                for (var updateTag : updateMetadataRequest.getTags()) {
                    var tagEntity = findTag(updateTag, metadataEntity);

                    if (UpdateTagItem.ActionEnum.ADD.equals(updateTag.getAction())
                            && tagEntity == null) {
                        addTag(updateTag, metadataEntity);
                    }

                    if (UpdateTagItem.ActionEnum.DELETE.equals(updateTag.getAction())
                            && tagEntity != null) {
                        deleteTag(tagEntity, metadataEntity);
                    }
                }
            }
        }
    }

    private TagEntity findTag(UpdateTagItem updateTag, MetadataEntity metadataEntity) {
        var matchColl = metadataEntity.getTags().stream()
                .filter(x -> x.getValue().equals(updateTag.getValue()))
                .toList();

        if (matchColl.size() > 1) {
            log.error("Tag collection for metadata={} has duplication '{}'",
                      metadataEntity.getId(),
                      updateTag.getValue());
            throw new IllegalStateException("Tag collection for metadata has duplication");
        }

        return matchColl.isEmpty() ? null : matchColl.getFirst();
    }

    private void addTag(UpdateTagItem updateTag, MetadataEntity metadataEntity) {
        var tagOpt = tagRepository.findByValue(updateTag.getValue());
        TagEntity tagEntity;
        if (tagOpt.isEmpty()) {
            log.info("Adding new tag of value {}", updateTag.getValue());
            tagEntity = new TagEntity();
            tagEntity.setValue(updateTag.getValue());
            tagEntity = tagRepository.save(tagEntity);
        } else {
            tagEntity = tagOpt.get();
        }
        log.info("Adding metadata {} tag {} of value {}",
                 metadataEntity.getId(),
                 tagEntity.getId(),
                 tagEntity.getValue());
        metadataEntity.getTags().add(tagEntity);
    }

    private void deleteTag(TagEntity tagEntity, MetadataEntity metadataEntity) {
        log.info("Deleting metadata {} tag {} of value {}",
                 metadataEntity.getId(),
                 tagEntity.getId(),
                 tagEntity.getValue());
        metadataEntity.getTags().remove(tagEntity);
    }

    private void validateUpdateTags(List<UpdateTagItem> tags) {
        Set<String> values = new HashSet<>();
        for (var tag : tags) {
            if (tag == null
                    || tag.getValue() == null
                    || tag.getValue().isBlank()
                    || tag.getValue().length() > 100
                    || tag.getAction() == null) {
                log.error("Invalid null tag");
                throw new BadRequestException();
            }

            if (!values.add(tag.getValue())) {
                log.error("Tag change duplication");
                throw new BadRequestException();
            }
        }
    }

    //endregion
}
