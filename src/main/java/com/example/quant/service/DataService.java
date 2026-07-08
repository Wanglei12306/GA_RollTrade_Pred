package com.example.quant.service;

import com.example.quant.model.DataPreview;
import com.example.quant.model.KLine;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 内存数据服务（单用户单标的）。缓存当前上传/加载的 K 线数据并提供预览。
 */
@Service
public class DataService {

    private List<KLine> klines = new ArrayList<>();
    private String dataName = "";

    public void store(List<KLine> klines, String dataName) {
        this.klines = klines;
        this.dataName = dataName;
    }

    public boolean hasData() {
        return !klines.isEmpty();
    }

    public List<KLine> getKlines() {
        return klines;
    }

    public String getDataName() {
        return dataName;
    }

    public DataPreview preview() {
        int n = klines.size();
        LocalDate start = n > 0 ? klines.get(0).getDate() : null;
        LocalDate end = n > 0 ? klines.get(n - 1).getDate() : null;
        List<String> fields = List.of("date", "open", "high", "low", "close", "volume");
        List<KLine> head = klines.subList(0, Math.min(10, n));

        // 价格序列（数据量大时采样，避免页面卡顿）
        int target = Math.min(n, 240);
        List<LocalDate> priceDates = new ArrayList<>(target);
        double[] closes = new double[target];
        if (n > 0) {
            double step = (double) n / target;
            for (int i = 0; i < target; i++) {
                int idx = Math.min(n - 1, (int) (i * step));
                priceDates.add(klines.get(idx).getDate());
                closes[i] = klines.get(idx).getClose();
            }
        }
        return new DataPreview(dataName, n, start, end, fields, new ArrayList<>(head), priceDates, closes);
    }

    public void clear() {
        klines = new ArrayList<>();
        dataName = "";
    }
}
