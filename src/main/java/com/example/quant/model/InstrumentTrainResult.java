package com.example.quant.model;

/**
 * 单个标的在批量训练中的结果：成功则带训练摘要，失败则带错误信息。
 */
public record InstrumentTrainResult(
        String name,
        boolean success,
        String error,
        TrainSummary summary
) {}
