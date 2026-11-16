
package com.marketlab.service;

import com.marketlab.model.Holding;

import java.util.List;

public class ServiceTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {

        test("1. Buy quantity zero", () -> {
            PortfolioServiceImpl p = createPortfolio();
            checkUnchanged(p, () ->
                    expectException(
                            IllegalArgumentException.class,
                            () -> p.buy("RELIANCE", 0)
                    )
            );
        });

        test("2. Buy negative quantity", () -> {
            PortfolioServiceImpl p = createPortfolio();
            checkUnchanged(p, () ->
                    expectException(
                            IllegalArgumentException.class,
                            () -> p.buy("RELIANCE", -5)
                    )
            );
        });

        test("3. Sell zero or negative quantity", () -> {
            PortfolioServiceImpl p = createPortfolio();
            p.buy("RELIANCE", 5);

            checkUnchanged(p, () ->
                    expectException(
                            IllegalArgumentException.class,
                            () -> p.sell("RELIANCE", 0)
                    )
            );

            checkUnchanged(p, () ->
                    expectException(
                            IllegalArgumentException.class,
                            () -> p.sell("RELIANCE", -5)
                    )
            );
        });

        test("4. Buy unknown stock", () -> {
            PortfolioServiceImpl p = createPortfolio();

            checkUnchanged(p, () ->
                    expectException(
                            IllegalArgumentException.class,
                            () -> p.buy("FAKE", 5)
                    )
            );
        });

        test("5. Sell unknown stock", () -> {
            PortfolioServiceImpl p = createPortfolio();

            checkUnchanged(p, () ->
                    expectException(
                            InsufficientSharesException.class,
                            () -> p.sell("FAKE", 5)
                    )
            );
        });

        test("6. Buy more than available cash", () -> {
            PortfolioServiceImpl p = createPortfolio();

            checkUnchanged(p, () ->
                    expectException(
                            InsufficientFundsException.class,
                            () -> p.buy("RELIANCE", 1000)
                    )
            );
        });

        test("7. Buy exactly what cash allows", () -> {
            CsvPriceService price = new CsvPriceService();
            double exact = price.getPrice("RELIANCE") * 10;
            PortfolioServiceImpl p = new PortfolioServiceImpl(price, exact);

            p.buy("RELIANCE", 10);

            check(Math.abs(p.getCash()) < 0.001, "Cash should be zero");
            check(p.getHoldings().size() == 1, "Holding should exist");
            check(p.getHoldings().getFirst().quantity() == 10, "Quantity should be 10");
        });

        test("8. Sell stock not owned", () -> {
            PortfolioServiceImpl p = createPortfolio();

            checkUnchanged(p, () ->
                    expectException(
                            InsufficientSharesException.class,
                            () -> p.sell("RELIANCE", 5)
                    )
            );
        });

        test("9. Sell more shares than owned", () -> {
            PortfolioServiceImpl p = createPortfolio();
            p.buy("RELIANCE", 5);

            checkUnchanged(p, () ->
                    expectException(
                            InsufficientSharesException.class,
                            () -> p.sell("RELIANCE", 10)
                    )
            );
        });

        test("10. Sell all shares", () -> {
            PortfolioServiceImpl p = createPortfolio();

            p.buy("RELIANCE", 5);
            p.sell("RELIANCE", 5);

            check(p.getHoldings().isEmpty(),
                    "Holding should be removed");
        });

        test("11. Buy twice at different prices", () -> {
            CsvPriceService price = new CsvPriceService();
            PortfolioServiceImpl p = new PortfolioServiceImpl(price);

            double p0 = price.getPrice("RELIANCE");
            p.buy("RELIANCE", 2);
            price.tick();
            double p1 = price.getPrice("RELIANCE");
            p.buy("RELIANCE", 2);

            double expected = (p0 + p1) / 2;   // equal quantities, so a simple average
            double average = p.getHoldings().get(0).avgBuyPrice();

            check(Math.abs(average - expected) < 0.001, "Average price should be " + expected);
        });

        test("12. Sell part of a holding", () -> {
            CsvPriceService price = new CsvPriceService();
            PortfolioServiceImpl p =
                    new PortfolioServiceImpl(price);

            p.buy("RELIANCE", 10);

            double oldAverage =
                    p.getHoldings().get(0).avgBuyPrice();

            p.sell("RELIANCE", 5);

            double newAverage =
                    p.getHoldings().get(0).avgBuyPrice();

            check(oldAverage == newAverage,
                    "Average price should remain unchanged");

            check(p.getHoldings().get(0).quantity() == 5,
                    "Five shares should remain");
        });

        test("13. P&L after price tick", () -> {
            CsvPriceService price = new CsvPriceService();
            PortfolioServiceImpl p = new PortfolioServiceImpl(price);

            double p0 = price.getPrice("RELIANCE");
            p.buy("RELIANCE", 10);
            price.tick();
            double p1 = price.getPrice("RELIANCE");

            double expectedPnL = (p1 - p0) * 10;
            check(Math.abs(p.getTotalPnL() - expectedPnL) < 0.001, "P&L should equal " + expectedPnL);
        });

        test("14. Failed operations preserve portfolio", () -> {
            PortfolioServiceImpl p = createPortfolio();

            p.buy("RELIANCE", 5);

            checkUnchanged(p, () -> {
                expectException(
                        IllegalArgumentException.class,
                        () -> p.buy("RELIANCE", 0)
                );

                expectException(
                        IllegalArgumentException.class,
                        () -> p.buy("FAKE", 5)
                );

                expectException(
                        InsufficientSharesException.class,
                        () -> p.sell("RELIANCE", 10)
                );
            });
        });
        System.out.println("\n========== TEST SUMMARY ==========");
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);
        System.out.println("==================================");
    }

    // Creates a fresh portfolio for a test
    private static PortfolioServiceImpl createPortfolio() {
        return new PortfolioServiceImpl(
                new CsvPriceService()
        );
    }

    // Runs one test and reports its result
    private static void test(String name, Runnable action) {
        try {
            action.run();
            passed++;
            System.out.println("PASS: " + name);
        } catch (Throwable e) {
            failed++;
            System.out.println("FAIL: " + name);
            System.out.println("Reason: " + e.getMessage());
        }
    }

    // Checks whether the expected exception is thrown
    private static void expectException(
            Class<? extends Throwable> expected,
            Runnable action) {

        try {
            action.run();
        } catch (Throwable e) {
            if (expected.isInstance(e)) {
                return;
            }

            throw new AssertionError(
                    "Expected " + expected.getSimpleName()
                            + " but got " + e.getClass().getSimpleName()
            );
        }

        throw new AssertionError(
                "Expected " + expected.getSimpleName()
                        + " but no exception was thrown"
        );
    }

    // Checks that a condition is true
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    // Checks that failed operations do not change cash or holdings
    private static void checkUnchanged(
            PortfolioServiceImpl p,
            Runnable operation) {

        double oldCash = p.getCash();
        List<Holding> oldHoldings = p.getHoldings();

        operation.run();

        check(Math.abs(p.getCash() - oldCash) < 0.001,
                "Cash changed after failed operation");

        check(p.getHoldings().equals(oldHoldings),
                "Holdings changed after failed operation");
    }
}
