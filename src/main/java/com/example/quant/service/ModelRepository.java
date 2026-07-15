package com.example.quant.service;

import com.example.quant.model.TrainedModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 训练产物持久化：把 {@link TrainedModel} 序列化为 JSON 落盘到 models/ 目录，
 * 并提供按模型 id 加载、列出全部模型。复用 Spring Boot 自带 Jackson（ObjectMapper），无需额外依赖。
 * <p>文件名 = 数据名 + 训练时间戳，保证同一数据多次训练不会互相覆盖。
 */
@Service
public class ModelRepository {

    private static final Logger log = LoggerFactory.getLogger(ModelRepository.class);
    private static final Path DIR = Path.of("models");

    private final ObjectMapper mapper;

    public ModelRepository(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** 保存模型，返回落盘文件路径。文件名含训练时间戳，多次训练不覆盖。 */
    public Path save(TrainedModel model) {
        try {
            Files.createDirectories(DIR);
            String fileStem = sanitize(model.dataName()) + "_" + sanitize(model.trainedAt());
            Path file = DIR.resolve(fileStem + ".json");
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), model);
            return file;
        } catch (IOException e) {
            throw new IllegalStateException("模型保存失败：" + e.getMessage(), e);
        }
    }

    /** 按模型 id（落盘文件 stem）加载；不存在返回 null。 */
    public TrainedModel load(String modelId) {
        if (modelId == null || !isValidId(modelId)) return null;
        Path file = DIR.resolve(modelId + ".json");
        if (!Files.exists(file)) return null;
        try {
            return mapper.readValue(file.toFile(), TrainedModel.class);
        } catch (IOException e) {
            throw new IllegalStateException("模型加载失败：" + e.getMessage(), e);
        }
    }

    /** 列出全部已落盘模型（按训练时间倒序，最新在前）。 */
    public List<ModelInfo> list() {
        List<ModelInfo> out = new ArrayList<>();
        if (!Files.isDirectory(DIR)) return out;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(DIR, "*.json")) {
            for (Path p : ds) {
                try {
                    TrainedModel m = mapper.readValue(p.toFile(), TrainedModel.class);
                    String id = p.getFileName().toString().replaceFirst("\\.json$", "");
                    out.add(new ModelInfo(id, m.modelName(), m.dataName(), m.trainedAt(),
                            m.fitness(), m.indicators()));
                } catch (IOException e) {
                    log.warn("跳过无法解析的模型文件 {}：{}", p.getFileName(), e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("列出模型失败：{}", e.getMessage());
        }
        out.sort(Comparator.comparing(ModelInfo::trainedAt, Comparator.reverseOrder()));
        return out;
    }

    /** 文件名安全化：仅保留字母数字、点、下划线、连字符，去扩展名。 */
    private static String sanitize(String name) {
        if (name == null || name.isBlank()) return "model";
        String base = name.toLowerCase().endsWith(".csv") ? name.substring(0, name.length() - 4) : name;
        return base.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /** modelId 仅允许安全字符，防止路径穿越。 */
    private static boolean isValidId(String id) {
        return id.matches("[a-zA-Z0-9._-]+");
    }

    /** 模型清单条目：id（用于加载引用）+ 展示信息。 */
    public record ModelInfo(String id, String modelName, String dataName,
                            String trainedAt, double fitness, List<String> indicators) {}
}
