package cz.kso.fabric.storage.mappers;

import cz.kso.fabric.storage.api.model.DirectoryMetadata;
import cz.kso.fabric.storage.entities.DirectoryEntity;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public interface DirectoryMapper {

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    static DirectoryMetadata toDto(DirectoryEntity entity) {
        return new DirectoryMetadata()
                .id(entity.getId())
                .name(entity.getName())
                .parentId(entity.getParent() == null ? null : entity.getParent().getId())
                .createdAt(entity.getCreatedAt().atOffset(ZoneOffset.UTC))
                .additionalData(MetadataMapper.toDto(entity.getMetadata()));
    }
}
