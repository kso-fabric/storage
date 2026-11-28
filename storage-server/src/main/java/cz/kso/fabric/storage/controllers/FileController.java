package cz.kso.fabric.storage.controllers;

import cz.kso.fabric.storage.api.FileApi;
import cz.kso.fabric.storage.api.model.*;
import cz.kso.fabric.storage.services.FileService;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

@RestController
public class FileController implements FileApi {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @Override
    public ResponseEntity<ItemDTO> getFile(UUID id) {
        return ResponseEntity.ok(fileService.getFile(id));
    }

    @Override
    public ResponseEntity<ItemListDTO> listFiles(@Nullable UUID directoryId,
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
        return ResponseEntity.ok(fileService.listFiles(directoryId, limit, limit));
    }

    @Override
    public ResponseEntity<ItemDTO> updateFile(UUID id, UpdateFileRequest updateFileRequest) {
        return ResponseEntity.ok(fileService.updateFile(id, updateFileRequest));
    }

    @Override
    public ResponseEntity<ItemDTO> uploadFile(String xFileName,
                                                   Resource body,
                                                   UUID xDirectoryId,
                                                   String xContentSHA256,
                                                   String xContentType) {
        try (InputStream input = body.getInputStream()) {
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(fileService.uploadFile(xFileName,
                                                 xDirectoryId,
                                                 xContentSHA256,
                                                 xContentType,
                                                 body.contentLength(),
                                                 input));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public ResponseEntity<Void> updateFileContent(UUID id,
                                                  Resource body,
                                                  String xContentSHA256,
                                                  String xContentType) {
        try (InputStream input = body.getInputStream()) {
            fileService.updateFileContent(id,
                                          xContentSHA256,
                                          xContentType,
                                          body.contentLength(),
                                          input);
            return ResponseEntity.noContent().build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public ResponseEntity<Resource> downloadFile(UUID id) {
        var fileDownload = fileService.prepareDownload(id);

        return ResponseEntity.ok()
                .contentType(fileDownload.contentType())
                .contentLength(fileDownload.size())
                .header("Content-Digest", fileDownload.contentDigest())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(fileDownload.name(), StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .body(fileDownload.resource());
    }

    @Override
    public ResponseEntity<Void> deleteFile(UUID id) {
        try {
            fileService.deleteFile(id);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return ResponseEntity.noContent().build();
    }
}
