package com.marketlab.service;

import com.marketlab.model.PricePoint;
import com.marketlab.model.Stock;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CsvPriceService implements PriceService {

    private static final List<String> SYMBOLS =
            List.of("RELIANCE", "TCS", "INFY", "HDFCBANK", "ITC");

    private final Map<String, List<PricePoint>> data = new LinkedHashMap<>();
    private final int maxIndex;
    private int index;

    public CsvPriceService() {
        int minSize = Integer.MAX_VALUE;
        for (String s : SYMBOLS) {
            List<PricePoint> points = load(s);
            data.put(s, points);
            minSize = Math.min(minSize, points.size());
        }
        maxIndex = minSize - 1;
        index = Math.min(50, maxIndex); // start with some history available
    }

    private List<PricePoint> load(String symbol) {
        String path = "/data/" + symbol + ".csv";
        InputStream in = getClass().getResourceAsStream(path);
        if (in == null) throw new IllegalStateException("Missing file: " + path);

        List<PricePoint> points = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split(",");
                try {
                    LocalDate date = LocalDate.parse(p[0].trim());
                    double close = Double.parseDouble(p[1].trim());
                    points.add(new PricePoint(date.atStartOfDay(), close));
                } catch (Exception ignored) {
                    // header or malformed row, skip
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read " + path, e);
        }
        return points;
    }

    @Override
    public List<Stock> getAllStocks() {
        List<Stock> list = new ArrayList<>();
        for (String s : SYMBOLS) list.add(new Stock(s, s, getPrice(s)));
        return list;
    }

    @Override
    public double getPrice(String symbol) {
        List<PricePoint> points = data.get(symbol);
        if (points == null) throw new IllegalArgumentException("Unknown symbol: " + symbol);
        return points.get(index).price();
    }

    @Override
    public List<PricePoint> getHistory(String symbol, int count) {
        List<PricePoint> points = data.get(symbol);
        if (points == null) throw new IllegalArgumentException("Unknown symbol: " + symbol);
        int from = Math.max(0, index - count + 1);
        return new ArrayList<>(points.subList(from, index + 1));
    }

    @Override
    public void tick() {
        if (index < maxIndex) index++;
    }
}