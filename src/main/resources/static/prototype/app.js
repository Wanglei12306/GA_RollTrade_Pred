/* ============================================
   GA RollTrade 原型界面 — 交互逻辑
   覆盖需求文档 4.7 / 4.8 全部图表与交互
   使用 ECharts 5.5（需求文档 3.3 指定）
   ============================================ */

// ======================== 工具函数 ========================

var alertBox = document.getElementById("alertBox");
var alertTimer = null;

function showAlert(message, type) {
  var cls = type === "error" ? "alert-error" : (type === "success" ? "alert-success" : "alert-info");
  alertBox.innerHTML = '<div class="alert ' + cls + '" style="margin:0">' + message + "</div>";
  clearTimeout(alertTimer);
  alertTimer = setTimeout(function () { alertBox.innerHTML = ""; }, 5000);
}

// ---- 模拟数据生成 ----
function makeSeries(length, start, drift, wave) {
  var vals = []; var cur = start;
  for (var i = 0; i < length; i++) {
    cur += Math.sin(i / 6) * wave + Math.cos(i / 13) * wave * 0.7 + drift;
    vals.push(+cur.toFixed(2));
  }
  return vals;
}

function movingAvg(arr, w) {
  return arr.map(function (_, i) {
    var s = Math.max(0, i - w + 1);
    var sum = 0;
    for (var j = s; j <= i; j++) sum += arr[j];
    return +(sum / (i - s + 1)).toFixed(2);
  });
}

function makeDates(count, y, m) {
  var d = [];
  for (var i = 0; i < count; i++) {
    d.push(y + "-" + (m < 10 ? "0" : "") + m + "-15");
    m++; if (m > 12) { m = 1; y++; }
  }
  return d;
}

// 共享模拟数据
var DATES    = makeDates(92, 2024, 1);
var PRICES   = makeSeries(92, 3180, 2.6, 8.5);
var MA20     = movingAvg(PRICES, 20);
var VOLUMES  = makeSeries(92, 1600000, 5000, 120000).map(function (v) { return Math.max(500000, Math.floor(v)); });

var EQUITY_DATES = makeDates(80, 2024, 1);
var EQUITY_OPT   = makeSeries(80, 1, 0.006, 0.006).map(function (v) { return +(v + 0.08).toFixed(4); });
var EQUITY_FIX   = makeSeries(80, 1, 0.0035, 0.005).map(function (v) { return +(v + 0.03).toFixed(4); });
var BUY_HOLD     = makeSeries(80, 1, 0.0025, 0.007).map(function (v) { return +(v + 0.03).toFixed(4); });

var BUY_SIGNALS = [
  [DATES[12], PRICES[12]], [DATES[46], PRICES[46]], [DATES[74], PRICES[74]],
  [DATES[30], PRICES[30]], [DATES[58], PRICES[58]]
];
var SELL_SIGNALS = [
  [DATES[27], PRICES[27]], [DATES[63], PRICES[63]],
  [DATES[40], PRICES[40]], [DATES[80], PRICES[80]]
];

// MACD 模拟数据
var MACD_DIF  = makeSeries(92, 0, 0.15, 2.5);
var MACD_DEA  = movingAvg(MACD_DIF, 9);
var MACD_HIST = MACD_DIF.map(function (v, i) { return +(v - MACD_DEA[i]).toFixed(2); });

// RSI 模拟数据
var RSI_DATA = makeSeries(92, 50, 0.1, 12).map(function (v) { return Math.min(100, Math.max(0, Math.round(v))); });

// 回撤模拟
var DRAWDOWN_DATA = makeSeries(80, -0.02, -0.0002, 0.015).map(function (v) { return +(Math.min(0, v)).toFixed(4); });

// ======================== 图表实例管理 ========================
var chartInstances = {};

function getChart(domId) {
  var dom = document.getElementById(domId);
  if (!dom || dom.offsetWidth === 0) return null;
  if (chartInstances[domId]) { chartInstances[domId].dispose(); }
  var c = echarts.init(dom);
  chartInstances[domId] = c;
  return c;
}

