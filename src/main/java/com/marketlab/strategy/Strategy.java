package com.marketlab.strategy;

import com.marketlab.model.PricePoint;
import java.util.List;

public interface Strategy {

    Signal decide(String symbol, List<PricePoint> history);
}