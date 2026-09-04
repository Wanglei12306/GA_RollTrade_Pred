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

    // —— 择时模型训练参数（当前版本唯一训练目标：择时）——
    private int forecastDays = 5;          // 标签 horizon：预测未来几根的方向
    private double labelThreshold = 0.0;   // 涨跌中性带，|未来收益| <= 该值视为中性(0)

    // —— 训练适应度权重：五维盈亏综合（驱动 GA 寻优，见 FitnessEvaluator）——
    private double wReturn = 0.05;
    private double wDrawdown = 0.3;
    private double wSharpe = 0.20;
    private double wWinRate = 0.10;
    private double wProfitLoss = 0.15;
    private double wAcc =0.2;

    // —— 防过拟合：训练窗 fit/val 切分的泛化差距惩罚（C1）与活跃指标复杂度惩罚（C2）——
    private double generalizationPenalty = 0.25;  // λ：|F(fit)-F(val)| 越大越罚，偏好两段都稳的策略
    private double complexityPenalty = 0.05;      // 活跃指标占比惩罚，偏好简单可泛化策略

    // —— 辅助可验证性权重（不驱动训练，仅用于方向预测准确率展示）——
    private double wAccuracy = 0.6;
    private double wReturnWeighted = 0.4;

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

    public double getGeneralizationPenalty() { return generalizationPenalty; }
    public void setGeneralizationPenalty(double generalizationPenalty) { this.generalizationPenalty = generalizationPenalty; }

    public double getComplexityPenalty() { return complexityPenalty; }
    public void setComplexityPenalty(double complexityPenalty) { this.complexityPenalty = complexityPenalty; }

    public int getForecastDays() { return forecastDays; }
    public void setForecastDays(int forecastDays) { this.forecastDays = forecastDays; }

    public double getLabelThreshold() { return labelThreshold; }
    public void setLabelThreshold(double labelThreshold) { this.labelThreshold = labelThreshold; }

    public double getwAccuracy() { return wAccuracy; }
    public void setwAccuracy(double wAccuracy) { this.wAccuracy = wAccuracy; }

    public double getwReturnWeighted() { return wReturnWeighted; }
    public void setwReturnWeighted(double wReturnWeighted) { this.wReturnWeighted = wReturnWeighted; }

    public double getwAcc() {return wAcc;}
}
