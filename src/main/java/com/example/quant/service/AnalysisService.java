package com.example.quant.service;

import com.example.quant.backtest.BacktestEngine;
import com.example.quant.data.CsvLoader;
import com.example.quant.indicator.Indicator;
import com.example.quant.indicator.IndicatorPool;
import com.example.quant.model.AnalysisReport;
import com.example.quant.model.BacktestResult;
import com.example.quant.model.Chromosome;
import com.example.quant.model.FolderTrainResult;
import com.example.quant.model.InstrumentTrainResult;
import com.example.quant.model.KLine;
import com.example.quant.model.Signal;
import com.example.quant.model.StrategyConfig;
import com.example.quant.model.TimingLabel;
import com.example.quant.model.TrainSummary;
import com.example.quant.model.TrainedModel;
import com.example.quant.model.WindowResult;
import com.example.quant.model.PredictionResult;
import com.example.quant.model.BarPrediction;
import com.example.quant.rolling.RollingWindowService;
import com.example.quant.strategy.FixedStrategy;
import com.example.quant.strategy.SignalGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 分析编排服务：滚动训练择时模型 → 保存模型 → 优化策略回测 + 固定策略回测 → 汇总报告。
 * <p>当前版本唯一训练目标：择时。{@link #train(StrategyConfig)} 仅训练并落盘模型；
 * {@link #run(StrategyConfig)} 在训练基础上追加回测评估。
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
    private final ModelRepository modelRepository;
    private final CsvLoader csvLoader;

    private AnalysisReport lastReport;
    private TrainSummary lastTrainSummary;
    private RollingWindowService.RollingResult lastRolling;
    private FolderTrainResult lastFolderResult;
    private PredictionResult lastPrediction;

    public AnalysisService(DataService dataService, IndicatorPool indicatorPool,
                           RollingWindowService rollingWindowService,
                           SignalGenerator signalGenerator, FixedStrategy fixedStrategy,
                           BacktestEngine backtestEngine, ModelRepository modelRepository,
                           CsvLoader csvLoader) {
        this.dataService = dataService;
        this.indicatorPool = indicatorPool;
        this.rollingWindowService = rollingWindowService;
        this.signalGenerator = signalGenerator;
        this.fixedStrategy = fixedStrategy;
        this.backtestEngine = backtestEngine;
        this.modelRepository = modelRepository;
        this.csvLoader = csvLoader;
    }

    /**
     * 训练择时模型：跑滚动窗口 GA 训练，把最后一个窗口的最优染色体落盘为模型，
     * 返回训练摘要（含各窗口训练/测试准确率，用于可验证性）。
     */
    public TrainSummary train(StrategyConfig config) {
        if (!dataService.hasData()) {
            throw new IllegalStateException("请先上传或加载行情数据");
        }
        var klines = dataService.getKlines();
        var indicators = indicatorPool.selected(config.getIndicators());

        log.info("开始择时模型训练：{} 根 K 线，{} 个指标", klines.size(), indicators.size());
        var rolling = rollingWindowService.run(klines, indicators, config);
        lastRolling = rolling;
        List<WindowResult> windows = rolling.windows();

        // 取最后一个窗口的最优染色体作为最终模型（覆盖最新行情段）
        Chromosome finalModel = windows.get(windows.size() - 1).getBestChromosome();
        TrainedModel snapshot = TrainedModel.from(finalModel, dataService.getDataName(),
                config.getForecastDays(), config.getLabelThreshold(),
                indicators.stream().map(Indicator::name).toList());
        java.nio.file.Path modelPath = modelRepository.save(snapshot);

        List<Double> trainAccs = new ArrayList<>();
        List<Double> testAccs = new ArrayList<>();
        double sumTrain = 0, sumTest = 0;
        for (WindowResult w : windows) {
            trainAccs.add(w.getTrainAccuracy());
            testAccs.add(w.getTestAccuracy());
            if (!Double.isNaN(w.getTrainAccuracy())) sumTrain += w.getTrainAccuracy();
            if (!Double.isNaN(w.getTestAccuracy())) sumTest += w.getTestAccuracy();
        }
        int n = windows.size();
        lastTrainSummary = new TrainSummary(
                dataService.getDataName(),
                n,
                n > 0 ? sumTrain / n : Double.NaN,
                n > 0 ? sumTest / n : Double.NaN,
                finalModel.getFitness(),
                modelPath.toString(),
                trainAccs, testAccs);
        log.info("训练完成：{} 个窗口，平均训练准确率={}, 平均测试准确率={}, 模型={}",
                n,
                String.format("%.4f", lastTrainSummary.avgTrainAccuracy()),
                String.format("%.4f", lastTrainSummary.avgTestAccuracy()),
                modelPath);
        return lastTrainSummary;
    }

    /**
     * 加载已训练择时模型，对当前加载的数据逐根生成预测（BUY/SELL/HOLD = 未来 N 日涨跌方向预测），
     * 并在标签可得的根上计算方向预测准确率。这是模型落盘文件的消费入口：
     * 训练一次 → 保存模型 → 后续加载数据即可预测，无需重训。
     * <p>用模型自身保存的指标名列表重建指标集，保证染色体数组与指标顺序与训练时一致。
     *
     * @param modelName 训练落盘时的数据名（模型文件名主体，如 "标的A.csv"）
     */
    public PredictionResult predict(String modelName) {
        if (!dataService.hasData()) {
            throw new IllegalStateException("请先上传或加载行情数据");
        }
        TrainedModel model = modelRepository.load(modelName);
        if (model == null) {
            throw new IllegalStateException("未找到模型：" + modelName
                    + "（请先对该数据调用 /api/train 训练）");
        }
        Chromosome chr = model.toChromosome();
        var indicators = indicatorPool.selected(model.indicators());
        List<KLine> klines = dataService.getKlines();

        log.info("加载模型预测：模型={}（训练于 {}，fitness={}），数据={} {} 根",
                modelName, model.trainedAt(),
                String.format("%.4f", model.fitness()),
                dataService.getDataName(), klines.size());

        double[][][] cache = signalGenerator.precomputeScores(indicators, klines);
        Signal[] signals = signalGenerator.generate(chr, indicators.size(), cache);
        // 阈值自适应：labelThreshold<=0 时按当前数据波动率取中性带，与训练侧 accuracy 语义一致
        double threshold = TimingLabel.adaptiveThreshold(klines, model.forecastDays(), model.labelThreshold());
        double[] labels = TimingLabel.labels(klines, model.forecastDays(), threshold);

        int countBuy = 0, countSell = 0, countHold = 0;
        int verified = 0, forward = 0;        // verified=标签可得根数；forward=尾部不可标注根数
        int committed = 0, correct = 0;        // committed=已表态(非HOLD)且可验证(信息性)；correct=命中标签
        List<BarPrediction> bars = new ArrayList<>(klines.size());
        for (int t = 0; t < klines.size(); t++) {
            KLine k = klines.get(t);
            Signal sig = signals[t];
            int dir = switch (sig) {
                case BUY -> 1;
                case SELL -> -1;
                case HOLD -> 0;
            };
            switch (sig) {
                case BUY -> countBuy++;
                case SELL -> countSell++;
                case HOLD -> countHold++;
            }
            Integer label = null;
            boolean ok = false;
            if (t < labels.length && !Double.isNaN(labels[t])) {
                label = (int) labels[t];
                verified++;
                if (dir == label) {
                    correct++;
                    ok = true;                // 逐根：信号是否匹配标签（含 HOLD 匹配中性）
                }
                if (dir != 0) committed++;    // 信息性：已表态根数
            } else {
                forward++;
            }
            bars.add(new BarPrediction(
                    k.getDate() == null ? null : k.getDate().toString(),
                    k.getClose(),
                    sig.name(),
                    dir,
                    label,
                    ok));
        }
        // 方向准确率：全部可验证根上信号是否匹配标签（HOLD 匹配中性算正确），与训练侧 accuracy 口径一致
        Double accuracy = verified > 0 ? (double) correct / verified : null;

        lastPrediction = new PredictionResult(
                dataService.getDataName(),
                modelName,
                model.trainedAt(),
                model.forecastDays(),
                model.fitness(),
                klines.size(),
                countBuy, countSell, countHold,
                verified, forward,
                accuracy,
                bars);
        log.info("预测完成：BUY={} SELL={} HOLD={}，可验证 {} 根（已表态 {} 根准确率={}），向前预测 {} 根",
                countBuy, countSell, countHold,
                verified, committed,
                accuracy == null ? "N/A" : String.format("%.4f", accuracy),
                forward);
        return lastPrediction;
    }

    public AnalysisReport run(StrategyConfig config) {
        // 训练（同时落盘模型、刷新 lastTrainSummary 与 lastRolling）
        train(config);
        var indicators = indicatorPool.selected(config.getIndicators());

        // 复用训练阶段已跑出的样本外信号，避免重复训练
        var rolling = lastRolling;
        List<KLine> outKlines = rolling.outOfSampleKlines();
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

        // 多指标信号曲线：每个勾选指标用第 0 组候选参数在样本外 K 线上的信号评分
        List<AnalysisReport.IndicatorCurve> indicatorCurves = new ArrayList<>();
        for (int i = 0; i < indicators.size(); i++) {
            double[] scores = outCache[i][0];
            indicatorCurves.add(new AnalysisReport.IndicatorCurve(indicators.get(i).name(), scores));
        }

        // 年度收益统计：从优化策略资金曲线按年汇总
        List<AnalysisReport.AnnualReturn> annualReturns = annualReturns(optimized);

        lastReport = new AnalysisReport(optimized, fixed, rolling.windows(),
                outKlines, config, dataService.getDataName(), indicatorCurves, annualReturns);
        return lastReport;
    }

    /**
     * 按年汇总回测年度收益率：每年末净值 / 上年末净值 - 1；首年以该年首个净值为基准。
     */
    private static List<AnalysisReport.AnnualReturn> annualReturns(BacktestResult result) {
        List<java.time.LocalDate> dates = result.getDates();
        double[] equity = result.getEquityCurve();
        if (dates.isEmpty() || equity.length == 0) return List.of();

        java.util.Map<Integer, double[]> yearly = new java.util.TreeMap<>();  // year -> [firstEq, lastEq]
        for (int i = 0; i < dates.size() && i < equity.length; i++) {
            int y = dates.get(i).getYear();
            double eq = equity[i];
            double[] b = yearly.computeIfAbsent(y, k -> new double[]{eq, eq});  // [首净值, 末净值]
            b[1] = eq;   // 不断更新到该年末
        }
        List<AnalysisReport.AnnualReturn> out = new ArrayList<>();
        double prevYearEnd = 0;
        for (var e : yearly.entrySet()) {
            double first = e.getValue()[0];
            double last = e.getValue()[1];
            double rate = prevYearEnd > 0 ? last / prevYearEnd - 1 : last / first - 1;
            out.add(new AnalysisReport.AnnualReturn(e.getKey(), rate));
            prevYearEnd = last;
        }
        return out;
    }

    public AnalysisReport getLastReport() {
        return lastReport;
    }

    public TrainSummary getLastTrainSummary() {
        return lastTrainSummary;
    }

    public FolderTrainResult getLastFolderResult() {
        return lastFolderResult;
    }

    public PredictionResult getLastPrediction() {
        return lastPrediction;
    }

    /**
     * 文件夹批量训练：递归遍历 {@code folder} 下所有 {@code .csv}（每个视为一个标的），
     * 逐个加载并训练择时模型，各自落盘。单个标的失败（数据不足、格式错误等）不中断整体，记为失败项。
     * <p>标的命名：CSV 直接位于 {@code folder} 顶层时用文件名（去 .csv）；位于子目录时用
     * 其所在子目录名（如 {@code 中证1000/159629.SZ/1d.csv} → 标的名 {@code 159629.SZ}），
     * 避免多个同名 {@code 1d.csv} 在模型落盘时冲突。
     */
    public FolderTrainResult trainFolder(Path folder, StrategyConfig config) {
        StrategyConfig cfg = normalizeConfig(config);
        List<Path> csvs = listCsvs(folder);
        if (csvs.isEmpty()) {
            throw new IllegalStateException("文件夹内没有 CSV 文件：" + folder);
        }
        log.info("批量训练：{} 个标的，文件夹={}", csvs.size(), folder);

        List<InstrumentTrainResult> items = new ArrayList<>();
        int ok = 0, fail = 0;
        for (Path csv : csvs) {
            String name = instrumentName(folder, csv);
            try (InputStream in = Files.newInputStream(csv)) {
                List<KLine> klines = csvLoader.load(in);
                dataService.store(klines, name);
                TrainSummary summary = train(cfg);
                items.add(new InstrumentTrainResult(name, true, null, summary));
                ok++;
            } catch (Exception e) {
                log.warn("标的 {} 训练失败：{}", name, e.getMessage());
                items.add(new InstrumentTrainResult(name, false, e.getMessage(), null));
                fail++;
            }
        }
        lastFolderResult = new FolderTrainResult(folder.toString(), csvs.size(), ok, fail, items);
        log.info("批量训练完成：成功 {}/{}，失败 {}", ok, csvs.size(), fail);
        return lastFolderResult;
    }

    /** 配置归一化：未指定指标时默认启用全部指标。 */
    private StrategyConfig normalizeConfig(StrategyConfig config) {
        StrategyConfig cfg = config != null ? config : new StrategyConfig();
        if (cfg.getIndicators() == null || cfg.getIndicators().isEmpty()) {
            cfg.setIndicators(indicatorPool.allNames());
        }
        return cfg;
    }

    /**
     * 递归列出文件夹下全部 CSV（按路径排序，保证批量顺序确定）。
     * 支持两种数据布局：顶层散放 CSV，或「父目录/标的/1d.csv」式嵌套。
     */
    private List<Path> listCsvs(Path folder) {
        if (!Files.isDirectory(folder)) {
            throw new IllegalArgumentException("路径不是文件夹：" + folder);
        }
        try (Stream<Path> s = Files.walk(folder)) {
            return s.filter(p -> p.toString().toLowerCase().endsWith(".csv"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("读取文件夹失败：" + e.getMessage(), e);
        }
    }

    /**
     * 推断标的名：CSV 直接位于 {@code root} 下 → 文件名去 .csv；
     * 否则取相对 {@code root} 的第一级子目录名（如 {@code 159629.SZ/1d.csv} → {@code 159629.SZ}）。
     */
    private static String instrumentName(Path root, Path csv) {
        Path rel = root.relativize(csv);
        if (rel.getNameCount() <= 1) {
            String fn = csv.getFileName().toString();
            return fn.toLowerCase().endsWith(".csv") ? fn.substring(0, fn.length() - 4) : fn;
        }
        return rel.getName(0).toString();
    }
}
