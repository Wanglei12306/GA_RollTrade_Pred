package com.example.quant.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 异步文件夹批量训练的进度快照。
 * <p>由 {@code AnalysisService.startBatchTrain} 创建并在每完成一个标的时更新；
 * 前端通过 {@code GET /api/batch-progress?jobId=} 轮询读取进度。
 * <p>字段更新与快照读取均经 {@code synchronized} 保护，避免异步训练线程与
 * 轮询请求线程间的可见性/并发修改问题。
 */
public class BatchTrainJob {

    public enum Status { RUNNING, DONE, FAILED }

    private final String jobId;
    private final String folder;
    private final int total;
    private final long startedAt;
    private Status status;
    private int done;
    private int ok;
    private int fail;
    private String current;
    private long finishedAt;
    private final List<InstrumentTrainResult> items = new ArrayList<>();

    public BatchTrainJob(String jobId, String folder, int total, long startedAt) {
        this.jobId = jobId;
        this.folder = folder;
        this.total = total;
        this.startedAt = startedAt;
        this.status = Status.RUNNING;
    }

    /** 标记开始训练某个标的。 */
    public synchronized void startInstrument(String name) {
        this.current = name;
    }

    /** 记录一个标的训练完成（成功或失败），累计 done/ok/fail 并追加结果项。 */
    public synchronized void finishInstrument(InstrumentTrainResult item) {
        this.items.add(item);
        this.done++;
        if (item.success()) {
            this.ok++;
        } else {
            this.fail++;
        }
    }

    /** 全部标的处理完毕。 */
    public synchronized void markDone() {
        this.status = Status.DONE;
        this.finishedAt = System.currentTimeMillis();
        this.current = null;
    }

    /** 整体失败（如启动异常）。 */
    public synchronized void markFailed(String reason) {
        this.status = Status.FAILED;
        this.finishedAt = System.currentTimeMillis();
        this.current = reason;
    }

    /** 返回线程安全的进度快照（拷贝 items 列表）。 */
    public synchronized Snapshot snapshot() {
        return new Snapshot(jobId, folder, total, startedAt, status, done, ok, fail,
                current, finishedAt, new ArrayList<>(items));
    }

    /** 进度快照（不可变），供序列化给前端。 */
    public record Snapshot(
            String jobId,
            String folder,
            int total,
            long startedAt,
            Status status,
            int done,
            int ok,
            int fail,
            String current,
            long finishedAt,
            List<InstrumentTrainResult> instruments
    ) {}
}
