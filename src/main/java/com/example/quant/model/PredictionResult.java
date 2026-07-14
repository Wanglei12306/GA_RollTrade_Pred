package com.example.quant.model;

import java.util.List;

/**
 * 加载已训练择时模型、对当前数据逐根预测的结果汇总。
 * <p>这使 {@link com.example.quant.service.ModelRepository} 落盘的模型真正被消费：
 * 训练一次 → 保存模型 → 后续任意时刻加载数据并调用预测，无需重训。
 *
 * @param dataName       预测所用数据名（即当前加载的数据）
 * @param modelName      模型来源数据名（训练时落盘的文件名主体）
 * @param modelTrainedAt 模型训练完成时间（ISO-8601）
 * @param forecastDays   预测/标签 horizon
 * @param modelFitness   模型训练适应度
 * @param barCount       预测覆盖的 K 线数
 * @param countBuy       预测为 BUY 的根数
 * @param countSell      预测为 SELL 的根数
 * @param countHold      预测为 HOLD 的根数
 * @param verifiedCount  标签可得的根数（可计算准确率）
 * @param forwardCount   尾部无法标注、属纯粹向前预测的根数
 * @param accuracy       可验证根上的方向预测准确率 [0,1]；无可验证根为 null
 * @param bars           逐根预测明细
 */
public record PredictionResult(
        String dataName,
        String modelName,
        String modelTrainedAt,
        int forecastDays,
        double modelFitness,
        int barCount,
        int countBuy,
        int countSell,
        int countHold,
        int verifiedCount,
        int forwardCount,
        Double accuracy,
        List<BarPrediction> bars
) {}
