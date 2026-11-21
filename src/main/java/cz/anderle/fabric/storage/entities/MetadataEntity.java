package cz.anderle.fabric.storage.entities;

import cz.anderle.fabric.storage.config.Constants;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = Constants.Table.Metadata.TABLE_NAME)
@Getter
@Setter
public class MetadataEntity {

    @Id
    private UUID id;

    @Column(name = Constants.Table.Metadata.DESCRIPTION)
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = Constants.Table.Metadata.CUSTOM, columnDefinition = "jsonb")
    private Map<String, Object> custom;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = Constants.Table.MetadataTag.TABLE_NAME,
            joinColumns = @JoinColumn(name = Constants.Table.MetadataTag.METADATA_ID),
            inverseJoinColumns = @JoinColumn(name = Constants.Table.MetadataTag.TAG_ID)
    )
    private Set<TagEntity> tags = new HashSet<>();
}
