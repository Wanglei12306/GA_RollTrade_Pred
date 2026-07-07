package com.example.quant.controller;

import com.example.quant.data.CsvLoader;
import com.example.quant.data.DataValidationException;
import com.example.quant.indicator.IndicatorPool;
import com.example.quant.model.AnalysisReport;
import com.example.quant.model.DataPreview;
import com.example.quant.service.AnalysisService;
import com.example.quant.service.DataService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * REST 接口：数据上传/加载、预览、指标列表、结果获取。
 */
@RestController
public class ApiController {

    private static final String SAMPLE_PATH = "static/sample/sample_stock.csv";

    private final CsvLoader csvLoader;
    private final DataService dataService;
    private final AnalysisService analysisService;
    private final IndicatorPool indicatorPool;

    public ApiController(CsvLoader csvLoader, DataService dataService,
                         AnalysisService analysisService, IndicatorPool indicatorPool) {
        this.csvLoader = csvLoader;
        this.dataService = dataService;
        this.analysisService = analysisService;
        this.indicatorPool = indicatorPool;
    }

    @PostMapping("/api/upload")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return badRequest("上传文件不能为空");
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".csv")) {
            return badRequest("仅支持 CSV 格式文件");
        }
        try (InputStream in = file.getInputStream()) {
            var klines = csvLoader.load(in);
            dataService.store(klines, name);
            return ResponseEntity.ok(dataService.preview());
        } catch (DataValidationException e) {
            return badRequest(e.getMessage());
        } catch (IOException e) {
            return badRequest("文件读取失败：" + e.getMessage());
        }
    }

    @GetMapping("/api/sample")
    public ResponseEntity<?> sample() {
        try (InputStream in = new ClassPathResource(SAMPLE_PATH).getInputStream()) {
            var klines = csvLoader.load(in);
            dataService.store(klines, "sample_stock.csv");
            return ResponseEntity.ok(dataService.preview());
        } catch (DataValidationException e) {
            return badRequest(e.getMessage());
        } catch (IOException e) {
            return badRequest("示例数据加载失败：" + e.getMessage());
        }
    }

    @GetMapping("/api/preview")
    public ResponseEntity<?> preview() {
        if (!dataService.hasData()) {
            return ResponseEntity.ok(Map.of("hasData", false));
        }
        return ResponseEntity.ok(dataService.preview());
    }

    @GetMapping("/api/indicators")
    public List<String> indicators() {
        return indicatorPool.allNames();
    }

    @GetMapping("/api/result")
    public ResponseEntity<?> result() {
        AnalysisReport report = analysisService.getLastReport();
        if (report == null) {
            return ResponseEntity.ok(Map.of("hasResult", false));
        }
        return ResponseEntity.ok(report);
    }

    private ResponseEntity<?> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}
