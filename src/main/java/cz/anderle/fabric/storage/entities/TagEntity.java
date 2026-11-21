package cz.anderle.fabric.storage.entities;

import cz.anderle.fabric.storage.config.Constants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = Constants.Table.Tag.TABLE_NAME)
@Getter
@Setter
public class TagEntity {

    @Id
    private UUID id;

    @Column(
            name = Constants.Table.Tag.VALUE,
            nullable = false,
            unique = true,
            length = 100
    )
    private String value;
}
