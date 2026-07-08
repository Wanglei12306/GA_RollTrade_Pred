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
}
