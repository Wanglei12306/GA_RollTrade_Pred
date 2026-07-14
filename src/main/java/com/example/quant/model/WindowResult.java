package com.example.quant.model;

import java.time.LocalDate;

/**
 * 单个滚动窗口的训练/预测结果。
 * <p>trainAccuracy / testAccuracy 为择时方向预测准确率（[0,1]，无有效样本为 NaN），
 * 用于「模型训练效果」可验证性展示。
 */
public class WindowResult {

    private final LocalDate trainStart;
    private final LocalDate trainEnd;
    private final LocalDate predictStart;
    private final LocalDate predictEnd;
    private final double bestFitness;
    private final double trainAccuracy;
    private final double testAccuracy;
    private final Chromosome bestChromosome;

    public WindowResult(LocalDate trainStart, LocalDate trainEnd, LocalDate predictStart,
                        LocalDate predictEnd, double bestFitness,
                        double trainAccuracy, double testAccuracy,
                        Chromosome bestChromosome) {
        this.trainStart = trainStart;
        this.trainEnd = trainEnd;
        this.predictStart = predictStart;
        this.predictEnd = predictEnd;
        this.bestFitness = bestFitness;
        this.trainAccuracy = trainAccuracy;
        this.testAccuracy = testAccuracy;
        this.bestChromosome = bestChromosome;
    }

    public LocalDate getTrainStart() { return trainStart; }
    public LocalDate getTrainEnd() { return trainEnd; }
    public LocalDate getPredictStart() { return predictStart; }
    public LocalDate getPredictEnd() { return predictEnd; }
    public double getBestFitness() { return bestFitness; }
    public double getTrainAccuracy() { return trainAccuracy; }
    public double getTestAccuracy() { return testAccuracy; }
    public Chromosome getBestChromosome() { return bestChromosome; }
}
