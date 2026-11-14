
package com.marketlab.service;

import com.marketlab.model.Holding;
import java.util.List;

public interface PortfolioService {

    double getCash();

    void buy(String symbol, int qty);

    void sell(String symbol, int qty);

    List<Holding> getHoldings();

    double getTotalPnL();
}
