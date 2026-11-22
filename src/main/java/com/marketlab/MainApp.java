package com.marketlab;

import com.marketlab.engine.*;
import com.marketlab.model.*;
import com.marketlab.service.*;
import com.marketlab.strategy.*;
import javafx.animation.*;
import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.*;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

@SuppressWarnings({"unchecked", "deprecation"})
public class MainApp extends Application {
    private static final String GREEN = "green", RED = "crimson";   // W3C named colors
    private static final double SECONDS_PER_DAY = 2.0;              // simulation speed

    private final PriceService prices = new CsvPriceService();
    private final PortfolioService portfolio = new PortfolioServiceImpl(prices);
    private final TradingEngine engine = new TradingEngine(prices, portfolio);

    private Timeline timer;
    private String selected;
    private final Label dateLbl = new Label();

    // market tab
    private final TableView<Stock> watch = new TableView<>();
    private final LineChart<String, Number> chart = new LineChart<>(new CategoryAxis(), new NumberAxis());
    private final Map<String, Double> shownPrices = new LinkedHashMap<>();   // date -> price on chart
    private final TextField qtyField = new TextField("1");
    private final CheckBox maBox = new CheckBox("Show MA 10/30");
    private final ComboBox<OrderType> typeBox = new ComboBox<>();
    private final TextField limitField = new TextField();
    private final TableView<Order> pendingTable = new TableView<>();
    private final Label priceLbl = new Label(), msgLbl = new Label(),
            clickLbl = new Label("Hover or click a point on the graph to see its price");

    // portfolio tab
    private final TableView<PosRow> posTable = new TableView<>();
    private final TableView<Trade> tradeTable = new TableView<>();
    private final Label cashLbl = new Label(), valueLbl = new Label(), pnlLbl = new Label(), dayLbl = new Label();
    private double lastPnl = Double.NaN, lastDay = Double.NaN;
    // backtest tab
    private final ComboBox<String> btBox = new ComboBox<>();
    private final Label btLbl = new Label(), maLbl = new Label(), bhLbl = new Label();
    private final LineChart<Number, Number> btChart = new LineChart<>(new NumberAxis(), new NumberAxis());
    private final TableView<Trade> btTrades = new TableView<>();

    record PosRow(String sym, int qty, double avg, double cur, double pnl, double dayPnl) {}

    private void flash(Node n, boolean up) {
        String s = n.getStyle();
        final String base = (s.isEmpty() || s.endsWith(";")) ? s : s + ";";
        n.setStyle(base + "-fx-background-color:" + (up ? "lightgreen" : "lightpink") + ";");
        PauseTransition p = new PauseTransition(Duration.millis(800));
        p.setOnFinished(e -> n.setStyle(base));
        p.play();
    }

    @Override
    public void start(Stage stage) {
        selected = prices.getAllStocks().getFirst().symbol();
        TabPane tabs = new TabPane(tab("Market", marketPane()), tab("Portfolio", portfolioPane()), tab("Backtest", backtestPane()));

        Button pause = new Button("Pause");
        pause.setOnAction(e -> {
            if (timer.getStatus() == Animation.Status.RUNNING) { timer.pause(); pause.setText("Resume"); }
            else { timer.play(); pause.setText("Pause"); }
        });
        Slider speed = new Slider(0.5, 4, 1);
        speed.valueProperty().addListener((o, a, b) -> timer.setRate(b.doubleValue()));
        HBox top = new HBox(14, new Label("MarketLab"), dateLbl, pause, new Label("Speed"), speed);
        top.setPadding(new Insets(8));
        top.getStyleClass().add("top-bar");

        timer = new Timeline(new KeyFrame(Duration.seconds(SECONDS_PER_DAY), e -> { engine.onTick(); refreshAll(); }));
        timer.setCycleCount(Animation.INDEFINITE);
        timer.play();

        refreshAll();
        Scene scene = new Scene(new BorderPane(tabs, top, null, null, null), 1150, 760);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/marketlab.css")).toExternalForm());
        stage.setScene(scene);
        stage.setTitle("MarketLab");
        stage.show();
    }

