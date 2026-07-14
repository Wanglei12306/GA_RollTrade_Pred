package com.example.quant.model;

/**
 * 单根 K 线的预测结果：模型对该根 K 线未来 {@code forecastDays} 日方向的预测，
 * 以及（若标签可得）是否预测正确。
 *
 * @param date      K 线日期
 * @param close     收盘价
 * @param signal    阈值化后的交易信号：BUY / SELL / HOLD
 * @param direction 预测方向：BUY→+1，SELL→-1，HOLD→0
 * @param label     真实涨跌方向标签（+1/-1/0）；尾部无法标注的根为 null
 * @param correct   预测方向是否等于标签；label 为 null 时为 false
 */
public record BarPrediction(
        String date,
        double close,
        String signal,
        int direction,
        Integer label,
        boolean correct
) {}
