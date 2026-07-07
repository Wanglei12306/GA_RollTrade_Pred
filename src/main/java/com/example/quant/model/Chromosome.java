package com.example.quant.model;

import java.util.Arrays;

/**
 * 遗传算法染色体 —— 一套完整交易策略编码（docx 四大维度）：
 * <ol>
 *   <li>mask：指标选择掩码</li>
 *   <li>paramIndex：各指标在候选参数集中的索引</li>
 *   <li>weight：多指标融合权重</li>
 *   <li>buyThreshold / sellThreshold：多空交易阈值</li>
 * </ol>
 * 适应度 fitness 在评估后写入。
 */
public class Chromosome {

    private boolean[] mask;
    private int[] paramIndex;
    private double[] weight;
    private double buyThreshold;
    private double sellThreshold;
    private double fitness = Double.NaN;

    public Chromosome(boolean[] mask, int[] paramIndex, double[] weight,
                      double buyThreshold, double sellThreshold) {
        this.mask = mask;
        this.paramIndex = paramIndex;
        this.weight = weight;
        this.buyThreshold = buyThreshold;
        this.sellThreshold = sellThreshold;
    }

    public Chromosome copy() {
        Chromosome c = new Chromosome(
                Arrays.copyOf(mask, mask.length),
                Arrays.copyOf(paramIndex, paramIndex.length),
                Arrays.copyOf(weight, weight.length),
                buyThreshold, sellThreshold);
        c.fitness = this.fitness;   // 保留适应度，精英个体无需重复评估
        return c;
    }

    /** 选中指标数量（至少为 1，由生成逻辑保证）。 */
    public int selectedCount() {
        int c = 0;
        for (boolean b : mask) if (b) c++;
        return c;
    }

    public boolean[] getMask() { return mask; }
    public int[] getParamIndex() { return paramIndex; }
    public double[] getWeight() { return weight; }
    public double getBuyThreshold() { return buyThreshold; }
    public double getSellThreshold() { return sellThreshold; }
    public double getFitness() { return fitness; }

    public void setMask(boolean[] mask) { this.mask = mask; }
    public void setParamIndex(int[] paramIndex) { this.paramIndex = paramIndex; }
    public void setWeight(double[] weight) { this.weight = weight; }
    public void setBuyThreshold(double buyThreshold) { this.buyThreshold = buyThreshold; }
    public void setSellThreshold(double sellThreshold) { this.sellThreshold = sellThreshold; }
    public void setFitness(double fitness) { this.fitness = fitness; }

    @Override
    public String toString() {
        return "Chromosome{selected=" + selectedCount() +
                ", buy=" + buyThreshold + ", sell=" + sellThreshold +
                ", fitness=" + fitness + "}";
    }
}
