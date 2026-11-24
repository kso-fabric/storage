package cz.kso.fabric.storage.repositories.projections;

import java.util.UUID;

public record FileSizeProjection(UUID id, Long size) {
}
