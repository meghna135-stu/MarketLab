package com.marketlab.service;

import com.marketlab.model.Holding;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PortfolioServiceImpl implements PortfolioService {

    private static final double STARTING_CASH = 100_000;

    private final PriceService priceService;
    private final Map<String, Holding> holdings = new LinkedHashMap<>();
    private double cash = STARTING_CASH;

    public PortfolioServiceImpl(PriceService priceService) {
        this.priceService = priceService;
    }

    @Override
    public double getCash() {
        return cash;
    }

    @Override
    public void buy(String symbol, int qty) {
        if (qty <= 0) throw new IllegalArgumentException("Quantity must be positive");
        double price = priceService.getPrice(symbol);
        double cost = price * qty;
        if (cost > cash) throw new IllegalArgumentException("Not enough cash");

        cash -= cost;
        Holding old = holdings.get(symbol);
        if (old == null) {
            holdings.put(symbol, new Holding(symbol, qty, price));
        } else {
            int newQty = old.quantity() + qty;
            double newAvg = (old.avgBuyPrice() * old.quantity() + cost) / newQty;
            holdings.put(symbol, new Holding(symbol, newQty, newAvg));
        }
    }

    @Override
    public void sell(String symbol, int qty) {
        if (qty <= 0) throw new IllegalArgumentException("Quantity must be positive");
        Holding h = holdings.get(symbol);
        if (h == null || h.quantity() < qty) throw new IllegalArgumentException("Not enough shares");

        cash += priceService.getPrice(symbol) * qty;
        int remaining = h.quantity() - qty;
        if (remaining == 0) holdings.remove(symbol);
        else holdings.put(symbol, new Holding(symbol, remaining, h.avgBuyPrice()));
    }

    @Override
    public List<Holding> getHoldings() {
        return new ArrayList<>(holdings.values());
    }

    @Override
    public double getTotalPnL() {
        double value = 0;
        for (Holding h : holdings.values()) {
            value += priceService.getPrice(h.symbol()) * h.quantity();
        }
        return cash + value - STARTING_CASH;
    }
}