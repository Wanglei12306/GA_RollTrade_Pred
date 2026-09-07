package com.example.quant.data;

import com.example.quant.model.KLine;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * CSV 行情数据加载器：解析、字段映射、数值与日期校验、时间升序保证。
 * 手动以首行作为表头，避免依赖 CSVFormat 的自动表头检测。
 */
@Component
public class CsvLoader {

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setTrim(true)
            .build();

    /**
     * 从输入流加载 K 线数据。
     *
     * @throws DataValidationException 文件为空、缺字段、数值/日期非法、时间非升序等
     */
    public List<KLine> load(InputStream inputStream) {
        List<KLine> klines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
             CSVParser parser = new CSVParser(reader, FORMAT)) {

            // Iterate records directly. parser.getRecords() duplicates the complete
            // file in memory before KLine objects are created, which is prohibitive
            // for large intraday exports.
            Iterator<CSVRecord> records = parser.iterator();
            if (!records.hasNext()) {
                throw new DataValidationException("上传文件不能为空");
            }

            // 首行作为表头，建立 名称→列索引 映射
            CSVRecord headerRec = records.next();
            Map<String, String> headerMap = new HashMap<>();
            Map<String, Integer> headerIndex = new HashMap<>();
            for (int i = 0; i < headerRec.size(); i++) {
                String h = headerRec.get(i);
                if (h == null) continue;
                h = h.replace("﻿", "").trim();   // 去 UTF-8 BOM 与首尾空白，避免首列名带 BOM 导致别名匹配失败
                if (!h.isBlank()) {
                    headerMap.put(h, h);
                    headerIndex.putIfAbsent(h, i);
                }
            }
            DataValidator.requireHeaders(headerMap);

            String dateCol = DataValidator.findHeader(headerMap, DataValidator.dateAliases());
            String openCol = DataValidator.findHeader(headerMap, DataValidator.openAliases());
            String highCol = DataValidator.findHeader(headerMap, DataValidator.highAliases());
            String lowCol = DataValidator.findHeader(headerMap, DataValidator.lowAliases());
            String closeCol = DataValidator.findHeader(headerMap, DataValidator.closeAliases());
            String volCol = DataValidator.findHeader(headerMap, DataValidator.volumeAliases());

            LocalDate lastDate = null;
            int rowNumber = 1;
            while (records.hasNext()) {
                CSVRecord rec = records.next();
                rowNumber++;
                if (rec.size() == 0 || (rec.size() == 1 && rec.get(0).isBlank())) continue;

                LocalDate date = DataValidator.parseDate(getByHeader(rec, headerIndex, dateCol));
                double open = DataValidator.parseDouble(getByHeader(rec, headerIndex, openCol), "open");
                double high = DataValidator.parseDouble(getByHeader(rec, headerIndex, highCol), "high");
                double low = DataValidator.parseDouble(getByHeader(rec, headerIndex, lowCol), "low");
                double close = DataValidator.parseDouble(getByHeader(rec, headerIndex, closeCol), "close");
                double volume = volCol == null ? 0.0
                        : DataValidator.parseDouble(getByHeader(rec, headerIndex, volCol), "volume");

                DataValidator.validateValues(open, high, low, close, volume);

                if (lastDate != null && date.isBefore(lastDate)) {
                    throw new DataValidationException(
                            "数据未按时间升序排列，第 " + rowNumber + " 行日期 " + date + " 早于前一行 " + lastDate);
                }
                lastDate = date;
                klines.add(new KLine(date, open, high, low, close, volume));
            }
        } catch (DataValidationException e) {
            throw e;
        } catch (IOException e) {
            throw new DataValidationException("文件读取失败：" + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new DataValidationException("CSV 解析失败：" + e.getMessage(), e);
        }

        if (klines.isEmpty()) {
            throw new DataValidationException("上传文件不能为空");
        }
        return klines;
    }

    private String getByHeader(CSVRecord rec, Map<String, Integer> headerIndex, String header) {
        Integer idx = headerIndex.get(header);
        if (idx == null || idx >= rec.size()) return "";
        String v = rec.get(idx);
        return v == null ? "" : v;
    }
}
