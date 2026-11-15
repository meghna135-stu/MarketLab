package com.marketlab.service;

import com.marketlab.model.PricePoint;
import java.util.List;

/** Moving-average crossover backtest: buy when short MA crosses above long MA, sell when it crosses below. */
public class Backtester {

    public record Result(double strategyValue, double buyHoldValue, int trades, double startCash) {
        public double strategyReturnPct() { return (strategyValue / startCash - 1) * 100; }
        public double buyHoldReturnPct()  { return (buyHoldValue / startCash - 1) * 100; }
    }

    public static Result run(List<PricePoint> h, int shortW, int longW, double startCash) {
        if (shortW >= longW) throw new IllegalArgumentException("Short window must be smaller than long window");
        if (h.size() <= longW + 1) throw new IllegalArgumentException("Not enough history");

        double cash = startCash;
        int shares = 0, trades = 0;

        for (int i = longW; i < h.size(); i++) {
            boolean wasBelow = avg(h, i - 1, shortW) <= avg(h, i - 1, longW);
            boolean isAbove  = avg(h, i, shortW) > avg(h, i, longW);
            double price = h.get(i).price();

            if (wasBelow && isAbove && shares == 0) {            // golden cross -> buy
                shares = (int) (cash / price);
                cash -= shares * price;
                if (shares > 0) trades++;
            } else if (!wasBelow && !isAbove && shares > 0) {    // death cross -> sell
                cash += shares * price;
                shares = 0;
                trades++;
            }
        }
        double last = h.getLast().price();
        double strategy = cash + shares * last;

        double first = h.get(longW).price();                     // same start point as strategy
        int bhShares = (int) (startCash / first);
        double buyHold = (startCash - bhShares * first) + bhShares * last;

        return new Result(strategy, buyHold, trades, startCash);
    }

    private static double avg(List<PricePoint> h, int end, int w) {
        double sum = 0;
        for (int j = end - w + 1; j <= end; j++) sum += h.get(j).price();
        return sum / w;
    }
}