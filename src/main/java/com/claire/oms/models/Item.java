package com.claire.oms.models;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class Item {
    String productId;
    int quantity;
}
