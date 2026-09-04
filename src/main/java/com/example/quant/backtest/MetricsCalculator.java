package com.example.quant.backtest;

import com.example.quant.model.PerformanceMetrics;
import com.example.quant.model.TradeRecord;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 绩效指标计算器。由资金曲线与交易记录推导收益、风险、稳定性指标。
 */
@Component
public class MetricsCalculator {

    private static final double DAYS_PER_YEAR = 365.25;

    public PerformanceMetrics compute(List<LocalDate> dates, double[] equity,
                                      List<TradeRecord> trades, double[] closes) {
        double baseline = equity.length > 0 ? equity[0] : 0;
        return compute(dates, equity, trades, closes, baseline);
    }

    /** 使用真实初始资金计算收益，避免首根交易手续费改变收益率分母。 */
    public PerformanceMetrics compute(List<LocalDate> dates, double[] equity,
                                      List<TradeRecord> trades, double[] closes,
                                      double initialCapital) {
        int n = equity.length;
        double baseline = initialCapital > 0 ? initialCapital : (n > 0 ? equity[0] : 0);
        double cumulative = n > 0 && baseline > 0 ? equity[n - 1] / baseline - 1 : 0;
        double periodsPerYear = periodsPerYear(dates);
        double annual = annualReturn(dates, equity, baseline, periodsPerYear);
        double maxDd = maxDrawdown(equity);
        int maxDdDuration = maxDrawdownDuration(equity);
        double sharpe = sharpe(dates, equity, periodsPerYear);
        double buyHold = (closes != null && closes.length > 1 && closes[0] > 0)
                ? closes[closes.length - 1] / closes[0] - 1 : 0;

        // 仅以 SELL（完成一次开平配对）计为完整交易
        double wins = 0, losses = 0, winSum = 0, lossSum = 0;
        int completeTrades = 0;
        for (TradeRecord t : trades) {
            if ("SELL".equals(t.getDirection())) {
                completeTrades++;
                if (t.getPnl() > 0) {
                    wins++;
                    winSum += t.getPnl();
                } else if (t.getPnl() < 0) {
                    losses++;
                    lossSum += -t.getPnl();
                }
            }
        }
        double winRate = completeTrades > 0 ? wins / completeTrades : 0;
        double avgWin = wins > 0 ? winSum / wins : 0;
        double avgLoss = losses > 0 ? lossSum / losses : 0;
        double profitLossRatio = avgLoss > 0 ? avgWin / avgLoss : (avgWin > 0 ? 99.0 : 0);

        return new PerformanceMetrics(cumulative, annual, maxDd, maxDdDuration, sharpe,
                winRate, profitLossRatio, completeTrades, buyHold);
    }

    private double annualReturn(List<LocalDate> dates, double[] equity,
                                double baseline, double periodsPerYear) {
        if (equity.length < 2 || baseline <= 0) return 0;
        double total = equity[equity.length - 1] / baseline;
        if (total <= 0) return -1;
        double years = elapsedYears(dates);
        if (years <= 0) years = (double) (equity.length - 1) / periodsPerYear;
        if (years <= 0) return 0;
        return Math.pow(total, 1.0 / years) - 1;
    }

    private double maxDrawdown(double[] equity) {
        if (equity == null || equity.length == 0) return 0;
        double peak = equity[0];
        double maxDd = 0;
        for (double v : equity) {
            if (v > peak) peak = v;
            if (peak > 0) {
                double dd = (peak - v) / peak;
                if (dd > maxDd) maxDd = dd;
            }
        }
        return maxDd;
    }

    private int maxDrawdownDuration(double[] equity) {
        if (equity.length < 2) return 0;
        double peak = equity[0];
        int current = 0, longest = 0;
        for (double v : equity) {
            if (v >= peak) {
                peak = v;
                current = 0;
            } else {
                current++;
                longest = Math.max(longest, current);
            }
        }
        return longest;
    }

    private double sharpe(List<LocalDate> dates, double[] equity, double periodsPerYear) {
        if (equity.length < 3) return 0;
        double[] rets = new double[equity.length - 1];
        double sum = 0;
        for (int i = 1; i < equity.length; i++) {
            if (equity[i - 1] <= 0) {
                rets[i - 1] = 0;
            } else {
                rets[i - 1] = equity[i] / equity[i - 1] - 1;
            }
            sum += rets[i - 1];
        }
        double mean = sum / rets.length;
        double var = 0;
        for (double r : rets) var += (r - mean) * (r - mean);
        double std = Math.sqrt(var / Math.max(1, rets.length - 1));
        if (std < 1e-12) return 0;
        return mean / std * Math.sqrt(periodsPerYear);
    }

    private double periodsPerYear(List<LocalDate> dates) {
        if (dates == null || dates.size() < 2) return 252;
        long[] gaps = new long[dates.size() - 1];
        int count = 0;
        for (int i = 1; i < dates.size(); i++) {
            if (dates.get(i) == null || dates.get(i - 1) == null) continue;
            long gap = java.time.temporal.ChronoUnit.DAYS.between(dates.get(i - 1), dates.get(i));
            if (gap > 0) gaps[count++] = gap;
        }
        if (count == 0) return 252;
        java.util.Arrays.sort(gaps, 0, count);
        double medianDays = gaps[count / 2];
        if (medianDays <= 2) return 252;
        if (medianDays <= 10) return 52;
        if (medianDays <= 40) return 12;
        return Math.max(1, DAYS_PER_YEAR / medianDays);
    }

    private double elapsedYears(List<LocalDate> dates) {
        if (dates == null || dates.size() < 2 || dates.get(0) == null || dates.get(dates.size() - 1) == null) return 0;
        long days = java.time.temporal.ChronoUnit.DAYS.between(dates.get(0), dates.get(dates.size() - 1));
        return days > 0 ? days / DAYS_PER_YEAR : 0;
    }
}
