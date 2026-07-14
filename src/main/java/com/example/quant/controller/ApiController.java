package com.example.quant.controller;

import com.example.quant.data.CsvLoader;
import com.example.quant.data.DataValidationException;
import com.example.quant.indicator.IndicatorPool;
import com.example.quant.model.AnalysisReport;
import com.example.quant.model.DataPreview;
import com.example.quant.model.StrategyConfig;
import com.example.quant.model.TrainSummary;
import com.example.quant.service.AnalysisService;
import com.example.quant.service.DataService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
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

    /**
     * 显式训练入口：用 GA 在当前数据集上训练择时模型，落盘并返回训练摘要
     *（窗口数、各窗口训练/测试准确率、模型路径）。
     */
    @PostMapping("/api/train")
    public ResponseEntity<?> train(@RequestBody StrategyConfig config) {
        if (config == null) {
            return badRequest("缺少策略配置");
        }
        if (!dataService.hasData()) {
            return badRequest("请先上传或加载行情数据");
        }
        try {
            TrainSummary summary = analysisService.train(config);
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            return badRequest("训练失败：" + e.getMessage());
        }
    }

    /** 查看最近一次训练摘要 / 当前模型信息。 */
    @GetMapping("/api/model")
    public ResponseEntity<?> model() {
        TrainSummary summary = analysisService.getLastTrainSummary();
        if (summary == null) {
            return ResponseEntity.ok(Map.of("hasModel", false));
        }
        return ResponseEntity.ok(summary);
    }

    /**
     * 预测入口：加载已训练择时模型（按 {@code model} 指定的数据名，对应训练时落盘的模型文件），
     * 对当前上传/加载的数据逐根生成 BUY/SELL/HOLD 方向预测，并在标签可得的根上给出准确率。
     * <p>典型流程：先 {@code POST /api/train} 训练并落盘模型 → 上传新数据 → 调本接口预测。
     */
    @PostMapping("/api/predict")
    public ResponseEntity<?> predict(@RequestParam String model) {
        if (model == null || model.isBlank()) {
            return badRequest("缺少 model 参数（训练时使用的数据名）");
        }
        if (!dataService.hasData()) {
            return badRequest("请先上传或加载行情数据");
        }
        try {
            return ResponseEntity.ok(analysisService.predict(model));
        } catch (Exception e) {
            return badRequest("预测失败：" + e.getMessage());
        }
    }

    /** 查看最近一次预测结果。 */
    @GetMapping("/api/predict")
    public ResponseEntity<?> lastPrediction() {
        var result = analysisService.getLastPrediction();
        if (result == null) {
            return ResponseEntity.ok(Map.of("hasResult", false));
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 文件夹批量训练：对 {@code path} 指向的文件夹内每个 CSV（一个标的）逐个训练择时模型。
     * 可选 JSON body 传 StrategyConfig；不传则用默认配置（启用全部指标）。
     */
    @PostMapping("/api/train-folder")
    public ResponseEntity<?> trainFolder(@RequestParam String path,
                                         @RequestBody(required = false) StrategyConfig config) {
        if (path == null || path.isBlank()) {
            return badRequest("缺少 path 参数（文件夹路径）");
        }
        try {
            return ResponseEntity.ok(analysisService.trainFolder(Path.of(path), config));
        } catch (Exception e) {
            return badRequest("批量训练失败：" + e.getMessage());
        }
    }

    /** 查看最近一次文件夹批量训练结果。 */
    @GetMapping("/api/train-folder")
    public ResponseEntity<?> lastFolderResult() {
        var result = analysisService.getLastFolderResult();
        if (result == null) {
            return ResponseEntity.ok(Map.of("hasResult", false));
        }
        return ResponseEntity.ok(result);
    }

    private ResponseEntity<?> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}
