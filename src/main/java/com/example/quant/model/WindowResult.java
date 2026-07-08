package com.example.quant.model;

import java.time.LocalDate;

/**
 * 单个滚动窗口的训练/预测结果。
 */
public class WindowResult {

    private final LocalDate trainStart;
    private final LocalDate trainEnd;
    private final LocalDate predictStart;
    private final LocalDate predictEnd;
    private final double bestFitness;
    private final Chromosome bestChromosome;

    public WindowResult(LocalDate trainStart, LocalDate trainEnd, LocalDate predictStart,
                        LocalDate predictEnd, double bestFitness, Chromosome bestChromosome) {
        this.trainStart = trainStart;
        this.trainEnd = trainEnd;
        this.predictStart = predictStart;
        this.predictEnd = predictEnd;
        this.bestFitness = bestFitness;
        this.bestChromosome = bestChromosome;
    }

    public LocalDate getTrainStart() { return trainStart; }
    public LocalDate getTrainEnd() { return trainEnd; }
    public LocalDate getPredictStart() { return predictStart; }
    public LocalDate getPredictEnd() { return predictEnd; }
    public double getBestFitness() { return bestFitness; }
    public Chromosome getBestChromosome() { return bestChromosome; }
}
