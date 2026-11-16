# MarketLab

A desktop stock-market simulator built with JavaFX. It replays two years of real NSE daily prices as a live-feeling market, lets you trade with virtual cash, and includes a strategy backtester.

## Features

- **Market tab:** watchlist with daily change, price chart (green when rising, red when falling), optional MA 10/30 overlay, hover or click on the chart to see the price on any date, and a trade panel.
- **Portfolio tab:** cash, holdings value, total P&L, day P&L, a holdings table (quantity, average buy price, current price, P&L, day P&L) with color-coded values that flash on change, and a trade history.
- **Backtest tab:** runs a Moving Average 10/30 crossover strategy against Buy & Hold on the full history, comparing final value, return, number of trades and the equity curves.
- **Simulation controls:** pause/resume and a speed slider. Each tick is one trading day.
- **Order engine:** market and limit orders, with rejection reasons (insufficient funds or shares).

## How the simulation works

Each stock has about two years of daily closing prices. A pointer marks "today", and each tick moves it forward one day. Prices and charts only show data up to that pointer, so the history looks like a live feed. The data is daily, not intraday. Trading is long-only, with no fees or slippage.

## Run it

Requirements: JDK 21 and Maven.

```bash
mvn clean javafx:run
```

Use the Maven command rather than the IDE's Run button, which fails with "JavaFX runtime components are missing".

To run the tests, run `com.marketlab.service.ServiceTest` (14 cases covering validation, weighted average price and P&L).

## Architecture

```
com.marketlab
 ├─ model      Stock, PricePoint, Holding (records)
 ├─ service    PriceService / PortfolioService (interfaces)
 │             CsvPriceService, PortfolioServiceImpl, Indicators
 ├─ engine     TradingEngine (orders), BacktestEngine, Order, Trade, BacktestResult
 ├─ strategy   Strategy (interface), Signal, BuyAndHoldStrategy, MACrossoverStrategy
 └─ MainApp    JavaFX UI (Market, Portfolio, Backtest tabs)
```

The UI talks to interfaces, so the data source (CSV today, a live API later) can change without touching the UI. New strategies only need to implement `Strategy`.

Data: 2 years of NSE daily prices fetched with yfinance and stored as CSVs in `src/main/resources/data/`.

## Team

- **Meghna M Nambiar:** data pipeline, models, services (price feed, portfolio, indicators), tests, JavaFX UI, backtest integration.
- **Konda Prathyaksha Reddy:** trading engine (orders), backtest engine, strategy framework.

## Limitations and future work

- Daily data only, with no intraday prices.
- Long-only, with no shorting or margin.
- No transaction costs or slippage.
- Portfolio is not saved between runs.
- Stock names show as tickers.
- More strategies (RSI, momentum) can be added through the `Strategy` interface.