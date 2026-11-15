package com.marketlab.engine;

import com.marketlab.service.CsvPriceService;
import com.marketlab.strategy.BuyAndHoldStrategy;
import com.marketlab.strategy.Signal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BacktestEngineTest {

    @Test
    void buyAndHoldOnRelianceMatchesHandCalculation() {
        BacktestEngine backtester = new BacktestEngine(CsvPriceService::new);

        BacktestResult r = backtester.run("RELIANCE", new BuyAndHoldStrategy(), 100_000, 4);

        // day 1: buy floor(100000/1230) = 81 shares for 99,630 -> cash 370
        // day 5: price 1265 -> 370 + 81*1265 = 102,835
        assertEquals(102_835, r.finalValue(), 0.001);
        assertEquals(2_835, r.pnl(), 0.001);
        assertEquals(1, r.trades().size());
        assertEquals(5, r.valueHistory().size());
    }

    @Test
    void eachRunStartsFresh() {
        BacktestEngine backtester = new BacktestEngine(CsvPriceService::new);

        BacktestResult first = backtester.run("RELIANCE", new BuyAndHoldStrategy(), 100_000, 4);
        BacktestResult second = backtester.run("RELIANCE", new BuyAndHoldStrategy(), 100_000, 4);

        assertEquals(first.finalValue(), second.finalValue(), 0.001);
    }

    @Test
    void buyAndHoldSignalsBuyOnceThenHold() {
        BuyAndHoldStrategy s = new BuyAndHoldStrategy();
        assertEquals(Signal.BUY, s.decide("RELIANCE", java.util.List.of()));
        assertEquals(Signal.HOLD, s.decide("RELIANCE", java.util.List.of()));
        assertEquals(Signal.HOLD, s.decide("RELIANCE", java.util.List.of()));
    }
}