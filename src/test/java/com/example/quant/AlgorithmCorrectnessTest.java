package com.example.quant;

import com.example.quant.backtest.BacktestEngine;
import com.example.quant.backtest.MetricsCalculator;
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
import com.example.quant.rolling.RollingWindowService;
import com.example.quant.strategy.SignalGenerator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
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
    void rollingWindowStrictTimeIsolation() {
        List<KLine> k = synthetic(200, 50);   // 约 10 个月日线
        SignalGenerator gen = new SignalGenerator();
        FitnessEvaluator fe = new FitnessEvaluator(gen, new BacktestEngine(new MetricsCalculator()));
        GeneticAlgorithm ga = new GeneticAlgorithm(fe);
        RollingWindowService svc = new RollingWindowService(ga, gen);

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
        assertTrue(best.getFitness() >= -0.5, "适应度不应低于无交易惩罚值");
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
