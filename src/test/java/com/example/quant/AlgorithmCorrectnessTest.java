package com.example.quant;

import com.example.quant.backtest.BacktestEngine;
import com.example.quant.backtest.MetricsCalculator;
import com.example.quant.data.DataValidator;
import com.example.quant.ga.FitnessEvaluator;
import com.example.quant.ga.GeneticAlgorithm;
import com.example.quant.indicator.Indicator;
import com.example.quant.indicator.IndicatorMath;
import com.example.quant.indicator.IndicatorPool;
import com.example.quant.indicator.impl.MaIndicator;
import com.example.quant.indicator.impl.RsiIndicator;
import com.example.quant.model.Chromosome;
import com.example.quant.model.KLine;
import com.example.quant.model.Signal;
import com.example.quant.model.StrategyConfig;
import com.example.quant.model.TimingLabel;
import com.example.quant.model.TrainedModel;
import com.example.quant.rolling.RollingWindowService;
import com.example.quant.strategy.SignalGenerator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 算法正确性校验（对应计划「算法正确性重点校验」）：
 * 指标手算、信号范围、回测资金守恒、滚动窗口时序隔离、GA 染色体合法性。
 */
class AlgorithmCorrectnessTest {

    @Test
    void smaHandCalcMatches() {
        double[] v = {1, 2, 3, 4, 5};
        double[] ma = IndicatorMath.sma(v, 3);
        assertTrue(Double.isNaN(ma[0]));
        assertTrue(Double.isNaN(ma[1]));
        assertEquals(2.0, ma[2], 1e-9);
        assertEquals(3.0, ma[3], 1e-9);
        assertEquals(4.0, ma[4], 1e-9);
    }

    @Test
    void indicatorSignalScoresInRange() {
        List<KLine> k = synthetic(120, 100);
        IndicatorPool pool = new IndicatorPool();
        List<Indicator> indicators = pool.allNames().stream().map(pool::get).toList();
        for (Indicator ind : indicators) {
            double[] s = ind.signalScores(k, ind.candidateParams().get(0));
            assertEquals(k.size(), s.length, ind.name() + " 长度应等于 K 线数");
            for (double v : s) {
                assertTrue(v >= -1.0 && v <= 1.0, ind.name() + " 评分越界：" + v);
            }
        }
    }

    @Test
    void momentumIndicatorRegisteredAndValid() {
        IndicatorPool pool = new IndicatorPool();
        assertTrue(pool.allNames().contains("MOM"), "指标池应包含动量指标 MOM");
        assertEquals(9, pool.allNames().size(), "指标池应有 9 个指标");
        Indicator mom = pool.get("MOM");
        assertEquals(3, mom.candidateParams().size(), "MOM 应有 3 组候选参数");
        List<KLine> k = synthetic(120, 100);
        for (double[] params : mom.candidateParams()) {
            double[] s = mom.signalScores(k, params);
            assertEquals(k.size(), s.length, "MOM 评分长度应等于 K 线数");
            for (double v : s) assertTrue(v >= -1.0 && v <= 1.0, "MOM 评分越界：" + v);
        }
    }

