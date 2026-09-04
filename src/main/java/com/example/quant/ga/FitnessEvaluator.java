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
 *   <li>C1 fit/val 切分 + 泛化差距惩罚：训练窗等分两段，{@code 0.5*F(fit)+0.5*F(val)-λ*|F(fit)-F(val)|}，
 *       直接惩罚样本内爆表而验证段拉胯的过拟合个体；切片复用全窗缓存保留指标历史，无冷启动。</li>
 *   <li>C2 复杂度正则：减 {@code complexityPenalty * 活跃指标占比}，偏好简单可泛化策略。</li>
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
        return 0.5 * fitF + 0.5 * valF - config.getGeneralizationPenalty() * gap - complexity;
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
                config.getInitialCapital(), config.getCommissionRate());
        PerformanceMetrics m = result.getMetrics();
        if (m.getTradeCount() < 1) return NO_TRADE_PENALTY;

        double rScore  = softSat(m.getAnnualReturn(), 0.5);
        double ddScore = 1 - Math.min(m.getMaxDrawdown(), 1);
        double shScore = softSat(m.getSharpe(), 2.0);
        double wrScore = m.getWinRate();
        double plScore = softSat(m.getProfitLossRatio(), 2.0);

        double acc = accuracy(chr, indicators.size(), klines, cache, config);
        if (Double.isNaN(acc)) acc = 0.5; // 无法计算时给个中性分
        double accScore = acc;

        return config.getwReturn() * rScore
                + config.getwDrawdown() * ddScore
                + config.getwSharpe() * shScore
                + config.getwWinRate() * wrScore
                + config.getwProfitLoss() * plScore
                + config.getwAcc() * accScore;
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
