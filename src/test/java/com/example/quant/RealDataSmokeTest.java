package com.example.quant;

import com.example.quant.backtest.BacktestEngine;
import com.example.quant.backtest.MetricsCalculator;
import com.example.quant.data.CsvLoader;
import com.example.quant.ga.FitnessEvaluator;
import com.example.quant.ga.GeneticAlgorithm;
import com.example.quant.indicator.Indicator;
import com.example.quant.indicator.IndicatorPool;
import com.example.quant.model.KLine;
import com.example.quant.model.StrategyConfig;
import com.example.quant.model.TrainSummary;
import com.example.quant.model.AnalysisReport;
import com.example.quant.rolling.RollingWindowService;
import com.example.quant.service.AnalysisService;
import com.example.quant.service.DataService;
import com.example.quant.service.ModelRepository;
import com.example.quant.strategy.FixedStrategy;
import com.example.quant.strategy.SignalGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 临时 smoke 测试：用真实 A 股月度数据跑一次滚动训练，观察适应度/准确率/表态率。
 * 文件不存在时自动跳过（Assumptions），不影响其他环境的 mvn test。
 */
class RealDataSmokeTest {

    private static final String CSV =
            "C:/Users/34173/Desktop/基于遗传算法的量化策略分析系统/A股数据/000001/000001_monthly.csv";

    @Test
    void rollingTrainOnRealMonthlyData() throws Exception {
        Path p = Path.of(CSV);
        Assumptions.assumeTrue(Files.exists(p), "测试数据不存在，跳过：" + CSV);

        List<KLine> klines = new CsvLoader().load(new FileInputStream(p.toFile()));
        System.out.printf("REALDATA loaded %d bars, %s .. %s%n",
                klines.size(), klines.get(0).getDate(), klines.get(klines.size() - 1).getDate());

        IndicatorPool pool = new IndicatorPool();
        List<Indicator> indicators = pool.allNames().stream().map(pool::get).toList();

        SignalGenerator gen = new SignalGenerator();
        BacktestEngine be = new BacktestEngine(new MetricsCalculator());
        FitnessEvaluator fe = new FitnessEvaluator(gen, be);
        GeneticAlgorithm ga = new GeneticAlgorithm(fe);
        RollingWindowService svc = new RollingWindowService(ga, gen, fe);

        // 月度数据：1 月 = 1 根 bar，训练窗需 >= MIN_TRAIN_BARS(20)，故取 24 个月
        StrategyConfig cfg = new StrategyConfig();
        cfg.setIndicators(pool.allNames());
        cfg.setPopulationSize(40);
        cfg.setGenerations(40);
        cfg.setTrainMonths(24);
        cfg.setPredictMonths(6);
        cfg.setStepMonths(6);
        cfg.setForecastDays(3);

        var res = svc.run(klines, indicators, cfg);
        int i = 0;
        for (var w : res.windows()) {
            // 重新生成样本内信号，统计 BUY/SELL/HOLD 分布，确认 GA 是否真的在样本内交易
            List<KLine> trainSlice = klines.stream()
                    .filter(k -> !k.getDate().isBefore(w.getTrainStart()) && !k.getDate().isAfter(w.getTrainEnd()))
                    .toList();
            double[][][] tc = gen.precomputeScores(indicators, trainSlice);
            com.example.quant.model.Signal[] ts = gen.generate(w.getBestChromosome(), indicators.size(), tc);
            int b = 0, s = 0, h = 0;
            for (var sig : ts) { if (sig == com.example.quant.model.Signal.BUY) b++; else if (sig == com.example.quant.model.Signal.SELL) s++; else h++; }
            System.out.printf("REALDATA WIN %d fit=%.4f trainAcc=%.4f testAcc=%.4f buyThr=%.3f sellThr=%.3f | inSample BUY=%d SELL=%d HOLD=%d%n",
                    ++i, w.getBestFitness(), w.getTrainAccuracy(), w.getTestAccuracy(),
                    w.getBestChromosome().getBuyThreshold(), w.getBestChromosome().getSellThreshold(),
                    b, s, h);
        }
        System.out.printf("REALDATA POOLED acc=%.4f commit=%.4f bars=%d windows=%d%n",
                res.pooledAccuracy(), res.pooledCommitment(),
                res.outOfSampleKlines().size(), res.windows().size());
    }

