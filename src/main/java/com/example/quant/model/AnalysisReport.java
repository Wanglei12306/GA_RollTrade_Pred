package com.example.quant.model;

import java.util.List;

/**
 * 一次完整分析的汇总报告：优化策略与固定策略的回测结果对比 + 滚动窗口记录 +
 * 多指标信号曲线 + 年度收益统计。
 */
public class AnalysisReport {

    private final BacktestResult optimized;
    private final BacktestResult fixed;
    private final List<WindowResult> windows;
    private final List<KLine> outOfSampleKlines;   // 样本外行情（用于价格图与买卖点）
    private final StrategyConfig config;
    private final String dataName;
    private final List<IndicatorCurve> indicatorCurves;   // 多指标信号曲线（样本外）
    private final List<AnnualReturn> annualReturns;       // 年度收益统计
    private final Double directionalAccuracy;   // 辅助：方向预测准确率 [0,1]，无可标注样本为 null
    private final Double commitmentRate;        // 辅助：表态率 [0,1]

    public AnalysisReport(BacktestResult optimized, BacktestResult fixed, List<WindowResult> windows,
                          List<KLine> outOfSampleKlines, StrategyConfig config, String dataName,
                          List<IndicatorCurve> indicatorCurves, List<AnnualReturn> annualReturns,
                          Double directionalAccuracy, Double commitmentRate) {
        this.optimized = optimized;
        this.fixed = fixed;
        this.windows = windows;
        this.outOfSampleKlines = outOfSampleKlines;
        this.config = config;
        this.dataName = dataName;
        this.indicatorCurves = indicatorCurves;
        this.annualReturns = annualReturns;
        this.directionalAccuracy = directionalAccuracy;
        this.commitmentRate = commitmentRate;
    }

    public BacktestResult getOptimized() { return optimized; }
    public BacktestResult getFixed() { return fixed; }
    public List<WindowResult> getWindows() { return windows; }
    public List<KLine> getOutOfSampleKlines() { return outOfSampleKlines; }
    public StrategyConfig getConfig() { return config; }
    public String getDataName() { return dataName; }
    public List<IndicatorCurve> getIndicatorCurves() { return indicatorCurves; }
    public List<AnnualReturn> getAnnualReturns() { return annualReturns; }
    public Double getDirectionalAccuracy() { return directionalAccuracy; }
    public Double getCommitmentRate() { return commitmentRate; }

    /** 多指标信号曲线：指标名 + 样本外每根 K 线的信号评分（[-1,1]）。 */
    public record IndicatorCurve(String name, double[] scores) {}

    /** 年度收益统计：年份 + 该年收益率。 */
    public record AnnualReturn(int year, double returnRate) {}
}
