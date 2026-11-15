

package com.marketlab.service;

import com.marketlab.model.PricePoint;
import com.marketlab.model.Stock;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CsvPriceService implements PriceService {

    private final Map<String, List<PricePoint>> stockData =
            new LinkedHashMap<>();

    private final Map<String, String> stockNames =
            new LinkedHashMap<>();

    private int currentIndex = 0;

    public CsvPriceService() {

        stockNames.put("RELIANCE", "Reliance Industries");
        stockNames.put("TCS", "Tata Consultancy Services");
        stockNames.put("INFY", "Infosys");
        stockNames.put("HDFCBANK", "HDFC Bank");
        stockNames.put("ICICIBANK", "ICICI Bank");

        loadAllStocks();
    }

    private void loadAllStocks() {

        for (String symbol : stockNames.keySet()) {

            List<PricePoint> prices = new ArrayList<>();

            String fileName = "/data/" + symbol + ".csv";

            try (InputStream input =
                         CsvPriceService.class.getResourceAsStream(fileName)) {

                if (input == null) {
                    throw new IllegalStateException(
                            "CSV file not found: " + fileName);
                }

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(
                                input, StandardCharsets.UTF_8));

                String line;

                while ((line = reader.readLine()) != null) {

                    // Remove a possible UTF-8 BOM
                    if (line.startsWith("\uFEFF")) {
                        line = line.substring(1);
                    }

                    // Skip the CSV header
                    if (line.startsWith("Date,")) {
                        continue;
                    }

                    if (line.isBlank()) {
                        continue;
                    }

                    String[] values = line.split(",");

                    LocalDate date =
                            LocalDate.parse(values[0].trim());

                    double closePrice =
                            Double.parseDouble(values[1].trim());

                    PricePoint point = new PricePoint(
                            date.atStartOfDay(),
                            closePrice
                    );

                    prices.add(point);
                }

            } catch (IOException e) {
                throw new IllegalStateException(
                        "Error reading " + fileName, e);
            }

            if (prices.isEmpty()) {
                throw new IllegalStateException(
                        "No price data found in " + fileName);
            }

            stockData.put(symbol, prices);
        }
    }

    @Override
    public List<Stock> getAllStocks() {

        List<Stock> stocks = new ArrayList<>();

        for (String symbol : stockData.keySet()) {

            stocks.add(new Stock(
                    symbol,
                    stockNames.get(symbol),
                    getPrice(symbol)
            ));
        }

        return stocks;
    }

    @Override
    public double getPrice(String symbol) {

        List<PricePoint> prices = stockData.get(symbol);

        if (prices == null) {
            throw new IllegalArgumentException(
                    "Unknown stock: " + symbol);
        }

        return prices.get(currentIndex).price();
    }

    @Override
    public List<PricePoint> getHistory(String symbol, int points) {

        List<PricePoint> prices = stockData.get(symbol);

        if (prices == null) {
            throw new IllegalArgumentException(
                    "Unknown stock: " + symbol);
        }

        if (points <= 0) {
            return new ArrayList<>();
        }

        int endIndex = currentIndex + 1;
        int startIndex = Math.max(0, endIndex - points);

        return new ArrayList<>(
                prices.subList(startIndex, endIndex)
        );
    }

    @Override
    public void tick() {

        int lastIndex = Integer.MAX_VALUE;

        for (List<PricePoint> prices : stockData.values()) {
            lastIndex = Math.min(lastIndex, prices.size() - 1);
        }

        if (currentIndex < lastIndex) {
            currentIndex++;
        }
    }
}