    @Test
    void backtestCapitalConservation() {
        // 价格序列 10,11,12,11,10；bar0 买入、bar2 卖出
        List<KLine> k = new ArrayList<>();
        double[] closes = {10, 11, 12, 11, 10};
        for (int i = 0; i < closes.length; i++) {
            k.add(new KLine(LocalDate.of(2024, 1, i + 1), closes[i], closes[i], closes[i], closes[i], 1000));
        }
        Signal[] sig = {Signal.BUY, Signal.HOLD, Signal.SELL, Signal.HOLD, Signal.HOLD};
        double capital = 1_000_000;
        double rate = 0.0003;
        BacktestEngine engine = new BacktestEngine(new MetricsCalculator());
        var result = engine.run(k, sig, "test", capital, rate);

        // 1) 完整交易数 = SELL 数 = 1
        long sells = result.getTrades().stream().filter(t -> "SELL".equals(t.getDirection())).count();
        assertEquals(1, sells);
        assertEquals(1, result.getMetrics().getTradeCount());

        // 2) 资金守恒：实现盈亏之和 == 期末净值 - 初始资金（平仓状态下）
        double realized = result.getTrades().stream()
                .filter(t -> "SELL".equals(t.getDirection()))
                .mapToDouble(t -> t.getPnl()).sum();
        double finalEquity = result.getEquityCurve()[result.getEquityCurve().length - 1];
        assertEquals(realized, finalEquity - capital, 1e-6, "资金守恒不成立");

        // 3) 净值非负
        for (double e : result.getEquityCurve()) assertTrue(e > 0, "净值为负");
        // 4) 最大回撤在 [0,1]
        assertTrue(result.getMetrics().getMaxDrawdown() >= 0 && result.getMetrics().getMaxDrawdown() <= 1);
    }

    @Test
    void backtestRiskControlsLimitLossAndCloseOpenPosition() {
        List<KLine> k = new ArrayList<>();
        // bar0 产生 BUY，bar1 执行买入，bar2 跌破止损线后退出。
        double[] closes = {100, 100, 90, 80};
        for (int i = 0; i < closes.length; i++) {
            k.add(new KLine(LocalDate.of(2024, 2, i + 1), closes[i], closes[i], closes[i], closes[i], 1000));
        }
        Signal[] sig = {Signal.BUY, Signal.HOLD, Signal.HOLD, Signal.HOLD};
        BacktestEngine engine = new BacktestEngine(new MetricsCalculator());
        var result = engine.run(k, sig, "risk", 1_000_000, 0.0003,
                0.5, 0.10, 20);

        // 10% 止损应在第三根 bar 退出，期末不应残留仓位。
        assertEquals(1, result.getMetrics().getTradeCount());
        assertEquals(2, result.getTrades().size());
        assertEquals("SELL", result.getTrades().get(1).getDirection());
        assertEquals(90.0, result.getTrades().get(1).getPrice(), 1e-9);
        assertTrue(result.getMetrics().getMaxDrawdown() < 0.06,
                "半仓止损后的回撤不应接近全仓 10%：" + result.getMetrics().getMaxDrawdown());
    }

    @Test
    void drawdownBudgetHaltsReentryAfterRiskExit() {
        List<KLine> k = new ArrayList<>();
        double[] closes = {100, 100, 120, 120, 90, 80, 70, 60, 60};
        for (int i = 0; i < closes.length; i++) {
            double c = closes[i];
            k.add(new KLine(LocalDate.of(2024, 3, i + 1), c, c, c, c, 1000));
        }
        // 风险退出后仍持续发 BUY；风险闸门不应允许再次入场并继续侵蚀净值。
        Signal[] signals = {Signal.BUY, Signal.HOLD, Signal.HOLD, Signal.HOLD,
                Signal.BUY, Signal.BUY, Signal.BUY, Signal.BUY, Signal.BUY};
        BacktestEngine engine = new BacktestEngine(new MetricsCalculator());
        var result = engine.run(k, signals, "drawdown-budget", 1_000_000, 0.0,
                1.0, 0.0, 0, 0, 0.25);

        assertEquals(1, result.getMetrics().getTradeCount());
        assertEquals(0.25, result.getMetrics().getMaxDrawdown(), 1e-9);
        assertEquals(900_000.0, result.getEquityCurve()[result.getEquityCurve().length - 1], 1e-6);
    }

