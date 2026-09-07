package com.example.quant.indicator.impl;

import com.example.quant.indicator.Indicator;
import com.example.quant.indicator.IndicatorMath;
import com.example.quant.model.KLine;

import java.util.List;

/**
 * 动量指标 MOM/ROC（动量类）。
 * 信号：ROC = close[t]/close[t-N] - 1，正动量看多 +1，负动量看空 -1，预热段中性 0。
 * 反映价格变化速率，用于捕捉趋势强度与方向。
 */
public class MomentumIndicator implements Indicator {

    @Override
    public String name() { return "MOM"; }

    @Override
    public List<double[]> candidateParams() {
        return List.of(new double[]{10}, new double[]{20}, new double[]{30});
    }

    @Override
    public String paramLabel(double[] params) {
        return "MOM(" + (int) params[0] + ")";
    }

    @Override
    public double[] signalScores(List<KLine> klines, double[] params) {
        int n = klines.size();
        int period = (int) params[0];
        double[] close = IndicatorMath.closes(klines);
        double[] score = new double[n];
        for (int i = 0; i < n; i++) {
            if (i < period) {
                score[i] = 0;   // 预热段中性
                continue;
            }
            double prev = close[i - period];
            if (prev <= 0) {
                score[i] = 0;
                continue;
            }
            double roc = close[i] / prev - 1;
            // 连续动量：ROC 的百分比变化率，3% 半饱和（正动量看多、负动量看空）。
            score[i] = IndicatorMath.grade(roc, 0.03);
        }
        return score;
    }
}
