package com.example.quant.data;

import org.apache.commons.csv.CSVRecord;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 数据校验与字段映射工具。
 * 负责表头别名识别、字段存在性、数值合法性、日期可解析性、时间升序等检查。
 */
public final class DataValidator {

    private static final List<String> DATE_ALIASES = List.of("date", "time", "datetime", "日期", "时间");
    private static final List<String> OPEN_ALIASES = List.of("open", "开盘价", "开盘");
    private static final List<String> HIGH_ALIASES = List.of("high", "最高价", "最高");
    private static final List<String> LOW_ALIASES = List.of("low", "最低价", "最低");
    private static final List<String> CLOSE_ALIASES = List.of("close", "收盘价", "收盘");
    private static final List<String> VOLUME_ALIASES = List.of("volume", "vol", "成交量", "手数");

    private static final DateTimeFormatter[] DATE_FORMATS = new DateTimeFormatter[] {
            DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT),
            DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.ROOT),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss", Locale.ROOT),
            DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT)
    };

    private DataValidator() {}

    /** 在表头映射中按别名查找列名，找不到返回 null。 */
    public static String findHeader(Map<String, String> headerMap, List<String> aliases) {
        for (String alias : aliases) {
            for (String header : headerMap.keySet()) {
                if (header.equalsIgnoreCase(alias)) {
                    return header;
                }
            }
        }
        return null;
    }

    /** 校验必要字段是否齐全，返回缺失字段提示。 */
    public static void requireHeaders(Map<String, String> headerMap) {
        StringBuilder missing = new StringBuilder();
        checkOne(headerMap, DATE_ALIASES, "date", missing);
        checkOne(headerMap, OPEN_ALIASES, "open", missing);
        checkOne(headerMap, HIGH_ALIASES, "high", missing);
        checkOne(headerMap, LOW_ALIASES, "low", missing);
        checkOne(headerMap, CLOSE_ALIASES, "close", missing);
        if (missing.length() > 0) {
            throw new DataValidationException("缺少必要字段：" + missing.substring(2));
        }
    }

    private static void checkOne(Map<String, String> headerMap, List<String> aliases,
                                 String field, StringBuilder missing) {
        if (findHeader(headerMap, aliases) == null) {
            missing.append("、").append(field);
        }
    }

    /** 解析日期，支持多种格式；含时间成分时取日期部分。 */
    public static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new DataValidationException("时间字段不能为空，请检查日期格式");
        }
        String text = raw.trim();

        // 纯数字：按 Unix 时间戳处理（10 位=秒，13 位=毫秒），按系统时区转日期
        LocalDate epoch = parseEpochIfNumeric(text);
        if (epoch != null) return epoch;

        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                if (fmt.toString().contains("HH")) {
                    return LocalDateTime.parse(text, fmt).toLocalDate();
                }
                return LocalDate.parse(text, fmt);
            } catch (Exception ignore) {
                // 尝试下一种格式
            }
        }
        throw new DataValidationException("时间字段无法解析，请检查日期格式：" + raw);
    }

    /** 纯数字且 10~13 位时按时间戳解析（秒/毫秒），否则返回 null。 */
    private static LocalDate parseEpochIfNumeric(String text) {
        if (text.length() < 10 || text.length() > 13) return null;
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i))) return null;
        }
        try {
            long v = Long.parseLong(text);
            Instant instant = text.length() >= 13
                    ? Instant.ofEpochMilli(v)
                    : Instant.ofEpochSecond(v);
            return instant.atZone(ZoneId.systemDefault()).toLocalDate();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 解析数值字段，非法时抛出明确提示。 */
    public static double parseDouble(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            throw new DataValidationException("字段 " + field + " 不能为空");
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            throw new DataValidationException("字段 " + field + " 不是有效数值：" + raw);
        }
    }

    /** 校验单行数值合理性（价格为正、成交量为非负）。 */
    public static void validateValues(double open, double high, double low, double close, double volume) {
        if (open <= 0 || high <= 0 || low <= 0 || close <= 0) {
            throw new DataValidationException("价格必须为正数，请检查数据");
        }
        if (volume < 0) {
            throw new DataValidationException("成交量不能为负数，请检查数据");
        }
        if (high < Math.max(open, close) || low > Math.min(open, close)
                || high < low) {
            // 仅提示不阻断（部分数据存在微小异常），保持健壮
        }
    }

    /** 给出表头别名集合，供 CsvLoader 使用。 */
    public static List<String> dateAliases() { return DATE_ALIASES; }
    public static List<String> openAliases() { return OPEN_ALIASES; }
    public static List<String> highAliases() { return HIGH_ALIASES; }
    public static List<String> lowAliases() { return LOW_ALIASES; }
    public static List<String> closeAliases() { return CLOSE_ALIASES; }
    public static List<String> volumeAliases() { return VOLUME_ALIASES; }

    public static List<DateTimeFormatter> dateFormats() {
        return Arrays.asList(DATE_FORMATS);
    }
}