    // ---------- panes ----------
    private Pane marketPane() {
        watch.getColumns().add(col("Symbol", Stock::symbol));
        watch.getColumns().add(col("Name", Stock::name));
        watch.getColumns().add(col("Price", (Stock s) -> String.format("₹%.2f", s.price())));
        watch.getColumns().add(colored("Chg %", (Stock s) -> String.format("%+.2f%%", chg(s.symbol())), (Stock s) -> chg(s.symbol()), true));
        watch.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        watch.setPrefWidth(380);
        watch.getSelectionModel().selectedItemProperty().addListener((o, a, s) -> {
            if (s != null) { selected = s.symbol(); refreshChart(); }
        });

        chart.setCreateSymbols(false);
        chart.setAnimated(false);
        chart.getStyleClass().add("market-chart");
        ((NumberAxis) chart.getYAxis()).setForceZeroInRange(false);

        // hover -> tooltip with price; click -> pin price under the chart
        Tooltip tip = new Tooltip();
        tip.setStyle("-fx-font-size: 13px;");
        chart.setOnMouseMoved(e -> {
            String t = priceAt(e);
            if (t == null) { tip.hide(); return; }
            tip.setText(t);
            tip.show(chart, e.getScreenX() + 12, e.getScreenY() + 12);
        });
        chart.setOnMouseExited(e -> tip.hide());
        chart.setOnMouseClicked(e -> { String t = priceAt(e); if (t != null) clickLbl.setText(t); });
        VBox.setVgrow(chart, Priority.ALWAYS);

        Button buy = new Button("Buy"), sell = new Button("Sell");
        String btn = "-fx-text-fill:white;-fx-font-size:15px;-fx-font-weight:bold;";
        buy.setStyle("-fx-background-color:" + GREEN + ";" + btn);
        sell.setStyle("-fx-background-color:" + RED + ";" + btn);
        buy.setPrefSize(120, 38);
        sell.setPrefSize(120, 38);
        buy.setOnAction(e -> trade(OrderSide.BUY));
        sell.setOnAction(e -> trade(OrderSide.SELL));
        qtyField.setPrefSize(100, 38);
        maBox.setOnAction(e -> refreshChart());

        Label tradeTitle = new Label("Trade panel");
        tradeTitle.setStyle("-fx-font-size:15px;-fx-font-weight:bold;");

        typeBox.getItems().addAll(OrderType.values());
        typeBox.setValue(OrderType.MARKET);
        limitField.setPromptText("Limit price");
        limitField.setPrefSize(120, 38);
        limitField.visibleProperty().bind(typeBox.valueProperty().isEqualTo(OrderType.LIMIT));
        limitField.managedProperty().bind(limitField.visibleProperty());

        HBox row = new HBox(12, new Label("Quantity:"), qtyField,
                new Label("Type:"), typeBox, limitField, buy, sell);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox opts = new HBox(20, maBox, msgLbl);

        pendingTable.getColumns().add(col("ID", (Order o) -> String.valueOf(o.id())));
        pendingTable.getColumns().add(col("Symbol", Order::symbol));
        pendingTable.getColumns().add(col("Side", (Order o) -> o.side().toString()));
        pendingTable.getColumns().add(col("Qty", (Order o) -> String.valueOf(o.qty())));
        pendingTable.getColumns().add(col("Limit", (Order o) -> String.format("₹%.2f", o.limitPrice())));
        pendingTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        pendingTable.setPrefHeight(90);
        pendingTable.setPlaceholder(new Label("No pending orders"));

        Button cancel = new Button("Cancel selected order");
        cancel.setOnAction(e -> {
            Order o = pendingTable.getSelectionModel().getSelectedItem();
            if (o == null) { alert("Select a pending order to cancel."); return; }
            engine.cancelOrder(o.id());
            msgLbl.setText("Cancelled order #" + o.id());
            refreshAll();
        });

        VBox tradeCard = new VBox(10, tradeTitle, row, opts,
                new Label("Pending orders"), pendingTable, cancel);
        tradeCard.setPadding(new Insets(14));
        tradeCard.setStyle("-fx-background-color:white;-fx-border-color:lightsteelblue;"
                + "-fx-border-radius:6;-fx-background-radius:6;");

        priceLbl.setStyle("-fx-font-size:18px;-fx-font-weight:bold;");
        clickLbl.setStyle("-fx-font-size:14px;");
        VBox right = new VBox(8, priceLbl, chart, clickLbl, tradeCard);
        right.setPadding(new Insets(0, 0, 0, 10));
        BorderPane bp = new BorderPane(right, null, null, null, watch);
        bp.setPadding(new Insets(10));
        return bp;
    }