    @Test
    void drawdownCooldownAllowsReentryAfterRecoveryWindow() {
        List<KLine> k = new ArrayList<>();
        double[] closes = {100, 100, 75, 75, 75, 100, 150};
        for (int i = 0; i < closes.length; i++) {
            double c = closes[i];
            k.add(new KLine(LocalDate.of(2024, 4, i + 1), c, c, c, c, 1000));
        }
        // 首次风险退出后冷却两根，之后允许在恢复阶段重新入场。
        Signal[] signals = {Signal.BUY, Signal.HOLD, Signal.HOLD, Signal.BUY,
                Signal.HOLD, Signal.HOLD, Signal.HOLD};
        BacktestEngine engine = new BacktestEngine(new MetricsCalculator());
        var result = engine.run(k, signals, "drawdown-cooldown", 1_000_000, 0.0,
                1.0, 0.0, 0, 0, 0.25, 0, 2);

        assertEquals(2, result.getMetrics().getTradeCount(), "冷却结束后应允许第二次完整交易");
        assertTrue(result.getEquityCurve()[result.getEquityCurve().length - 1] > 1_000_000,
                "恢复阶段重新入场后应能捕获后续上涨");
    }

    @Test
    void rollingWindowStrictTimeIsolation() {
        List<KLine> k = synthetic(200, 50);   // 约 10 个月日线
        SignalGenerator gen = new SignalGenerator();
        FitnessEvaluator fe = new FitnessEvaluator(gen, new BacktestEngine(new MetricsCalculator()));
        GeneticAlgorithm ga = new GeneticAlgorithm(fe);
        RollingWindowService svc = new RollingWindowService(ga, gen, fe);

        StrategyConfig cfg = new StrategyConfig();
        cfg.setPopulationSize(10);
        cfg.setGenerations(5);
        cfg.setTrainMonths(3);
        cfg.setPredictMonths(1);
        cfg.setStepMonths(1);

        var indicators = List.<Indicator>of(new MaIndicator(), new RsiIndicator());
        var res = svc.run(k, indicators, cfg);
        assertFalse(res.windows().isEmpty(), "应至少产生一个窗口");

        for (var w : res.windows()) {
            // 预测窗口必须严格晚于训练窗口
            assertTrue(w.getPredictStart().isAfter(w.getTrainEnd()),
                    "时序隔离失败：" + w.getTrainEnd() + " -> " + w.getPredictStart());
        }
        // 相邻窗口训练起点应向前推进（步长 > 0）
        for (int i = 1; i < res.windows().size(); i++) {
            assertTrue(res.windows().get(i).getTrainStart().isAfter(res.windows().get(i - 1).getTrainStart()),
                    "窗口未按步长推进");
        }
    }

    @Test
    void gaReturnsValidChromosome() {
        List<KLine> k = synthetic(90, 100);
        SignalGenerator gen = new SignalGenerator();
        FitnessEvaluator fe = new FitnessEvaluator(gen, new BacktestEngine(new MetricsCalculator()));
        GeneticAlgorithm ga = new GeneticAlgorithm(fe);

        var indicators = List.<Indicator>of(new MaIndicator(), new RsiIndicator());
        double[][][] cache = gen.precomputeScores(indicators, k);
        StrategyConfig cfg = new StrategyConfig();
        cfg.setPopulationSize(12);
        cfg.setGenerations(6);

        Chromosome best = ga.evolve(indicators, k, cache, cfg);
        assertTrue(best.selectedCount() >= 1, "染色体至少选中一个指标");
        assertTrue(best.getBuyThreshold() > 0, "买入阈值应为正");
        assertTrue(best.getSellThreshold() < 0, "卖出阈值应为负");
        assertFalse(Double.isNaN(best.getFitness()), "适应度应已计算");
        assertTrue(best.getFitness() >= -1, "五维盈亏适应度下界：" + best.getFitness());
    }

    // —— 择时模型训练目标校验 ——

