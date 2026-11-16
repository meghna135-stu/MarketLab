package com.marketlab.strategy;

import com.marketlab.model.PricePoint;
import java.util.List;

/** Buy when short MA crosses above long MA, sell when it crosses below. Stateless: uses only the history it is given. */
public class MACrossoverStrategy implements Strategy {
    private final int shortW, longW;

    public MACrossoverStrategy(int shortW, int longW) {
        if (shortW >= longW) throw new IllegalArgumentException("Short window must be smaller than long window");
        this.shortW = shortW;
        this.longW = longW;
    }

    @Override
    public Signal decide(String symbol, List<PricePoint> h) {
        int n = h.size();
        if (n <= longW) return Signal.HOLD;                      // not enough data yet
        boolean wasBelow = avg(h, n - 2, shortW) <= avg(h, n - 2, longW);
        boolean isAbove  = avg(h, n - 1, shortW) > avg(h, n - 1, longW);
        if (wasBelow && isAbove) return Signal.BUY;
        if (!wasBelow && !isAbove) return Signal.SELL;
        return Signal.HOLD;
    }

    private static double avg(List<PricePoint> h, int end, int w) {
        double sum = 0;
        for (int j = end - w + 1; j <= end; j++) sum += h.get(j).price();
        return sum / w;
    }
}