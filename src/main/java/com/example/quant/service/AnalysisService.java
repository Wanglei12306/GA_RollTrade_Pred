package com.example.quant.service;

import com.example.quant.backtest.BacktestEngine;
import com.example.quant.indicator.IndicatorPool;
import com.example.quant.model.AnalysisReport;
import com.example.quant.model.BacktestResult;
import com.example.quant.model.Signal;
import com.example.quant.model.StrategyConfig;
import com.example.quant.rolling.RollingWindowService;
import com.example.quant.strategy.FixedStrategy;
import com.example.quant.strategy.SignalGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 分析编排服务：滚动训练预测 → 优化策略回测 + 固定策略回测 → 汇总报告。
 */
@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    private final DataService dataService;
    private final IndicatorPool indicatorPool;
    private final RollingWindowService rollingWindowService;
    private final SignalGenerator signalGenerator;
    private final FixedStrategy fixedStrategy;
    private final BacktestEngine backtestEngine;

    private AnalysisReport lastReport;

    public AnalysisService(DataService dataService, IndicatorPool indicatorPool,
                           RollingWindowService rollingWindowService,
                           SignalGenerator signalGenerator, FixedStrategy fixedStrategy,
                           BacktestEngine backtestEngine) {
        this.dataService = dataService;
        this.indicatorPool = indicatorPool;
        this.rollingWindowService = rollingWindowService;
        this.signalGenerator = signalGenerator;
        this.fixedStrategy = fixedStrategy;
        this.backtestEngine = backtestEngine;
    }

    public AnalysisReport run(StrategyConfig config) {
        if (!dataService.hasData()) {
            throw new IllegalStateException("请先上传或加载行情数据");
        }
        var klines = dataService.getKlines();
        var indicators = indicatorPool.selected(config.getIndicators());

        log.info("开始滚动训练预测：{} 根 K 线，{} 个指标", klines.size(), indicators.size());
        var rolling = rollingWindowService.run(klines, indicators, config);
        List<com.example.quant.model.KLine> outKlines = rolling.outOfSampleKlines();
        Signal[] optimizedSignals = rolling.optimizedSignals();
        log.info("样本外预测信号 {} 根，共 {} 个滚动窗口", outKlines.size(), rolling.windows().size());

        // 固定策略：在同一批样本外 K 线上用固定染色体生成信号
        double[][][] outCache = signalGenerator.precomputeScores(indicators, outKlines);
        Signal[] fixedSignals = fixedStrategy.generate(indicators, outCache, signalGenerator);

        BacktestResult optimized = backtestEngine.run(outKlines, optimizedSignals, "optimized",
                config.getInitialCapital(), config.getCommissionRate());
        BacktestResult fixed = backtestEngine.run(outKlines, fixedSignals, "fixed",
                config.getInitialCapital(), config.getCommissionRate());

        log.info("回测完成：优化策略累计收益={}, 固定策略累计收益={}",
                String.format("%.2f%%", optimized.getMetrics().getCumulativeReturn() * 100),
                String.format("%.2f%%", fixed.getMetrics().getCumulativeReturn() * 100));

        lastReport = new AnalysisReport(optimized, fixed, rolling.windows(),
                outKlines, config, dataService.getDataName());
        return lastReport;
    }

    public AnalysisReport getLastReport() {
        return lastReport;
    }
}
