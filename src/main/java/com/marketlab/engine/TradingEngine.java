
package com.marketlab.engine;

import com.marketlab.service.InsufficientFundsException;
import com.marketlab.service.InsufficientSharesException;
import com.marketlab.service.PortfolioService;
import com.marketlab.service.PriceService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TradingEngine {

    private final PriceService priceService;
    private final PortfolioService portfolio;

    private final Map<Integer, Order> orders =
            new LinkedHashMap<>();

    private final List<Trade> trades =
            new ArrayList<>();

    private int nextId = 1;
    private int tickCount = 0;

    public TradingEngine(
            PriceService priceService,
            PortfolioService portfolio) {

        if (priceService == null || portfolio == null) {
            throw new IllegalArgumentException(
                    "Services cannot be null");
        }

        this.priceService = priceService;
        this.portfolio = portfolio;
    }

    public Order placeOrder(
            String symbol,
            OrderSide side,
            OrderType type,
            int qty,
            Double limitPrice) {

        if (qty <= 0) {
            throw new IllegalArgumentException(
                    "Quantity must be positive");
        }

        if (type == null || side == null) {
            throw new IllegalArgumentException(
                    "Order type and side cannot be null");
        }

        if (type == OrderType.LIMIT &&
                (limitPrice == null || limitPrice <= 0)) {

            throw new IllegalArgumentException(
                    "Limit orders need a positive limit price");
        }

        priceService.getPrice(symbol);

        Order order = new Order(
                nextId++,
                symbol,
                side,
                type,
                qty,
                type == OrderType.LIMIT ? limitPrice : null,
                OrderStatus.PENDING,
                null
        );

        orders.put(order.id(), order);

        if (isFillable(order)) {
            execute(order);
        }

        return orders.get(order.id());
    }

    public void onTick() {

        priceService.tick();
        tickCount++;

        for (Order order : new ArrayList<>(orders.values())) {

            if (order.status() == OrderStatus.PENDING
                    && isFillable(order)) {

                execute(order);
            }
        }
    }

    public boolean cancelOrder(int id) {

        Order order = orders.get(id);

        if (order == null ||
                order.status() != OrderStatus.PENDING) {
            return false;
        }

        orders.put(
                id,
                order.withStatus(OrderStatus.CANCELLED, null)
        );

        return true;
    }

    public List<Order> getPendingOrders() {

        return orders.values().stream()
                .filter(o -> o.status() == OrderStatus.PENDING)
                .toList();
    }

    public List<Order> getAllOrders() {
        return new ArrayList<>(orders.values());
    }

    public List<Trade> getTrades() {
        return new ArrayList<>(trades);
    }

    private boolean isFillable(Order order) {

        if (order.type() == OrderType.MARKET) {
            return true;
        }

        double price = priceService.getPrice(order.symbol());

        return order.side() == OrderSide.BUY
                ? price <= order.limitPrice()
                : price >= order.limitPrice();
    }

    private void execute(Order order) {

        double price = priceService.getPrice(order.symbol());

        try {

            if (order.side() == OrderSide.BUY) {
                portfolio.buy(order.symbol(), order.qty());
            } else {
                portfolio.sell(order.symbol(), order.qty());
            }

            trades.add(new Trade(
                    tickCount,
                    order.symbol(),
                    order.side(),
                    order.qty(),
                    price
            ));

            orders.put(
                    order.id(),
                    order.withStatus(OrderStatus.FILLED, null)
            );

        } catch (InsufficientFundsException |
                 InsufficientSharesException e) {

            orders.put(
                    order.id(),
                    order.withStatus(OrderStatus.REJECTED, e.getMessage())
            );
        }
    }
}
