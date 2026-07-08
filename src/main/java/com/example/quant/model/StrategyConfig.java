package com.example.quant.model;

import java.util.List;

/**
 * 前端提交的策略参数配置。
 */
public class StrategyConfig {

    private List<String> indicators;        // 启用的指标名
    private int populationSize = 80;
    private int generations = 120;
    private double crossoverRate = 0.78;
    private double mutationRate = 0.08;

    private int trainMonths = 3;            // 训练窗口（月）
    private int predictMonths = 1;          // 预测窗口（月）
    private int stepMonths = 1;             // 滚动步长（月）

    private double initialCapital = 1_000_000;
    private double commissionRate = 0.0003; // 万三手续费

    // 适应度五维权重：年化收益、(1-最大回撤)、夏普、胜率、盈亏比
    private double wReturn = 0.30;
    private double wDrawdown = 0.25;
    private double wSharpe = 0.20;
    private double wWinRate = 0.10;
    private double wProfitLoss = 0.15;

    public List<String> getIndicators() { return indicators; }
    public void setIndicators(List<String> indicators) { this.indicators = indicators; }

    public int getPopulationSize() { return populationSize; }
    public void setPopulationSize(int populationSize) { this.populationSize = populationSize; }

    public int getGenerations() { return generations; }
    public void setGenerations(int generations) { this.generations = generations; }

    public double getCrossoverRate() { return crossoverRate; }
    public void setCrossoverRate(double crossoverRate) { this.crossoverRate = crossoverRate; }

    public double getMutationRate() { return mutationRate; }
    public void setMutationRate(double mutationRate) { this.mutationRate = mutationRate; }

    public int getTrainMonths() { return trainMonths; }
    public void setTrainMonths(int trainMonths) { this.trainMonths = trainMonths; }

    public int getPredictMonths() { return predictMonths; }
    public void setPredictMonths(int predictMonths) { this.predictMonths = predictMonths; }

    public int getStepMonths() { return stepMonths; }
    public void setStepMonths(int stepMonths) { this.stepMonths = stepMonths; }

    public double getInitialCapital() { return initialCapital; }
    public void setInitialCapital(double initialCapital) { this.initialCapital = initialCapital; }

    public double getCommissionRate() { return commissionRate; }
    public void setCommissionRate(double commissionRate) { this.commissionRate = commissionRate; }

    public double getwReturn() { return wReturn; }
    public void setwReturn(double wReturn) { this.wReturn = wReturn; }

    public double getwDrawdown() { return wDrawdown; }
    public void setwDrawdown(double wDrawdown) { this.wDrawdown = wDrawdown; }

    public double getwSharpe() { return wSharpe; }
    public void setwSharpe(double wSharpe) { this.wSharpe = wSharpe; }

    public double getwWinRate() { return wWinRate; }
    public void setwWinRate(double wWinRate) { this.wWinRate = wWinRate; }

    public double getwProfitLoss() { return wProfitLoss; }
    public void setwProfitLoss(double wProfitLoss) { this.wProfitLoss = wProfitLoss; }
}
