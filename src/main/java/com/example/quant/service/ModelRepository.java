package com.example.quant.service;

import com.example.quant.model.TrainedModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 训练产物持久化：把 {@link TrainedModel} 序列化为 JSON 落盘到 models/ 目录，
 * 并提供按数据名加载。复用 Spring Boot 自带 Jackson（ObjectMapper），无需额外依赖。
 */
@Service
public class ModelRepository {

    private static final Path DIR = Path.of("models");

    private final ObjectMapper mapper;

    public ModelRepository(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** 保存模型，返回落盘文件路径。 */
    public Path save(TrainedModel model) {
        try {
            Files.createDirectories(DIR);
            Path file = DIR.resolve(sanitize(model.dataName()) + "-timing-model.json");
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), model);
            return file;
        } catch (IOException e) {
            throw new IllegalStateException("模型保存失败：" + e.getMessage(), e);
        }
    }

    /** 按数据名加载模型；不存在返回 null。 */
    public TrainedModel load(String dataName) {
        Path file = DIR.resolve(sanitize(dataName) + "-timing-model.json");
        if (!Files.exists(file)) return null;
        try {
            return mapper.readValue(file.toFile(), TrainedModel.class);
        } catch (IOException e) {
            throw new IllegalStateException("模型加载失败：" + e.getMessage(), e);
        }
    }

    /** 文件名安全化：仅保留字母数字、点、下划线、连字符，去扩展名。 */
    private static String sanitize(String name) {
        if (name == null || name.isBlank()) return "model";
        String base = name.toLowerCase().endsWith(".csv") ? name.substring(0, name.length() - 4) : name;
        return base.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
