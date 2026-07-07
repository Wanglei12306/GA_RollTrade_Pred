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

    private static final int TRADING_DAYS_PER_YEAR = 252;

    public PerformanceMetrics compute(List<LocalDate> dates, double[] equity,
                                      List<TradeRecord> trades, double[] closes) {
        int n = equity.length;
        double cumulative = n > 0 ? equity[n - 1] / equity[0] - 1 : 0;
        double annual = annualReturn(equity);
        double maxDd = maxDrawdown(equity);
        double sharpe = sharpe(equity);
        double buyHold = (closes != null && closes.length > 1) ? closes[closes.length - 1] / closes[0] - 1 : 0;

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

        return new PerformanceMetrics(cumulative, annual, maxDd, sharpe,
                winRate, profitLossRatio, completeTrades, buyHold);
    }

    private double annualReturn(double[] equity) {
        if (equity.length < 2 || equity[0] <= 0) return 0;
        double total = equity[equity.length - 1] / equity[0];
        if (total <= 0) return -1;
        double years = (double) equity.length / TRADING_DAYS_PER_YEAR;
        if (years <= 0) return 0;
        return Math.pow(total, 1.0 / years) - 1;
    }

    private double maxDrawdown(double[] equity) {
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

    private double sharpe(double[] equity) {
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
        double std = Math.sqrt(var / rets.length);
        if (std == 0) return 0;
        return mean / std * Math.sqrt(TRADING_DAYS_PER_YEAR);
    }
}
