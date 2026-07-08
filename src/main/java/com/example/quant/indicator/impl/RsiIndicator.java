package com.example.quant.indicator.impl;

import com.example.quant.indicator.Indicator;
import com.example.quant.indicator.IndicatorMath;
import com.example.quant.model.KLine;

import java.util.List;

/**
 * RSI 相对强弱指标（震荡类）。
 * 信号：<30 超卖看多 +1，>70 超买看空 -1，其余中性（均值回归）。
 */
public class RsiIndicator implements Indicator {

    @Override
    public String name() { return "RSI"; }

    @Override
    public List<double[]> candidateParams() {
        return List.of(new double[]{6}, new double[]{12}, new double[]{24});
    }

    @Override
    public String paramLabel(double[] params) {
        return "RSI(" + (int) params[0] + ")";
    }

    @Override
    public double[] signalScores(List<KLine> klines, double[] params) {
        int n = klines.size();
        int period = (int) params[0];
        double[] close = IndicatorMath.closes(klines);
        double[] rsi = new double[n];
        double[] gain = new double[n];
        double[] loss = new double[n];
        for (int i = 1; i < n; i++) {
            double ch = close[i] - close[i - 1];
            gain[i] = Math.max(ch, 0);
            loss[i] = Math.max(-ch, 0);
        }
        for (int i = period; i < n; i++) {
            double avgGain = 0, avgLoss = 0;
            for (int j = i - period + 1; j <= i; j++) {
                avgGain += gain[j];
                avgLoss += loss[j];
            }
            avgGain /= period;
            avgLoss /= period;
            if (avgLoss == 0) {
                rsi[i] = 100.0;
            } else {
                double rs = avgGain / avgLoss;
                rsi[i] = 100 - 100 / (1 + rs);
            }
        }
        double[] score = new double[n];
        for (int i = 0; i < n; i++) {
            double v = (i >= period) ? rsi[i] : 50;   // 预热段视为中性
            if (v < 30) score[i] = 1;
            else if (v > 70) score[i] = -1;
            else score[i] = 0;
        }
        return score;
    }
}
