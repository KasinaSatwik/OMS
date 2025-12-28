package com.claire.oms.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.context.event.EventListener;
import com.claire.oms.models.OrderConfirmedEvent;
import com.claire.oms.models.OrderCancelledEvent;

@Service
public class NotificationService {

    private final Logger log = LoggerFactory.getLogger(NotificationService.class);

    @Async("taskExecutor")
    public void notifyOrderConfirmed(Long orderId) {
        try {
            // mock notification - don't throw to avoid impacting core flow
            log.info("[notify] order confirmed: ORD-{}", orderId);
        } catch (Exception ex) {
            log.error("notification failed for order {}: {}", orderId, ex.getMessage());
        }
    }

    @Async("taskExecutor")
    public void notifyOrderCancelled(Long orderId) {
        try {
            log.info("[notify] order cancelled: ORD-{}", orderId);
        } catch (Exception ex) {
            log.error("notification failed for order {}: {}", orderId, ex.getMessage());
        }
    }

    @EventListener
    public void onOrderConfirmed(OrderConfirmedEvent ev) {
        notifyOrderConfirmed(ev.getOrderId());
    }

    @EventListener
    public void onOrderCancelled(OrderCancelledEvent ev) {
        notifyOrderCancelled(ev.getOrderId());
    }
}
