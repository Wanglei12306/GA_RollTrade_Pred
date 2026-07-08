package com.example.quant.indicator;

import com.example.quant.model.KLine;

import java.util.List;

/**
 * 指标数学计算工具：SMA、EMA、标准差、极值等。
 * 所有方法返回与输入等长的数组，预热段用 Double.NaN 表示。
 */
public final class IndicatorMath {

    private IndicatorMath() {}

    public static double[] closes(List<KLine> k) {
        double[] a = new double[k.size()];
        for (int i = 0; i < k.size(); i++) a[i] = k.get(i).getClose();
        return a;
    }
    public static double[] highs(List<KLine> k) {
        double[] a = new double[k.size()];
        for (int i = 0; i < k.size(); i++) a[i] = k.get(i).getHigh();
        return a;
    }
    public static double[] lows(List<KLine> k) {
        double[] a = new double[k.size()];
        for (int i = 0; i < k.size(); i++) a[i] = k.get(i).getLow();
        return a;
    }
    public static double[] volumes(List<KLine> k) {
        double[] a = new double[k.size()];
        for (int i = 0; i < k.size(); i++) a[i] = k.get(i).getVolume();
        return a;
    }

    /** 简单移动平均。前 period-1 个为 NaN。 */
    public static double[] sma(double[] vals, int period) {
        int n = vals.length;
        double[] out = new double[n];
        double sum = 0;
        int count = 0;
        for (int i = 0; i < n; i++) {
            if (Double.isNaN(vals[i])) {
                out[i] = Double.NaN;
                continue;
            }
            sum += vals[i];
            count++;
            if (count < period) {
                out[i] = Double.NaN;
            } else {
                out[i] = sum / period;
                sum -= vals[i - period + 1];
            }
        }
        return out;
    }

    /** 指数移动平均。alpha = 2/(period+1)。首值取前 period 个的均值（不足则取首个有效值）。 */
    public static double[] ema(double[] vals, int period) {
        int n = vals.length;
        double[] out = new double[n];
        double alpha = 2.0 / (period + 1);
        double prev = Double.NaN;
        double sum = 0;
        int count = 0;
        for (int i = 0; i < n; i++) {
            if (Double.isNaN(vals[i])) {
                out[i] = Double.NaN;
                continue;
            }
            count++;
            if (count < period) {
                sum += vals[i];
                out[i] = Double.NaN;
            } else if (count == period) {
                sum += vals[i];
                prev = sum / period;
                out[i] = prev;
            } else {
                prev = alpha * vals[i] + (1 - alpha) * prev;
                out[i] = prev;
            }
        }
        return out;
    }

    /** 总体标准差（用于布林带）。前 period-1 个为 NaN。 */
    public static double[] std(double[] vals, int period, double[] periodMean) {
        int n = vals.length;
        double[] out = new double[n];
        for (int i = period - 1; i < n; i++) {
            if (Double.isNaN(periodMean[i])) {
                out[i] = Double.NaN;
                continue;
            }
            double sumSq = 0;
            for (int j = i - period + 1; j <= i; j++) {
                double d = vals[j] - periodMean[i];
                sumSq += d * d;
            }
            out[i] = Math.sqrt(sumSq / period);
        }
        for (int i = 0; i < period - 1 && i < n; i++) {
            out[i] = Double.NaN;
        }
        return out;
    }

    /** 滚动最高值。前 period-1 个为 NaN。 */
    public static double[] highest(double[] vals, int period) {
        int n = vals.length;
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            if (i < period - 1) {
                out[i] = Double.NaN;
                continue;
            }
            double max = Double.NEGATIVE_INFINITY;
            for (int j = i - period + 1; j <= i; j++) {
                if (vals[j] > max) max = vals[j];
            }
            out[i] = max;
        }
        return out;
    }

    /** 滚动最低值。前 period-1 个为 NaN。 */
    public static double[] lowest(double[] vals, int period) {
        int n = vals.length;
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            if (i < period - 1) {
                out[i] = Double.NaN;
                continue;
            }
            double min = Double.POSITIVE_INFINITY;
            for (int j = i - period + 1; j <= i; j++) {
                if (vals[j] < min) min = vals[j];
            }
            out[i] = min;
        }
        return out;
    }

    /** true range 序列：max(h-l, |h-prevC|, |l-prevC|)。首根用 h-l。 */
    public static double[] trueRange(double[] high, double[] low, double[] close) {
        int n = high.length;
        double[] out = new double[n];
        out[0] = high[0] - low[0];
        for (int i = 1; i < n; i++) {
            double a = high[i] - low[i];
            double b = Math.abs(high[i] - close[i - 1]);
            double c = Math.abs(low[i] - close[i - 1]);
            out[i] = Math.max(a, Math.max(b, c));
        }
        return out;
    }
}
