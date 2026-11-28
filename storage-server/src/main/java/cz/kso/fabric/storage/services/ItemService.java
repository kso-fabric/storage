package cz.kso.fabric.storage.services;

import cz.kso.fabric.storage.api.model.ItemDTO;
import cz.kso.fabric.storage.api.model.ItemListDTO;
import cz.kso.fabric.storage.entities.ItemEntity;
import cz.kso.fabric.storage.entities.MetadataEntity;
import cz.kso.fabric.storage.entities.enums.ItemTypeDb;
import cz.kso.fabric.storage.exceptions.ConflictException;
import cz.kso.fabric.storage.exceptions.NotFoundException;
import cz.kso.fabric.storage.mappers.ItemMapper;
import cz.kso.fabric.storage.repositories.ItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ItemService {

    private final ItemRepository itemRepository;

    @Transactional(readOnly = true)
    public ItemListDTO listItems(UUID directoryId, ItemTypeDb type, Integer limit, Integer page) {
        log.info("Listing items => type: {}, limit: {}, page: {}, directoryId: {}", type, limit, page, directoryId);
        // TODO authorization
        var pageable = PageRequest.of(
                page,
                limit,
                Sort.by(Sort.Order.asc("name"),
                        Sort.Order.asc("id"))
        );

        Page<ItemEntity> items;
        if (type == null) {
            items = itemRepository.findByParentId(directoryId, pageable);
        } else {
            items = itemRepository.findByParentIdAndType(directoryId, type, pageable);
        }

        var resp = new ItemListDTO();
        resp.setTotal(items.getTotalElements());
        resp.setTotalPages((long) items.getTotalPages());
        resp.setLimit(limit);
        for (var item : items) {
            resp.addItemsItem(ItemMapper.toDto(item));
        }

        return resp;
    }

    @Transactional(readOnly = true)
    public ItemDTO getItem(UUID id, ItemTypeDb type) {
        log.info("Getting item {}: {}", type, id);
        // TODO authorization
        ItemEntity item;
        if (type == null) {
            item = itemRepository.findById(id)
                    .orElseThrow(() -> new NotFoundException("Any", id));
        } else {
            item = itemRepository.findByIdAndType(id, type)
                    .orElseThrow(() -> new NotFoundException(type.name(), id));
        }

        return ItemMapper.toDto(item);
    }

    @Transactional(readOnly = true)
    public ItemEntity createItem(String name,
                                 UUID parentId,
                                 ItemTypeDb type) {
        log.info("Creating new item {} in directory {} named '{}'", type, parentId, name);
        // TODO authorization
        if (type == null) {
            throw new IllegalArgumentException("Type cannot be null");
        }

        if (parentId != null && !itemRepository.existsDirectoryById(parentId)) {
            throw new NotFoundException("Directory", parentId);
        }

        if (itemRepository.existsByParentIdAndNameAndType(parentId, name, type)) {
            throw new ConflictException("%s with name '%s' already exists in %s",
                                        type.name(),
                                        name,
                                        parentId);
        }

        var item = new ItemEntity();
        item.setId(UUID.randomUUID());
        item.setName(name);
        item.setParentId(parentId);
        item.setSize(0);
        item.setType(type);
        item.setMetadata(new MetadataEntity());

        return item;
    }
}