    private Pane portfolioPane() {
        posTable.getColumns().add(col("Symbol", PosRow::sym));
        posTable.getColumns().add(col("Qty", (PosRow r) -> String.valueOf(r.qty())));
        posTable.getColumns().add(col("Avg Buy", (PosRow r) -> String.format("₹%.2f", r.avg())));
        posTable.getColumns().add(col("Current", (PosRow r) -> String.format("₹%.2f", r.cur())));
        posTable.getColumns().add(colored("P&L", (PosRow r) -> String.format("%+.2f", r.pnl()), PosRow::pnl,true));
        posTable.getColumns().add(colored("Day P&L", (PosRow r) -> String.format("%+.2f", r.dayPnl()), PosRow::dayPnl,true));
        posTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        posTable.setPlaceholder(new Label("No holdings yet. Buy something in the Market tab."));

        addTradeCols(tradeTable);

        HBox summary = new HBox(24, cashLbl, valueLbl, pnlLbl, dayLbl);
        summary.setStyle("-fx-font-size:15px;-fx-font-weight:bold;");
        VBox v = new VBox(10, summary, posTable, new Label("Trade history"), tradeTable);
        v.setPadding(new Insets(10));
        VBox.setVgrow(posTable, Priority.ALWAYS);
        VBox.setVgrow(tradeTable, Priority.ALWAYS);
        return v;
    }

    private Pane backtestPane() {
        for (Stock s : prices.getAllStocks()) btBox.getItems().add(s.symbol());
        btBox.getSelectionModel().selectFirst();
        Button run = new Button("Run backtest: MA 10/30 vs Buy & Hold");
        run.setOnAction(e -> backtest());

        btChart.setCreateSymbols(false);
        btChart.setAnimated(false);
        btChart.getStyleClass().add("bt-chart");
        btChart.getXAxis().setLabel("Trading day");
        btChart.getYAxis().setLabel("Portfolio value (₹)");
        ((NumberAxis) btChart.getYAxis()).setForceZeroInRange(false);
        addTradeCols(btTrades);
        btTrades.setPrefHeight(200);
        VBox.setVgrow(btChart, Priority.ALWAYS);

        VBox v = new VBox(10, new HBox(10, btBox, run), btLbl, new HBox(30, maLbl, bhLbl),
                btChart, new Label("MA strategy trades"), btTrades);
        v.setPadding(new Insets(10));
        return v;
    }

    // ---------- actions ----------
    private void trade(OrderSide side) {
        try {
            int q = Integer.parseInt(qtyField.getText().trim());
            OrderType type = typeBox.getValue();
            Double lp = type == OrderType.LIMIT ? Double.parseDouble(limitField.getText().trim()) : null;
            Order o = engine.placeOrder(selected, side, type, q, lp);
            if (o.status() == OrderStatus.REJECTED) alert(o.reason());
            else if (o.status() == OrderStatus.PENDING)
                msgLbl.setText(String.format("Limit order placed: %s %d %s @ ₹%.2f", side, q, selected, lp));
            else msgLbl.setText((side == OrderSide.BUY ? "Bought " : "Sold ") + q + " " + selected);
        } catch (NumberFormatException e) {
            alert("Enter valid numbers for quantity and limit price.");
        } catch (Exception e) {
            alert(e.getMessage());
        }
        refreshAll();
    }

