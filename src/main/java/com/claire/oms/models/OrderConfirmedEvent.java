package com.claire.oms.models;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class OrderConfirmedEvent {
    Long orderId;
}
