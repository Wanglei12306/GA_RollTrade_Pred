package com.example.quant.indicator.impl;

import com.example.quant.indicator.Indicator;
import com.example.quant.indicator.IndicatorMath;
import com.example.quant.model.KLine;

import java.util.List;

/**
 * OBV 能量潮（成交量类）。
 * OBV 累积：涨加量、跌减量。信号：OBV 高于其均线看多，低于看空。
 */
public class ObvIndicator implements Indicator {

    @Override
    public String name() { return "OBV"; }

    @Override
    public List<double[]> candidateParams() {
        return List.of(new double[]{10}, new double[]{20});
    }

    @Override
    public String paramLabel(double[] params) {
        return "OBV-MA(" + (int) params[0] + ")";
    }

    @Override
    public double[] signalScores(List<KLine> klines, double[] params) {
        int n = klines.size();
        int period = (int) params[0];
        double[] close = IndicatorMath.closes(klines);
        double[] vol = IndicatorMath.volumes(klines);

        double[] obv = new double[n];
        for (int i = 1; i < n; i++) {
            double delta;
            if (close[i] > close[i - 1]) delta = vol[i];
            else if (close[i] < close[i - 1]) delta = -vol[i];
            else delta = 0;
            obv[i] = obv[i - 1] + delta;
        }
        double[] obvMa = IndicatorMath.sma(obv, period);

        double[] score = new double[n];
        for (int i = 0; i < n; i++) {
            if (Double.isNaN(obvMa[i])) {
                score[i] = 0;
                continue;
            }
            double diff = obv[i] - obvMa[i];
            double scale = Math.abs(obvMa[i]) + 1;
            double ratio = diff / scale;
            if (ratio > 0.01) score[i] = 1;
            else if (ratio < -0.01) score[i] = -1;
            else score[i] = 0;
        }
        return score;
    }
}
