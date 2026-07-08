package com.example.quant.model;

import java.time.LocalDate;
import java.util.List;

/**
 * 数据预览信息。
 */
public class DataPreview {

    private final String dataName;
    private final int rowCount;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final List<String> fields;
    private final List<KLine> headRows;        // 前 N 行
    private final List<LocalDate> priceDates;  // 价格序列日期
    private final double[] priceCloses;        // 收盘价序列

    public DataPreview(String dataName, int rowCount, LocalDate startDate, LocalDate endDate,
                       List<String> fields, List<KLine> headRows,
                       List<LocalDate> priceDates, double[] priceCloses) {
        this.dataName = dataName;
        this.rowCount = rowCount;
        this.startDate = startDate;
        this.endDate = endDate;
        this.fields = fields;
        this.headRows = headRows;
        this.priceDates = priceDates;
        this.priceCloses = priceCloses;
    }

    public String getDataName() { return dataName; }
    public int getRowCount() { return rowCount; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public List<String> getFields() { return fields; }
    public List<KLine> getHeadRows() { return headRows; }
    public List<LocalDate> getPriceDates() { return priceDates; }
    public double[] getPriceCloses() { return priceCloses; }
}
