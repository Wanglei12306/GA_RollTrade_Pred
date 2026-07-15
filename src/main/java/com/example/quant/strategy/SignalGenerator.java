package com.example.quant.strategy;

import com.example.quant.indicator.Indicator;
import com.example.quant.model.Chromosome;
import com.example.quant.model.KLine;
import com.example.quant.model.Signal;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 交易信号生成器。
 * <p>核心算法：
 * <ol>
 *   <li>预计算每个指标每组候选参数的信号评分（与染色体无关，按窗口缓存一次）。</li>
 *   <li>对给定染色体，按掩码选中指标、按权重加权融合，得到每根 K 线的综合得分。</li>
 *   <li>依据买卖阈值把得分转化为 BUY/SELL/HOLD 信号。</li>
 * </ol>
 */
@Component
public class SignalGenerator {

    /**
     * 预计算信号评分缓存：cache[indicatorIndex][candidateIndex][bar]。
     * 与染色体无关，每个训练/预测窗口只算一次，大幅加速遗传算法评估。
     */
    public double[][][] precomputeScores(List<Indicator> indicators, List<KLine> klines) {
        double[][][] cache = new double[indicators.size()][][];
        for (int i = 0; i < indicators.size(); i++) {
            List<double[]> candidates = indicators.get(i).candidateParams();
            cache[i] = new double[candidates.size()][];
            for (int c = 0; c < candidates.size(); c++) {
                cache[i][c] = indicators.get(i).signalScores(klines, candidates.get(c));
            }
        }
        return cache;
    }

    /** 计算染色体的综合得分序列（未阈值化），长度等于 K 线数。 */
    public double[] compositeScores(Chromosome chr, int indicatorCount, double[][][] cache) {
        int bars = cache.length == 0 || cache[0].length == 0 ? 0
                : cache[0][0].length;
        double[] scores = new double[bars];
        boolean[] mask = chr.getMask();
        int[] paramIndex = chr.getParamIndex();
        double[] weight = chr.getWeight();

        double wsum = 0;
        double[] weighted = new double[bars];
        for (int i = 0; i < indicatorCount; i++) {
            if (!mask[i]) continue;
            int cand = clampParam(paramIndex[i], cache[i].length);
            double w = weight[i];
            if (w <= 0) w = 0.0001;
            double[] s = cache[i][cand];
            for (int b = 0; b < bars; b++) weighted[b] += w * s[b];
            wsum += w;
        }
        if (wsum <= 0) {
            // 无选中指标，全中性
            return scores;
        }
        for (int b = 0; b < bars; b++) scores[b] = weighted[b] / wsum;
        return scores;
    }

    /** 生成 BUY/SELL/HOLD 信号序列。 */
    public Signal[] generate(Chromosome chr, int indicatorCount, double[][][] cache) {
        double[] scores = compositeScores(chr, indicatorCount, cache);
        Signal[] signals = new Signal[scores.length];
        for (int b = 0; b < scores.length; b++) {
            signals[b] = Signal.of(scores[b], chr.getBuyThreshold(), chr.getSellThreshold());
        }
        return signals;
    }

    private int clampParam(int idx, int candidateCount) {
        if (candidateCount <= 0) return 0;
        if (idx < 0) return 0;
        if (idx >= candidateCount) return candidateCount - 1;
        return idx;
    }

    /**
     * 切片缓存 bar 维度 [from, to)：在已预计算的窗口上划分子区间。
     * 切片复用原始评分序列，保留指标历史（前段 bar 的 lookback 已算入），无冷启动。
     */
    public static double[][][] sliceBars(double[][][] cache, int from, int to) {
        double[][][] out = new double[cache.length][][];
        for (int i = 0; i < cache.length; i++) {
            out[i] = new double[cache[i].length][];
            for (int c = 0; c < cache[i].length; c++) {
                out[i][c] = java.util.Arrays.copyOfRange(cache[i][c], from, to);
            }
        }
        return out;
    }
}
