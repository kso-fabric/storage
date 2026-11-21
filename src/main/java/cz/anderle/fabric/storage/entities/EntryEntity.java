package cz.anderle.fabric.storage.entities;

import cz.anderle.fabric.storage.config.Constants;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@MappedSuperclass
@Getter
@Setter
public abstract class EntryEntity {

    @Column(name = Constants.Table.Entry.NAME, nullable = false)
    private String name;

    @CreationTimestamp
    @Column(
            name = Constants.Table.Entry.CREATED_AT,
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = Constants.Table.Entry.UPDATED_AT, nullable = false)
    private Instant updatedAt;

    @OneToOne(
            fetch = FetchType.LAZY,
            optional = false,
            cascade = CascadeType.PERSIST
    )
    @JoinColumn(
            name = Constants.Table.Entry.METADATA_ID,
            nullable = false,
            unique = true
    )
    private MetadataEntity metadata;
}
