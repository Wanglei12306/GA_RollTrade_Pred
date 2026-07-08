package com.example.quant.backtest;

import com.example.quant.model.BacktestResult;
import com.example.quant.model.KLine;
import com.example.quant.model.Signal;
import com.example.quant.model.TradeRecord;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 回测引擎（仅做多）。
 * <p>资金守恒：任一时刻 equity = cash + shares * close。
 * BUY 满仓进场（收盘价），SELL 全部平仓。手续费 = 费率 × 成交额。
 */
@Component
public class BacktestEngine {

    private final MetricsCalculator metricsCalculator;

    public BacktestEngine(MetricsCalculator metricsCalculator) {
        this.metricsCalculator = metricsCalculator;
    }

    /**
     * 在 klines 上按 signals 执行回测。
     *
     * @param klines         行情序列（含完整区间，含预测窗口前后）
     * @param signals        与 klines 等长的信号序列
     * @param name           结果名（optimized / fixed）
     * @param initialCapital 初始资金
     * @param commissionRate 手续费率
     */
    public BacktestResult run(List<KLine> klines, Signal[] signals, String name,
                              double initialCapital, double commissionRate) {
        int n = klines.size();
        double cash = initialCapital;
        double shares = 0;
        double entryPrice = 0;
        double entryCommission = 0;

        double[] equity = new double[n];
        List<TradeRecord> trades = new ArrayList<>();
        List<BacktestResult.SignalPoint> signalPoints = new ArrayList<>();
        double[] closes = new double[n];

        for (int i = 0; i < n; i++) {
            KLine bar = klines.get(i);
            double price = bar.getClose();
            closes[i] = price;
            Signal sig = (i < signals.length) ? signals[i] : Signal.HOLD;

            if (sig == Signal.BUY && shares == 0 && cash > 0) {
                double notional = cash;
                double commission = notional * commissionRate;
                shares = (notional - commission) / price;
                entryPrice = price;
                entryCommission = commission;
                cash = 0;
                trades.add(new TradeRecord(bar.getDate(), "BUY", price, shares, commission, 0, shares));
                signalPoints.add(new BacktestResult.SignalPoint(bar.getDate(), "BUY", price));
            } else if (sig == Signal.SELL && shares > 0) {
                double notional = shares * price;
                double commission = notional * commissionRate;
                double realized = shares * (price - entryPrice) - entryCommission - commission;
                cash = notional - commission;
                trades.add(new TradeRecord(bar.getDate(), "SELL", price, shares, commission, realized, 0));
                signalPoints.add(new BacktestResult.SignalPoint(bar.getDate(), "SELL", price));
                shares = 0;
                entryPrice = 0;
                entryCommission = 0;
            }

            equity[i] = cash + shares * price;
        }

        double[] drawdown = drawdownCurve(equity);
        List<java.time.LocalDate> dates = klines.stream().map(KLine::getDate).toList();
        var metrics = metricsCalculator.compute(dates, equity, trades, closes);
        return new BacktestResult(name, metrics, dates, equity, drawdown, trades, signalPoints);
    }

    private double[] drawdownCurve(double[] equity) {
        double[] dd = new double[equity.length];
        double peak = equity[0];
        for (int i = 0; i < equity.length; i++) {
            if (equity[i] > peak) peak = equity[i];
            dd[i] = peak > 0 ? (peak - equity[i]) / peak : 0;
        }
        return dd;
    }
}
