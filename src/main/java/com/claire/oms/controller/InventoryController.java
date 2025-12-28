package com.claire.oms.controller;

import com.claire.oms.dto.InventoryAdjustRequest;
import com.claire.oms.dto.InventoryRequest;
import com.claire.oms.dto.InventoryResponse;
import com.claire.oms.entity.Inventory;
import com.claire.oms.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    @Autowired
    private InventoryService inventoryService;

    @PostMapping
    public ResponseEntity<InventoryResponse> createOrSet(@Valid @RequestBody InventoryRequest req) {
        Inventory inv = inventoryService.createOrSetInventory(req.getProductId(), req.getQuantity());
        return ResponseEntity.ok(InventoryResponse.builder()
                .productId(inv.getProductId())
                .availableQuantity(inv.getAvailableQuantity())
                .version(inv.getVersion())
                .build());
    }

    @PutMapping("/{productId}")
    public ResponseEntity<InventoryResponse> setQuantity(@PathVariable String productId, @Valid @RequestBody InventoryAdjustRequest req) {
        Inventory inv = inventoryService.setInventoryQuantity(productId, req.getQuantity());
        return ResponseEntity.ok(InventoryResponse.builder()
                .productId(inv.getProductId())
                .availableQuantity(inv.getAvailableQuantity())
                .version(inv.getVersion())
                .build());
    }

    @PatchMapping("/{productId}/adjust")
    public ResponseEntity<InventoryResponse> addQuantity(@PathVariable String productId, @Valid @RequestBody InventoryAdjustRequest req) {
        Inventory inv = inventoryService.adjustInventory(productId, req.getQuantity());
        return ResponseEntity.ok(InventoryResponse.builder()
                .productId(inv.getProductId())
                .availableQuantity(inv.getAvailableQuantity())
                .version(inv.getVersion())
                .build());
    }
}
