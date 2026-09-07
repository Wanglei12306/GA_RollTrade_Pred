package com.example.quant.ga;

import com.example.quant.indicator.Indicator;
import com.example.quant.model.Chromosome;
import com.example.quant.model.KLine;
import com.example.quant.model.StrategyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * 遗传算法核心：种群初始化、锦标赛选择、均匀交叉、变异、精英保留。
 * <p>本项目用 GA <b>优化量化交易策略</b>：染色体即一套完整策略参数（指标选择掩码、指标参数索引、
 * 融合权重、买卖阈值四大维度），训练目标见 {@link FitnessEvaluator}（五维盈亏综合适应度：
 * 年化收益、最大回撤、夏普、胜率、盈亏比）。精英保留保证每代最优适应度单调非降。
 * <p>当前版本唯一训练目标：择时策略优化；选股 / 仓位等目标留待后续扩展。
 */
@Component
public class GeneticAlgorithm {

    private static final Logger log = LoggerFactory.getLogger(GeneticAlgorithm.class);

    private final FitnessEvaluator fitnessEvaluator;
    private final Random random = new Random(42);
    /** 每代注入少量随机个体，避免复杂指标组合在早期收敛后失去探索能力。 */
    private static final double IMMIGRANT_RATE = 0.10;
    /** 负累计收益的帕累托前沿解额外降权，避免“低回撤负收益”的空转策略胜出。 */
    private static final double PARETO_NEGATIVE_RETURN_PENALTY = 0.3;

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
        // 同一滚动窗口重复运行应得到可复现实验结果；不同窗口仍用日期扰动种子保持搜索路径独立。
        long windowSeed = 42L;
        if (trainKlines != null && !trainKlines.isEmpty() && trainKlines.get(0).getDate() != null) {
            windowSeed ^= trainKlines.get(0).getDate().toEpochDay() * 31L;
        }
        random.setSeed(windowSeed);
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
                Chromosome child;
                if (random.nextDouble() < IMMIGRANT_RATE) {
                    // 随机移民不继承父代适应度，下一步会正常评估。
                    child = randomChromosome(n, candCounts);
                } else {
                    Chromosome p1 = tournament(pop);
                    Chromosome p2 = tournament(pop);
                    child = random.nextDouble() < config.getCrossoverRate()
                            ? crossover(p1, p2, n, candCounts)
                            : p1.copy();
                    // 前期扩大搜索半径，后期降低扰动，兼顾探索与收敛。
                    double progress = gens <= 0 ? 1.0 : (double) g / gens;
                    double scheduledRate = config.getMutationRate() * (1.25 - 0.75 * progress);
                    mutate(child, clamp(scheduledRate, 0.01, 0.50), n, candCounts);
                }
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

