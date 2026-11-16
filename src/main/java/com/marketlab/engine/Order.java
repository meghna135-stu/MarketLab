package com.marketlab.engine;

public record Order(
        int id,
        String symbol,
        OrderSide side,
        OrderType type,
        int qty,
        Double limitPrice,
        OrderStatus status,
        String reason
) {

    public Order withStatus(OrderStatus newStatus, String newReason) {
        return new Order(
                id,
                symbol,
                side,
                type,
                qty,
                limitPrice,
                newStatus,
                newReason
        );
    }
}