package com.example.quant.model;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * 训练产物快照：把 GA 训练得到的最优染色体（择时模型）与训练元信息一起序列化落盘，
 * 便于后续加载复用。由 {@link com.example.quant.service.ModelRepository} 读写为 JSON。
 *
 * @param modelName 人类可读模型名（数据名 + 训练时间），用于展示与选择
 * @param trainedAt 训练完成时间（ISO-8601），由 from(...) 写入；亦作为落盘文件名的一部分保证唯一
 * @param indicators 训练时使用的指标名列表（有序）；预测时必须按同序重建指标集，
 *                   染色体的 mask/paramIndex/weight 才能与指标正确对齐。
 */
public record TrainedModel(
        String modelName,
        String dataName,
        int forecastDays,
        double labelThreshold,
        String trainedAt,
        double fitness,
        List<String> indicators,
        boolean[] mask,
        int[] paramIndex,
        double[] weight,
        double buyThreshold,
        double sellThreshold
) {

    /** 由染色体构造训练快照（拷贝数组，避免与运行时个体别名）。 */
    public static TrainedModel from(Chromosome c, String dataName, int forecastDays,
                                    double labelThreshold, List<String> indicators) {
        String trainedAt = Instant.now().toString();
        return new TrainedModel(
                dataName + " @ " + trainedAt,
                dataName,
                forecastDays,
                labelThreshold,
                trainedAt,
                c.getFitness(),
                List.copyOf(indicators),
                Arrays.copyOf(c.getMask(), c.getMask().length),
                Arrays.copyOf(c.getParamIndex(), c.getParamIndex().length),
                Arrays.copyOf(c.getWeight(), c.getWeight().length),
                c.getBuyThreshold(),
                c.getSellThreshold()
        );
    }

    /** 还原为可执行染色体。 */
    public Chromosome toChromosome() {
        Chromosome c = new Chromosome(
                Arrays.copyOf(mask, mask.length),
                Arrays.copyOf(paramIndex, paramIndex.length),
                Arrays.copyOf(weight, weight.length),
                buyThreshold, sellThreshold);
        c.setFitness(fitness);
        return c;
    }
}
