package cz.kso.fabric.storage.controllers;

import cz.kso.fabric.storage.api.ItemApi;
import cz.kso.fabric.storage.api.model.*;
import cz.kso.fabric.storage.services.ItemService;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ItemController implements ItemApi {

    private final ItemService itemService;

    @Override
    public ResponseEntity<ItemDTO> getItem(UUID id) {
        return ResponseEntity.ok(itemService.getItem(id, null));
    }

    @Override
    public ResponseEntity<ItemListDTO> listItems(@Nullable UUID directoryId,
                                                 @Nullable String name,
                                                 @Nullable Set<@Size(min = 1, max = 100) String> tags,
                                                 @Nullable Long minSize,
                                                 @Nullable Long maxSize,
                                                 @Nullable OffsetDateTime createdFrom,
                                                 @Nullable OffsetDateTime createdTo,
                                                 @Nullable OffsetDateTime updatedFrom,
                                                 @Nullable OffsetDateTime updatedTo,
                                                 ItemSort sort,
                                                 SortOrder order,
                                                 Integer limit,
                                                 Integer page,
                                                 @Nullable ItemType type) {
        return ResponseEntity.ok(itemService.listItems(directoryId,
                                                       null,
                                                       limit,
                                                       page));
    }
}
