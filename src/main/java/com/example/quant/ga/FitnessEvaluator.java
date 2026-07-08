package com.example.quant.ga;

import com.example.quant.backtest.BacktestEngine;
import com.example.quant.indicator.Indicator;
import com.example.quant.model.Chromosome;
import com.example.quant.model.KLine;
import com.example.quant.model.PerformanceMetrics;
import com.example.quant.model.StrategyConfig;
import com.example.quant.strategy.SignalGenerator;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 适应度评估器。
 * <p>在训练窗口上对染色体生成信号并执行迷你回测，由绩效指标按可配权重综合成适应度。
 * 综合维度：年化收益、(1-最大回撤)、夏普、胜率、盈亏比 —— 避免单一收益目标过拟合。
 * <p>无交易（全 HOLD）策略直接给低分，引导算法探索主动交易策略。
 */
@Component
public class FitnessEvaluator {

    private final SignalGenerator signalGenerator;
    private final BacktestEngine backtestEngine;

    public FitnessEvaluator(SignalGenerator signalGenerator, BacktestEngine backtestEngine) {
        this.signalGenerator = signalGenerator;
        this.backtestEngine = backtestEngine;
    }

    public double evaluate(Chromosome chr, List<Indicator> indicators,
                           List<KLine> trainKlines, double[][][] trainCache, StrategyConfig config) {
        var signals = signalGenerator.generate(chr, indicators.size(), trainCache);
        var result = backtestEngine.run(trainKlines, signals, "train",
                config.getInitialCapital(), config.getCommissionRate());
        PerformanceMetrics m = result.getMetrics();

        if (m.getTradeCount() < 1) {
            // 无完整交易：低分，惩罚消极策略
            return -0.5;
        }

        double rScore = clamp(m.getAnnualReturn() / 0.5, -1, 1);          // 年化 50% → 1
        double ddScore = 1 - Math.min(m.getMaxDrawdown(), 1);             // 回撤越小越高
        double shScore = clamp(m.getSharpe() / 3.0, -1, 1);               // 夏普 3 → 1
        double wrScore = m.getWinRate();                                  // 0..1
        double plScore = clamp(m.getProfitLossRatio() / 3.0, 0, 1);       // 盈亏比 3 → 1

        double fitness = config.getwReturn() * rScore
                + config.getwDrawdown() * ddScore
                + config.getwSharpe() * shScore
                + config.getwWinRate() * wrScore
                + config.getwProfitLoss() * plScore;
        return fitness;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
