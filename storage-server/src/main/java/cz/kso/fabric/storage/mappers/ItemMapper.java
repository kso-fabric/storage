package cz.kso.fabric.storage.mappers;

import cz.kso.fabric.storage.api.model.ItemDTO;
import cz.kso.fabric.storage.entities.ItemEntity;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public interface ItemMapper {

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    static ItemDTO toDto(ItemEntity entity) {
        return new ItemDTO()
                .id(entity.getId())
                .name(entity.getName())
                .parentId(entity.getParentId())
                .size(entity.getSize())
                .contentType(entity.getContentType())
                .checksum(entity.getChecksum())
                .createdAt(entity.getCreatedAt().atOffset(ZoneOffset.UTC))
                .updatedAt(entity.getUpdatedAt().atOffset(ZoneOffset.UTC))
                .additionalData(MetadataMapper.toDto(entity.getMetadata()));
    }
}
