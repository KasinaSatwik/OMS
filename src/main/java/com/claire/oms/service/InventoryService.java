package com.claire.oms.service;

import com.claire.oms.entity.Inventory;
import com.claire.oms.entity.Order;
import com.claire.oms.repository.DeadLetterEventRepository;
import com.claire.oms.repository.InventoryRepository;
import com.claire.oms.repository.OrderRepository;
import com.claire.oms.entity.DeadLetterEvent;
import com.claire.oms.dto.ItemDto;
import com.claire.oms.models.OrderCreatedEvent;
import com.claire.oms.models.OrderConfirmedEvent;
import com.claire.oms.models.OrderFailedEvent;
import com.claire.oms.models.OrderCancelledEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.OptimisticLockException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private DeadLetterEventRepository dlq;

    @Autowired
    private ApplicationEventPublisher publisher;

    @Async("taskExecutor")
    @EventListener
    @Retryable(value = {OptimisticLockException.class}, maxAttempts = 3, backoff = @Backoff(delay = 200))
    @Transactional
    public void handleOrderCreated(OrderCreatedEvent ev) {
        // idempotence: only process if order still CREATED
        Order order = orderRepository.findById(ev.getOrderId()).orElse(null);
        if (order == null) return;
        if (order.getStatus() != Order.Status.CREATED) return;

        try {
            // attempt to reserve
            for (ItemDto it : ev.getItems()) {
                Inventory inv = inventoryRepository.findById(it.getProductId()).orElse(null);
                if (inv == null || inv.getAvailableQuantity() < it.getQuantity()) {
                    order.setStatus(Order.Status.FAILED);
                    orderRepository.save(order);
                    dlq.save(createDlq(ev, "insufficient stock for " + it.getProductId()));
                    publisher.publishEvent(OrderFailedEvent.builder().orderId(order.getId()).reason("insufficient stock").build());
                    return;
                }
                inv.setAvailableQuantity(inv.getAvailableQuantity() - it.getQuantity());
                inventoryRepository.save(inv); // can throw OptimisticLockException
            }
            // all reserved
            order.setStatus(Order.Status.CONFIRMED);
            orderRepository.save(order);
            publisher.publishEvent(OrderConfirmedEvent.builder().orderId(order.getId()).build());
        } catch (Exception ex) {
            // persist to dead letter
            dlq.save(createDlq(ev, ex.getMessage()));
            order.setStatus(Order.Status.FAILED);
            orderRepository.save(order);
            publisher.publishEvent(OrderFailedEvent.builder().orderId(order.getId()).reason(ex.getMessage()).build());
        }
    }

    @Async("taskExecutor")
    @EventListener
    @Transactional
    public void handleOrderCancelled(OrderCancelledEvent ev) {
        Order order = orderRepository.findById(ev.getOrderId()).orElse(null);
        if (order == null) return;
        // release reserved quantities for items (best-effort)
        try {
            for (var it : order.getItems()) {
                Inventory inv = inventoryRepository.findById(it.getProductId()).orElse(new Inventory(it.getProductId(), 0));
                inv.setAvailableQuantity(inv.getAvailableQuantity() + it.getQuantity());
                inventoryRepository.save(inv);
            }
        } catch (Exception ex) {
            dlq.save(createDlq(ev, ex.getMessage()));
        }
    }

    private DeadLetterEvent createDlq(Object payload, String error) {
        DeadLetterEvent d = new DeadLetterEvent();
        d.setPayload(payload.toString());
        d.setErrorMessage(error);
        d.setRetryCount(0);
        return d;
    }

    // --- Inventory management APIs (used by controller) ---
    @Transactional
    public Inventory createOrSetInventory(String productId, int quantity) {
        Inventory inv = inventoryRepository.findById(productId).orElse(new Inventory(productId, 0));
        inv.setAvailableQuantity(quantity);
        Inventory saved = inventoryRepository.save(inv);
        log.info("Set inventory {} = {}", productId, quantity);
        return saved;
    }

    @Transactional
    public Inventory adjustInventory(String productId, int delta) {
        Inventory inv = inventoryRepository.findById(productId).orElse(new Inventory(productId, 0));
        int newQty = inv.getAvailableQuantity() + delta;
        if (newQty < 0) {
            throw new IllegalArgumentException("Resulting quantity cannot be negative");
        }
        inv.setAvailableQuantity(newQty);
        Inventory saved = inventoryRepository.save(inv);
        log.info("Adjusted inventory {} by {} -> {}", productId, delta, newQty);
        return saved;
    }

    @Transactional
    public Inventory setInventoryQuantity(String productId, int quantity) {
        if (quantity < 0) throw new IllegalArgumentException("Quantity cannot be negative");
        return createOrSetInventory(productId, quantity);
    }
}
