package cz.anderle.fabric.storage.repositories;

import cz.anderle.fabric.storage.entities.FileEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface FileRepository extends JpaRepository<FileEntity, UUID> {

    Page<FileEntity> findByDirectory_Id(UUID directoryId,
                                        Pageable pageable);

    Page<FileEntity> findByDirectoryIsNull(Pageable pageable);
}
