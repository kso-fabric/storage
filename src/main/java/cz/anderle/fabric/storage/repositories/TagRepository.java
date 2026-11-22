package cz.anderle.fabric.storage.repositories;

import cz.anderle.fabric.storage.entities.TagEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TagRepository extends JpaRepository<TagEntity, UUID> {

    Optional<TagEntity> findByValue(String value);
}
