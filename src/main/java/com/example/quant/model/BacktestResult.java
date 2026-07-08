package com.example.quant.model;

import java.time.LocalDate;
import java.util.List;

/**
 * 一次回测的完整结果。
 */
public class BacktestResult {

    private final String name;                       // optimized / fixed / buyHold
    private final PerformanceMetrics metrics;
    private final List<LocalDate> dates;             // 资金曲线日期轴
    private final double[] equityCurve;              // 资金净值曲线
    private final double[] drawdownCurve;            // 回撤曲线（正值，0 表示无回撤）
    private final List<TradeRecord> trades;          // 逐笔交易
    private final List<SignalPoint> signals;         // 买卖信号点

    public BacktestResult(String name, PerformanceMetrics metrics, List<LocalDate> dates,
                          double[] equityCurve, double[] drawdownCurve,
                          List<TradeRecord> trades, List<SignalPoint> signals) {
        this.name = name;
        this.metrics = metrics;
        this.dates = dates;
        this.equityCurve = equityCurve;
        this.drawdownCurve = drawdownCurve;
        this.trades = trades;
        this.signals = signals;
    }

    public String getName() { return name; }
    public PerformanceMetrics getMetrics() { return metrics; }
    public List<LocalDate> getDates() { return dates; }
    public double[] getEquityCurve() { return equityCurve; }
    public double[] getDrawdownCurve() { return drawdownCurve; }
    public List<TradeRecord> getTrades() { return trades; }
    public List<SignalPoint> getSignals() { return signals; }

    /** 信号点：日期 + 信号类型，用于图表标记。 */
    public static class SignalPoint {
        private final LocalDate date;
        private final String signal;   // BUY / SELL
        private final double price;

        public SignalPoint(LocalDate date, String signal, double price) {
            this.date = date;
            this.signal = signal;
            this.price = price;
        }
        public LocalDate getDate() { return date; }
        public String getSignal() { return signal; }
        public double getPrice() { return price; }
    }
}
