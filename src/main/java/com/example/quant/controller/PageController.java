package com.example.quant.controller;

import com.example.quant.indicator.IndicatorPool;
import com.example.quant.model.StrategyConfig;
import com.example.quant.service.AnalysisService;
import com.example.quant.service.DataService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 页面控制器：首页、数据管理、参数配置、回测结果，以及运行回测的表单提交。
 */
@Controller
public class PageController {

    private final IndicatorPool indicatorPool;
    private final DataService dataService;
    private final AnalysisService analysisService;

    public PageController(IndicatorPool indicatorPool, DataService dataService,
                          AnalysisService analysisService) {
        this.indicatorPool = indicatorPool;
        this.dataService = dataService;
        this.analysisService = analysisService;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("hasData", dataService.hasData());
        model.addAttribute("hasResult", analysisService.getLastReport() != null);
        return "index";
    }

    @GetMapping("/data")
    public String data(Model model) {
        model.addAttribute("hasData", dataService.hasData());
        return "data";
    }

    @GetMapping("/config")
    public String config(Model model) {
        model.addAttribute("indicators", indicatorPool.allNames());
        model.addAttribute("config", new StrategyConfig());
        model.addAttribute("hasData", dataService.hasData());
        model.addAttribute("models", analysisService.listModels());
        return "config";
    }

    @GetMapping("/result")
    public String result() {
        return "result";
    }

    @PostMapping("/run")
    public String run(@ModelAttribute StrategyConfig config, RedirectAttributes attrs) {
        try {
            if (!dataService.hasData()) {
                attrs.addFlashAttribute("error", "请先上传或加载行情数据");
                return "redirect:/config";
            }
            analysisService.run(config);
            return "redirect:/result";
        } catch (Exception e) {
            attrs.addFlashAttribute("error", e.getMessage());
            return "redirect:/config";
        }
    }

    /** 用已训练模型对当前数据回测（不重训）：选模型 + 资金/手续费 → 重定向结果页。 */
    @PostMapping("/backtest-model")
    public String backtestModel(@RequestParam String modelId,
                                @RequestParam(defaultValue = "1000000") double initialCapital,
                                @RequestParam(defaultValue = "0.0003") double commissionRate,
                                @RequestParam(defaultValue = "0.6") double maxPositionRatio,
                                @RequestParam(defaultValue = "0.10") double stopLossRatio,
                                @RequestParam(defaultValue = "60") int maxHoldingBars,
                                @RequestParam(defaultValue = "3") int minHoldingBars,
                                @RequestParam(defaultValue = "0.25") double maxDrawdownLimit,
                                @RequestParam(defaultValue = "30") int drawdownCooldownBars,
                                @RequestParam(defaultValue = "120") int trendFilterBars,
                                RedirectAttributes attrs) {
        try {
            if (!dataService.hasData()) {
                attrs.addFlashAttribute("error", "请先上传或加载行情数据");
                return "redirect:/config";
            }
            if (modelId == null || modelId.isBlank()) {
                attrs.addFlashAttribute("error", "请选择一个已训练模型");
                return "redirect:/config";
            }
            StrategyConfig cfg = new StrategyConfig();
            cfg.setInitialCapital(initialCapital);
            cfg.setCommissionRate(commissionRate);
            cfg.setMaxPositionRatio(maxPositionRatio);
            cfg.setStopLossRatio(stopLossRatio);
            cfg.setMaxHoldingBars(maxHoldingBars);
            cfg.setMinHoldingBars(minHoldingBars);
            cfg.setMaxDrawdownLimit(maxDrawdownLimit);
            cfg.setDrawdownCooldownBars(drawdownCooldownBars);
            cfg.setTrendFilterBars(trendFilterBars);
            analysisService.backtestWithModel(modelId, cfg);
            return "redirect:/result";
        } catch (Exception e) {
            attrs.addFlashAttribute("error", e.getMessage());
            return "redirect:/config";
        }
    }
}
