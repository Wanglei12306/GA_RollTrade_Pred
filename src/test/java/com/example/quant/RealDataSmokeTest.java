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
import com.example.quant.rolling.RollingWindowService;
import com.example.quant.strategy.SignalGenerator;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
}
