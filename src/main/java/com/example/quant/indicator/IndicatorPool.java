package com.example.quant.indicator;

import com.example.quant.indicator.impl.AtrIndicator;
import com.example.quant.indicator.impl.BollIndicator;
import com.example.quant.indicator.impl.CciIndicator;
import com.example.quant.indicator.impl.KdjIndicator;
import com.example.quant.indicator.impl.MacdIndicator;
import com.example.quant.indicator.impl.MaIndicator;
import com.example.quant.indicator.impl.ObvIndicator;
import com.example.quant.indicator.impl.RsiIndicator;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 技术指标池：注册全部 8 个指标，并按名称提供选中的子集。
 * 指标顺序固定（LinkedHashMap 保序），染色体按此顺序编码。
 */
@Component
public class IndicatorPool {

    private final Map<String, Indicator> registry = new LinkedHashMap<>();

    public IndicatorPool() {
        register(new MaIndicator());
        register(new MacdIndicator());
        register(new RsiIndicator());
        register(new BollIndicator());
        register(new KdjIndicator());
        register(new AtrIndicator());
        register(new ObvIndicator());
        register(new CciIndicator());
    }

    private void register(Indicator indicator) {
        registry.put(indicator.name(), indicator);
    }

    /** 全部指标名（有序）。 */
    public List<String> allNames() {
        return List.copyOf(registry.keySet());
    }

    public Indicator get(String name) {
        return registry.get(name);
    }

    /** 按选中名称列表返回有序指标列表（保持池中顺序）。 */
    public List<Indicator> selected(List<String> names) {
        if (names == null || names.isEmpty()) {
            throw new IllegalArgumentException("请至少选择一个技术指标");
        }
        return registry.entrySet().stream()
                .filter(e -> names.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .toList();
    }
}