    private void backtest() {
        try {
            String s = btBox.getValue();
            var be = new BacktestEngine(CsvPriceService::new);
            var fh = prices.getFullHistory(s);
            int steps = fh.size() - 51;
            BacktestResult ma = be.run(s, new MACrossoverStrategy(10, 30), 100_000, steps);
            BacktestResult bh = be.run(s, new BuyAndHoldStrategy(), 100_000, steps);

            btChart.getData().clear();
            btChart.getData().addAll(curve("MA 10/30", ma), curve("Buy & Hold", bh));
            btTrades.getItems().setAll(ma.trades());

            String range = fh.get(50).time().toLocalDate() + " to " + fh.getLast().time().toLocalDate();
            boolean maWins = ma.returnPercent() >= bh.returnPercent();
            btLbl.setText(s + " (" + range + ")  |  MA trades: " + ma.trades().size()
                    + "  |  Winner: " + (maWins ? "MA 10/30" : "Buy & Hold"));
            maLbl.setText(String.format("MA 10/30: ₹%.2f (%+.2f%%)", ma.finalValue(), ma.returnPercent()));
            bhLbl.setText(String.format("Buy & Hold: ₹%.2f (%+.2f%%)", bh.finalValue(), bh.returnPercent()));
            maLbl.setStyle("-fx-font-weight:bold;-fx-font-size:14px;-fx-text-fill:" + (maWins ? GREEN : RED));
            bhLbl.setStyle("-fx-font-weight:bold;-fx-font-size:14px;-fx-text-fill:" + (maWins ? RED : GREEN));
        } catch (Exception e) {
            maLbl.setText("");
            bhLbl.setText("");
            btLbl.setText("Backtest error: " + e.getMessage());
        }
    }

    // ---------- refresh ----------
    private void refreshAll() {
        watch.getItems().setAll(prices.getAllStocks());
        for (int i = 0; i < watch.getItems().size(); i++)
            if (watch.getItems().get(i).symbol().equals(selected)) { watch.getSelectionModel().select(i); break; }
        watch.refresh();
        refreshChart();

        List<PosRow> rows = new ArrayList<>();
        double value = 0, day = 0;
        for (Holding h : portfolio.getHoldings()) {
            double cur = prices.getPrice(h.symbol());
            double prev = prevClose(h.symbol());
            rows.add(new PosRow(h.symbol(), h.quantity(), h.avgBuyPrice(), cur,
                    (cur - h.avgBuyPrice()) * h.quantity(), (cur - prev) * h.quantity()));
            value += cur * h.quantity();
            day += (cur - prev) * h.quantity();
        }
        posTable.getItems().setAll(rows);
        posTable.refresh();
        List<Trade> trades = engine.getTrades();
        Collections.reverse(trades);
        tradeTable.getItems().setAll(trades);
        Order sel = pendingTable.getSelectionModel().getSelectedItem();
        pendingTable.getItems().setAll(engine.getPendingOrders());
        if (sel != null) pendingTable.getItems().stream()
                .filter(o -> o.id() == sel.id()).findFirst()
                .ifPresent(o -> pendingTable.getSelectionModel().select(o));
        double pnl = portfolio.getTotalPnL();
        cashLbl.setText(String.format("Cash: ₹%.2f", portfolio.getCash()));
        valueLbl.setText(String.format("Holdings: ₹%.2f", value));
        pnlLbl.setText(String.format("Total P&L: %+.2f", pnl));
        dayLbl.setText(String.format("Day P&L: %+.2f", day));
        pnlLbl.setStyle("-fx-text-fill:" + (pnl >= 0 ? GREEN : RED));
        dayLbl.setStyle("-fx-text-fill:" + (day >= 0 ? GREEN : RED));
        if (!Double.isNaN(lastPnl) && pnl != lastPnl) flash(pnlLbl, pnl > lastPnl);
        if (!Double.isNaN(lastDay) && day != lastDay) flash(dayLbl, day > lastDay);
        lastPnl = pnl;
        lastDay = day;
        String first = prices.getAllStocks().getFirst().symbol();
        dateLbl.setText("Date: " + prices.getHistory(first, 1).getFirst().time().toLocalDate());
    }

