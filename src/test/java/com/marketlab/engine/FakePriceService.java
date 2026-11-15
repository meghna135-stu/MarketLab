package com.marketlab.engine;

import com.marketlab.model.PricePoint;
import com.marketlab.model.Stock;
import com.marketlab.service.PriceService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class FakePriceService implements PriceService {

    private final Map<String, List<Double>> prices = Map.of(
            "RELIANCE",
            List.of(1230.0, 1240.0, 1235.0, 1250.0, 1265.0)
    );

    private int currentIndex = 0;

    @Override
    public List<Stock> getAllStocks() {
        return List.of(
                new Stock("RELIANCE", "Reliance Industries",
                        getPrice("RELIANCE"))
        );
    }

    @Override
    public double getPrice(String symbol) {
        List<Double> history = prices.get(symbol);

        if (history == null) {
            throw new IllegalArgumentException("Unknown stock: " + symbol);
        }

        return history.get(currentIndex);
    }

    @Override
    public List<PricePoint> getHistory(String symbol, int points) {
        List<Double> history = prices.get(symbol);

        if (history == null) {
            throw new IllegalArgumentException("Unknown stock: " + symbol);
        }

        int end = currentIndex + 1;
        int start = Math.max(0, end - points);

        return java.util.stream.IntStream.range(start, end)
                .mapToObj(i -> new PricePoint(
                        LocalDateTime.of(2025, 1, 1, 0, 0).plusDays(i),
                        history.get(i)
                ))
                .toList();
    }

    @Override
    public List<PricePoint> getFullHistory(String symbol) {
        List<Double> history = prices.get(symbol);

        if (history == null) {
            throw new IllegalArgumentException("Unknown stock: " + symbol);
        }

        return java.util.stream.IntStream.range(0, history.size())
                .mapToObj(i -> new PricePoint(
                        LocalDateTime.of(2025, 1, 1, 0, 0).plusDays(i),
                        history.get(i)
                ))
                .toList();
    }

    @Override
    public void tick() {
        if (currentIndex < 4) {
            currentIndex++;
        }
    }
}