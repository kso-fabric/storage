package cz.kso.fabric.storage.repositories;

import cz.kso.fabric.storage.entities.ItemEntity;
import cz.kso.fabric.storage.entities.enums.ItemTypeDb;
import cz.kso.fabric.storage.repositories.projections.ItemSizeProjection;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@Repository
public interface ItemRepository extends JpaRepository<ItemEntity, UUID> {

    //region Queries

    Optional<ItemEntity> findByIdAndType(UUID id, ItemTypeDb type);

    default Optional<ItemEntity> findFileById(UUID id) {
        return findByIdAndType(id, ItemTypeDb.FILE);
    }

    default Optional<ItemEntity> findDirectoryById(UUID id) {
        return findByIdAndType(id, ItemTypeDb.DIRECTORY);
    }

    Page<ItemEntity> findByParentId(@Nullable UUID parentId, Pageable pageable);

    Page<ItemEntity> findByParentIdAndType(@Nullable UUID parentId, ItemTypeDb type, Pageable pageable);

    @Query("""
            SELECT f.id
            FROM ItemEntity f
            WHERE f.type = ItemTypeDb.FILE
            """)
    Stream<UUID> streamAllFileIds();

    @Query("""
            SELECT new cz.kso.fabric.storage.repositories.projections.ItemSizeProjection(f.id, f.size)
            FROM ItemEntity f
            WHERE f.id IN :ids AND f.type = ItemTypeDb.FILE
            """)
    List<ItemSizeProjection> findFileSizesByIds(@Param("ids") Collection<UUID> ids);

    @Query("""
            SELECT item.id
            FROM ItemEntity item
            WHERE item.type = :type
                AND item.parentId IS NOT DISTINCT FROM :parentId
            ORDER BY item.id
            """)
    List<UUID> findIdsByParentIdAndType(@Nullable @Param("parentId") UUID parentId,
                                        @Param("type") ItemTypeDb type, Pageable pageable);

    default List<UUID> findFileIdsByParentId(@Nullable UUID parentId, Pageable pageable) {
        return findIdsByParentIdAndType(parentId, ItemTypeDb.FILE, pageable);
    }

    default List<UUID> findDirectoryIdsByParentId(@Nullable UUID parentId, Pageable pageable) {
        return findIdsByParentIdAndType(parentId, ItemTypeDb.DIRECTORY, pageable);
    }

    //endregion

    //region Exists

    boolean existsByParentIdAndName(@Nullable UUID parentId, String name);

    boolean existsByParentIdAndNameAndIdNot(@Nullable UUID parentId, String name, UUID id);

    /// Has directory items.
    /// @param parentId Tested directory.
    boolean existsByParentId(@Nullable UUID parentId);

    boolean existsByParentIdAndNameAndType(@Nullable UUID parentId,
                                           String name,
                                           ItemTypeDb type);

    boolean existsByIdAndType(UUID id, ItemTypeDb type);

    default boolean existsFileById(UUID id) {
        return existsByIdAndType(id, ItemTypeDb.FILE);
    }

    default boolean existsDirectoryById(UUID id) {
        return existsByIdAndType(id, ItemTypeDb.DIRECTORY);
    }

    //endregion

    //region Calcs

    @Query("""
            SELECT COALESCE(SUM(f.size), 0)
            FROM ItemEntity f
            WHERE f.type = ItemTypeDb.FILE
            """)
    long getTotalFileSize();

    @Query("SELECT COUNT(*) FROM ItemEntity f WHERE f.type = ItemTypeDb.FILE")
    long countFiles();

    //endregion

}
