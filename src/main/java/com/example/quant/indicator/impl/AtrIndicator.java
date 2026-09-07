package com.example.quant.indicator.impl;

import com.example.quant.indicator.Indicator;
import com.example.quant.indicator.IndicatorMath;
import com.example.quant.model.KLine;

import java.util.List;

/**
 * ATR 平均真实波幅（波动率类，作动量突破过滤）。
 * 信号：当日收盘变化超过 1 倍 ATR 视为突破方向。
 */
public class AtrIndicator implements Indicator {

    @Override
    public String name() { return "ATR"; }

    @Override
    public List<double[]> candidateParams() {
        return List.of(new double[]{14}, new double[]{20});
    }

    @Override
    public String paramLabel(double[] params) {
        return "ATR(" + (int) params[0] + ")";
    }

    @Override
    public double[] signalScores(List<KLine> klines, double[] params) {
        int n = klines.size();
        int period = (int) params[0];
        double[] high = IndicatorMath.highs(klines);
        double[] low = IndicatorMath.lows(klines);
        double[] close = IndicatorMath.closes(klines);
        double[] tr = IndicatorMath.trueRange(high, low, close);
        double[] atr = IndicatorMath.sma(tr, period);

        double[] score = new double[n];
        for (int i = 0; i < n; i++) {
            if (i < 1 || Double.isNaN(atr[i]) || atr[i] == 0) {
                score[i] = 0;
                continue;
            }
            // 连续突破强度：单根变化以 ATR 归一化，1 倍 ATR 即满信号（动量突破方向）。
            double change = close[i] - close[i - 1];
            score[i] = IndicatorMath.clamp01(change / atr[i], 1.0);
        }
        return score;
    }
}