    @Test
    void epochTimestampParsedConsistently() {
        // 同一时刻的秒级 / 毫秒级时间戳应解析为同一日期（毫秒级 1659456000000 = 2022-08-03 +08:00）
        LocalDate fromSec = DataValidator.parseDate("1659456000");
        LocalDate fromMillis = DataValidator.parseDate("1659456000000");
        assertEquals(fromSec, fromMillis, "秒/毫秒时间戳应解析为同一日期");
        // 普通日期格式仍正常解析
        assertEquals(LocalDate.of(2023, 1, 3), DataValidator.parseDate("2023-01-03"));
    }


    @Test
    void timingLabelHandCalc() {
        // 价格：100, 110, 90, 120；forecastDays=1
        List<KLine> k = new ArrayList<>();
        double[] closes = {100, 110, 90, 120};
        for (double c : closes) k.add(kl(c));
        double[] lbl = TimingLabel.labels(k, 1, 0.0);
        // t0: 110/100-1 = +0.1  -> +1
        // t1:  90/110-1 ≈ -0.18 -> -1
        // t2: 120/90-1  ≈ +0.33 -> +1
        // t3: 尾部不可标注 -> NaN
        assertEquals(1, lbl[0], 1e-9);
        assertEquals(-1, lbl[1], 1e-9);
        assertEquals(1, lbl[2], 1e-9);
        assertTrue(Double.isNaN(lbl[3]), "尾部应无法标注");
    }

    @Test
    void fitnessNoTradePenaltyAndActiveNonNegative() {
        List<KLine> k = synthetic(120, 100);
        SignalGenerator gen = new SignalGenerator();
        BacktestEngine be = new BacktestEngine(new MetricsCalculator());
        FitnessEvaluator fe = new FitnessEvaluator(gen, be);
        var indicators = List.<Indicator>of(new MaIndicator(), new RsiIndicator());
        double[][][] cache = gen.precomputeScores(indicators, k);
        StrategyConfig cfg = new StrategyConfig();
        cfg.setForecastDays(5);

        int n = indicators.size();
        boolean[] mask = new boolean[n];
        Arrays.fill(mask, true);
        int[] pi = new int[n];
        double[] w = new double[n];
        Arrays.fill(w, 1.0);

        // 全 HOLD：买入阈值极高、卖出阈值极低 → 永不触发 → 无完整交易 → 惩罚分
        Chromosome allHold = new Chromosome(mask, pi, w, 1e9, -1e9);
        assertEquals(FitnessEvaluator.NO_TRADE_PENALTY,
                fe.evaluate(allHold, indicators, k, cache, cfg), 1e-9,
                "无完整交易应得惩罚分");

        // GA 进化出的最优策略：五维盈亏适应度，下界不低于无交易惩罚
        GeneticAlgorithm ga = new GeneticAlgorithm(fe);
        cfg.setPopulationSize(12);
        cfg.setGenerations(6);
        Chromosome best = ga.evolve(indicators, k, cache, cfg);
        assertTrue(best.getFitness() >= FitnessEvaluator.NO_TRADE_PENALTY,
                "最优策略适应度不应低于无交易惩罚：" + best.getFitness());

        // accuracy 作为辅助可验证性指标仍可计算（方向预测准确率 ∈ [0,1] 或 NaN）
        double acc = fe.accuracy(best, indicators.size(), k, cache, cfg);
        assertTrue(Double.isNaN(acc) || (acc >= 0 && acc <= 1),
                "辅助方向准确率应在 [0,1] 或 NaN：" + acc);
    }

