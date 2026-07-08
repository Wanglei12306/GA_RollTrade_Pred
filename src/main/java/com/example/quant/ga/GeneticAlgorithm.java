package com.example.quant.ga;

import com.example.quant.indicator.Indicator;
import com.example.quant.model.Chromosome;
import com.example.quant.model.KLine;
import com.example.quant.model.StrategyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * 遗传算法核心：种群初始化、锦标赛选择、均匀交叉、变异、精英保留。
 * <p>编码维度：指标选择掩码、指标参数索引、融合权重、买卖阈值（docx 四大维度）。
 * 精英保留保证每代最优适应度单调非降。
 */
@Component
public class GeneticAlgorithm {

    private static final Logger log = LoggerFactory.getLogger(GeneticAlgorithm.class);

    private final FitnessEvaluator fitnessEvaluator;
    private final Random random = new Random(42);

    public GeneticAlgorithm(FitnessEvaluator fitnessEvaluator) {
        this.fitnessEvaluator = fitnessEvaluator;
    }

    /**
     * 在一个训练窗口上进化出最优染色体。
     *
     * @param trainCache 该窗口预计算的指标信号评分缓存
     */
    public Chromosome evolve(List<Indicator> indicators, List<KLine> trainKlines,
                             double[][][] trainCache, StrategyConfig config) {
        int n = indicators.size();
        int[] candCounts = candidateCounts(indicators);
        int popSize = config.getPopulationSize();
        int gens = config.getGenerations();

        List<Chromosome> pop = new ArrayList<>(popSize);
        for (int i = 0; i < popSize; i++) pop.add(randomChromosome(n, candCounts));
        evaluateAll(pop, indicators, trainKlines, trainCache, config);

        Chromosome best = bestOf(pop);
        log.info("GA 启动：种群={}, 迭代={}, 初始最优适应度={}", popSize, gens, fmt(best.getFitness()));

        for (int g = 1; g <= gens; g++) {
            pop.sort(Comparator.comparingDouble(Chromosome::getFitness).reversed());

            List<Chromosome> next = new ArrayList<>(popSize);
            // 精英保留：Top 2 直接进入下一代（不变异，保证单调非降）
            next.add(pop.get(0).copy());
            if (popSize > 1) next.add(pop.get(1).copy());

            while (next.size() < popSize) {
                Chromosome p1 = tournament(pop);
                Chromosome p2 = tournament(pop);
                Chromosome child = random.nextDouble() < config.getCrossoverRate()
                        ? crossover(p1, p2, n, candCounts)
                        : p1.copy();
                mutate(child, config.getMutationRate(), n, candCounts);
                child.setFitness(Double.NaN);
                next.add(child);
            }

            evaluateAll(next, indicators, trainKlines, trainCache, config);
            pop = next;

            Chromosome genBest = bestOf(pop);
            if (genBest.getFitness() > best.getFitness()) best = genBest.copy();

            if (g == 1 || g % 20 == 0 || g == gens) {
                log.info("GA 第 {} 代：最优适应度={}", g, fmt(genBest.getFitness()));
            }
        }

        log.info("GA 完成：最终最优适应度={}", fmt(best.getFitness()));
        return best;
    }

    private void evaluateAll(List<Chromosome> pop, List<Indicator> indicators,
                             List<KLine> trainKlines, double[][][] trainCache, StrategyConfig config) {
        for (Chromosome c : pop) {
            if (Double.isNaN(c.getFitness())) {
                c.setFitness(fitnessEvaluator.evaluate(c, indicators, trainKlines, trainCache, config));
            }
        }
    }

    private Chromosome bestOf(List<Chromosome> pop) {
        return pop.stream().max(Comparator.comparingDouble(Chromosome::getFitness)).orElseThrow();
    }

    private Chromosome tournament(List<Chromosome> pop) {
        int k = 3;
        Chromosome best = null;
        for (int i = 0; i < k; i++) {
            Chromosome cand = pop.get(random.nextInt(pop.size()));
            if (best == null || cand.getFitness() > best.getFitness()) best = cand;
        }
        return best;
    }

    private Chromosome crossover(Chromosome a, Chromosome b, int n, int[] candCounts) {
        boolean[] mask = new boolean[n];
        int[] paramIndex = new int[n];
        double[] weight = new double[n];
        for (int i = 0; i < n; i++) {
            mask[i] = random.nextBoolean() ? a.getMask()[i] : b.getMask()[i];
            paramIndex[i] = random.nextBoolean() ? a.getParamIndex()[i] : b.getParamIndex()[i];
            weight[i] = random.nextBoolean() ? a.getWeight()[i] : b.getWeight()[i];
        }
        double buy = 0.5 * (a.getBuyThreshold() + b.getBuyThreshold());
        double sell = 0.5 * (a.getSellThreshold() + b.getSellThreshold());
        Chromosome child = new Chromosome(mask, paramIndex, weight, buy, sell);
        ensureValid(child, n, candCounts);
        return child;
    }

    private void mutate(Chromosome c, double rate, int n, int[] candCounts) {
        for (int i = 0; i < n; i++) {
            if (random.nextDouble() < rate) c.getMask()[i] = !c.getMask()[i];
            if (random.nextDouble() < rate && candCounts[i] > 0)
                c.getParamIndex()[i] = random.nextInt(candCounts[i]);
            if (random.nextDouble() < rate)
                c.getWeight()[i] = clamp(c.getWeight()[i] + (random.nextDouble() - 0.5) * 0.6, 0.1, 1.5);
        }
        if (random.nextDouble() < rate)
            c.setBuyThreshold(clamp(c.getBuyThreshold() + (random.nextDouble() - 0.5) * 0.2, 0.05, 0.8));
        if (random.nextDouble() < rate)
            c.setSellThreshold(clamp(c.getSellThreshold() + (random.nextDouble() - 0.5) * 0.2, -0.8, -0.05));
        ensureValid(c, n, candCounts);
    }

    private Chromosome randomChromosome(int n, int[] candCounts) {
        boolean[] mask = new boolean[n];
        int[] paramIndex = new int[n];
        double[] weight = new double[n];
        for (int i = 0; i < n; i++) {
            mask[i] = random.nextDouble() < 0.6;
            paramIndex[i] = candCounts[i] > 0 ? random.nextInt(candCounts[i]) : 0;
            weight[i] = 0.2 + random.nextDouble() * 0.8;
        }
        double buy = 0.1 + random.nextDouble() * 0.5;
        double sell = -(0.1 + random.nextDouble() * 0.5);
        Chromosome c = new Chromosome(mask, paramIndex, weight, buy, sell);
        ensureValid(c, n, candCounts);
        return c;
    }

    /** 保证至少选中一个指标，避免空策略。 */
    private void ensureValid(Chromosome c, int n, int[] candCounts) {
        boolean any = false;
        for (boolean b : c.getMask()) if (b) { any = true; break; }
        if (!any && n > 0) c.getMask()[random.nextInt(n)] = true;
        for (int i = 0; i < n; i++) {
            if (candCounts[i] > 0 && c.getParamIndex()[i] >= candCounts[i])
                c.getParamIndex()[i] = random.nextInt(candCounts[i]);
        }
    }

    private int[] candidateCounts(List<Indicator> indicators) {
        int[] counts = new int[indicators.size()];
        for (int i = 0; i < indicators.size(); i++)
            counts[i] = indicators.get(i).candidateParams().size();
        return counts;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static String fmt(double v) {
        return Double.isNaN(v) ? "NaN" : String.format("%.4f", v);
    }
}
