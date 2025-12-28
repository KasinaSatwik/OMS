package com.claire.oms.controller;

import com.claire.oms.dto.CreateOrderRequest;
import com.claire.oms.dto.ItemDto;
import com.claire.oms.dto.OrderResponseDto;
import com.claire.oms.entity.Order;
import com.claire.oms.service.OrderService;
import com.claire.oms.utility.Constants;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
@RequestMapping(Constants.API_ORDERS)
public class OrderController {

    @Autowired
    private OrderService orderService;

    @PostMapping
    public ResponseEntity<?> createOrder(@RequestBody CreateOrderRequest req) {
        Order o = orderService.createOrder(req);
        List<ItemDto> items = o.getItems().stream()
                .map(it -> ItemDto.builder().productId(it.getProductId()).quantity(it.getQuantity()).build())
                .collect(Collectors.toList());
        return ResponseEntity.status(201).body(new OrderResponseDto(Constants.ORDER_PREFIX + o.getId(), o.getStatus().name(), items));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<?> cancelOrder(@PathVariable String orderId) {
        Order o = orderService.cancelOrder(orderId);
        List<ItemDto> items = o.getItems().stream()
                .map(it -> ItemDto.builder().productId(it.getProductId()).quantity(it.getQuantity()).build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(new OrderResponseDto(Constants.ORDER_PREFIX + o.getId(), o.getStatus().name(), items));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<?> getOrder(@PathVariable String orderId) {
        Long id = Long.parseLong(orderId.replace(Constants.ORDER_PREFIX, ""));
        Order o = orderService.getOrder(id);
        if (o == null) return ResponseEntity.notFound().build();
        List<ItemDto> items = o.getItems().stream()
                .map(it -> ItemDto.builder().productId(it.getProductId()).quantity(it.getQuantity()).build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(new OrderResponseDto(Constants.ORDER_PREFIX + o.getId(), o.getStatus().name(), items));
    }

    @GetMapping
    public ResponseEntity<List<OrderResponseDto>> listOrders() {
        List<Order> orders = orderService.listOrders();
        List<OrderResponseDto> dtos = orders.stream().map(o -> {
            List<ItemDto> items = o.getItems().stream()
                    .map(it -> ItemDto.builder().productId(it.getProductId()).quantity(it.getQuantity()).build())
                    .collect(Collectors.toList());
            return new OrderResponseDto(Constants.ORDER_PREFIX + o.getId(), o.getStatus().name(), items);
        }).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }
}
