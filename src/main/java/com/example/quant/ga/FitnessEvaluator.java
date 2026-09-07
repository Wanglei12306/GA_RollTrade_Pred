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
 * <p><b>防过拟合三项</b>（仍由五维盈亏驱动，准确率不进适应度）：
 * <ul>
 *   <li>B1 软饱和归一化：各维度用 {@code x/(|x|+k)} 软饱和替代硬 clip，顶端保留梯度，
 *       避免短窗高收益把适应度顶到 0.9999 失去区分度。</li>
 *   <li>C1 fit/val 切分 + 泛化差距惩罚：验证段权重默认高于训练段，并惩罚两段差距，
 *       直接压低样本内爆表而验证段拉胯的过拟合个体；切片复用全窗缓存保留指标历史，无冷启动。</li>
 *   <li>C2 复杂度正则：减 {@code complexityPenalty * 活跃指标占比}，偏好简单可泛化策略。</li>
 *   <li>C3 换手正则：对超过每 30 根 K 线 1 次完整交易的个体扣分，降低手续费和信号噪声。</li>
 *   <li>无完整交易（tradeCount &lt; 1，全 HOLD）→ {@value #NO_TRADE_PENALTY}，引导算法探索主动交易策略。</li>
 * </ul>
 * <p>另提供 {@link #accuracy} 作为<b>辅助可验证性指标</b>（未来 N 日涨跌方向预测准确率），
 * 不驱动训练，仅用于训练/测试集可验证性展示与 predict 流程。
 */
@Component
public class FitnessEvaluator {

    /** 无主动交易（全 HOLD / 无完整交易）的惩罚分，保证低于任何主动交易策略。 */
    public static final double NO_TRADE_PENALTY = -0.5;
    /** fit/val 切分时每段至少需要的 bar 数；不足则回退整窗评估。 */
    private static final int MIN_HALF_BARS = 10;

    /**
     * NSGA-II 双目标评价结果（提高收益 + 降低回撤）。
     * 两个目标均为<b>最大化</b>方向：{@code returnScore} 为累计收益（软饱和），{@code drawdownScore}=1-最大回撤；
     * {@code cumulativeReturn} 与 {@code tradeCount} 供最终择优与无交易判定，不参与支配比较。
     */
    public record Objectives(double returnScore, double drawdownScore,
                             double cumulativeReturn, int tradeCount) {}

    /** 无主动交易的占位目标：收益与回撤都取最差，保证被任何真实交易个体支配。 */
    private static final Objectives NO_TRADE_OBJECTIVES = new Objectives(-1.0, 0.0, 0.0, 0);

    private final SignalGenerator signalGenerator;
    private final BacktestEngine backtestEngine;

    public FitnessEvaluator(SignalGenerator signalGenerator, BacktestEngine backtestEngine) {
        this.signalGenerator = signalGenerator;
        this.backtestEngine = backtestEngine;
    }

    /**
     * 训练适应度：训练窗等分 fit/val 两段 → 各跑五维软饱和得分 → 均值减泛化差距与复杂度惩罚。
     * 接口签名不变，{@link GeneticAlgorithm} 无需改动。
     */
    public double evaluate(Chromosome chr, List<Indicator> indicators,
                           List<KLine> trainKlines, double[][][] trainCache, StrategyConfig config) {
        int n = trainKlines.size();
        int mid = n / 2;
        // 窗口过短无法有效切分：回退整窗评估（仍用软饱和 + 复杂度惩罚）
        if (mid < MIN_HALF_BARS || (n - mid) < MIN_HALF_BARS) {
            return wholeScore(chr, indicators, trainKlines, trainCache, config);
        }

        // 切片复用全窗缓存：val 段评分已含 fit 段历史，无冷启动
        double[][][] fitCache = SignalGenerator.sliceBars(trainCache, 0, mid);
        double[][][] valCache = SignalGenerator.sliceBars(trainCache, mid, n);
        List<KLine> fitKlines = trainKlines.subList(0, mid);
        List<KLine> valKlines = trainKlines.subList(mid, n);

        double fitF = halfScore(chr, indicators, fitKlines, fitCache, config);
        double valF = halfScore(chr, indicators, valKlines, valCache, config);
        // 两段都无交易 → 消极策略，强惩罚
        if (fitF <= NO_TRADE_PENALTY && valF <= NO_TRADE_PENALTY) {
            return NO_TRADE_PENALTY;
        }


        double gap = Math.abs(fitF - valF);
        double complexity = config.getComplexityPenalty() * activeRatio(chr, indicators.size());
        double validationWeight = clamp(config.getValidationWeight(), 0.0, 1.0);
        double robust = (1.0 - validationWeight) * fitF + validationWeight * valF;
        return robust - config.getGeneralizationPenalty() * gap - complexity;
    }

    /** 整窗评估（窗口过短的回退路径）：五维软饱和得分减复杂度惩罚。 */
    private double wholeScore(Chromosome chr, List<Indicator> indicators,
                              List<KLine> klines, double[][][] cache, StrategyConfig config) {
        double s = halfScore(chr, indicators, klines, cache, config);
        if (s <= NO_TRADE_PENALTY) return NO_TRADE_PENALTY;
        return s - config.getComplexityPenalty() * activeRatio(chr, indicators.size());
    }

    /**
     * 单段五维软饱和得分；无完整交易返回 {@link #NO_TRADE_PENALTY}。
     * 归一化用 {@code x/(|x|+k)} 软饱和：年化 50%→0.5、100%→0.67、200%→0.8；夏普/盈亏比同法，
     * 顶端渐近满分但保留梯度，避免硬 clip 把短窗高收益顶满导致 0.9999 饱和。
     */
    private double halfScore(Chromosome chr, List<Indicator> indicators,
                             List<KLine> klines, double[][][] cache, StrategyConfig config) {
        Signal[] signals = signalGenerator.generate(chr, indicators.size(), cache);
        var result = backtestEngine.run(klines, signals, "train",
                config.getInitialCapital(), config.getCommissionRate(),
                config.getMaxPositionRatio(), config.getStopLossRatio(), config.getMaxHoldingBars(),
                config.getMinHoldingBars(), config.getMaxDrawdownLimit(), config.getTrendFilterBars(),
                config.getDrawdownCooldownBars(), config.isAllowShortPositions());
        PerformanceMetrics m = result.getMetrics();
        if (m.getTradeCount() < 1) return NO_TRADE_PENALTY;

        double rScore  = softSat(m.getAnnualReturn(), 0.5);
        double dd = Math.min(m.getMaxDrawdown(), 1);
        double duration = klines.size() > 1
                ? Math.min(1.0, (double) m.getMaxDrawdownDuration() / (klines.size() - 1)) : 0;
        // 同样的回撤幅度，持续越久风险越高；仍归入回撤维度，保持五维目标不变。
        double ddScore = (1 - dd) * (1 - 0.5 * duration);
        double shScore = softSat(m.getSharpe(), 2.0);
        double wrScore = m.getWinRate();
        double plScore = softSat(m.getProfitLossRatio(), 2.0);

        // 交易样本过少时向 0 收缩，防止单笔偶然盈利主导遗传选择。
        double raw = config.getwReturn() * rScore
                + config.getwDrawdown() * ddScore
                + config.getwSharpe() * shScore
                + config.getwWinRate() * wrScore
                + config.getwProfitLoss() * plScore;
        int minTrades = Math.max(1, config.getMinTradesForFitness());
        double confidence = Math.min(1.0, (double) m.getTradeCount() / minTrades);
        // 以每 30 根 K 线 1 次完整交易作为中性换手水平；过高换手会放大手续费、滑点和噪声。
        double expectedTrades = Math.max(1.0, klines.size() / 30.0);
        double turnover = Math.min(1.0, m.getTradeCount() / expectedTrades);
        double turnoverCost = Math.max(0.0, config.getTurnoverPenalty()) * turnover;
        // 回撤分本身始终为正，若只做线性加权，"空仓/小亏损"可能比有盈利但波动更大的策略得分更高。
        // 对亏损采用非对称惩罚，让 GA 明确优先淘汰负收益个体，同时保留盈利策略的排序梯度。
        double loss = Math.max(0.0, -m.getCumulativeReturn());
        double lossPenalty = Math.max(0.0, config.getNegativeReturnPenalty()) * Math.min(1.0, loss);
        return raw * confidence - turnoverCost - lossPenalty;
    }

    /**
     * NSGA-II 双目标评价（提高收益 + 降低回撤）。
     * 与 {@link #evaluate} 采用相同的 fit/val 切分防过拟合：两个目标各自做“验证段加权 +
     * 泛化差距惩罚”，返回”累计收益”与”回撤保护”两个最大化目标供帕累托排序。
     * 无完整交易 → {@link #NO_TRADE_OBJECTIVES}（被任何真实交易个体支配）。
     */
    public Objectives objectives(Chromosome chr, List<Indicator> indicators,
                                 List<KLine> trainKlines, double[][][] trainCache, StrategyConfig config) {
        int n = trainKlines.size();
        int mid = n / 2;
        // 窗口过短无法有效切分：回退整窗双目标评估
        if (mid < MIN_HALF_BARS || (n - mid) < MIN_HALF_BARS) {
            return segmentObjectives(chr, indicators, trainKlines, trainCache, config);
        }

        double[][][] fitCache = SignalGenerator.sliceBars(trainCache, 0, mid);
        double[][][] valCache = SignalGenerator.sliceBars(trainCache, mid, n);
        List<KLine> fitKlines = trainKlines.subList(0, mid);
        List<KLine> valKlines = trainKlines.subList(mid, n);

        Objectives fit = segmentObjectives(chr, indicators, fitKlines, fitCache, config);
        Objectives val = segmentObjectives(chr, indicators, valKlines, valCache, config);
        if (fit.tradeCount() < 1 && val.tradeCount() < 1) return NO_TRADE_OBJECTIVES;

        double vw = clamp(config.getValidationWeight(), 0.0, 1.0);
        double gap = config.getGeneralizationPenalty();
        double ret = blend(fit.returnScore(), val.returnScore(), vw)
                - gap * Math.abs(fit.returnScore() - val.returnScore());
        double drawdown = blend(fit.drawdownScore(), val.drawdownScore(), vw)
                - gap * Math.abs(fit.drawdownScore() - val.drawdownScore());
        double cum = blend(fit.cumulativeReturn(), val.cumulativeReturn(), vw);
        return new Objectives(ret, drawdown, cum, Math.max(fit.tradeCount(), val.tradeCount()));
    }

    /** 单段双目标：累计收益软饱和为最大化目标 1，(1-最大回撤) 为最大化目标 2。 */
    private Objectives segmentObjectives(Chromosome chr, List<Indicator> indicators,
                                         List<KLine> klines, double[][][] cache, StrategyConfig config) {
        Signal[] signals = signalGenerator.generate(chr, indicators.size(), cache);
        var result = backtestEngine.run(klines, signals, "train",
                config.getInitialCapital(), config.getCommissionRate(),
                config.getMaxPositionRatio(), config.getStopLossRatio(), config.getMaxHoldingBars(),
                config.getMinHoldingBars(), config.getMaxDrawdownLimit(), config.getTrendFilterBars(),
                config.getDrawdownCooldownBars(), config.isAllowShortPositions());
        PerformanceMetrics m = result.getMetrics();
        if (m.getTradeCount() < 1) return NO_TRADE_OBJECTIVES;
        // 累计收益软饱和：50%→0.5、100%→0.67、200%→0.8，直接奖励绝对收益（提高收益目标）。
        // 用累计收益而非年化，避免长期空仓把年化摊薄到近 0、令收益目标失去区分度。
        double ret = softSat(m.getCumulativeReturn(), 0.5);
        double drawdown = 1.0 - Math.min(m.getMaxDrawdown(), 1.0);
        return new Objectives(ret, drawdown, m.getCumulativeReturn(), m.getTradeCount());
    }

    /** 验证段加权混合：validationWeight 越高越偏向验证段，抑制样本内过拟合。 */
    private static double blend(double fit, double val, double validationWeight) {
        return (1.0 - validationWeight) * fit + validationWeight * val;
    }

    /** 软饱和 x/(|x|+k)：保号、顶端渐近 ±1；NaN→0、+Inf→1、-Inf→-1。 */
    private static double softSat(double v, double k) {
        if (Double.isNaN(v)) return 0;
        if (Double.isInfinite(v)) return v > 0 ? 1 : -1;
        return v / (Math.abs(v) + k);
    }

    /** 染色体活跃指标占比 [0,1]，用于复杂度惩罚。 */
    private static double activeRatio(Chromosome chr, int indicatorCount) {
        if (indicatorCount <= 0) return 0;
        boolean[] mask = chr.getMask();
        int active = 0;
        for (boolean b : mask) if (b) active++;
        return (double) active / indicatorCount;
    }

    /**
     * 方向预测准确率（[0,1]，无可标注样本返回 NaN）—— <b>辅助可验证性指标</b>，不驱动训练。
     * 把染色体的 BUY/SELL/HOLD 信号与未来 N 日涨跌方向标签（{@link TimingLabel}）比对，
     * 全部可标注 bar 计入分母，HOLD 匹配中性标签算正确。阈值自适应：
     * {@link StrategyConfig#getLabelThreshold()} &lt;= 0 时按窗口波动率自动取中性带，
     * 避免 threshold=0 下标签恒为 ±1、HOLD 被自动判错。
     */
    public double accuracy(Chromosome chr, int indicatorCount, List<KLine> klines,
                           double[][][] cache, StrategyConfig config) {
        Signal[] signals = signalGenerator.generate(chr, indicatorCount, cache);
        double threshold = TimingLabel.adaptiveThreshold(klines, config.getForecastDays(), config.getLabelThreshold());
        return TimingLabel.directionalAccuracy(signals, klines, config.getForecastDays(), threshold);
    }

    /**
     * 表态率（[0,1]，伴生指标）：标签可得 bar 中策略发出 BUY/SELL 的占比。
     * 表态率过低时准确率不具备统计意义，暴露靠全 HOLD 刷高准确率的策略。
     */
    public double commitmentRate(Chromosome chr, int indicatorCount, List<KLine> klines,
                                 double[][][] cache, StrategyConfig config) {
        Signal[] signals = signalGenerator.generate(chr, indicatorCount, cache);
        return TimingLabel.commitmentRate(signals, klines, config.getForecastDays());
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
