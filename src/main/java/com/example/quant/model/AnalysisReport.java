package com.example.quant.model;

import java.util.List;

/**
 * 一次完整分析的汇总报告：优化策略与固定策略的回测结果对比 + 滚动窗口记录。
 */
public class AnalysisReport {

    private final BacktestResult optimized;
    private final BacktestResult fixed;
    private final List<WindowResult> windows;
    private final List<KLine> outOfSampleKlines;   // 样本外行情（用于价格图与买卖点）
    private final StrategyConfig config;
    private final String dataName;

    public AnalysisReport(BacktestResult optimized, BacktestResult fixed, List<WindowResult> windows,
                          List<KLine> outOfSampleKlines, StrategyConfig config, String dataName) {
        this.optimized = optimized;
        this.fixed = fixed;
        this.windows = windows;
        this.outOfSampleKlines = outOfSampleKlines;
        this.config = config;
        this.dataName = dataName;
    }

    public BacktestResult getOptimized() { return optimized; }
    public BacktestResult getFixed() { return fixed; }
    public List<WindowResult> getWindows() { return windows; }
    public List<KLine> getOutOfSampleKlines() { return outOfSampleKlines; }
    public StrategyConfig getConfig() { return config; }
    public String getDataName() { return dataName; }
}
