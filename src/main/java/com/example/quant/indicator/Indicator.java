package com.example.quant.indicator;

import com.example.quant.model.KLine;

import java.util.List;

/**
 * 技术指标接口。每个指标按标准含义输出每根 K 线的信号评分（[-1,1] 或 {-1,0,+1}），
 * 用于遗传算法的加权融合。score[i] 对应 klines.get(i)。
 */
public interface Indicator {

    /** 指标名（如 MA、MACD）。 */
    String name();

    /** 候选参数集（多组差异化参数，避免参数固化）。 */
    List<double[]> candidateParams();

    /** 参数可读标签（如 "MA(20)"）。 */
    String paramLabel(double[] params);

    /**
     * 每根 K 线的信号评分，长度等于 klines.size()。预热段返回 0（视为中性）。
     * +1 看多，-1 看空，0 中性。
     */
    double[] signalScores(List<KLine> klines, double[] params);
}
