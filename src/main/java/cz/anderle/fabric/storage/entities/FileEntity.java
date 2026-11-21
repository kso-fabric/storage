package cz.anderle.fabric.storage.entities;

import cz.anderle.fabric.storage.config.Constants;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = Constants.Table.File.TABLE_NAME)
public class FileEntity extends EntryEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = Constants.Table.File.DIRECTORY_ID)
    private DirectoryEntity directory;
}
