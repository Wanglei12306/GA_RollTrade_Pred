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

    /**
     * 自适应中性带阈值：{@code configured > 0} 时直接采用；否则按窗口内 horizon 收益率
     * （close[t+h]/close[t]-1，与标签同尺度）的标准差一半自适应，使中性带随波动率伸缩，
     * 跨标的/跨周期无需手调。样本不足时退化为 0（纯二分类）。
     */
    public static double adaptiveThreshold(List<KLine> klines, int forecastDays, double configured) {
        if (configured > 0) return configured;
        int n = klines.size();
        int last = n - forecastDays;          // 可计算 horizon 收益的末位
        if (last < 3) return 0.0;
        double sum = 0;
        int cnt = 0;
        for (int t = 0; t < last; t++) {
            double p0 = klines.get(t).getClose();
            if (p0 <= 0) continue;
            sum += klines.get(t + forecastDays).getClose() / p0 - 1.0;
            cnt++;
        }
        if (cnt < 3) return 0.0;
        double mean = sum / cnt;
        double sq = 0;
        for (int t = 0; t < last; t++) {
            double p0 = klines.get(t).getClose();
            if (p0 <= 0) continue;
            double r = klines.get(t + forecastDays).getClose() / p0 - 1.0 - mean;
            sq += r * r;
        }
        return 0.5 * Math.sqrt(sq / (cnt - 1));
    }

    /**
     * 方向预测准确率（辅助可验证性指标，[0,1]，无可标注样本返回 NaN）。
     * 把信号 BUY/SELL/HOLD 映射为 {+1,-1,0}，与 horizon 涨跌方向标签比对，<b>全部可标注 bar 计入分母</b>：
     * <ul>
     *   <li>HOLD 匹配中性标签(0) → 正确（正确避险/观望震荡）</li>
     *   <li>HOLD 遇 ±1 标签 → 错误（漏判了行情方向）</li>
     *   <li>BUY/SELL 方向命中 → 正确；反向或过度交易(±1 vs 0) → 错误</li>
     * </ul>
     * 配合 {@link #adaptiveThreshold} 的中性带，HOLD 不再像 threshold=0 时那样被自动判错，
     * 同时保留全部样本以避免小窗口频繁 NaN。仅当窗口可标注 bar 不足时返回 NaN。
     * 表态率 {@link #commitmentRate} 伴生展示，暴露靠 HOLD 拉高准确率的保守策略。
     */
    public static double directionalAccuracy(Signal[] signals, List<KLine> klines,
                                             int forecastDays, double threshold) {
        double[] labels = labels(klines, forecastDays, threshold);
        int n = Math.min(labels.length, signals.length);
        int valid = 0, correct = 0;
        for (int t = 0; t < n; t++) {
            if (Double.isNaN(labels[t])) continue;
            valid++;
            int pred = switch (signals[t]) {
                case BUY -> 1;
                case SELL -> -1;
                case HOLD -> 0;
            };
            if (pred == (int) labels[t]) correct++;
        }
        return valid > 0 ? (double) correct / valid : Double.NaN;
    }

    /**
     * 表态率（与准确率伴生展示）：标签可得的 bar 中，策略发出 BUY/SELL（非 HOLD）的占比，[0,1]。
     * 暴露「靠全 HOLD 在小样本上刷高准确率」的策略——表态率过低时准确率不具备统计意义。
     */
    public static double commitmentRate(Signal[] signals, List<KLine> klines, int forecastDays) {
        int n = Math.min(klines.size(), signals.length);
        int lastLabelable = klines.size() - forecastDays;
        int labelable = 0, committed = 0;
        for (int t = 0; t < n; t++) {
            if (t >= lastLabelable) continue;  // 尾部无法标注，不计入
            labelable++;
            if (signals[t] != Signal.HOLD) committed++;
        }
        return labelable > 0 ? (double) committed / labelable : Double.NaN;
    }
}
