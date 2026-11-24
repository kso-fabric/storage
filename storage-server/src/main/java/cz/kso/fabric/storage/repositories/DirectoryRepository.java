package cz.kso.fabric.storage.repositories;

import cz.kso.fabric.storage.entities.DirectoryEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DirectoryRepository extends JpaRepository<DirectoryEntity, UUID> {

    @Query("""
    SELECT d.id
    FROM DirectoryEntity d
    WHERE d.parent.id = :parentId
    ORDER BY d.id
    """)
    List<UUID> findDirectoryIdsByParent_Id(UUID parentId,
                                           Pageable pageable);

    Boolean existsByParent_Id(UUID parentId);
}