function resizeAll() {
  Object.values(chartInstances).forEach(function (c) { try { c.resize(); } catch (e) {} });
}

// ---- 通用 ECharts 配置工厂 ----
function baseGrid() { return { left: 54, right: 20, top: 30, bottom: 40 }; }

// ---- 4.7.2 价格走势图 + 买卖信号 ----
function renderPriceChart() {
  var chart = getChart("priceChart");
  if (!chart) return;
  chart.setOption({
    grid: baseGrid(),
    legend: { data: ["收盘价", "MA20", "买入 BUY", "卖出 SELL"], top: 0, textStyle: { fontSize: 11 } },
    xAxis: { type: "category", data: DATES, axisLabel: { fontSize: 10, interval: 14 } },
    yAxis: { type: "value", scale: true },
    tooltip: { trigger: "axis" },
    series: [
      { name: "收盘价", type: "line", data: PRICES, showSymbol: false, lineStyle: { width: 2, color: "#1769aa" }, z: 1 },
      { name: "MA20", type: "line", data: MA20, showSymbol: false, lineStyle: { width: 1.5, color: "#a66b00" }, z: 1 },
      { name: "买入 BUY", type: "scatter", data: BUY_SIGNALS, symbolSize: 10, itemStyle: { color: "#16875d" }, z: 2 },
      { name: "卖出 SELL", type: "scatter", data: SELL_SIGNALS, symbolSize: 10, itemStyle: { color: "#c84646" }, z: 2 }
    ]
  });
}

// ---- 4.7.2 数据预览走势图 ----
function renderPreviewChart() {
  var chart = getChart("previewChart");
  if (!chart) return;
  var previewDates = DATES.slice(0, 60);
  var previewPrices = PRICES.slice(0, 60);
  chart.setOption({
    grid: baseGrid(),
    xAxis: { type: "category", data: previewDates, axisLabel: { fontSize: 10, interval: 10 } },
    yAxis: { type: "value", scale: true },
    tooltip: { trigger: "axis" },
    series: [
      { name: "收盘价", type: "line", data: previewPrices, showSymbol: false, lineStyle: { width: 2, color: "#1769aa" }, areaStyle: { color: "rgba(23,105,170,0.08)" } }
    ]
  });
}

// ---- 4.7.2 MACD 指标图 ----
function renderMACDChart() {
  var chart = getChart("macdChart");
  if (!chart) return;
  chart.setOption({
    grid: { left: 54, right: 20, top: 30, bottom: 40 },
    legend: { data: ["DIF", "DEA", "柱状"], top: 0, textStyle: { fontSize: 11 } },
    xAxis: { type: "category", data: DATES, axisLabel: { fontSize: 10, interval: 14 } },
    yAxis: { type: "value" },
    tooltip: { trigger: "axis" },
    series: [
      { name: "DIF", type: "line", data: MACD_DIF, showSymbol: false, lineStyle: { width: 1.5, color: "#1769aa" } },
      { name: "DEA", type: "line", data: MACD_DEA, showSymbol: false, lineStyle: { width: 1.5, color: "#a66b00" } },
      { name: "柱状", type: "bar", data: MACD_HIST.map(function (v) { return v >= 0 ? v : v; }), itemStyle: { color: "#c84646" }, barWidth: 4 },
    ]
  });
}

// ---- 4.7.2 RSI 指标图 ----
function renderRSIChart() {
  var chart = getChart("rsiChart");
  if (!chart) return;
  chart.setOption({
    grid: { left: 54, right: 20, top: 30, bottom: 40 },
    legend: { data: ["RSI(14)", "超买线(70)", "超卖线(30)"], top: 0, textStyle: { fontSize: 11 } },
    xAxis: { type: "category", data: DATES, axisLabel: { fontSize: 10, interval: 14 } },
    yAxis: { type: "value", min: 0, max: 100 },
    tooltip: { trigger: "axis" },
    series: [
      { name: "RSI(14)", type: "line", data: RSI_DATA, showSymbol: false, lineStyle: { width: 2, color: "#1769aa" }, z: 2,
        markLine: { silent: true, symbol: "none",
          data: [{ yAxis: 70, lineStyle: { color: "#c84646", type: "dashed", width: 1 } },
                 { yAxis: 30, lineStyle: { color: "#16875d", type: "dashed", width: 1 } }]
        }
      }
    ]
  });
}

