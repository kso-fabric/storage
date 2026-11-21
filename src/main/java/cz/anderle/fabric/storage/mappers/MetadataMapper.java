package cz.anderle.fabric.storage.mappers;

import cz.anderle.fabric.storage.api.model.Metadata;
import cz.anderle.fabric.storage.entities.MetadataEntity;
import cz.anderle.fabric.storage.entities.TagEntity;

import java.util.stream.Collectors;

public interface MetadataMapper {

    static Metadata toDto(MetadataEntity entity) {
        if (entity == null) {
            return null;
        }

        return new Metadata()
                .description(entity.getDescription())
                .custom(entity.getCustom())
                .tags(entity.getTags()
                              .stream()
                              .map(TagEntity::getValue)
                              .collect(Collectors.toSet()));
    }
}
