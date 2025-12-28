package com.claire.oms.utility;

public final class Constants {
    private Constants() {}

    // API paths
    public static final String API_ORDERS = "/api/orders";
    public static final String API_INVENTORY = "/api/inventory";

    // Order status labels
    public static final String STATUS_CREATED = "CREATED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    // Messages
    public static final String MSG_INSUFFICIENT_STOCK = "insufficient stock";
    public static final String MSG_INSUFFICIENT_STOCK_FOR = "insufficient stock for ";
    public static final String EXC_RESULT_NEG = "Resulting quantity cannot be negative";
    public static final String EXC_QUANTITY_NEG = "Quantity cannot be negative";

    // Order messages / labels
    public static final String ORDER_NOT_FOUND = "order not found";
    public static final String ORDER_CANNOT_CANCEL_AFTER_CONFIRM = "Cannot cancel after confirmation";
    public static final String ORDER_INVALID_ID = "invalid id";

    // Order id prefix
    public static final String ORDER_PREFIX = "ORD-";
}