    private void refreshChart() {
        List<PricePoint> h = prices.getHistory(selected, 90);
        List<Double> ma10 = Indicators.movingAverage(h, 10), ma30 = Indicators.movingAverage(h, 30);
        XYChart.Series<String, Number> p = new XYChart.Series<>(), s10 = new XYChart.Series<>(), s30 = new XYChart.Series<>();
        p.setName(selected); s10.setName("MA 10"); s30.setName("MA 30");
        shownPrices.clear();
        for (int i = Math.max(0, h.size() - 60); i < h.size(); i++) {
            String d = h.get(i).time().toLocalDate().toString();
            shownPrices.put(d, h.get(i).price());
            p.getData().add(new XYChart.Data<>(d, h.get(i).price()));
            if (i - 9 >= 0) s10.getData().add(new XYChart.Data<>(d, ma10.get(i - 9)));
            if (i - 29 >= 0) s30.getData().add(new XYChart.Data<>(d, ma30.get(i - 29)));
        }
        chart.getData().clear();
        chart.getData().add(p);
        if (maBox.isSelected()) chart.getData().addAll(s10, s30);

        double c = chg(selected);
        chart.getStyleClass().removeAll("up", "down");
        chart.getStyleClass().add(c >= 0 ? "up" : "down");
        priceLbl.setText(String.format("%s  ₹%.2f  (%+.2f%%)", selected, prices.getPrice(selected), c));
        priceLbl.setTextFill(javafx.scene.paint.Color.web(c >= 0 ? GREEN : RED));
    }

    // ---------- helpers ----------
    private String priceAt(MouseEvent e) {
        var local = chart.getXAxis().sceneToLocal(e.getSceneX(), e.getSceneY());
        String date = chart.getXAxis().getValueForDisplay(local.getX());
        Double p = date == null ? null : shownPrices.get(date);
        return p == null ? null : String.format("%s  |  %s  |  ₹%.2f", selected, date, p);
    }

    private double prevClose(String s) {
        List<PricePoint> h = prices.getHistory(s, 2);
        return h.size() < 2 ? prices.getPrice(s) : h.getFirst().price();
    }

    private double chg(String s) {
        double prev = prevClose(s);
        return prev == 0 ? 0 : (prices.getPrice(s) - prev) / prev * 100;
    }

    private XYChart.Series<Number, Number> curve(String name, BacktestResult r) {
        XYChart.Series<Number, Number> s = new XYChart.Series<>();
        s.setName(name);
        for (int i = 0; i < r.valueHistory().size(); i++) s.getData().add(new XYChart.Data<>(i, r.valueHistory().get(i)));
        return s;
    }

    private void addTradeCols(TableView<Trade> t) {
        t.getColumns().add(col("Day", (Trade x) -> String.valueOf(x.tick())));
        t.getColumns().add(colored("Side", (Trade x) -> x.side().toString(), (Trade x) -> x.side() == OrderSide.BUY ? 1 : -1));
        t.getColumns().add(col("Symbol", Trade::symbol));
        t.getColumns().add(col("Qty", (Trade x) -> String.valueOf(x.qty())));
        t.getColumns().add(col("Price", (Trade x) -> String.format("₹%.2f", x.price())));
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    private <T> TableColumn<T, String> col(String title, Function<T, String> f) {
        TableColumn<T, String> c = new TableColumn<>(title);
        c.setCellValueFactory(d -> new SimpleStringProperty(f.apply(d.getValue())));
        return c;
    }

    private <T> TableColumn<T, String> colored(String title, Function<T, String> f, ToDoubleFunction<T> v) {
        return colored(title, f, v, false);
    }

    private <T> TableColumn<T, String> colored(String title, Function<T, String> f, ToDoubleFunction<T> v, boolean blink) {
        TableColumn<T, String> c = col(title, f);
        c.setCellFactory(x -> new TableCell<>() {
            private double last = Double.NaN;
            private int lastIdx = -1;
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                T item = getTableRow() == null ? null : getTableRow().getItem();
                if (empty || s == null || item == null) { setText(null); setStyle(""); last = Double.NaN; return; }
                double val = v.applyAsDouble(item);
                setText(s);
                setStyle("-fx-text-fill:" + (val >= 0 ? GREEN : RED) + ";-fx-font-weight:bold;");
                if (blink && getIndex() == lastIdx && !Double.isNaN(last) && val != last) flash(this, val > last);
                last = val;
                lastIdx = getIndex();
            }
        });
        return c;
    }


    private Tab tab(String name, Pane content) {
        Tab t = new Tab(name, content);
        t.setClosable(false);
        return t;
    }

    private void alert(String msg) {
        new Alert(Alert.AlertType.WARNING, msg).show();
    }

    public static void main(String[] args) { launch(args); }
}