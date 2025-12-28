package com.claire.oms.controller;

import com.claire.oms.dto.CreateOrderRequest;
import com.claire.oms.dto.OrderResponseDto;
import com.claire.oms.entity.Order;
import com.claire.oms.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @PostMapping
    public ResponseEntity<?> createOrder(@RequestBody CreateOrderRequest req) {
        String orderId = orderService.createOrder(req);
        return ResponseEntity.status(201).body(new OrderResponseDto(orderId, "CREATED", null));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<?> cancelOrder(@PathVariable String orderId) {
        String id = orderService.cancelOrder(orderId);
        return ResponseEntity.ok(new OrderResponseDto(id, "CANCELLED", null));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<?> getOrder(@PathVariable String orderId) {
        Long id = Long.parseLong(orderId.replace("ORD-", ""));
        Order o = orderService.getOrder(id);
        if (o == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(new OrderResponseDto("ORD-" + o.getId(), o.getStatus().name(), null));
    }
}
