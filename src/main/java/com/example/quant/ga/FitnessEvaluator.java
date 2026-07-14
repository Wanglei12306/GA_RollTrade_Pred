package com.example.quant.ga;

import com.example.quant.backtest.BacktestEngine;
import com.example.quant.indicator.Indicator;
import com.example.quant.model.Chromosome;
import com.example.quant.model.KLine;
import com.example.quant.model.PerformanceMetrics;
import com.example.quant.model.Signal;
import com.example.quant.model.StrategyConfig;
import com.example.quant.model.TimingLabel;
import com.example.quant.strategy.SignalGenerator;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 适应度评估器（GA 训练目标）。
 * <p>策略优化范式：在训练窗口上对染色体生成信号并执行迷你回测，由五维绩效指标按可配权重
 * 综合成适应度。综合维度：年化收益、(1-最大回撤)、夏普、胜率、盈亏比 —— 避免单一收益目标过拟合，
 * 兼顾盈利能力、稳定性与抗风险能力。
 * <ul>
 *   <li>无完整交易（tradeCount &lt; 1，全 HOLD）→ {@value #NO_TRADE_PENALTY}，引导算法探索主动交易策略。</li>
 *   <li>各维度归一化后按 {@link StrategyConfig} 的 wReturn/wDrawdown/wSharpe/wWinRate/wProfitLoss 加权。</li>
 * </ul>
 * <p>另提供 {@link #accuracy} 作为<b>辅助可验证性指标</b>（未来 N 日涨跌方向预测准确率），
 * 不驱动训练，仅用于训练/测试集可验证性展示与 predict 流程。
 */
@Component
public class FitnessEvaluator {

    /** 无主动交易（全 HOLD / 无完整交易）的惩罚分，保证低于任何主动交易策略。 */
    public static final double NO_TRADE_PENALTY = -0.5;

    private final SignalGenerator signalGenerator;
    private final BacktestEngine backtestEngine;

    public FitnessEvaluator(SignalGenerator signalGenerator, BacktestEngine backtestEngine) {
        this.signalGenerator = signalGenerator;
        this.backtestEngine = backtestEngine;
    }

    /**
     * 训练适应度：在训练窗口上对染色体生成信号 → 迷你回测 → 五维绩效加权。
     * 接口签名不变，{@link GeneticAlgorithm} 无需改动。
     */
    public double evaluate(Chromosome chr, List<Indicator> indicators,
                           List<KLine> trainKlines, double[][][] trainCache, StrategyConfig config) {
        Signal[] signals = signalGenerator.generate(chr, indicators.size(), trainCache);
        var result = backtestEngine.run(trainKlines, signals, "train",
                config.getInitialCapital(), config.getCommissionRate());
        PerformanceMetrics m = result.getMetrics();

        if (m.getTradeCount() < 1) {
            // 无完整交易：低分，惩罚消极策略
            return NO_TRADE_PENALTY;
        }

        double rScore = clamp(m.getAnnualReturn() / 0.5, -1, 1);          // 年化 50% → 1
        double ddScore = 1 - Math.min(m.getMaxDrawdown(), 1);             // 回撤越小越高
        double shScore = clamp(m.getSharpe() / 3.0, -1, 1);               // 夏普 3 → 1
        double wrScore = m.getWinRate();                                  // 0..1
        double plScore = clamp(m.getProfitLossRatio() / 3.0, 0, 1);       // 盈亏比 3 → 1

        return config.getwReturn() * rScore
                + config.getwDrawdown() * ddScore
                + config.getwSharpe() * shScore
                + config.getwWinRate() * wrScore
                + config.getwProfitLoss() * plScore;
    }

    /**
     * 方向预测准确率（[0,1]，无有效样本返回 NaN）—— <b>辅助可验证性指标</b>，不驱动训练。
     * 用于训练/测试集可验证性展示与 predict 流程：把染色体的 BUY/SELL/HOLD 信号映射为
     * {+1,-1,0}，与未来 N 日涨跌方向标签（{@link TimingLabel}）比对。
     */
    public double accuracy(Chromosome chr, int indicatorCount, List<KLine> klines,
                           double[][][] cache, StrategyConfig config) {
        int h = config.getForecastDays();
        double[] labels = TimingLabel.labels(klines, h, config.getLabelThreshold());
        int[] pred = predictions(chr, indicatorCount, cache);
        int valid = 0, correct = 0;
        for (int t = 0; t < labels.length; t++) {
            if (Double.isNaN(labels[t])) continue;
            valid++;
            if (pred[t] == (int) labels[t]) correct++;
        }
        return valid > 0 ? (double) correct / valid : Double.NaN;
    }

    /** 染色体在缓存上的方向预测序列：BUY→+1, SELL→-1, HOLD→0。 */
    private int[] predictions(Chromosome chr, int indicatorCount, double[][][] cache) {
        Signal[] signals = signalGenerator.generate(chr, indicatorCount, cache);
        int[] pred = new int[signals.length];
        for (int i = 0; i < signals.length; i++) {
            pred[i] = switch (signals[i]) {
                case BUY -> 1;
                case SELL -> -1;
                case HOLD -> 0;
            };
        }
        return pred;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
