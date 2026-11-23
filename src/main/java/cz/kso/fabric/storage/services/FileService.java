package cz.kso.fabric.storage.services;

import cz.kso.fabric.storage.api.model.FileListResponse;
import cz.kso.fabric.storage.api.model.FileMetadata;
import cz.kso.fabric.storage.api.model.UpdateItemRequest;
import cz.kso.fabric.storage.api.model.UpdateTagItem;
import cz.kso.fabric.storage.config.StorageProperties;
import cz.kso.fabric.storage.entities.DirectoryEntity;
import cz.kso.fabric.storage.entities.FileEntity;
import cz.kso.fabric.storage.entities.MetadataEntity;
import cz.kso.fabric.storage.entities.TagEntity;
import cz.kso.fabric.storage.exceptions.BadRequestException;
import cz.kso.fabric.storage.exceptions.ChecksumMismatchException;
import cz.kso.fabric.storage.exceptions.ConflictException;
import cz.kso.fabric.storage.exceptions.NotFoundException;
import cz.kso.fabric.storage.mappers.FileMapper;
import cz.kso.fabric.storage.repositories.DirectoryRepository;
import cz.kso.fabric.storage.repositories.FileRepository;
import cz.kso.fabric.storage.repositories.TagRepository;
import cz.kso.fabric.storage.utils.MapperUtils;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
public class FileService {

    private final StorageProperties storageProperties;
    private final FileRepository fileRepository;
    private final DirectoryRepository directoryRepository;
    private final TagRepository tagRepository;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;

    FileService(StorageProperties storageProperties, FileRepository fileRepository, DirectoryRepository directoryRepository, TagRepository tagRepository, EntityManager entityManager, TransactionTemplate transactionTemplate) {
        this.storageProperties = storageProperties;
        this.fileRepository = fileRepository;
        this.directoryRepository = directoryRepository;
        this.tagRepository = tagRepository;
        this.entityManager = entityManager;
        this.transactionTemplate = transactionTemplate;
    }

    @Transactional(readOnly = true)
    public FileListResponse listFiles(UUID directoryId, Integer limit, Integer offset) {
        log.info("Listing files with limit: {}, offset: {}, directoryId: {}", limit, offset, directoryId);
        // TODO authorization
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

    public FileMetadata getFile(UUID id) {
        log.info("Getting file: {}", id);
        // TODO authorization
        var fileEntity = fileRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("File", id));

        return FileMapper.toDto(fileEntity);
    }

    @Transactional
    public FileMetadata updateFile(UUID id, UpdateItemRequest request) {
        log.info("Updating file: {}", id);
        // TODO authorization
        var fileEntity = fileRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("File", id));

        MapperUtils.applyIfPresent(request.getName(), fileEntity::setName);

        if (request.getDirectoryId().isPresent()) {
            var dirId = request.getDirectoryId().get();
            DirectoryEntity dirEntity = null;
            if (dirId != null) {
                dirEntity = directoryRepository.findById(dirId)
                        .orElseThrow(() -> new NotFoundException("Directory", dirId));
            }
            fileEntity.setDirectory(dirEntity);
        }

        updateMetadata(request, fileEntity);

        return FileMapper.toDto(fileEntity);
    }

    public FileMetadata uploadFile(String fileName,
                                   UUID directoryId,
                                   String sha256,
                                   String contentType,
                                   long contentLength,
                                   InputStream input) throws IOException {
        log.info("Uploading new file {} in directory {}", fileName, directoryId);
        // TODO authorization
        DirectoryEntity dirEntity = null;
        if (directoryId != null) {
            dirEntity = directoryRepository.findById(directoryId)
                    .orElseThrow(() -> new NotFoundException("Directory", directoryId));
        }

        if (directoryId == null
                ? fileRepository.existsByDirectoryIsNullAndName(fileName)
                : fileRepository.existsByDirectory_IdAndName(directoryId, fileName)) {
            throw new ConflictException("File %s already exists in directory %s",
                                        fileName,
                                        directoryId);
        }

        if (contentLength >= 0) {
            log.info("Content-Length: {}", contentLength);
            // TODO add free space check
        }

        var fileEntity = new FileEntity();
        fileEntity.setId(UUID.randomUUID());
        fileEntity.setName(fileName);
        fileEntity.setDirectory(dirEntity);
        fileEntity.setContentType(contentType);
        fileEntity.setMetadata(new MetadataEntity());

        var path = getFilePath(fileEntity);
        var digest = getDigest();

        try {
            try (var digestInput = new DigestInputStream(input, digest)) {
                Files.copy(digestInput, path);
            }

            fileEntity.setChecksum(calculateChecksum(sha256, digest));
            fileEntity.setSize(Files.size(path));

            transactionTemplate.executeWithoutResult(status -> {
                entityManager.persist(fileEntity);
                entityManager.flush();
            });

            log.info("Uploaded file {}", fileEntity.getId());
            return FileMapper.toDto(fileEntity);
        } catch (IOException | RuntimeException e) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException cleanupException) {
                e.addSuppressed(cleanupException);
            }
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public FileDownload prepareDownload(UUID id) {
        log.info("Getting file content: {}", id);

        // TODO authorization
        var fileEntity = getFile(id);

        var file = Path.of(storageProperties.getDiscRoot(), id.toString()).toFile();
        if (!file.exists()) {
            throw new IllegalStateException("File " + file.getAbsolutePath() + " was not found");
        }
        log.info("File was found: {}", file.getAbsolutePath());

        return new FileDownload(fileEntity.getName(),
                                getFileContentType(fileEntity),
                                fileEntity.getSize(),
                                getContentDigest(fileEntity),
                                new FileSystemResource(file));
    }

    public void deleteFile(UUID id) throws IOException {
        log.info("Deleting file: {}", id);

        transactionTemplate.executeWithoutResult(status -> {
            var fileEntity = fileRepository.findById(id)
                    .orElseThrow(() -> new NotFoundException("File", id));

            fileRepository.delete(fileEntity);
        });

        var path = Path.of(storageProperties.getDiscRoot(), id.toString());
        if (Files.deleteIfExists(path)) {
            log.info("File {} deleted", id);
        } else {
            log.warn("File {} existed in database but not on disk", id);
        }
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

    //region Upload file

    private Path getFilePath(FileEntity fileEntity) throws IOException {
        var path = Path.of(storageProperties.getDiscRoot(),
                           fileEntity.getId().toString());
        Files.createDirectories(path.getParent());
        log.info("Uploading new file to: {}", path);
        return path;
    }

    private MessageDigest getDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String calculateChecksum(String sha256, MessageDigest digest) {
        var calculatedChecksum = HexFormat.of()
                .formatHex(digest.digest());

        if (sha256 != null && !sha256.equalsIgnoreCase(calculatedChecksum)) {
            throw new ChecksumMismatchException();
        }

        return calculatedChecksum;
    }

    //endregion

    //region Prepare download

    private MediaType getFileContentType(FileMetadata metadata) {
        return metadata.getContentType() != null
                ? MediaType.parseMediaType(metadata.getContentType())
                : MediaType.APPLICATION_OCTET_STREAM;
    }

    private String getContentDigest(FileMetadata metadata) {
        byte[] digest = HexFormat.of().parseHex(metadata.getChecksum());
        return "sha-256=:" + Base64.getEncoder().encodeToString(digest) + ":";
    }

    public record FileDownload(String name,
                               MediaType contentType,
                               long size,
                               String contentDigest,
                               Resource resource) {
    }

    //endregion
}
