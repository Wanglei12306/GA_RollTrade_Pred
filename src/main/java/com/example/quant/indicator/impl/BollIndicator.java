package com.example.quant.indicator.impl;

import com.example.quant.indicator.Indicator;
import com.example.quant.indicator.IndicatorMath;
import com.example.quant.model.KLine;

import java.util.List;

/**
 * 布林带 BOLL（波动类）。
 * 信号：收盘价在带内的相对位置 [下轨→+1, 上轨→-1]，连续评分（均值回归）。
 */
public class BollIndicator implements Indicator {

    @Override
    public String name() { return "BOLL"; }

    @Override
    public List<double[]> candidateParams() {
        return List.of(new double[]{20, 2.0}, new double[]{20, 2.5}, new double[]{10, 2.0});
    }

    @Override
    public String paramLabel(double[] params) {
        return "BOLL(" + (int) params[0] + "," + params[1] + ")";
    }

    @Override
    public double[] signalScores(List<KLine> klines, double[] params) {
        int n = klines.size();
        int period = (int) params[0];
        double mult = params[1];
        double[] close = IndicatorMath.closes(klines);
        double[] mid = IndicatorMath.sma(close, period);
        double[] std = IndicatorMath.std(close, period, mid);
        double[] score = new double[n];
        for (int i = 0; i < n; i++) {
            if (Double.isNaN(mid[i]) || Double.isNaN(std[i]) || std[i] == 0) {
                score[i] = 0;
                continue;
            }
            double upper = mid[i] + mult * std[i];
            double lower = mid[i] - mult * std[i];
            double pos = (close[i] - lower) / (upper - lower);
            pos = Math.max(0, Math.min(1, pos));
            score[i] = 1 - 2 * pos;   // 下轨 +1，上轨 -1
        }
        return score;
    }
}
