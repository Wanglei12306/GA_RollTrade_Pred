package com.example.quant.model;

/**
 * 交易信号类型（第一版仅做多）。
 */
public enum Signal {
    BUY,   // 买入 / 开仓
    SELL,  // 卖出 / 平仓
    HOLD;  // 持仓 / 观望

    public static Signal of(double score, double buyThreshold, double sellThreshold) {
        if (score >= buyThreshold) return BUY;
        if (score <= sellThreshold) return SELL;
        return HOLD;
    }
}
