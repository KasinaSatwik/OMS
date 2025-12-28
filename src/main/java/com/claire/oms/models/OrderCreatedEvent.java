package com.claire.oms.models;

import lombok.Builder;
import lombok.Value;

import com.claire.oms.dto.ItemDto;
import java.util.List;

@Value
@Builder
public class OrderCreatedEvent {
    Long orderId;
    List<ItemDto> items;
}
