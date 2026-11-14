package com.marketlab.service;

import com.marketlab.model.PricePoint;

import java.util.ArrayList;
import java.util.List;

public class Indicators {

    /** Simple moving average over `window` points. Output is shorter by window-1. */
    public static List<Double> movingAverage(List<PricePoint> history, int window) {
        List<Double> result = new ArrayList<>();
        for (int i = window - 1; i < history.size(); i++) {
            double sum = 0;
            for (int j = i - window + 1; j <= i; j++) sum += history.get(j).price();
            result.add(sum / window);
        }
        return result;
    }

    /** Daily returns as fractions (0.01 = +1%). */
    public static List<Double> dailyReturns(List<PricePoint> history) {
        List<Double> result = new ArrayList<>();
        for (int i = 1; i < history.size(); i++) {
            double prev = history.get(i - 1).price();
            result.add((history.get(i).price() - prev) / prev);
        }
        return result;
    }

    /** Standard deviation of daily returns. */
    public static double volatility(List<PricePoint> history) {
        List<Double> r = dailyReturns(history);
        if (r.isEmpty()) return 0;
        double mean = r.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double var = r.stream().mapToDouble(x -> (x - mean) * (x - mean)).sum() / r.size();
        return Math.sqrt(var);
    }
}