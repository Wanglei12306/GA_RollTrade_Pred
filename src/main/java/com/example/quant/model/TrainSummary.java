package com.example.quant.model;

import java.util.List;

/**
 * 一次择时模型训练的摘要：窗口数、平均训练/测试准确率、落盘模型路径等，用于可验证性展示。
 */
public record TrainSummary(
        String dataName,
        int windowCount,
        double avgTrainAccuracy,
        double avgTestAccuracy,
        double finalFitness,
        String modelPath,
        List<Double> trainAccuracies,
        List<Double> testAccuracies
) {}
