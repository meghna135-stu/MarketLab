package com.marketlab.service;

import com.marketlab.model.PricePoint;
import com.marketlab.model.Stock;

import java.util.ArrayList;
import java.util.List;

public interface PriceService {
    List<Stock> getAllStocks();
    double getPrice(String symbol);
    List<PricePoint> getHistory(String symbol, int points);
    List<PricePoint> getFullHistory(String symbol);
    void tick();
}
