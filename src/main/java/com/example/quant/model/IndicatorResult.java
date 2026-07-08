package com.example.quant.model;

/**
 * 技术指标计算结果。values 与 K 线序列等长，预热段用 Double.NaN 表示。
 */
public class IndicatorResult {

    private final String name;
    private final String paramLabel;   // 参数描述，如 "MA(20)"
    private final double[] values;

    public IndicatorResult(String name, String paramLabel, double[] values) {
        this.name = name;
        this.paramLabel = paramLabel;
        this.values = values;
    }

    public String getName() { return name; }
    public String getParamLabel() { return paramLabel; }
    public double[] getValues() { return values; }
}
