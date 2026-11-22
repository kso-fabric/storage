package cz.anderle.fabric.storage.mappers;

import cz.anderle.fabric.storage.api.model.FileMetadata;
import cz.anderle.fabric.storage.api.model.UpdateItemRequest;
import cz.anderle.fabric.storage.entities.DirectoryEntity;
import cz.anderle.fabric.storage.entities.FileEntity;
import org.openapitools.jackson.nullable.JsonNullable;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.function.Consumer;

public interface FileMapper {

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    static FileMetadata toDto(FileEntity entity) {
        return new FileMetadata()
                .id(entity.getId())
                .name(entity.getName())
                .directoryId(entity.getDirectory() == null ? null : entity.getDirectory().getId())
                .size(entity.getSize())
                .contentType(entity.getContentType())
                .checksum(entity.getChecksum())
                .createdAt(entity.getCreatedAt().atOffset(ZoneOffset.UTC))
                .updatedAt(entity.getUpdatedAt().atOffset(ZoneOffset.UTC))
                .additionalData(MetadataMapper.toDto(entity.getMetadata()));
    }
}
