package com.example.quant.indicator.impl;

import com.example.quant.indicator.Indicator;
import com.example.quant.indicator.IndicatorMath;
import com.example.quant.model.KLine;

import java.util.List;

/**
 * KDJ 随机指标（震荡类）。
 * RSV=(C-LLV)/(HHV-LLV)*100；K=SMA(RSV,k)；D=SMA(K,d)；J=3K-2D。
 * 信号：K/D 趋势 + J 超买超卖（J>100 偏空，J<0 偏多）。
 */
public class KdjIndicator implements Indicator {

    @Override
    public String name() { return "KDJ"; }

    @Override
    public List<double[]> candidateParams() {
        return List.of(new double[]{9, 3, 3}, new double[]{14, 3, 3});
    }

    @Override
    public String paramLabel(double[] params) {
        return "KDJ(" + (int) params[0] + "," + (int) params[1] + "," + (int) params[2] + ")";
    }

    @Override
    public double[] signalScores(List<KLine> klines, double[] params) {
        int n = klines.size();
        int rsvP = (int) params[0];
        int kP = (int) params[1];
        int dP = (int) params[2];
        double[] high = IndicatorMath.highs(klines);
        double[] low = IndicatorMath.lows(klines);
        double[] close = IndicatorMath.closes(klines);
        double[] hhv = IndicatorMath.highest(high, rsvP);
        double[] llv = IndicatorMath.lowest(low, rsvP);

        double[] rsv = new double[n];
        double[] k = new double[n];
        double[] d = new double[n];
        double[] j = new double[n];
        double kPrev = 50, dPrev = 50;
        for (int i = 0; i < n; i++) {
            if (i < rsvP - 1 || Double.isNaN(hhv[i]) || Double.isNaN(llv[i]) || hhv[i] == llv[i]) {
                rsv[i] = 50;
            } else {
                rsv[i] = (close[i] - llv[i]) / (hhv[i] - llv[i]) * 100;
            }
            // K = SMA(RSV, k)，递推形式 2/kP+1
            kPrev = (kPrev * (kP - 1) + rsv[i]) / kP;
            dPrev = (dPrev * (dP - 1) + kPrev) / dP;
            k[i] = kPrev;
            d[i] = dPrev;
            j[i] = 3 * kPrev - 2 * dPrev;
        }

        double[] score = new double[n];
        for (int i = 0; i < n; i++) {
            if (i < rsvP - 1) {
                score[i] = 0;
                continue;
            }
            int s = k[i] > d[i] ? 1 : (k[i] < d[i] ? -1 : 0);
            if (j[i] > 100) s = -1;   // 超买偏空
            if (j[i] < 0) s = 1;       // 超卖偏多
            score[i] = s;
        }
        return score;
    }
}
