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
 * 风控接口在信号产生后的下一根 K 线开盘成交，SELL 全部平仓。手续费 = 费率 × 成交额。
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
        // 保留旧接口语义，供已有调用方和单元测试使用；新业务路径使用下方风控重载。
        return run(klines, signals, name, initialCapital, commissionRate,
                1.0, 0.0, 0, 0, 0.0, 0, 0, false, 0);
    }

    /**
     * 带基础风控的回测：限制单标的资金占比、固定比例止损、最长持仓根数，
     * 并在区间结束时平掉剩余仓位，避免未实现盈亏和交易统计口径不一致。
     */
    public BacktestResult run(List<KLine> klines, Signal[] signals, String name,
                              double initialCapital, double commissionRate,
                              double maxPositionRatio, double stopLossRatio,
                              int maxHoldingBars) {
        return run(klines, signals, name, initialCapital, commissionRate,
                maxPositionRatio, stopLossRatio, maxHoldingBars, 0, 0.0, 0, 0, true, 1);
    }

    /** 带最短持仓限制的风控回测；止损和最长持仓不受最短持仓限制。 */
    public BacktestResult run(List<KLine> klines, Signal[] signals, String name,
                              double initialCapital, double commissionRate,
                              double maxPositionRatio, double stopLossRatio,
                              int maxHoldingBars, int minHoldingBars) {
        return run(klines, signals, name, initialCapital, commissionRate,
                maxPositionRatio, stopLossRatio, maxHoldingBars, minHoldingBars, 0.0, 0, 0, true, 1);
    }

    /** 带最大回撤风险预算的回测；达到峰值回撤阈值时强制降低风险敞口。 */
    public BacktestResult run(List<KLine> klines, Signal[] signals, String name,
                              double initialCapital, double commissionRate,
                              double maxPositionRatio, double stopLossRatio,
                              int maxHoldingBars, int minHoldingBars,
                              double maxDrawdownLimit) {
        return run(klines, signals, name, initialCapital, commissionRate,
                maxPositionRatio, stopLossRatio, maxHoldingBars, minHoldingBars,
                maxDrawdownLimit, 0, 0, true, 1);
    }

    /** 带长期趋势过滤的回测；收盘价低于长期均线时禁止新增长仓。 */
    public BacktestResult run(List<KLine> klines, Signal[] signals, String name,
                              double initialCapital, double commissionRate,
                              double maxPositionRatio, double stopLossRatio,
                              int maxHoldingBars, int minHoldingBars,
                              double maxDrawdownLimit, int trendFilterBars) {
        return run(klines, signals, name, initialCapital, commissionRate,
                maxPositionRatio, stopLossRatio, maxHoldingBars, minHoldingBars,
                maxDrawdownLimit, trendFilterBars, 0, true, 1);
    }

    /**
     * 带回撤冷却期的风控回测：风险退出后暂停开仓若干根 K 线，
     * 冷却结束后以当前净值重新锚定风险峰值，避免一次早期止损永久错过后续行情。
     * {@code drawdownCooldownBars=0} 保持永久锁定的严格风险预算语义。
     */
    public BacktestResult run(List<KLine> klines, Signal[] signals, String name,
                              double initialCapital, double commissionRate,
                              double maxPositionRatio, double stopLossRatio,
                              int maxHoldingBars, int minHoldingBars,
                              double maxDrawdownLimit, int trendFilterBars,
                              int drawdownCooldownBars) {
        return run(klines, signals, name, initialCapital, commissionRate,
                maxPositionRatio, stopLossRatio, maxHoldingBars, minHoldingBars,
                maxDrawdownLimit, trendFilterBars, drawdownCooldownBars, true, 1);
    }

    private BacktestResult run(List<KLine> klines, Signal[] signals, String name,
                               double initialCapital, double commissionRate,
                               double maxPositionRatio, double stopLossRatio,
                               int maxHoldingBars, int minHoldingBars, double maxDrawdownLimit,
                               int trendFilterBars,
                               int drawdownCooldownBars,
                               boolean forceCloseAtEnd,
                               int executionLagBars) {
        int n = klines.size();
        if (n == 0) {
            return new BacktestResult(name,
                    metricsCalculator.compute(List.of(), new double[0], List.of(), new double[0], initialCapital),
                    List.of(), new double[0], new double[0], List.of(), List.of());
        }
        double positionRatio = clamp(maxPositionRatio, 0.0, 1.0);
        double stopRatio = clamp(stopLossRatio, 0.0, 1.0);
        double drawdownLimit = clamp(maxDrawdownLimit, 0.0, 1.0);
        int cooldownBars = Math.max(0, drawdownCooldownBars);
        int trendBars = Math.max(0, trendFilterBars);
        double feeRate = clamp(commissionRate, 0.0, 1.0);
        int minHold = Math.max(0, minHoldingBars);
        double cash = initialCapital;
        double shares = 0;
        double entryPrice = 0;
        double entryCommission = 0;
        int entryBar = -1;
        double peakEquity = initialCapital;
        boolean drawdownHalted = false;
        int cooldownRemaining = 0;

        double[] equity = new double[n];
        List<TradeRecord> trades = new ArrayList<>();
        List<BacktestResult.SignalPoint> signalPoints = new ArrayList<>();
        double[] closes = new double[n];

        for (int i = 0; i < n; i++) {
            KLine bar = klines.get(i);
            double price = bar.getClose();
            closes[i] = price;
            // 冷却结束后允许重新交易，并从当前现金净值重新计算本轮风险预算。
            if (drawdownHalted && shares == 0 && cooldownRemaining > 0) {
                cooldownRemaining--;
            }
            if (drawdownHalted && shares == 0 && cooldownRemaining == 0 && cooldownBars > 0) {
                drawdownHalted = false;
                peakEquity = Math.max(cash, 0.0);
            }
            // 指标使用当前 bar 收盘价计算，最早下一根 bar 才允许成交，避免同收盘价前视。
            int signalIndex = i - Math.max(0, executionLagBars);
            Signal sig = (signalIndex >= 0 && signalIndex < signals.length)
                    ? signals[signalIndex] : Signal.HOLD;
            // 新风控接口按下一根开盘成交；旧五参数接口保持原有的收盘成交语义。
            double tradePrice = executionLagBars > 0 && bar.getOpen() > 0 ? bar.getOpen() : price;

            double stopPrice = entryPrice * (1.0 - stopRatio);
            boolean stopExit = shares > 0 && stopRatio > 0
                    && (price <= stopPrice || (bar.getLow() > 0 && bar.getLow() <= stopPrice));
            boolean timeExit = shares > 0 && maxHoldingBars > 0 && i - entryBar >= maxHoldingBars;
            // 用当日最低价估算盘中净值，达到风险预算时退出；交易价仍遵循开盘/止损成交规则。
            double lowEquity = shares > 0 && bar.getLow() > 0 ? cash + shares * bar.getLow() : cash + shares * price;
            boolean drawdownExit = shares > 0 && drawdownLimit > 0 && peakEquity > 0
                    && lowEquity <= peakEquity * (1.0 - drawdownLimit);
            boolean riskExit = stopExit || timeExit || drawdownExit;
            // 风险预算触发后先暂停开仓；冷却结束再以当前净值重置本轮峰值，
            // 避免在同一风险事件下反复损耗，也避免一次早期止损永久错过后续行情。
            if (drawdownExit) {
                drawdownHalted = true;
                cooldownRemaining = cooldownBars;
            }

            if (sig == Signal.BUY && !drawdownHalted && trendAllowsBuy(klines, i, trendBars)
                    && shares == 0 && cash > 0 && positionRatio > 0) {
                double notional = cash * positionRatio;
                double commission = notional * feeRate;
                shares = (notional - commission) / tradePrice;
                entryPrice = tradePrice;
                entryCommission = commission;
                entryBar = i;
                cash -= notional;
                trades.add(new TradeRecord(bar.getDate(), "BUY", tradePrice, shares, commission, 0, shares));
                signalPoints.add(new BacktestResult.SignalPoint(bar.getDate(), "BUY", tradePrice));
            } else if ((riskExit || (sig == Signal.SELL && i - entryBar >= minHold)) && shares > 0) {
                // 止损在盘中触发时按止损价成交；若开盘已跳空跌破止损线，则按开盘价成交。
                double drawdownStopPrice = shares > 0
                        ? (peakEquity * (1.0 - drawdownLimit) - cash) / shares : 0;
                double exitPrice = stopExit ? Math.min(tradePrice, stopPrice)
                        : (drawdownExit && drawdownStopPrice > 0
                        ? Math.min(tradePrice, drawdownStopPrice) : tradePrice);
                double notional = shares * exitPrice;
                double commission = notional * feeRate;
                double realized = shares * (exitPrice - entryPrice) - entryCommission - commission;
                cash += notional - commission;
                trades.add(new TradeRecord(bar.getDate(), "SELL", exitPrice, shares, commission, realized, 0));
                signalPoints.add(new BacktestResult.SignalPoint(bar.getDate(), "SELL", exitPrice));
                shares = 0;
                entryPrice = 0;
                entryCommission = 0;
                entryBar = -1;
            }

            equity[i] = cash + shares * price;
            if (equity[i] > peakEquity) peakEquity = equity[i];
        }

        // 期末不保留悬空仓位：最终净值仍按最后收盘价计价，同时补齐交易统计。
        if (forceCloseAtEnd && shares > 0) {
            KLine last = klines.get(n - 1);
            double price = last.getClose();
            double notional = shares * price;
            double commission = notional * feeRate;
            double realized = shares * (price - entryPrice) - entryCommission - commission;
            cash += notional - commission;
            trades.add(new TradeRecord(last.getDate(), "SELL", price, shares, commission, realized, 0));
            signalPoints.add(new BacktestResult.SignalPoint(last.getDate(), "SELL", price));
            shares = 0;
            equity[n - 1] = cash;
        }

        double[] drawdown = drawdownCurve(equity);
        List<java.time.LocalDate> dates = klines.stream().map(KLine::getDate).toList();
        var metrics = metricsCalculator.compute(dates, equity, trades, closes, initialCapital);
        return new BacktestResult(name, metrics, dates, equity, drawdown, trades, signalPoints);
    }

    private double[] drawdownCurve(double[] equity) {
        double[] dd = new double[equity.length];
        if (equity.length == 0) return dd;
        double peak = equity[0];
        for (int i = 0; i < equity.length; i++) {
            if (equity[i] > peak) peak = equity[i];
            dd[i] = peak > 0 ? (peak - equity[i]) / peak : 0;
        }
        return dd;
    }

    /** 趋势过滤只使用入场前的收盘价，且短样本自动关闭，避免训练窗口无交易。 */
    private boolean trendAllowsBuy(List<KLine> klines, int barIndex, int period) {
        if (period <= 0 || klines.size() < period * 3) return true;
        if (barIndex < period) return false;
        double sum = 0;
        for (int i = barIndex - period; i < barIndex; i++) sum += klines.get(i).getClose();
        double lastClose = klines.get(barIndex - 1).getClose();
        return Double.isFinite(lastClose) && lastClose >= sum / period;
    }

    private static double clamp(double value, double lo, double hi) {
        return Math.max(lo, Math.min(hi, value));
    }
}
