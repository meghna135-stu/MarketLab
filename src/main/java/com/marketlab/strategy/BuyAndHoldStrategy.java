package com.marketlab.strategy;

import com.marketlab.model.PricePoint;

import java.util.List;

public class BuyAndHoldStrategy implements Strategy {

    private boolean hasBought = false;

    @Override
    public Signal decide(String symbol, List<PricePoint> history) {
        if (!hasBought) {
            hasBought = true;
            return Signal.BUY;
        }
        return Signal.HOLD;
    }
}