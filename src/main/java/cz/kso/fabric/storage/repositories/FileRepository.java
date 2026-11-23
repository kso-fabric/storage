package cz.kso.fabric.storage.repositories;

import cz.kso.fabric.storage.entities.FileEntity;
import cz.kso.fabric.storage.repositories.projections.FileSizeProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Repository
public interface FileRepository extends JpaRepository<FileEntity, UUID> {

    Page<FileEntity> findByDirectory_Id(UUID directoryId, Pageable pageable);

    Page<FileEntity> findByDirectoryIsNull(Pageable pageable);

    Boolean existsByDirectoryIsNullAndName(String name);

    Boolean existsByDirectory_IdAndName(UUID directoryId, String name);

    Boolean existsByDirectory_Id(UUID directoryId);

    @Query("""
            SELECT f.id
            FROM FileEntity f
            """
    )
    Stream<UUID> streamAllIds();

    @Query("""
    SELECT new cz.kso.fabric.storage.repositories.projections.FileSizeProjection(f.id, f.size)
    FROM FileEntity f
    WHERE f.id IN :ids
    """)
    List<FileSizeProjection> findSizesByIds(@Param("ids") Collection<UUID> ids);

    @Query("SELECT COALESCE(SUM(f.size), 0) FROM FileEntity f")
    long getTotalFileSize();

    @Query("""
            SELECT f.id
            FROM FileEntity f
            WHERE f.directory.id = :directoryId
            ORDER BY f.id
            """)
    List<UUID> findFileIdsByDirectory_Id(UUID directoryId, Pageable pageable);
}
