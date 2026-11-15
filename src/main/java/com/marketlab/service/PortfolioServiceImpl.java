package com.marketlab.service;

import com.marketlab.model.Holding;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PortfolioServiceImpl implements PortfolioService {

    private final PriceService priceService;

    private final double startingCash;
    private double cash;

    private final Map<String, Holding> holdings =
            new LinkedHashMap<>();

    // Default starting cash: Rs. 1,00,000
    public PortfolioServiceImpl(PriceService priceService) {
        this(priceService, 100000.0);
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
        double totalCost = price * qty;

        if (totalCost > cash) {
            throw new InsufficientFundsException(
                    "Insufficient cash");
        }

        Holding oldHolding = holdings.get(symbol);

        int oldQuantity = 0;
        double oldAveragePrice = 0.0;

        if (oldHolding != null) {
            oldQuantity = oldHolding.quantity();
            oldAveragePrice = oldHolding.avgBuyPrice();
        }

        int newQuantity = oldQuantity + qty;

        double newAveragePrice =
                ((oldQuantity * oldAveragePrice) + totalCost)
                        / newQuantity;

        Holding newHolding = new Holding(
                symbol,
                newQuantity,
                newAveragePrice
        );

        holdings.put(symbol, newHolding);

        cash -= totalCost;
    }

    @Override
    public void sell(String symbol, int qty) {

        if (qty <= 0) {
            throw new IllegalArgumentException(
                    "Quantity must be positive");
        }

        Holding holding = holdings.get(symbol);

        if (holding == null || holding.quantity() < qty) {
            throw new InsufficientSharesException(
                    "Insufficient shares");
        }

        double sellingPrice = priceService.getPrice(symbol);

        cash += sellingPrice * qty;

        int remainingQuantity = holding.quantity() - qty;

        if (remainingQuantity == 0) {
            holdings.remove(symbol);
        } else {
            holdings.put(
                    symbol,
                    new Holding(
                            symbol,
                            remainingQuantity,
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

        double portfolioValue = cash;

        for (Holding holding : holdings.values()) {

            double currentPrice =
                    priceService.getPrice(holding.symbol());

            portfolioValue +=
                    currentPrice * holding.quantity();
        }

        return portfolioValue - startingCash;
    }
}