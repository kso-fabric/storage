package cz.kso.fabric.storage.services;

import cz.kso.fabric.storage.api.model.FileListResponse;
import cz.kso.fabric.storage.api.model.FileMetadata;
import cz.kso.fabric.storage.api.model.UpdateItemRequest;
import cz.kso.fabric.storage.config.StorageProperties;
import cz.kso.fabric.storage.entities.DirectoryEntity;
import cz.kso.fabric.storage.entities.FileEntity;
import cz.kso.fabric.storage.entities.MetadataEntity;
import cz.kso.fabric.storage.exceptions.ChecksumMismatchException;
import cz.kso.fabric.storage.exceptions.ConflictException;
import cz.kso.fabric.storage.exceptions.InsufficientStorageException;
import cz.kso.fabric.storage.exceptions.NotFoundException;
import cz.kso.fabric.storage.mappers.FileMapper;
import cz.kso.fabric.storage.repositories.DirectoryRepository;
import cz.kso.fabric.storage.repositories.FileRepository;
import cz.kso.fabric.storage.utils.MapperUtils;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
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
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private final StorageProperties storageProperties;
    private final FileRepository fileRepository;
    private final DirectoryRepository directoryRepository;
    private final StorageService storageService;
    private final MapperUtils mapperUtils;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;

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

    @Transactional
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
        mapperUtils.updateDirectoryIfPresent(request.getDirectoryId(), fileEntity::setDirectory);
        mapperUtils.updateMetadataIfPresent(request, fileEntity);

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

        log.info("Upload Content-Length: {}", contentLength);
        if (contentLength >= 0 && !storageService.hasEnoughSpace(contentLength)) {
            throw new InsufficientStorageException();
        }

        var fileEntity = new FileEntity();
        fileEntity.setId(UUID.randomUUID());
        fileEntity.setName(fileName);
        fileEntity.setDirectory(dirEntity);
        fileEntity.setContentType(contentType);
        fileEntity.setMetadata(new MetadataEntity());

        var path = getFilePath(fileEntity);
        writeFile(fileEntity, path, sha256, input);

        transactionTemplate.executeWithoutResult(_ -> {
            entityManager.persist(fileEntity);
            entityManager.flush();
        });

        log.info("Uploaded file {}", fileEntity.getId());
        return FileMapper.toDto(fileEntity);
    }

    @Transactional(rollbackFor = IOException.class)
    public void updateFileContent(UUID id,
                                  String sha256,
                                  String contentType,
                                  long contentLength,
                                  InputStream input) throws IOException {
        log.info("Updating file: {} content", id);

        if (contentLength >= 0 && !storageService.hasEnoughSpace(contentLength)) {
            throw new InsufficientStorageException();
        }

        var fileEntity = fileRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("File", id));

        var origPath = getFilePath(fileEntity);
        var tempPath = Files.createTempFile(origPath.getParent(), id + "-", ".tmp");
        var backupPath = Files.createTempFile(origPath.getParent(), id + "-", ".bak");

        boolean replaced = false;
        boolean committed = false;

        try {
            // writeFile() uses Files.copy(), which requires
            // the destination not to exist.
            Files.delete(tempPath);

            // Write and validate the new content without changing
            // the managed entity.
            var newContent = new FileEntity();
            writeFile(newContent, tempPath, sha256, input);

            // Preserve the original content for rollback.
            Files.copy(origPath, backupPath, StandardCopyOption.REPLACE_EXISTING);

            // Replace the physical content.
            Files.move(
                    tempPath,
                    origPath,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
            replaced = true;

            // The transaction is committed before executeWithoutResult returns.
            transactionTemplate.executeWithoutResult(_ -> {
                var entity = fileRepository.findById(id)
                        .orElseThrow(() -> new NotFoundException("File", id));

                entity.setChecksum(newContent.getChecksum());
                entity.setSize(newContent.getSize());
                entity.setContentType(contentType);

                fileRepository.saveAndFlush(entity);
            });

            committed = true;
            log.info("File {} content updated successfully", id);

        } catch (IOException | RuntimeException e) {
            if (replaced && !committed) {
                try {
                    Files.move(
                            backupPath,
                            origPath,
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING
                    );
                } catch (IOException restoreException) {
                    e.addSuppressed(restoreException);
                    log.error("Failed to restore original content of file {}", id,
                              restoreException);
                }
            }

            throw e;

        } finally {
            Files.deleteIfExists(tempPath);

            if (committed || !replaced) {
                Files.deleteIfExists(backupPath);
            }
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

    private void writeFile(FileEntity fileEntity,
                           Path path,
                           String sha256,
                           InputStream input) throws IOException {
        var digest = getDigest();

        try {
            try (var digestInput = new DigestInputStream(input, digest)) {
                Files.copy(digestInput, path);
            }

            fileEntity.setChecksum(calculateChecksum(sha256, digest));
            fileEntity.setSize(Files.size(path));
        } catch (IOException | RuntimeException e) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException cleanupException) {
                e.addSuppressed(cleanupException);
            }
            throw e;
        }
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
