package com.marketlab.engine;

public record Trade(
        int tick,
        String symbol,
        OrderSide side,
        int qty,
        double price
) {}
