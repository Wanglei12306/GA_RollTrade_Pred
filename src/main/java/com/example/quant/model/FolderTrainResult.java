package com.example.quant.model;

import java.util.List;

/**
 * 文件夹批量训练结果：对文件夹内每个 CSV（一个标的）逐个训练择时模型，
 * 汇总每个标的的成功/失败与训练摘要。
 */
public record FolderTrainResult(
        String folder,
        int total,
        int succeeded,
        int failed,
        List<InstrumentTrainResult> instruments
) {}