    /**
     * NSGA-II 多目标进化（参考论文 2111.13364：最大化累计收益 + 最小化最大回撤）。
     * 用非支配排序 + 拥挤距离构造选择压力，μ+λ 环境选择天然保留精英，
     * 最终从帕累托前沿（前沿 0）按“收益为主、回撤为辅”的偏好挑一个可执行策略。
     * <p>染色体 {@code fitness} 记为偏好分 {@code 0.6*收益分 + 0.4*回撤保护分}，
     * 仅用于日志展示与模型落盘；进化过程的选择不依赖该标量。
     */
    public Chromosome evolvePareto(List<Indicator> indicators, List<KLine> trainKlines,
                                   double[][][] trainCache, StrategyConfig config) {
        long windowSeed = 42L;
        if (trainKlines != null && !trainKlines.isEmpty() && trainKlines.get(0).getDate() != null) {
            windowSeed ^= trainKlines.get(0).getDate().toEpochDay() * 31L;
        }
        random.setSeed(windowSeed);
        int n = indicators.size();
        int[] candCounts = candidateCounts(indicators);
        int popSize = config.getPopulationSize();
        int gens = config.getGenerations();
        double retW = clamp(config.getParetoReturnWeight(), 0.0, 1.0);

        List<Chromosome> pop = new ArrayList<>(popSize);
        for (int i = 0; i < popSize; i++) pop.add(randomChromosome(n, candCounts));
        FitnessEvaluator.Objectives[] obj = evaluateObjectivesAll(pop, indicators, trainKlines, trainCache, config);
        log.info("NSGA-II 启动：种群={}, 迭代={}, 初始前沿0规模={}, 收益权重={}", popSize, gens, front0Count(obj), fmt(retW));

        for (int g = 1; g <= gens; g++) {
            double[][] matrix = toMatrix(obj);
            int[] rank = new int[popSize];
            double[] crowd = new double[popSize];
            fastNonDominatedSort(matrix, rank, crowd);

            // 基于(前沿 rank, 拥挤距离)的锦标赛选择产生子代
            List<Chromosome> offspring = new ArrayList<>(popSize);
            while (offspring.size() < popSize) {
                Chromosome child;
                if (random.nextDouble() < IMMIGRANT_RATE) {
                    child = randomChromosome(n, candCounts);
                } else {
                    Chromosome p1 = tournament(pop, rank, crowd);
                    Chromosome p2 = tournament(pop, rank, crowd);
                    child = random.nextDouble() < config.getCrossoverRate()
                            ? crossover(p1, p2, n, candCounts) : p1.copy();
                    double progress = gens <= 0 ? 1.0 : (double) g / gens;
                    double scheduledRate = config.getMutationRate() * (1.25 - 0.75 * progress);
                    mutate(child, clamp(scheduledRate, 0.01, 0.50), n, candCounts);
                }
                child.setFitness(Double.NaN);
                offspring.add(child);
            }
            FitnessEvaluator.Objectives[] offObj = evaluateObjectivesAll(offspring, indicators, trainKlines, trainCache, config);

            // μ+λ 环境选择：合并父代与子代，按(rank 升序, 拥挤距离降序)截断到 popSize。
            List<Chromosome> merged = new ArrayList<>(popSize * 2);
            merged.addAll(pop);
            merged.addAll(offspring);
            FitnessEvaluator.Objectives[] mergedObj = new FitnessEvaluator.Objectives[popSize * 2];
            System.arraycopy(obj, 0, mergedObj, 0, popSize);
            System.arraycopy(offObj, 0, mergedObj, popSize, popSize);

            double[][] mergedMatrix = toMatrix(mergedObj);
            int[] rankM = new int[merged.size()];
            double[] crowdM = new double[merged.size()];
            fastNonDominatedSort(mergedMatrix, rankM, crowdM);

            Integer[] order = new Integer[merged.size()];
            for (int i = 0; i < order.length; i++) order[i] = i;
            Arrays.sort(order, (a, b) -> rankM[a] != rankM[b]
                    ? Integer.compare(rankM[a], rankM[b]) : Double.compare(crowdM[b], crowdM[a]));

            pop = new ArrayList<>(popSize);
            obj = new FitnessEvaluator.Objectives[popSize];
            for (int k = 0; k < popSize; k++) {
                pop.add(merged.get(order[k]));
                obj[k] = mergedObj[order[k]];
            }

            if (g == 1 || g % 20 == 0 || g == gens) {
                log.info("NSGA-II 第 {} 代：前沿0规模={}, 最优偏好分={}",
                        g, front0Count(obj), fmt(bestPreference(pop)));
            }
        }

        double[][] finalMatrix = toMatrix(obj);
        int[] rankF = new int[popSize];
        double[] crowdF = new double[popSize];
        fastNonDominatedSort(finalMatrix, rankF, crowdF);
        int bestIdx = selectParetoIndex(obj, rankF, retW);
        Chromosome best = pop.get(bestIdx).copy();
        best.setFitness(preference(retW, obj[bestIdx].returnScore(), obj[bestIdx].drawdownScore()));
        log.info("NSGA-II 完成：收益分={}, 回撤保护分={}, 偏好适应度={}",
                fmt(obj[bestIdx].returnScore()), fmt(obj[bestIdx].drawdownScore()), fmt(best.getFitness()));
        return best;
    }

    /** 偏好分：retW*收益分 + (1-retW)*回撤保护分；retW 越大越偏“提高收益”。 */
    private static double preference(double retW, double returnScore, double drawdownScore) {
        return retW * returnScore + (1.0 - retW) * drawdownScore;
    }

