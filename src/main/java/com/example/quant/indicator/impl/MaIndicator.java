package com.example.quant.indicator.impl;

import com.example.quant.indicator.Indicator;
import com.example.quant.indicator.IndicatorMath;
import com.example.quant.model.KLine;

import java.util.List;

/**
 * 移动平均线（趋势类）。
 * 信号：收盘价相对 MA 的偏离幅度，超过中性带则给出方向。
 */
public class MaIndicator implements Indicator {

    @Override
    public String name() { return "MA"; }

    @Override
    public List<double[]> candidateParams() {
        return List.of(new double[]{5}, new double[]{10}, new double[]{20}, new double[]{60});
    }

    @Override
    public String paramLabel(double[] params) {
        return "MA(" + (int) params[0] + ")";
    }

    @Override
    public double[] signalScores(List<KLine> klines, double[] params) {
        int n = klines.size();
        int period = (int) params[0];
        double[] close = IndicatorMath.closes(klines);
        double[] ma = IndicatorMath.sma(close, period);
        double[] score = new double[n];
        for (int i = 0; i < n; i++) {
            if (Double.isNaN(ma[i]) || ma[i] == 0) {
                score[i] = 0;
                continue;
            }
            // 连续趋势强度：收盘价相对均线的百分比偏离，1% 半饱和。
            double ratio = (close[i] - ma[i]) / ma[i];
            score[i] = IndicatorMath.grade(ratio, 0.01);
        }
        return score;
    }
}
