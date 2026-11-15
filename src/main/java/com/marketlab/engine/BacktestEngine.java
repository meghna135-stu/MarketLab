package com.marketlab.engine;

import com.marketlab.model.Holding;
import com.marketlab.model.PricePoint;
import com.marketlab.service.PortfolioService;
import com.marketlab.service.PortfolioServiceImpl;
import com.marketlab.service.PriceService;
import com.marketlab.strategy.Signal;
import com.marketlab.strategy.Strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class BacktestEngine {

    // A factory, so every run starts from day 1 with fresh data
    private final Supplier<PriceService> priceServiceFactory;

    public BacktestEngine(Supplier<PriceService> priceServiceFactory) {
        if (priceServiceFactory == null) {
            throw new IllegalArgumentException("Price service factory cannot be null");
        }
        this.priceServiceFactory = priceServiceFactory;
    }

    /**
     * Replays the data for one stock. steps = number of ticks after day 1,
     * so a 5-day dataset uses steps = 4.
     */
    public BacktestResult run(String symbol, Strategy strategy,
                              double startingCash, int steps) {
        if (strategy == null) {
            throw new IllegalArgumentException("Strategy cannot be null");
        }
        if (startingCash <= 0 || steps < 0) {
            throw new IllegalArgumentException("Starting cash must be positive and steps >= 0");
        }

        PriceService prices = priceServiceFactory.get();
        PortfolioService portfolio = new PortfolioServiceImpl(prices, startingCash);
        TradingEngine engine = new TradingEngine(prices, portfolio);

        List<Double> values = new ArrayList<>();

        for (int day = 0; day <= steps; day++) {
            List<PricePoint> history = prices.getHistory(symbol, day + 1);
            Signal signal = strategy.decide(symbol, history);
            applySignal(signal, symbol, prices, portfolio, engine);

            values.add(startingCash + portfolio.getTotalPnL());

            if (day < steps) {
                engine.onTick();
            }
        }

        double finalValue = values.get(values.size() - 1);
        double pnl = finalValue - startingCash;
        return new BacktestResult(symbol, startingCash, finalValue, pnl,
                pnl / startingCash * 100.0, engine.getTrades(), values);
    }

    private void applySignal(Signal signal, String symbol, PriceService prices,
                             PortfolioService portfolio, TradingEngine engine) {
        if (signal == Signal.BUY) {
            int qty = (int) (portfolio.getCash() / prices.getPrice(symbol));
            if (qty > 0) {
                engine.placeOrder(symbol, OrderSide.BUY, OrderType.MARKET, qty, null);
            }
        } else if (signal == Signal.SELL) {
            int held = heldQuantity(portfolio, symbol);
            if (held > 0) {
                engine.placeOrder(symbol, OrderSide.SELL, OrderType.MARKET, held, null);
            }
        }
    }

    private int heldQuantity(PortfolioService portfolio, String symbol) {
        for (Holding h : portfolio.getHoldings()) {
            if (h.symbol().equals(symbol)) {
                return h.quantity();
            }
        }
        return 0;
    }
}