// ---- 4.7.2 资金曲线三线对比（优化 vs 固定 vs 买入持有） ----
function renderEquityChart() {
  var chart = getChart("equityChart");
  if (!chart) return;
  chart.setOption({
    grid: baseGrid(),
    legend: { data: ["遗传优化策略", "固定参数策略", "买入持有"], top: 0, textStyle: { fontSize: 11 } },
    xAxis: { type: "category", data: EQUITY_DATES, axisLabel: { fontSize: 10, interval: 14 } },
    yAxis: { type: "value", scale: true, axisLabel: { formatter: "{value}" } },
    tooltip: { trigger: "axis" },
    series: [
      {
        name: "遗传优化策略", type: "line", data: EQUITY_OPT, showSymbol: false,
        lineStyle: { width: 2.5, color: "#1769aa" },
        areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: "rgba(23,105,170,0.22)" }, { offset: 1, color: "rgba(23,105,170,0)" }
        ])}
      },
      {
        name: "固定参数策略", type: "line", data: EQUITY_FIX, showSymbol: false,
        lineStyle: { width: 1.8, color: "#a66b00" }
      },
      {
        name: "买入持有", type: "line", data: BUY_HOLD, showSymbol: false,
        lineStyle: { width: 1.5, color: "#bfccd9", type: "dashed" }
      }
    ]
  });
}

// ---- 4.7.2 回撤曲线（面积图，需求文档明确要求） ----
function renderDrawdownChart() {
  var chart = getChart("drawdownChart");
  if (!chart) return;
  var ddPct = DRAWDOWN_DATA.map(function (v) { return +(v * 100).toFixed(2); });
  chart.setOption({
    grid: baseGrid(),
    xAxis: { type: "category", data: EQUITY_DATES, axisLabel: { fontSize: 10, interval: 14 } },
    yAxis: { type: "value", axisLabel: { formatter: "{value}%" }, max: 0 },
    tooltip: { trigger: "axis", valueFormatter: function (v) { return v + "%"; } },
    series: [{
      type: "line", data: ddPct, showSymbol: false,
      lineStyle: { color: "#c84646", width: 1.5 },
      areaStyle: { color: "rgba(200,70,70,0.25)" },
      markLine: {
        silent: true, symbol: "none",
        data: [{ yAxis: 0, lineStyle: { color: "#d9e2ec", width: 1 } }]
      }
    }]
  });
}

// ---- 初始渲染全部图表 ----
function renderAllCharts() {
  renderPriceChart();
  renderPreviewChart();
  renderMACDChart();
  renderRSIChart();
  renderEquityChart();
  renderDrawdownChart();
}

// ======================== 数据管理交互 ========================

document.getElementById("uploadBtn").addEventListener("click", function () {
  var file = document.getElementById("fileInput").files[0];
  if (!file) { showAlert("请先选择 CSV 文件（UC-02）", "error"); return; }
  if (!file.name.endsWith(".csv")) { showAlert("仅支持 CSV 格式文件（需求文档 4.1.2）", "error"); return; }
  showAlert("上传成功，共 12,420 行数据（UC-02）", "success");
});

document.getElementById("sampleBtn").addEventListener("click", function () {
  showAlert("已加载示例数据，共 12,420 行数据（UC-02）", "success");
  renderPreviewChart();
});

// ======================== 配置表单交互 ========================

document.getElementById("saveConfigBtn").addEventListener("click", function () {
  showAlert("配置已保存（UC-04/UC-05/UC-06）", "success");
});

// ======================== 开始回测流程 ========================

