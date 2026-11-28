package cz.kso.fabric.storage.services;

import cz.kso.fabric.storage.api.model.StorageInfo;
import cz.kso.fabric.storage.api.model.StorageIntegrity;
import cz.kso.fabric.storage.config.StorageProperties;
import cz.kso.fabric.storage.repositories.ItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private final StorageProperties storageProperties;
    private final ItemRepository itemRepository;

    public StorageInfo getStorageInfo() throws IOException {
        log.info("Getting storage info");
        // TODO authorization
        var store = getFileStore();
        return new StorageInfo()
                .managedBytes(itemRepository.getTotalFileSize())
                .usedBytes(store.getTotalSpace() - store.getUnallocatedSpace())
                .usableBytes(store.getUsableSpace())
                .unallocatedBytes(store.getUnallocatedSpace())
                .blockSize(store.getBlockSize())
                .totalBytes(store.getTotalSpace());
    }

    @Transactional(readOnly = true)
    public StorageIntegrity getStorageIntegrity() throws IOException {
        log.info("Getting storage integrity");
        // TODO authorization

        var counters = new IntegrityCounters();
        var batch = new HashMap<UUID, Long>();
        var rootPath = Path.of(storageProperties.getDiscRoot());

        try (var files = Files.walk(rootPath)) {
            files
                    .filter(Files::isRegularFile)
                    .forEach(filePath -> processDiskFile(counters, filePath, batch));
        }

        if (!batch.isEmpty()) {
            processBatch(counters, batch);
        }

        try (var files = itemRepository.streamAllFileIds()) {
            files.forEach(id -> processDatabaseFile(counters, id, rootPath));
        }

        return new StorageIntegrity()
                .databaseFiles(itemRepository.countFiles())
                .databaseBytes(itemRepository.getTotalFileSize())
                .diskFiles(counters.diskFiles)
                .diskBytes(counters.diskBytes)
                .missingFiles(counters.missing)
                .orphanedFiles(counters.orphaned)
                .sizeMismatches(counters.sizeMismatches);
    }

    public boolean hasEnoughSpace(long fileSize) throws IOException {
        log.info("Deciding if file of size {} fits", fileSize);
        if (fileSize < 0) {
            throw new IllegalArgumentException("File size cannot be negative");
        }
        return fileSize <= getFileStore().getUsableSpace();
    }

    private FileStore getFileStore() throws IOException {
        Path path = Path.of(storageProperties.getDiscRoot());
        return Files.getFileStore(path);
    }

    //region Integrity

    private void processDiskFile(IntegrityCounters counters, Path path, HashMap<UUID, Long> batch) {
        long size;
        try {
            size = Files.size(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read file size: " + path, e);
        }

        counters.diskFiles++;
        counters.diskBytes += size;

        UUID id;
        try {
            id = UUID.fromString(path.toFile().getName());
        } catch (Exception e) {
            log.warn("File {} has invalid UUID", path.toFile().getName());
            counters.orphaned++;
            return;
        }

        batch.put(id, size);
        if (batch.size() >= storageProperties.getIntegrityBatchSize()) {
            processBatch(counters, batch);
        }
    }

    private void processDatabaseFile(IntegrityCounters counters, UUID id, Path rootPath) {
        var filePath = rootPath.resolve(id.toString());

        if (!Files.isRegularFile(filePath)) {
            log.warn("File {} is missing", id);
            counters.missing++;
        }
    }

    private void processBatch(IntegrityCounters counters,
                              Map<UUID, Long> diskFiles) {
        var databaseFiles = itemRepository.findFileSizesByIds(diskFiles.keySet());

        var foundIds = new HashSet<UUID>();

        for (var file : databaseFiles) {
            foundIds.add(file.id());

            long actualSize = diskFiles.get(file.id());

            if (actualSize != file.size()) {
                log.warn("File {} has size mismatch", file.id());
                counters.sizeMismatches++;
            }
        }

        for (var id : diskFiles.keySet()) {
            if (!foundIds.contains(id)) {
                log.warn("File {} is orphaned", id);
                counters.orphaned++;
            }
        }

        diskFiles.clear();
    }

    private static class IntegrityCounters {
        long diskFiles = 0;
        long diskBytes = 0;
        long missing = 0;
        long orphaned = 0;
        long sizeMismatches = 0;
    }

    //endregion
}
