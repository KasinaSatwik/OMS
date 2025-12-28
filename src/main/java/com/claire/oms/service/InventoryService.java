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
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.claire.oms.utility.Constants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Objects;

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
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 200))
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void handleOrderCreated(OrderCreatedEvent ev) {
        Order order = orderRepository.findById(ev.getOrderId()).orElse(null);
        if (order == null) return;
        if (order.getStatus() != Order.Status.CREATED) return;

        try {
            for (ItemDto it : ev.getItems()) {
                Inventory inv = inventoryRepository.findById(it.getProductId()).orElse(null);
                if (inv == null || inv.getAvailableQuantity() < it.getQuantity()) {
                    order.setStatus(Order.Status.FAILED);
                    orderRepository.save(order);
                    dlq.save(Objects.requireNonNull(createDlq(ev, Constants.MSG_INSUFFICIENT_STOCK_FOR + it.getProductId())));
                    publisher.publishEvent(OrderFailedEvent.builder().orderId(order.getId()).reason(Constants.MSG_INSUFFICIENT_STOCK).build());
                    return;
                }
                inv.setAvailableQuantity(inv.getAvailableQuantity() - it.getQuantity());
                inventoryRepository.save(inv); 
            }
            log.info("All items reserved for order {}", order.getId());
            order.setStatus(Order.Status.CONFIRMED);
            orderRepository.save(order);
            publisher.publishEvent(OrderConfirmedEvent.builder().orderId(order.getId()).build());
        } catch (Exception ex) {
            
            dlq.save(Objects.requireNonNull(createDlq(ev, ex.getMessage())));
            order.setStatus(Order.Status.FAILED);
            orderRepository.save(order);
            publisher.publishEvent(OrderFailedEvent.builder().orderId(order.getId()).reason(ex.getMessage()).build());
        }
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void handleOrderCancelled(OrderCancelledEvent ev) {
        Order order = orderRepository.findById(ev.getOrderId()).orElse(null);
        if (order == null) return;
        try {
            for (var it : order.getItems()) {
                Inventory inv = inventoryRepository.findById(it.getProductId()).orElse(new Inventory(it.getProductId(), 0));
                inv.setAvailableQuantity(inv.getAvailableQuantity() + it.getQuantity());
                inventoryRepository.save(inv);
            }
        } catch (Exception ex) {
            dlq.save(Objects.requireNonNull(createDlq(ev, ex.getMessage())));
        }
    }

    private DeadLetterEvent createDlq(Object payload, String error) {
        DeadLetterEvent d = new DeadLetterEvent();
        d.setPayload(payload.toString());
        d.setErrorMessage(error);
        d.setRetryCount(0);
        return d;
    }

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
            throw new IllegalArgumentException(Constants.EXC_RESULT_NEG);
        }
        inv.setAvailableQuantity(newQty);
        Inventory saved = inventoryRepository.save(inv);
        log.info("Adjusted inventory {} by {} -> {}", productId, delta, newQty);
        return saved;
    }

    @Transactional
    public Inventory setInventoryQuantity(String productId, int quantity) {
        if (quantity < 0) throw new IllegalArgumentException(Constants.EXC_QUANTITY_NEG);
        return createOrSetInventory(productId, quantity);
    }

    @Transactional(readOnly = true)
    public java.util.List<Inventory> listInventory() {
        return inventoryRepository.findAll();
    }
}