    /** 从帕累托前沿（rank==0）中按“收益为主、回撤为辅”偏好挑一个解，返回其下标。 */
    private static int selectParetoIndex(FitnessEvaluator.Objectives[] obj, int[] rank, double retW) {
        int bestIdx = 0;
        double bestPref = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < obj.length; i++) {
            if (rank[i] != 0) continue;   // 仅考虑非支配解
            double pref = preference(retW, obj[i].returnScore(), obj[i].drawdownScore());
            if (obj[i].cumulativeReturn() < 0) pref -= PARETO_NEGATIVE_RETURN_PENALTY;
            if (pref > bestPref) { bestPref = pref; bestIdx = i; }
        }
        return bestIdx;
    }

    /** 评估种群双目标，并把可解释的偏好分写入 fitness（仅展示/落盘用）。 */
    private FitnessEvaluator.Objectives[] evaluateObjectivesAll(List<Chromosome> pop, List<Indicator> indicators,
            List<KLine> trainKlines, double[][][] trainCache, StrategyConfig config) {
        double retW = clamp(config.getParetoReturnWeight(), 0.0, 1.0);
        FitnessEvaluator.Objectives[] arr = new FitnessEvaluator.Objectives[pop.size()];
        for (int i = 0; i < pop.size(); i++) {
            arr[i] = fitnessEvaluator.objectives(pop.get(i), indicators, trainKlines, trainCache, config);
            pop.get(i).setFitness(preference(retW, arr[i].returnScore(), arr[i].drawdownScore()));
        }
        return arr;
    }

    /** 目标向量 → 二维矩阵（仅两列：收益分、回撤保护分）。 */
    private static double[][] toMatrix(FitnessEvaluator.Objectives[] objs) {
        double[][] m = new double[objs.length][2];
        for (int i = 0; i < objs.length; i++) {
            m[i][0] = objs[i].returnScore();
            m[i][1] = objs[i].drawdownScore();
        }
        return m;
    }

    /** 前沿 0（非支配解）规模。 */
    private static int front0Count(FitnessEvaluator.Objectives[] obj) {
        double[][] m = toMatrix(obj);
        int[] rank = new int[m.length];
        double[] crowd = new double[m.length];
        fastNonDominatedSort(m, rank, crowd);
        int c = 0;
        for (int r : rank) if (r == 0) c++;
        return c;
    }

    private static double bestPreference(List<Chromosome> pop) {
        double best = Double.NEGATIVE_INFINITY;
        for (Chromosome c : pop) if (c.getFitness() > best) best = c.getFitness();
        return best;
    }

    /** Deb 快速非支配排序：rank[i] 记所属前沿层，crowd[i] 记该层拥挤距离。 */
    private static void fastNonDominatedSort(double[][] obj, int[] rank, double[] crowd) {
        int n = obj.length;
        List<List<Integer>> dominatesList = new ArrayList<>(n);
        int[] dominatedBy = new int[n];
        for (int i = 0; i < n; i++) dominatesList.add(new ArrayList<>());
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                if (dominates(obj[i], obj[j])) dominatesList.get(i).add(j);
                else if (dominates(obj[j], obj[i])) dominatedBy[i]++;
            }
        }
        List<Integer> front = new ArrayList<>();
        for (int i = 0; i < n; i++) if (dominatedBy[i] == 0) { rank[i] = 0; front.add(i); }
        int currentRank = 0;
        List<Integer> current = front;
        while (!current.isEmpty()) {
            assignCrowding(obj, current, crowd);
            List<Integer> next = new ArrayList<>();
            for (int i : current) {
                for (int j : dominatesList.get(i)) {
                    if (--dominatedBy[j] == 0) { rank[j] = currentRank + 1; next.add(j); }
                }
            }
            currentRank++;
            current = next;
        }
    }

    /** 支配判定（最大化）：a 各目标不劣于 b 且至少一个更优。 */
    private static boolean dominates(double[] a, double[] b) {
        boolean anyBetter = false;
        for (int d = 0; d < a.length; d++) {
            if (a[d] < b[d]) return false;
            if (a[d] > b[d]) anyBetter = true;
        }
        return anyBetter;
    }

    /** 前沿内拥挤距离：边界点设为无穷，其余按各目标归一化间距累加。 */
    private static void assignCrowding(double[][] obj, List<Integer> front, double[] crowd) {
        for (int i : front) crowd[i] = 0;
        if (front.size() <= 2) {
            for (int i : front) crowd[i] = Double.POSITIVE_INFINITY;
            return;
        }
        int m = obj[0].length;
        for (int d = 0; d < m; d++) {
            final int dim = d;
            List<Integer> idx = new ArrayList<>(front);
            idx.sort(Comparator.comparingDouble(i -> obj[i][dim]));
            crowd[idx.get(0)] = Double.POSITIVE_INFINITY;
            crowd[idx.get(idx.size() - 1)] = Double.POSITIVE_INFINITY;
            double range = obj[idx.get(idx.size() - 1)][d] - obj[idx.get(0)][d];
            if (range < 1e-12) continue;
            for (int k = 1; k < idx.size() - 1; k++) {
                crowd[idx.get(k)] += (obj[idx.get(k + 1)][d] - obj[idx.get(k - 1)][d]) / range;
            }
        }
    }

    /** 帕累托锦标赛：先比前沿层（越小越优），再比拥挤距离（越大越分散）。 */
    private Chromosome tournament(List<Chromosome> pop, int[] rank, double[] crowd) {
        int k = 3;
        int best = -1;
        for (int i = 0; i < k; i++) {
            int cand = random.nextInt(pop.size());
            if (best == -1 || better(cand, best, rank, crowd)) best = cand;
        }
        return pop.get(best);
    }

    private static boolean better(int a, int b, int[] rank, double[] crowd) {
        if (rank[a] != rank[b]) return rank[a] < rank[b];
        return crowd[a] > crowd[b];
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
