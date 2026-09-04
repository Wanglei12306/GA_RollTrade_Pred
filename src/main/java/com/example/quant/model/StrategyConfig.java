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

    // 论文采用 2 年训练 + 1 年样本外测试；较长窗口能减少短期噪声过拟合。
    private int trainMonths = 24;           // 训练窗口（月）
    private int predictMonths = 12;         // 预测窗口（月）
    private int stepMonths = 12;            // 滚动步长（月）

    private double initialCapital = 1_000_000;
    private double commissionRate = 0.0003; // 万三手续费

    // —— 回测风控参数 ——
    // 默认六成仓，兼顾收益弹性与单一标的风险。
    private double maxPositionRatio = 0.6;
    private double stopLossRatio = 0.10;
    private int maxHoldingBars = 60;
    private int minHoldingBars = 3;           // 过滤信号抖动，止损仍可提前触发
    private double maxDrawdownLimit = 0.25;   // 从净值峰值回撤达到该比例时触发风险预算平仓
    private int drawdownCooldownBars = 30;    // 回撤风控后的冷却期；0 表示永久禁止重新开仓
    private int trendFilterBars = 120;        // 长期均线趋势过滤；弱势阶段保持空仓

    // —— 择时模型训练参数（当前版本唯一训练目标：择时）——
    private int forecastDays = 5;          // 标签 horizon：预测未来几根的方向
    private double labelThreshold = 0.0;   // 涨跌中性带，|未来收益| <= 该值视为中性(0)

    // —— 训练适应度权重：五维盈亏综合（驱动 GA 寻优，见 FitnessEvaluator）——
    // 参考论文的双目标思想：风险与风险调整收益略高于单纯收益。
    private double wReturn = 0.25;
    private double wDrawdown = 0.35;
    private double wSharpe = 0.25;
    private double wWinRate = 0.05;
    private double wProfitLoss = 0.10;
    /** 兼容旧配置字段；准确率仅展示，不参与训练适应度。 */
    private double wAcc = 0.0;

    // —— 防过拟合：训练窗 fit/val 切分的泛化差距惩罚（C1）与活跃指标复杂度惩罚（C2）——
    private double generalizationPenalty = 0.25;  // λ：|F(fit)-F(val)| 越大越罚，偏好两段都稳的策略
    private double complexityPenalty = 0.05;      // 活跃指标占比惩罚，偏好简单可泛化策略
    private double validationWeight = 0.60;       // 验证段权重，高于训练段以抑制样本内过拟合
    private int minTradesForFitness = 5;          // 交易样本不足时收缩适应度，避免单笔偶然交易胜出
    private double turnoverPenalty = 0.08;        // 高频换手惩罚，覆盖手续费和滑点的部分影响

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

    public double getMaxPositionRatio() { return maxPositionRatio; }
    public void setMaxPositionRatio(double maxPositionRatio) { this.maxPositionRatio = maxPositionRatio; }

    public double getStopLossRatio() { return stopLossRatio; }
    public void setStopLossRatio(double stopLossRatio) { this.stopLossRatio = stopLossRatio; }

    public int getMaxHoldingBars() { return maxHoldingBars; }
    public void setMaxHoldingBars(int maxHoldingBars) { this.maxHoldingBars = maxHoldingBars; }

    public int getMinHoldingBars() { return minHoldingBars; }
    public void setMinHoldingBars(int minHoldingBars) { this.minHoldingBars = minHoldingBars; }

    public double getMaxDrawdownLimit() { return maxDrawdownLimit; }
    public void setMaxDrawdownLimit(double maxDrawdownLimit) { this.maxDrawdownLimit = maxDrawdownLimit; }

    public int getDrawdownCooldownBars() { return drawdownCooldownBars; }
    public void setDrawdownCooldownBars(int drawdownCooldownBars) { this.drawdownCooldownBars = drawdownCooldownBars; }

    public int getTrendFilterBars() { return trendFilterBars; }
    public void setTrendFilterBars(int trendFilterBars) { this.trendFilterBars = trendFilterBars; }

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

    public double getValidationWeight() { return validationWeight; }
    public void setValidationWeight(double validationWeight) { this.validationWeight = validationWeight; }

    public int getMinTradesForFitness() { return minTradesForFitness; }
    public void setMinTradesForFitness(int minTradesForFitness) { this.minTradesForFitness = minTradesForFitness; }

    public double getTurnoverPenalty() { return turnoverPenalty; }
    public void setTurnoverPenalty(double turnoverPenalty) { this.turnoverPenalty = turnoverPenalty; }

    public int getForecastDays() { return forecastDays; }
    public void setForecastDays(int forecastDays) { this.forecastDays = forecastDays; }

    public double getLabelThreshold() { return labelThreshold; }
    public void setLabelThreshold(double labelThreshold) { this.labelThreshold = labelThreshold; }

    public double getwAccuracy() { return wAccuracy; }
    public void setwAccuracy(double wAccuracy) { this.wAccuracy = wAccuracy; }

    public double getwReturnWeighted() { return wReturnWeighted; }
    public void setwReturnWeighted(double wReturnWeighted) { this.wReturnWeighted = wReturnWeighted; }

    public double getwAcc() { return wAcc; }
}
