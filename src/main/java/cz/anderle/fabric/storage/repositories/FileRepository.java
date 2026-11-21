package cz.anderle.fabric.storage.repositories;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class FileRepository {

//    private final DSLContext dsl;
//
//    public Optional<FileWithMetadata> findById(UUID id) {
//        return dsl.select(
//                        FILE,
//                        METADATA,
//                        multiset(
//                                selectFrom(TAG)
//                                        .where(TAG.METADATA_ID.eq(METADATA.ID))
//                        ).convertFrom(result -> result.into(TAG))
//                )
//                .from(FILE)
//                .leftJoin(METADATA)
//                .on(FILE.METADATA_ID.eq(METADATA.ID))
//                .where(FILE.ID.eq(id))
//                .fetchOptional(record -> new FileWithMetadata(
//                        record.value1(),
//                        record.value2().getId() == null
//                                ? null
//                                : new MetadataWithTags(
//                                record.value2(),
//                                record.value3()
//                        )
//                ));
//    }
//
//    public List<FileWithMetadata> findAllByDirectory(
//            UUID directoryId,
//            Integer limit,
//            Integer offset
//    ) {
//        return dsl.select(
//                        FILE,
//                        METADATA,
//                        multiset(
//                                selectFrom(TAG)
//                                        .where(TAG.METADATA_ID.eq(METADATA.ID))
//                        ).convertFrom(result -> result.into(TAG))
//                )
//                .from(FILE)
//                .leftJoin(METADATA)
//                .on(FILE.METADATA_ID.eq(METADATA.ID))
//                .where(FILE.DIRECTORY_ID.isNotDistinctFrom(directoryId))
//                .orderBy(FILE.NAME.asc(), FILE.ID.asc())
//                .limit(limit)
//                .offset(offset)
//                .fetch(record -> new FileWithMetadata(
//                        record.value1(),
//                        record.value2().getId() == null
//                                ? null
//                                : new MetadataWithTags(
//                                record.value2(),
//                                record.value3()
//                        )
//                ));
//    }
//
//    public FileRecord save(FileRecord record) {
//        record.attach(dsl.configuration());
//        record.store();
//        return record;
//    }
}
