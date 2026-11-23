package cz.kso.fabric.storage.repositories;

import cz.kso.fabric.storage.entities.FileEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FileRepository extends JpaRepository<FileEntity, UUID> {

    Page<FileEntity> findByDirectory_Id(UUID directoryId,
                                        Pageable pageable);

    Page<FileEntity> findByDirectoryIsNull(Pageable pageable);

    Boolean existsByDirectoryIsNullAndName(String name);

    Boolean existsByDirectory_IdAndName(UUID directoryId, String name);

    @Query("""
    SELECT f.id
    FROM FileEntity f
    WHERE f.directory.id = :directoryId
    ORDER BY f.id
    """)
    List<UUID> findFileIdsByDirectoryId(
            UUID directoryId,
            Pageable pageable
    );
}
