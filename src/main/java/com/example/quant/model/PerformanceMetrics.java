package com.example.quant.model;

/**
 * 回测绩效指标。
 */
public class PerformanceMetrics {

    private final double cumulativeReturn;   // 累计收益率
    private final double annualReturn;       // 年化收益率
    private final double maxDrawdown;        // 最大回撤（正值表示幅度）
    private final int maxDrawdownDuration;   // 最大回撤持续根数
    private final double sharpe;             // 夏普比率
    private final double winRate;            // 胜率
    private final double profitLossRatio;    // 盈亏比
    private final int tradeCount;            // 完整交易次数
    private final double buyHoldReturn;      // 买入持有基准收益

    public PerformanceMetrics(double cumulativeReturn, double annualReturn, double maxDrawdown,
                              double sharpe, double winRate, double profitLossRatio,
                              int tradeCount, double buyHoldReturn) {
        this(cumulativeReturn, annualReturn, maxDrawdown, 0, sharpe, winRate,
                profitLossRatio, tradeCount, buyHoldReturn);
    }

    public PerformanceMetrics(double cumulativeReturn, double annualReturn, double maxDrawdown,
                              int maxDrawdownDuration, double sharpe, double winRate,
                              double profitLossRatio, int tradeCount, double buyHoldReturn) {
        this.cumulativeReturn = cumulativeReturn;
        this.annualReturn = annualReturn;
        this.maxDrawdown = maxDrawdown;
        this.maxDrawdownDuration = maxDrawdownDuration;
        this.sharpe = sharpe;
        this.winRate = winRate;
        this.profitLossRatio = profitLossRatio;
        this.tradeCount = tradeCount;
        this.buyHoldReturn = buyHoldReturn;
    }

    public double getCumulativeReturn() { return cumulativeReturn; }
    public double getAnnualReturn() { return annualReturn; }
    public double getMaxDrawdown() { return maxDrawdown; }
    public int getMaxDrawdownDuration() { return maxDrawdownDuration; }
    public double getSharpe() { return sharpe; }
    public double getWinRate() { return winRate; }
    public double getProfitLossRatio() { return profitLossRatio; }
    public int getTradeCount() { return tradeCount; }
    public double getBuyHoldReturn() { return buyHoldReturn; }
}