    @Test
    void trainedModelRoundTripAlignsIndicators() {
        // 预测入口 predict() 依赖：模型落盘 → 重新加载 → 用保存的指标名重建指标集，
        // 染色体数组必须与训练时同序对齐。本测试校验该往返一致性。
        List<KLine> k = synthetic(120, 100);
        SignalGenerator gen = new SignalGenerator();
        FitnessEvaluator fe = new FitnessEvaluator(gen, new BacktestEngine(new MetricsCalculator()));
        GeneticAlgorithm ga = new GeneticAlgorithm(fe);

        var indicators = List.<Indicator>of(new MaIndicator(), new RsiIndicator());
        double[][][] cache = gen.precomputeScores(indicators, k);
        StrategyConfig cfg = new StrategyConfig();
        cfg.setPopulationSize(10);
        cfg.setGenerations(5);
        cfg.setForecastDays(5);
        Chromosome best = ga.evolve(indicators, k, cache, cfg);

        List<String> names = indicators.stream().map(Indicator::name).toList();
        TrainedModel model = TrainedModel.from(best, "test.csv", cfg.getForecastDays(),
                cfg.getLabelThreshold(), names);

        // 指标名按序保存
        assertEquals(names, model.indicators(), "模型应保存训练时的指标名顺序");

        // 用模型保存的指标名重建指标集，重新生成的信号应与训练时一致
        IndicatorPool pool = new IndicatorPool();
        var rebuilt = pool.selected(model.indicators());
        Chromosome restored = model.toChromosome();
        double[][][] cache2 = gen.precomputeScores(rebuilt, k);
        Signal[] s1 = gen.generate(best, indicators.size(), cache);
        Signal[] s2 = gen.generate(restored, rebuilt.size(), cache2);
        assertEquals(s1.length, s2.length);
        for (int i = 0; i < s1.length; i++) {
            assertEquals(s1[i], s2[i], "重建模型信号应与训练时一致 @bar " + i);
        }
        // 阈值与适应度还原
        assertEquals(best.getBuyThreshold(), restored.getBuyThreshold(), 1e-12);
        assertEquals(best.getSellThreshold(), restored.getSellThreshold(), 1e-12);
        assertEquals(best.getFitness(), restored.getFitness(), 1e-12);
    }

    @Test
    void trainAccuracyRecorded() {
        List<KLine> k = synthetic(200, 50);
        SignalGenerator gen = new SignalGenerator();
        FitnessEvaluator fe = new FitnessEvaluator(gen, new BacktestEngine(new MetricsCalculator()));
        GeneticAlgorithm ga = new GeneticAlgorithm(fe);
        RollingWindowService svc = new RollingWindowService(ga, gen, fe);

        StrategyConfig cfg = new StrategyConfig();
        cfg.setPopulationSize(10);
        cfg.setGenerations(5);
        cfg.setTrainMonths(3);
        cfg.setPredictMonths(1);
        cfg.setStepMonths(1);
        cfg.setForecastDays(5);

        var indicators = List.<Indicator>of(new MaIndicator(), new RsiIndicator());
        var res = svc.run(k, indicators, cfg);
        assertFalse(res.windows().isEmpty(), "应至少产生一个窗口");
        for (var w : res.windows()) {
            assertTrue(Double.isNaN(w.getTrainAccuracy()) || (w.getTrainAccuracy() >= 0 && w.getTrainAccuracy() <= 1),
                    "训练准确率应在 [0,1] 或 NaN");
            assertTrue(Double.isNaN(w.getTestAccuracy()) || (w.getTestAccuracy() >= 0 && w.getTestAccuracy() <= 1),
                    "测试准确率应在 [0,1] 或 NaN");
        }
    }

    private static KLine kl(double close) {
        return new KLine(LocalDate.of(2024, 1, 1), close, close, close, close, 1000);
    }

    private List<KLine> synthetic(int bars, double startPrice) {
        List<KLine> k = new ArrayList<>();
        double p = startPrice;
        LocalDate d = LocalDate.of(2023, 1, 2);
        int day = 0;
        while (k.size() < bars) {
            d = d.plusDays(1);
            if (d.getDayOfWeek().getValue() > 5) continue;  // 仅工作日
            double ret = Math.sin(day / 5.0) * 0.02 + (day % 17 - 8) * 0.001;
            double close = Math.max(1, p * (1 + ret));
            k.add(new KLine(d, close, close * 1.01, close * 0.99, close, 10000));
            p = close;
            day++;
        }
        return k;
    }
} 
