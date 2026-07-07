package com.example.quant.indicator.impl;

import com.example.quant.indicator.Indicator;
import com.example.quant.indicator.IndicatorMath;
import com.example.quant.model.KLine;

import java.util.List;

/**
 * MACD（趋势/动量类）。
 * DIF = EMA(fast) - EMA(slow)；DEA = EMA(DIF, signal)；HIST = DIF - DEA。
 * 信号：HIST 方向（柱状正看多、负看空），带中性带。
 */
public class MacdIndicator implements Indicator {

    @Override
    public String name() { return "MACD"; }

    @Override
    public List<double[]> candidateParams() {
        return List.of(new double[]{12, 26, 9}, new double[]{5, 20, 7}, new double[]{8, 21, 5});
    }

    @Override
    public String paramLabel(double[] params) {
        return "MACD(" + (int) params[0] + "," + (int) params[1] + "," + (int) params[2] + ")";
    }

    @Override
    public double[] signalScores(List<KLine> klines, double[] params) {
        int n = klines.size();
        double[] close = IndicatorMath.closes(klines);
        double[] emaFast = IndicatorMath.ema(close, (int) params[0]);
        double[] emaSlow = IndicatorMath.ema(close, (int) params[1]);

        double[] dif = new double[n];
        for (int i = 0; i < n; i++) {
            dif[i] = (Double.isNaN(emaFast[i]) || Double.isNaN(emaSlow[i])) ? Double.NaN : emaFast[i] - emaSlow[i];
        }
        double[] dea = IndicatorMath.ema(stripNan(dif), (int) params[2]);
        double[] score = new double[n];
        for (int i = 0; i < n; i++) {
            if (Double.isNaN(dif[i]) || Double.isNaN(dea[i])) {
                score[i] = 0;
                continue;
            }
            double hist = dif[i] - dea[i];
            double ref = close[i] * 0.001;
            if (hist > ref) score[i] = 1;
            else if (hist < -ref) score[i] = -1;
            else score[i] = 0;
        }
        return score;
    }

    /** 将前导 NaN 视作无效点，便于对 DIF 再做 EMA；保持长度不变。 */
    private static double[] stripNan(double[] src) {
        double[] out = new double[src.length];
        System.arraycopy(src, 0, out, 0, src.length);
        return out;
    }
}
