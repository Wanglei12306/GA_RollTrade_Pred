package com.example.quant.rolling;

import com.example.quant.ga.FitnessEvaluator;
import com.example.quant.ga.GeneticAlgorithm;
import com.example.quant.model.Chromosome;
import com.example.quant.model.KLine;
import com.example.quant.model.Signal;
import com.example.quant.model.StrategyConfig;
import com.example.quant.model.TimingLabel;
import com.example.quant.model.WindowResult;
import com.example.quant.strategy.SignalGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 滚动窗口训练与外推预测服务。
 * <p>按年-月分桶，训练=连续 trainMonths 个月、预测=下 predictMonths 个月、步长=stepMonths。
 * 每轮：训练窗口跑遗传算法 → 最优染色体 → 预测窗口外推生成信号。
 * <p>严格时序隔离：预测窗口月份始终晚于训练窗口月份，预测数据绝不参与当轮训练。
 * 聚合所有预测窗口的样本外信号（按日期去重，首个窗口优先，避免步长小于预测窗口时重叠）。
 */
@Service
public class RollingWindowService {

    private static final Logger log = LoggerFactory.getLogger(RollingWindowService.class);
    private static final int MIN_TRAIN_BARS = 20;

    private final GeneticAlgorithm geneticAlgorithm;
    private final SignalGenerator signalGenerator;
    private final FitnessEvaluator fitnessEvaluator;

    public RollingWindowService(GeneticAlgorithm geneticAlgorithm, SignalGenerator signalGenerator,
                                FitnessEvaluator fitnessEvaluator) {
        this.geneticAlgorithm = geneticAlgorithm;
        this.signalGenerator = signalGenerator;
        this.fitnessEvaluator = fitnessEvaluator;
    }

    public RollingResult run(List<KLine> klines, List<com.example.quant.indicator.Indicator> indicators,
                             StrategyConfig config) {
        List<YearMonth> months = sortedMonths(klines);
        int train = config.getTrainMonths();
        int predict = config.getPredictMonths();
        int step = config.getStepMonths();

        if (months.size() < train + predict) {
            throw new IllegalStateException(
                    "数据量不足以完成滚动训练与预测：至少需要 " + (train + predict) + " 个月数据，当前 " + months.size() + " 个月");
        }

        // 月份 → 该月 K 线
        Map<YearMonth, List<KLine>> byMonth = groupByMonth(klines);

        // 样本外信号按日期聚合（去重，首个窗口优先）
        Map<LocalDate, KLine> outOfSampleDates = new TreeMap<>();
        Map<LocalDate, Signal> outOfSampleSignals = new LinkedHashMap<>();
        List<WindowResult> windows = new ArrayList<>();

        for (int start = 0; start + train + predict - 1 < months.size(); start += step) {
            YearMonth trainStart = months.get(start);
            YearMonth trainEnd = months.get(start + train - 1);
            YearMonth predictStart = months.get(start + train);
            YearMonth predictEnd = months.get(start + train + predict - 1);

            List<KLine> trainKlines = collect(byMonth, months, start, start + train - 1);
            List<KLine> predictKlines = collect(byMonth, months, start + train, start + train + predict - 1);

            if (trainKlines.size() < MIN_TRAIN_BARS) {
                log.warn("跳过窗口 {}~{}：训练数据 {} 根不足", trainStart, trainEnd, trainKlines.size());
                continue;
            }

            double[][][] trainCache = signalGenerator.precomputeScores(indicators, trainKlines);
            Chromosome best = geneticAlgorithm.evolve(indicators, trainKlines, trainCache, config);

            // 训练准确率：模型在训练集上的择时方向预测准确率（可验证性）；表态率伴生展示
            double trainAcc = fitnessEvaluator.accuracy(best, indicators.size(), trainKlines, trainCache, config);
            double trainCommit = fitnessEvaluator.commitmentRate(best, indicators.size(), trainKlines, trainCache, config);

            // 预测窗指标需携带训练窗历史：指标（MA/RSI/MACD…）有 lookback，仅在预测窗短 klines 上从头算
            // 会导致前 period-1 根全 NaN→评分 0→强制 HOLD，预测窗几乎不发出信号。
            // 拼接 train+predict 后算指标再切片取预测段：指标因果，无未来信息泄露，且训练段切片值与原逐窗计算一致。
            double[][][] predictCache = contextualCache(indicators, trainKlines, predictKlines);
            Signal[] predictSignals = signalGenerator.generate(best, indicators.size(), predictCache);

            // 测试准确率：模型在样本外预测窗口上的方向预测准确率；表态率伴生展示
            double testAcc = fitnessEvaluator.accuracy(best, indicators.size(), predictKlines, predictCache, config);
            double testCommit = fitnessEvaluator.commitmentRate(best, indicators.size(), predictKlines, predictCache, config);

            for (int i = 0; i < predictKlines.size(); i++) {
                LocalDate d = predictKlines.get(i).getDate();
                outOfSampleDates.putIfAbsent(d, predictKlines.get(i));
                outOfSampleSignals.putIfAbsent(d, predictSignals[i]);
            }

            windows.add(new WindowResult(
                    trainKlines.get(0).getDate(), trainKlines.get(trainKlines.size() - 1).getDate(),
                    predictKlines.get(0).getDate(), predictKlines.get(predictKlines.size() - 1).getDate(),
                    best.getFitness(), trainAcc, testAcc, best));

            log.info("窗口 {}: 训练 {}~{}, 预测 {}~{}, 适应度={}, 训练准确率={}, 训练表态率={}, 测试准确率={}, 测试表态率={}",
                    windows.size(), trainStart, trainEnd, predictStart, predictEnd,
                    String.format("%.4f", best.getFitness()),
                    String.format("%.4f", trainAcc), String.format("%.4f", trainCommit),
                    String.format("%.4f", testAcc), String.format("%.4f", testCommit));
        }

        if (outOfSampleDates.isEmpty()) {
            throw new IllegalStateException("未能生成任何样本外预测信号，请增加数据量或调整窗口参数");
        }

        List<KLine> outKlines = new ArrayList<>(outOfSampleDates.values());
        Signal[] optimizedSignals = new Signal[outKlines.size()];
        int idx = 0;
        for (KLine k : outKlines) {
            optimizedSignals[idx++] = outOfSampleSignals.get(k.getDate());
        }

        // pooled 样本外准确率：跨所有窗口聚合的样本外信号上的方向预测准确率（可验证性主数字）。
        // 单窗口 testAcc 因 predictMonths=1 样本量小而噪声大（易出现 0.0000），pooled 才是稳定可验证值。
        double pooledThreshold = TimingLabel.adaptiveThreshold(outKlines, config.getForecastDays(), config.getLabelThreshold());
        double pooledAcc = TimingLabel.directionalAccuracy(optimizedSignals, outKlines, config.getForecastDays(), pooledThreshold);
        double pooledCommit = TimingLabel.commitmentRate(optimizedSignals, outKlines, config.getForecastDays());
        log.info("样本外汇总：{} 根，pooled 准确率={}, pooled 表态率={}",
                outKlines.size(),
                Double.isNaN(pooledAcc) ? "N/A" : String.format("%.4f", pooledAcc),
                Double.isNaN(pooledCommit) ? "N/A" : String.format("%.4f", pooledCommit));

        return new RollingResult(outKlines, optimizedSignals, windows, pooledAcc, pooledCommit);
    }

