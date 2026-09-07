package com.example.quant.indicator.impl;

import com.example.quant.indicator.Indicator;
import com.example.quant.indicator.IndicatorMath;
import com.example.quant.model.KLine;

import java.util.List;

/**
 * CCI 顺势指标（乖离/顺势类）。
 * CCI = (TP - SMA(TP)) / (0.015 * MeanDev)，TP=(H+L+C)/3。
 * 信号：>100 超买偏空，<-100 超卖偏多。
 */
public class CciIndicator implements Indicator {

    @Override
    public String name() { return "CCI"; }

    @Override
    public List<double[]> candidateParams() {
        return List.of(new double[]{14}, new double[]{20});
    }

    @Override
    public String paramLabel(double[] params) {
        return "CCI(" + (int) params[0] + ")";
    }

    @Override
    public double[] signalScores(List<KLine> klines, double[] params) {
        int n = klines.size();
        int period = (int) params[0];
        double[] high = IndicatorMath.highs(klines);
        double[] low = IndicatorMath.lows(klines);
        double[] close = IndicatorMath.closes(klines);

        double[] tp = new double[n];
        for (int i = 0; i < n; i++) tp[i] = (high[i] + low[i] + close[i]) / 3.0;
        double[] tpMa = IndicatorMath.sma(tp, period);

        double[] cci = new double[n];
        for (int i = period - 1; i < n; i++) {
            if (Double.isNaN(tpMa[i])) continue;
            double meanDev = 0;
            for (int j = i - period + 1; j <= i; j++) meanDev += Math.abs(tp[j] - tpMa[i]);
            meanDev /= period;
            if (meanDev == 0) {
                cci[i] = 0;
            } else {
                cci[i] = (tp[i] - tpMa[i]) / (0.015 * meanDev);
            }
        }

        double[] score = new double[n];
        for (int i = 0; i < n; i++) {
            if (i < period - 1) {
                score[i] = 0;
                continue;
            }
            // 连续乖离：CCI 本身已按均值离差归一化，±150 满信号（超买偏空、超卖偏多）。
            score[i] = IndicatorMath.clamp01(-cci[i], 150);
        }
        return score;
    }
}
