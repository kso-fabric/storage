package cz.kso.fabric.storage.utils;

import cz.kso.fabric.storage.api.model.UpdateMetadataRequest;
import cz.kso.fabric.storage.api.model.UpdateTagItem;
import cz.kso.fabric.storage.entities.ItemEntity;
import cz.kso.fabric.storage.entities.MetadataEntity;
import cz.kso.fabric.storage.entities.TagEntity;
import cz.kso.fabric.storage.exceptions.BadRequestException;
import cz.kso.fabric.storage.repositories.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class MapperUtils {

    private final TagRepository tagRepository;

    public static <T> void applyIfPresent(JsonNullable<T> value, Consumer<T> setter) {
        if (value != null && value.isPresent()) {
            setter.accept(value.orElse(null));
        }
    }

    public void updateIfPresent(JsonNullable<UpdateMetadataRequest> request, ItemEntity entity) {
        if (request.isPresent()) {
            entity.setUpdatedAt(Instant.now());

            var updateMetadataRequest = request.get();
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

    //region Update metadata

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