    private List<YearMonth> sortedMonths(List<KLine> klines) {
        return klines.stream()
                .map(k -> YearMonth.from(k.getDate()))
                .distinct()
                .sorted()
                .toList();
    }

    private Map<YearMonth, List<KLine>> groupByMonth(List<KLine> klines) {
        Map<YearMonth, List<KLine>> map = new TreeMap<>();
        for (KLine k : klines) {
            map.computeIfAbsent(YearMonth.from(k.getDate()), m -> new ArrayList<>()).add(k);
        }
        return map;
    }

    private List<KLine> collect(Map<YearMonth, List<KLine>> byMonth, List<YearMonth> months, int from, int to) {
        List<KLine> out = new ArrayList<>();
        for (int i = from; i <= to; i++) {
            out.addAll(byMonth.get(months.get(i)));
        }
        return out;
    }

    /**
     * 带 train 历史上下文计算预测窗指标评分，再切片取预测段。
     * 避免 lookback 指标在短预测窗上冷启动（前 period-1 根 NaN→0→HOLD）导致预测窗几乎不发出信号。
     */
    private double[][][] contextualCache(List<com.example.quant.indicator.Indicator> indicators,
                                         List<KLine> trainKlines, List<KLine> predictKlines) {
        List<KLine> ctx = new ArrayList<>(trainKlines.size() + predictKlines.size());
        ctx.addAll(trainKlines);
        ctx.addAll(predictKlines);
        double[][][] full = signalGenerator.precomputeScores(indicators, ctx);
        return SignalGenerator.sliceBars(full, trainKlines.size(), ctx.size());
    }

    /** 滚动结果：样本外 K 线、优化策略信号、各窗口记录、pooled 样本外准确率与表态率。 */
    public record RollingResult(List<KLine> outOfSampleKlines, Signal[] optimizedSignals,
                                List<WindowResult> windows, double pooledAccuracy, double pooledCommitment) {}
}
