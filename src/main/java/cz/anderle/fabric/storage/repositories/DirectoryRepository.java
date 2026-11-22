package cz.anderle.fabric.storage.repositories;

import cz.anderle.fabric.storage.entities.DirectoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DirectoryRepository extends JpaRepository<DirectoryEntity, UUID> {

}
