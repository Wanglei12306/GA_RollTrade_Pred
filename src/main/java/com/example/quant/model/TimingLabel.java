package com.example.quant.model;

import java.util.List;

/**
 * 择时标签生成器（项目当前唯一训练目标：择时）。
 * <p>用未来 {@code forecastDays} 根的收益率方向作为监督标签：
 * <ul>
 *   <li>r &gt; threshold  → +1（未来上涨，应持/开仓）</li>
 *   <li>r &lt; -threshold → -1（未来下跌，应空仓/平仓）</li>
 *   <li>否则             → 0（中性，涨跌幅不足以判定方向）</li>
 *   <li>尾部 forecastDays 根无法标注 → NaN，训练/评估时跳过</li>
 * </ul>
 * 未来收益 r = close[t + forecastDays] / close[t] - 1。
 * <p>纯静态、无状态，便于单测手算验证（对应「算法可验证」要求）。
 */
public final class TimingLabel {

    private TimingLabel() {}

    /**
     * 生成与 klines 等长的择时标签序列。
     *
     * @param klines       K 线序列（需按时间升序）
     * @param forecastDays 预测 horizon（向前看几根），&gt; 0
     * @param threshold    涨跌中性带，&gt;= 0
     * @return 标签序列，值域 {+1, -1, 0, NaN}
     */
    public static double[] labels(List<KLine> klines, int forecastDays, double threshold) {
        if (forecastDays <= 0) {
            throw new IllegalArgumentException("forecastDays 必须 > 0：" + forecastDays);
        }
        int n = klines.size();
        double[] out = new double[n];
        int lastLabelable = n - forecastDays;   // 末尾 forecastDays 根无法标注
        for (int t = 0; t < n; t++) {
            if (t >= lastLabelable) {
                out[t] = Double.NaN;
                continue;
            }
            double now = klines.get(t).getClose();
            double future = klines.get(t + forecastDays).getClose();
            if (now <= 0) {
                out[t] = Double.NaN;
                continue;
            }
            double r = future / now - 1.0;
            if (r > threshold) out[t] = 1;
            else if (r < -threshold) out[t] = -1;
            else out[t] = 0;
        }
        return out;
    }

    /** 统计有效（非 NaN）标签数量，便于准确率分母计算。 */
    public static int validCount(double[] labels) {
        int c = 0;
        for (double v : labels) if (!Double.isNaN(v)) c++;
        return c;
    }
}
