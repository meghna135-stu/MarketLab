package com.marketlab.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PortfolioServiceImplTest {

    private PriceService prices;
    private PortfolioService portfolio;

    @BeforeEach
    void setUp() {
        prices = new CsvPriceService();
        portfolio = new PortfolioServiceImpl(prices, 100_000);
    }

    @Test
    void buyZeroOrNegativeQuantityThrows() {
        assertThrows(IllegalArgumentException.class, () -> portfolio.buy("RELIANCE", 0));
        assertThrows(IllegalArgumentException.class, () -> portfolio.buy("RELIANCE", -5));
    }

    @Test
    void sellZeroOrNegativeQuantityThrows() {
        portfolio.buy("RELIANCE", 5);
        assertThrows(IllegalArgumentException.class, () -> portfolio.sell("RELIANCE", 0));
        assertThrows(IllegalArgumentException.class, () -> portfolio.sell("RELIANCE", -1));
    }

    @Test
    void buyUnknownSymbolThrowsAndChangesNothing() {
        assertThrows(IllegalArgumentException.class, () -> portfolio.buy("FAKE", 1));
        assertEquals(100_000, portfolio.getCash(), 0.001);
        assertTrue(portfolio.getHoldings().isEmpty());
    }

    @Test
    void buyMoreThanCashAllowsThrowsAndChangesNothing() {
        assertThrows(InsufficientFundsException.class, () -> portfolio.buy("RELIANCE", 1000));
        assertEquals(100_000, portfolio.getCash(), 0.001);
        assertTrue(portfolio.getHoldings().isEmpty());
    }

    @Test
    void sellStockNotOwnedThrows() {
        assertThrows(InsufficientSharesException.class, () -> portfolio.sell("TCS", 1));
        assertThrows(InsufficientSharesException.class, () -> portfolio.sell("FAKE", 1));
    }

    @Test
    void sellMoreThanHeldThrowsAndKeepsHolding() {
        portfolio.buy("RELIANCE", 5);
        double cashBefore = portfolio.getCash();

        assertThrows(InsufficientSharesException.class, () -> portfolio.sell("RELIANCE", 10));

        assertEquals(cashBefore, portfolio.getCash(), 0.001);
        assertEquals(5, portfolio.getHoldings().get(0).quantity());
    }

    @Test
    void sellingEverythingRemovesHolding() {
        portfolio.buy("RELIANCE", 5);
        portfolio.sell("RELIANCE", 5);
        assertTrue(portfolio.getHoldings().isEmpty());
    }

    @Test
    void secondBuyAtDifferentPriceGivesWeightedAverage() {
        portfolio.buy("RELIANCE", 10);   // day 1: 1230
        prices.tick();
        portfolio.buy("RELIANCE", 10);   // day 2: 1240

        var h = portfolio.getHoldings().get(0);
        assertEquals(20, h.quantity());
        assertEquals(1235.0, h.avgBuyPrice(), 0.001);
    }

    @Test
    void partialSellKeepsAveragePrice() {
        portfolio.buy("RELIANCE", 10);
        prices.tick();
        portfolio.sell("RELIANCE", 4);

        var h = portfolio.getHoldings().get(0);
        assertEquals(6, h.quantity());
        assertEquals(1230.0, h.avgBuyPrice(), 0.001);
    }

    @Test
    void pnlMatchesPriceMovement() {
        portfolio.buy("RELIANCE", 10);   // at 1230
        prices.tick();                   // now 1240
        assertEquals(100.0, portfolio.getTotalPnL(), 0.001);
    }

    @Test
    void endToEndMatchesYourManualRun() {
        portfolio.buy("RELIANCE", 10);
        prices.tick();
        portfolio.sell("RELIANCE", 5);
        assertEquals(93_900, portfolio.getCash(), 0.001);
        assertEquals(100.0, portfolio.getTotalPnL(), 0.001);
    }
}