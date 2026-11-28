package cz.kso.fabric.storage.services;

import cz.kso.fabric.storage.api.model.CreateDirectoryRequest;
import cz.kso.fabric.storage.api.model.ItemDTO;
import cz.kso.fabric.storage.api.model.ItemListDTO;
import cz.kso.fabric.storage.api.model.UpdateDirectoryRequest;
import cz.kso.fabric.storage.entities.enums.ItemTypeDb;
import cz.kso.fabric.storage.exceptions.BadRequestException;
import cz.kso.fabric.storage.exceptions.ConflictException;
import cz.kso.fabric.storage.exceptions.NotFoundException;
import cz.kso.fabric.storage.mappers.ItemMapper;
import cz.kso.fabric.storage.repositories.ItemRepository;
import cz.kso.fabric.storage.utils.MapperUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DirectoryService {

    private final ItemRepository itemRepository;
    private final ItemService itemService;
    private final FileService fileService;
    private final MapperUtils mapperUtils;

    public ItemListDTO listDirectories(UUID directoryId, Integer limit, Integer page) {
        return itemService.listItems(directoryId, ItemTypeDb.DIRECTORY, limit, page);
    }

    public ItemDTO getDirectory(UUID id) {
        return itemService.getItem(id, ItemTypeDb.DIRECTORY);
    }

    @Transactional
    public ItemDTO createDirectory(CreateDirectoryRequest request) {
        var dirEntity = itemService.createItem(request.getName(),
                                               request.getParentId(),
                                               ItemTypeDb.DIRECTORY);
        dirEntity = itemRepository.saveAndFlush(dirEntity);

        log.info("New directory created: {}", dirEntity.getId());
        return ItemMapper.toDto(dirEntity);
    }

    @Transactional
    public ItemDTO updateDirectory(UUID id, UpdateDirectoryRequest request) {
        log.info("Updating directory: {}", id);
        // TODO authorization
        var dirEntity = itemRepository.findDirectoryById(id)
                .orElseThrow(() -> new NotFoundException("Directory", id));

        MapperUtils.applyIfPresent(request.getName(), dirEntity::setName);
        MapperUtils.applyIfPresent(request.getParentId(), dirEntity::setParentId);
        mapperUtils.updateIfPresent(request.getAdditionalData(), dirEntity);

        return ItemMapper.toDto(dirEntity);
    }

    public void deleteDirectory(UUID id, Boolean recursive) throws IOException {
        log.info("Deleting directory: {}", id);

        // TODO authorization
        // TODO locking
        if (id == null) {
            throw new BadRequestException("Cannot delete root directory");
        }

        if (!itemRepository.existsDirectoryById(id)) {
            throw new NotFoundException("Directory", id);
        }

        if (itemRepository.existsByParentId(id) && !Boolean.TRUE.equals(recursive)) {
            throw new ConflictException("Directory %s is not empty and recursive is not allowed", id);
        }

        if (Boolean.TRUE.equals(recursive)) {
            deleteDirectoryFiles(id);
            deleteDirectoriesInParent(id);
        }

        itemRepository.deleteById(id);
    }

    //region Delete directory

    private void deleteDirectoryFiles(UUID directoryId) throws IOException {
        var firstPage = PageRequest.of(0, 100);

        while (true) {
            var fileIds = itemRepository.findFileIdsByParentId(directoryId,
                                                               firstPage);

            if (fileIds.isEmpty()) {
                break;
            }

            for (UUID fileId : fileIds) {
                fileService.deleteFile(fileId);
            }
        }
    }

    private void deleteDirectoriesInParent(UUID parentId) throws IOException {
        var firstPage = PageRequest.of(0, 100);

        while (true) {
            var dirIds = itemRepository.findDirectoryIdsByParentId(parentId,
                                                                   firstPage);

            if (dirIds.isEmpty()) {
                break;
            }

            for (UUID dirId : dirIds) {
                deleteDirectory(dirId, true);
            }
        }
    }

    //endregion
}
