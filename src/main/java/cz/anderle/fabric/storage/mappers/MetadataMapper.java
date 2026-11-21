package cz.anderle.fabric.storage.mappers;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MetadataMapper {

//    public static Metadata toDto(MetadataWithTags record) {
//        if (record == null) {
//            return null;
//        }
//
//        Map<String, Object> addData = null;
//        if (record.metadata().getCustom() != null) {
//            try {
//                addData = new ObjectMapper().readValue(record.metadata().getCustom().data(),
//                                                       new TypeReference<>() {
//                                                       });
//            } catch (JsonProcessingException e) {
//                log.error("Invalid json in Metadata={}", record.metadata().getId(), e);
//                throw new IllegalStateException("Invalid JSON in stored metadata", e);
//            }
//        }
//
//        return new Metadata()
//                .description(record.metadata().getDescription())
//                .custom(addData)
//                .tags(record.tags()
//                              .stream()
//                              .map(TagRecord::getValue)
//                              .collect(Collectors.toSet()));
//    }
}