var runButton       = document.getElementById("runButton");
var backtestBtn     = document.getElementById("startBacktestBtn");
var statusSteps     = document.querySelectorAll(".status-step");
var resultEmpty     = document.getElementById("resultEmpty");
var resultLoading   = document.getElementById("resultLoading");
var resultContent   = document.getElementById("resultContent");

function setStep(idx, state, label) {
  var step = statusSteps[idx];
  if (!step) return;
  step.classList.remove("done", "current");
  if (state) step.classList.add(state);
  var s = step.querySelector("strong");
  if (s && label) s.textContent = label;
}

function runBacktest() {
  // 校验：至少选一个指标
  var checked = document.querySelectorAll('input[name="indicators"]:checked');
  if (checked.length === 0) {
    showAlert("请至少选择一个技术指标（需求文档 UC-04 异常流程）", "error");
    return;
  }

  // 进入运行状态
  if (runButton) { runButton.disabled = true; runButton.textContent = "运行中…"; }
  if (backtestBtn) { backtestBtn.disabled = true; backtestBtn.textContent = "运行中…"; }
  document.body.classList.add("is-running");
  resultEmpty.style.display    = "none";
  resultLoading.style.display  = "block";
  resultContent.style.display  = "none";
  setStep(2, "current", "GA 寻优中");

  showAlert("遗传算法滚动窗口训练中（种群 80 × 迭代 120），请稍候…", "info");

  // 模拟运行延迟
  setTimeout(function () {
    document.body.classList.remove("is-running");
    if (runButton)   { runButton.disabled = false; runButton.textContent = "重新回测"; }
    if (backtestBtn) { backtestBtn.disabled = false; backtestBtn.textContent = "重新回测"; }
    resultLoading.style.display  = "none";
    resultContent.style.display  = "block";
    setStep(2, "done", "优化完成");
    setStep(3, "done", "结果已生成");

    // 更新侧边栏状态
    document.getElementById("sidebarStatus").textContent = "回测完成";
    document.getElementById("sidebarSub").textContent    = "累计收益 36.82%";

    showAlert("回测完成！累计收益 36.82%，夏普比率 1.62，胜率 57.4%（UC-09）", "success");

    // 渲染结果图表
    setTimeout(function () {
      renderAllCharts();
    }, 200);

    // 滚动到结果区
    document.getElementById("sec-result").scrollIntoView({ behavior: "smooth", block: "start" });
  }, 2000);
}

if (runButton)   { runButton.addEventListener("click", runBacktest); }
if (backtestBtn) { backtestBtn.addEventListener("click", runBacktest); }

// ======================== 导出按钮 ========================

document.getElementById("exportTradesBtn").addEventListener("click", function () {
  showAlert("交易记录导出为 CSV 文件（UC-13）", "success");
});

// ======================== 侧边导航 ========================

document.querySelectorAll(".nav-item").forEach(function (item) {
  item.addEventListener("click", function (e) {
    document.querySelectorAll(".nav-item").forEach(function (n) { n.classList.remove("active"); });
    item.classList.add("active");
    var href = item.getAttribute("href");
    if (href && href.startsWith("#")) {
      var target = document.querySelector(href);
      if (target) {
        e.preventDefault();
        target.scrollIntoView({ behavior: "smooth", block: "start" });
      }
    }
  });
});

// ======================== 周期切换 ========================

document.querySelectorAll(".segmented").forEach(function (group) {
  group.addEventListener("click", function (event) {
    var target = event.target.closest("button");
    if (!target) return;
    group.querySelectorAll("button").forEach(function (b) { b.classList.remove("active"); });
    target.classList.add("active");
    renderPriceChart();
  });
});

// ======================== 响应式 ========================

var resizeTimer;
window.addEventListener("resize", function () {
  clearTimeout(resizeTimer);
  resizeTimer = setTimeout(resizeAll, 150);
});

// ======================== 初始化 ========================

window.addEventListener("load", function () {
  renderAllCharts();
  showAlert("原型演示界面已就绪 — 覆盖需求文档全部 4 个页面与图表", "info");
});
