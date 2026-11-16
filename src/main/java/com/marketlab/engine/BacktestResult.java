package com.marketlab.engine;

import java.util.List;

public record BacktestResult(
        String symbol,
        double startingCash,
        double finalValue,
        double pnl,
        double returnPercent,
        List<Trade> trades,
        List<Double> valueHistory   // portfolio value at the end of each day, for the chart
) {}