package cz.kso.fabric.storage.controllers;

import cz.kso.fabric.storage.api.FileApi;
import cz.kso.fabric.storage.api.model.FileListResponse;
import cz.kso.fabric.storage.api.model.FileMetadata;
import cz.kso.fabric.storage.api.model.UpdateItemRequest;
import cz.kso.fabric.storage.services.FileService;
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
import java.util.UUID;

@RestController
public class FileController implements FileApi {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @Override
    public ResponseEntity<FileListResponse> listFiles(UUID directoryId, Integer limit, Integer offset) {
        return ResponseEntity.ok(fileService.listFiles(directoryId, limit, offset));
    }

    @Override
    public ResponseEntity<FileMetadata> getFile(UUID id) {
        return ResponseEntity.ok(fileService.getFile(id));
    }

    @Override
    public ResponseEntity<FileMetadata> updateFile(UUID id, UpdateItemRequest updateFileRequest) {
        return ResponseEntity.ok(fileService.updateFile(id, updateFileRequest));
    }

    @Override
    public ResponseEntity<FileMetadata> uploadFile(String xFileName,
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
