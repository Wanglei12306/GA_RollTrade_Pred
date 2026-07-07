package com.example.quant.strategy;

import com.example.quant.indicator.Indicator;
import com.example.quant.model.Chromosome;
import com.example.quant.model.Signal;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 固定参数策略（用于与遗传优化策略对比，验证动态寻优有效性）。
 * 固定：启用全部选中指标、取每组候选参数的第 0 组、等权重、买卖阈值 ±0.3。
 */
@Component
public class FixedStrategy {

    private static final double FIXED_BUY = 0.3;
    private static final double FIXED_SELL = -0.3;

    /** 构造固定染色体。 */
    public Chromosome buildFixedChromosome(List<Indicator> indicators) {
        int n = indicators.size();
        boolean[] mask = new boolean[n];
        int[] paramIndex = new int[n];
        double[] weight = new double[n];
        for (int i = 0; i < n; i++) {
            mask[i] = true;
            paramIndex[i] = 0;
            weight[i] = 1.0;
        }
        return new Chromosome(mask, paramIndex, weight, FIXED_BUY, FIXED_SELL);
    }

    /** 用固定染色体生成信号。 */
    public Signal[] generate(List<Indicator> indicators, double[][][] cache,
                             SignalGenerator signalGenerator) {
        Chromosome fixed = buildFixedChromosome(indicators);
        return signalGenerator.generate(fixed, indicators.size(), cache);
    }
}
