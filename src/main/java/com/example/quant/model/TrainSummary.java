package com.example.quant.model;

import java.util.List;

/**
 * 一次择时模型训练的摘要：窗口数、平均训练/测试准确率、落盘模型 id/名/路径等，用于可验证性展示。
 *
 * @param modelId 模型 id（落盘文件 stem），后续预测/回测据此引用，避免同名数据覆盖歧义
 * @param modelName 人类可读模型名（数据名 + 训练时间）
 */
public record TrainSummary(
        String dataName,
        int windowCount,
        double avgTrainAccuracy,
        double avgTestAccuracy,
        double finalFitness,
        String modelId,
        String modelName,
        String modelPath,
        List<Double> trainAccuracies,
        List<Double> testAccuracies
) {}
