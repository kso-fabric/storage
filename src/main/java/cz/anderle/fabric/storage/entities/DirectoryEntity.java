package cz.anderle.fabric.storage.entities;

import cz.anderle.fabric.storage.config.Constants;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = Constants.Table.Directory.TABLE_NAME)
@Getter
@Setter
public class DirectoryEntity extends EntryEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = Constants.Table.Directory.PARENT_ID)
    private DirectoryEntity parent;
}