    /**
     * 端到端验证「训练一次→落盘→列模型→用模型回测不重训」链路：
     * 训练两次确认不互相覆盖（数据名+时间戳唯一），再用模型 id 回测当前数据，
     * 报告 windows 应为空（单模型回测，无滚动）。
     */
    @Test
    void trainPersistsModelAndBacktestWithoutRetrain() throws Exception {
        Path p = Path.of(CSV);
        Assumptions.assumeTrue(Files.exists(p), "测试数据不存在，跳过：" + CSV);

        DataService dataService = new DataService();
        IndicatorPool pool = new IndicatorPool();
        SignalGenerator gen = new SignalGenerator();
        BacktestEngine be = new BacktestEngine(new MetricsCalculator());
        FitnessEvaluator fe = new FitnessEvaluator(gen, be);
        GeneticAlgorithm ga = new GeneticAlgorithm(fe);
        RollingWindowService rolling = new RollingWindowService(ga, gen, fe);
        FixedStrategy fixed = new FixedStrategy();
        ModelRepository repo = new ModelRepository(new ObjectMapper());
        CsvLoader loader = new CsvLoader();
        AnalysisService svc = new AnalysisService(dataService, pool, rolling, gen, fixed, be, repo, loader);

        List<KLine> klines = loader.load(new FileInputStream(p.toFile()));
        dataService.store(klines, "000001_monthly.csv");

        StrategyConfig cfg = new StrategyConfig();
        cfg.setIndicators(pool.allNames());
        cfg.setPopulationSize(8);
        cfg.setGenerations(3);
        cfg.setTrainMonths(24);
        cfg.setPredictMonths(6);
        cfg.setStepMonths(6);
        cfg.setForecastDays(3);

        // 训练两次：文件名含时间戳，不应互相覆盖
        TrainSummary s1 = svc.train(cfg);
        TrainSummary s2 = svc.train(cfg);
        assertNotNull(s1.modelId(), "训练摘要应携带 modelId");
        assertNotEquals(s1.modelId(), s2.modelId(), "两次训练的 modelId 应不同（不覆盖）");
        var models = svc.listModels();
        assertTrue(models.size() >= 2, "应至少列出两个模型，实际 " + models.size());
        System.out.printf("REALDATA2 models=%d s1.id=%s s2.id=%s%n", models.size(), s1.modelId(), s2.modelId());

        // 用第一个模型回测当前数据（不重训）
        AnalysisReport report = svc.backtestWithModel(s1.modelId(), new StrategyConfig());
        assertNotNull(report.getOptimized());
        assertEquals(0, report.getWindows().size(), "单模型回测应无滚动窗口");
        System.out.printf("REALDATA2 backtestModel bars=%d optCum=%.4f fixedCum=%.4f%n",
                report.getOutOfSampleKlines().size(),
                report.getOptimized().getMetrics().getCumulativeReturn(),
                report.getFixed().getMetrics().getCumulativeReturn());
    }

    /**
     * 验证 train-folder 后缀筛选：一只股票目录下放月线 + 日线两个 CSV，
     * 用 suffix="_monthly.csv" 时只训练月线那一个（日线被排除），且标的名取子目录名（股票代码）。
     */
    @Test
    void trainFolderSuffixOneModelPerStock() throws Exception {
        Path root = Path.of("C:/Users/34173/Desktop/基于遗传算法的量化策略分析系统/A股数据");
        Path m1 = root.resolve("000001/000001_monthly.csv");
        Path m2 = root.resolve("000002/000002_monthly.csv");
        Path d1 = root.resolve("000001/000001_daily_hfq.csv");
        Assumptions.assumeTrue(Files.exists(m1) && Files.exists(m2) && Files.exists(d1),
                "测试数据不存在，跳过 train-folder 用例");

        Path tmp = Files.createTempDirectory("gafolder_");
        Files.createDirectories(tmp.resolve("000001"));
        Files.createDirectories(tmp.resolve("000002"));
        Files.copy(m1, tmp.resolve("000001/000001_monthly.csv"));
        Files.copy(d1, tmp.resolve("000001/000001_daily_hfq.csv"));   // 应被后缀筛选排除
        Files.copy(m2, tmp.resolve("000002/000002_monthly.csv"));

        AnalysisService svc = buildService();
        StrategyConfig cfg = new StrategyConfig();
        cfg.setIndicators(new IndicatorPool().allNames());
        cfg.setPopulationSize(8);
        cfg.setGenerations(3);
        cfg.setTrainMonths(24);
        cfg.setPredictMonths(6);
        cfg.setStepMonths(6);
        cfg.setForecastDays(3);

        var result = svc.trainFolder(tmp, cfg, "_monthly.csv");
        assertEquals(2, result.succeeded(), "应只训练 2 个月线 CSV（日线被筛选排除），实际 " + result.succeeded());
        var names = result.instruments().stream()
                .filter(com.example.quant.model.InstrumentTrainResult::success)
                .map(com.example.quant.model.InstrumentTrainResult::name).toList();
        assertTrue(names.contains("000001") && names.contains("000002"),
                "标的名应为股票代码（子目录名）：" + names);
        System.out.printf("REALDATA3 folder ok=%d names=%s%n", result.succeeded(), names);
    }

    private static AnalysisService buildService() {
        DataService dataService = new DataService();
        IndicatorPool pool = new IndicatorPool();
        SignalGenerator gen = new SignalGenerator();
        BacktestEngine be = new BacktestEngine(new MetricsCalculator());
        FitnessEvaluator fe = new FitnessEvaluator(gen, be);
        GeneticAlgorithm ga = new GeneticAlgorithm(fe);
        RollingWindowService rolling = new RollingWindowService(ga, gen, fe);
        FixedStrategy fixed = new FixedStrategy();
        ModelRepository repo = new ModelRepository(new ObjectMapper());
        CsvLoader loader = new CsvLoader();
        return new AnalysisService(dataService, pool, rolling, gen, fixed, be, repo, loader);
    }
}
