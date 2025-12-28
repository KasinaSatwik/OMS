package com.claire.oms.service;

import com.claire.oms.dto.CreateOrderRequest;
import com.claire.oms.entity.Order;
import com.claire.oms.entity.OrderItem;
import com.claire.oms.dto.ItemDto;
import com.claire.oms.models.OrderCreatedEvent;
import com.claire.oms.models.OrderCancelledEvent;
import com.claire.oms.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ApplicationEventPublisher publisher;

    @Transactional
    public String createOrder(CreateOrderRequest req) {
        Order o = new Order();
        o.setCustomerId(req.getCustomerId());
        o.setStatus(Order.Status.CREATED);
        List<OrderItem> items = req.getItems().stream()
                .map(i -> new OrderItem(i.getProductId(), i.getQuantity()))
                .collect(Collectors.toList());
        items.forEach(it -> it.setOrder(o));
        o.setItems(items);
        Order saved = orderRepository.save(o);
        // publish async event
    List<ItemDto> evItems = items.stream()
        .map(it -> ItemDto.builder().productId(it.getProductId()).quantity(it.getQuantity()).build())
        .collect(Collectors.toList());
    publisher.publishEvent(OrderCreatedEvent.builder().orderId(saved.getId()).items(evItems).build());
        return "ORD-" + saved.getId();
    }

    @Transactional
    public String cancelOrder(String orderIdStr) {
        Long id = parseOrderId(orderIdStr);
        Order o = orderRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("order not found"));
        if (o.getStatus() == Order.Status.CONFIRMED) {
            throw new IllegalStateException("Cannot cancel after confirmation");
        }
        o.setStatus(Order.Status.CANCELLED);
        orderRepository.save(o);
        publisher.publishEvent(OrderCancelledEvent.builder().orderId(o.getId()).build());
        return "ORD-" + o.getId();
    }

    @Transactional(readOnly = true)
    public Order getOrder(Long id) {
        return orderRepository.findById(id).orElse(null);
    }

    private Long parseOrderId(String orderIdStr) {
        if (orderIdStr == null) throw new IllegalArgumentException("invalid id");
        if (orderIdStr.startsWith("ORD-")) orderIdStr = orderIdStr.substring(4);
        return Long.parseLong(orderIdStr);
    }
}
