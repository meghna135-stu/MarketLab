package com.marketlab.engine;

import com.marketlab.service.CsvPriceService;
import com.marketlab.service.PortfolioService;
import com.marketlab.service.PortfolioServiceImpl;
import com.marketlab.service.PriceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TradingEngineTest {

    private PriceService prices;
    private PortfolioService portfolio;
    private TradingEngine engine;

    @BeforeEach
    void setUp() {
        prices = new CsvPriceService();
        portfolio = new PortfolioServiceImpl(prices, 100_000);
        engine = new TradingEngine(prices, portfolio);
    }

    // --- market orders ---

    @Test
    void marketBuyFillsImmediately() {
        Order o = engine.placeOrder("RELIANCE", OrderSide.BUY, OrderType.MARKET, 10, null);

        assertEquals(OrderStatus.FILLED, o.status());
        assertEquals(87_700, portfolio.getCash(), 0.001);   // 100000 - 10*1230
        assertEquals(1, portfolio.getHoldings().size());
        assertEquals(1, engine.getTrades().size());
    }

    @Test
    void marketBuyWithoutEnoughCashIsRejectedNotThrown() {
        Order o = engine.placeOrder("RELIANCE", OrderSide.BUY, OrderType.MARKET, 1000, null);

        assertEquals(OrderStatus.REJECTED, o.status());
        assertNotNull(o.reason());
        assertEquals(100_000, portfolio.getCash(), 0.001);
        assertTrue(engine.getTrades().isEmpty());
    }

    @Test
    void marketSellWithoutSharesIsRejected() {
        Order o = engine.placeOrder("RELIANCE", OrderSide.SELL, OrderType.MARKET, 5, null);

        assertEquals(OrderStatus.REJECTED, o.status());
        assertNotNull(o.reason());
    }

    // --- limit orders ---

    @Test
    void limitBuyAboveCurrentPriceFillsImmediately() {
        Order o = engine.placeOrder("RELIANCE", OrderSide.BUY, OrderType.LIMIT, 10, 1300.0);

        assertEquals(OrderStatus.FILLED, o.status());
        assertEquals(87_700, portfolio.getCash(), 0.001);   // fills at current price, not the limit
    }

    @Test
    void limitBuyBelowAllPricesStaysPending() {
        Order o = engine.placeOrder("RELIANCE", OrderSide.BUY, OrderType.LIMIT, 10, 1200.0);
        assertEquals(OrderStatus.PENDING, o.status());

        engine.onTick();
        engine.onTick();

        assertEquals(1, engine.getPendingOrders().size());
        assertEquals(100_000, portfolio.getCash(), 0.001);
    }

    @Test
    void limitSellFillsOnlyWhenPriceReachesLimit() {
        engine.placeOrder("RELIANCE", OrderSide.BUY, OrderType.MARKET, 10, null);   // at 1230
        Order sell = engine.placeOrder("RELIANCE", OrderSide.SELL, OrderType.LIMIT, 10, 1250.0);
        assertEquals(OrderStatus.PENDING, sell.status());

        engine.onTick();   // 1240
        engine.onTick();   // 1235
        assertEquals(1, engine.getPendingOrders().size());

        engine.onTick();   // 1250 -> fills
        assertTrue(engine.getPendingOrders().isEmpty());
        assertEquals(100_200, portfolio.getCash(), 0.001);   // 87700 + 10*1250
        assertTrue(portfolio.getHoldings().isEmpty());
        assertEquals(2, engine.getTrades().size());
    }

    // --- cancelling ---

    @Test
    void cancelPendingOrderWorksAndItNeverFills() {
        Order o = engine.placeOrder("RELIANCE", OrderSide.BUY, OrderType.LIMIT, 10, 1200.0);

        assertTrue(engine.cancelOrder(o.id()));
        assertFalse(engine.cancelOrder(o.id()));   // already cancelled

        engine.onTick();
        assertTrue(engine.getTrades().isEmpty());
        assertEquals(OrderStatus.CANCELLED, engine.getAllOrders().get(0).status());
    }

    @Test
    void cancelFilledOrderReturnsFalse() {
        Order o = engine.placeOrder("RELIANCE", OrderSide.BUY, OrderType.MARKET, 1, null);
        assertFalse(engine.cancelOrder(o.id()));
    }

    // --- invalid input ---

    @Test
    void zeroQuantityThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> engine.placeOrder("RELIANCE", OrderSide.BUY, OrderType.MARKET, 0, null));
    }

    @Test
    void limitOrderWithoutLimitPriceThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> engine.placeOrder("RELIANCE", OrderSide.BUY, OrderType.LIMIT, 5, null));
        assertThrows(IllegalArgumentException.class,
                () -> engine.placeOrder("RELIANCE", OrderSide.BUY, OrderType.LIMIT, 5, -10.0));
    }

    @Test
    void unknownSymbolThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> engine.placeOrder("FAKE", OrderSide.BUY, OrderType.MARKET, 1, null));
    }
}