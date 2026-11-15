
package com.marketlab.service;

import com.marketlab.model.Holding;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class PortfolioServiceImpl implements PortfolioService {

    private static final double STARTING_CASH = 100_000;

    private final PriceService priceService;
    private final double startingCash;

    private final Map<String, Holding> holdings =
            new LinkedHashMap<>();

    private double cash;

    // Default constructor: starting cash is Rs. 1,00,000
    public PortfolioServiceImpl(PriceService priceService) {
        this(priceService, STARTING_CASH);
    }

    // Constructor with custom starting cash
    public PortfolioServiceImpl(
            PriceService priceService, double startingCash) {

        if (priceService == null) {
            throw new IllegalArgumentException(
                    "Price service cannot be null");
        }

        if (startingCash < 0) {
            throw new IllegalArgumentException(
                    "Starting cash cannot be negative");
        }

        this.priceService = priceService;
        this.startingCash = startingCash;
        this.cash = startingCash;
    }

    @Override
    public double getCash() {
        return cash;
    }

    @Override
    public void buy(String symbol, int qty) {

        if (qty <= 0) {
            throw new IllegalArgumentException(
                    "Quantity must be positive");
        }

        double price = priceService.getPrice(symbol);
        double cost = price * qty;

        if (cost > cash) {
            throw new InsufficientFundsException("Not enough cash");
        }

        Holding old = holdings.get(symbol);

        int oldQuantity = 0;
        double oldAveragePrice = 0;

        if (old != null) {
            oldQuantity = old.quantity();
            oldAveragePrice = old.avgBuyPrice();
        }

        int newQuantity = oldQuantity + qty;

        double newAveragePrice =
                (oldAveragePrice * oldQuantity + cost)
                        / newQuantity;

        holdings.put(
                symbol,
                new Holding(symbol, newQuantity, newAveragePrice)
        );

        cash -= cost;
    }

    @Override
    public void sell(String symbol, int qty) {

        if (qty <= 0) {
            throw new IllegalArgumentException(
                    "Quantity must be positive");
        }

        Holding holding = holdings.get(symbol);

        if (holding == null || holding.quantity() < qty) {
            throw new InsufficientSharesException("Not enough shares");
        }

        cash += priceService.getPrice(symbol) * qty;

        int remaining = holding.quantity() - qty;

        if (remaining == 0) {
            holdings.remove(symbol);
        } else {
            holdings.put(
                    symbol,
                    new Holding(
                            symbol,
                            remaining,
                            holding.avgBuyPrice()
                    )
            );
        }
    }

    @Override
    public List<Holding> getHoldings() {
        return new ArrayList<>(holdings.values());
    }

    @Override
    public double getTotalPnL() {

        double portfolioValue = 0;

        for (Holding holding : holdings.values()) {
            portfolioValue +=
                    priceService.getPrice(holding.symbol())
                            * holding.quantity();
        }

        return cash + portfolioValue - startingCash;
    }
}
