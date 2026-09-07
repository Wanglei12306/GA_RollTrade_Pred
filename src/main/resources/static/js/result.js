const alertBox = document.getElementById("alertBox");

function showAlert(message, type) {
  const cls = type === "error" ? "alert-error" : "alert-info";
  alertBox.innerHTML = '<div class="alert ' + cls + '">' + message + "</div>";
}

const pct = v => (v * 100).toFixed(2) + "%";
const num = (v, d) => (v == null ? "—" : Number(v).toFixed(d));
const cls = v => v >= 0 ? "positive" : "negative";

function metric(label, value, extra) {
  return `<div class="metric"><span>${label}</span><strong class="${extra || ""}">${value}</strong></div>`;
}

function render(report) {
  document.getElementById("content").style.display = "";
  const opt = report.optimized;
  const fix = report.fixed;
  const singleModel = !report.windows || report.windows.length === 0;
  document.getElementById("subTitle").textContent = singleModel
    ? `数据：${report.dataName} · 单模型回测 · ${report.outOfSampleKlines.length} 根`
    : `数据：${report.dataName} · 滚动窗口 ${report.windows.length} 个 · 样本外 ${report.outOfSampleKlines.length} 根`;

  const m = opt.metrics;
  const dirAcc = report.directionalAccuracy;
  const commit = report.commitmentRate;
  document.getElementById("metricGrid").innerHTML = [
    metric("累计收益", pct(m.cumulativeReturn), cls(m.cumulativeReturn)),
    metric("年化收益", pct(m.annualReturn), cls(m.annualReturn)),
    metric("最大回撤", "-" + pct(m.maxDrawdown), "negative"),
    metric("回撤持续", num(m.maxDrawdownDuration, 0) + " 根"),
    metric("夏普比率", num(m.sharpe, 2)),
    metric("胜率", pct(m.winRate)),
    metric("盈亏比", num(m.profitLossRatio, 2)),
    metric("交易次数", m.tradeCount),
    metric("买入持有", pct(m.buyHoldReturn), cls(m.buyHoldReturn)),
    metric("方向准确率", dirAcc == null ? "—" : pct(dirAcc), dirAcc == null ? "" : (dirAcc >= 0.5 ? "positive" : "negative")),
    metric("表态率", commit == null ? "—" : pct(commit))
  ].join("");
  renderPrice(report);
  renderIndicators(report);
  renderEquity(opt, fix);
  renderDrawdown(opt);
  renderDrawdownDist(opt);
  renderCompare(m, fix.metrics);
  renderTrades(opt.trades);
  renderWindows(report.windows);
  renderAnnual(report.annualReturns);
}

function renderPrice(report) {
  const klines = report.outOfSampleKlines;
  const dates = klines.map(k => k.date);
  const closes = klines.map(k => k.close);
  const buys = report.optimized.signals.filter(s => s.signal === "BUY")
    .map(s => [s.date, s.price]);
  const sells = report.optimized.signals.filter(s => s.signal === "SELL")
    .map(s => [s.date, s.price]);
  const chart = echarts.init(document.getElementById("priceChart"));
  chart.setOption({
    grid: { left: 54, right: 20, top: 30, bottom: 40 },
    legend: { data: ["收盘价", "买入", "卖出"], top: 0 },
    xAxis: { type: "category", data: dates, axisLabel: { fontSize: 10 } },
    yAxis: { type: "value", scale: true },
    tooltip: { trigger: "axis" },
    series: [
      { name: "收盘价", type: "line", data: closes, showSymbol: false, lineStyle: { width: 2, color: "#1769aa" } },
      { name: "买入", type: "scatter", data: buys, symbolSize: 9, itemStyle: { color: "#16875d" } },
      { name: "卖出", type: "scatter", data: sells, symbolSize: 9, itemStyle: { color: "#c84646" } }
    ]
  });
}

function renderEquity(opt, fix) {
  const chart = echarts.init(document.getElementById("equityChart"));
  chart.setOption({
    grid: { left: 54, right: 20, top: 30, bottom: 40 },
    legend: { data: ["遗传优化策略", "固定参数策略"], top: 0 },
    xAxis: { type: "category", data: opt.dates, axisLabel: { fontSize: 10 } },
    yAxis: { type: "value", scale: true },
    tooltip: { trigger: "axis" },
    series: [
      { name: "遗传优化策略", type: "line", data: opt.equityCurve, showSymbol: false, lineStyle: { width: 2, color: "#1769aa" } },
      { name: "固定参数策略", type: "line", data: fix.equityCurve, showSymbol: false, lineStyle: { width: 2, color: "#a66b00" } }
    ]
  });
}

function renderDrawdown(opt) {
  const dd = opt.drawdownCurve.map(v => +(v * 100).toFixed(2));
  const chart = echarts.init(document.getElementById("drawdownChart"));
  chart.setOption({
    grid: { left: 54, right: 20, top: 20, bottom: 40 },
    xAxis: { type: "category", data: opt.dates, axisLabel: { fontSize: 10 } },
    yAxis: { type: "value", axisLabel: { formatter: "{value}%" } },
    tooltip: { trigger: "axis" },
    series: [{
      type: "line", data: dd, showSymbol: false, areaStyle: { color: "rgba(200,70,70,0.25)" },
      lineStyle: { color: "#c84646", width: 1.5 }
    }]
  });
}

function renderCompare(m, f) {
  const rows = [
    ["累计收益", pct(m.cumulativeReturn), pct(f.cumulativeReturn), pct(m.buyHoldReturn)],
    ["年化收益", pct(m.annualReturn), pct(f.annualReturn), "—"],
    ["最大回撤", "-" + pct(m.maxDrawdown), "-" + pct(f.maxDrawdown), "—"],
    ["回撤持续根数", num(m.maxDrawdownDuration, 0), num(f.maxDrawdownDuration, 0), "—"],
    ["夏普比率", num(m.sharpe, 2), num(f.sharpe, 2), "—"],
    ["胜率", pct(m.winRate), pct(f.winRate), "—"],
    ["盈亏比", num(m.profitLossRatio, 2), num(f.profitLossRatio, 2), "—"],
    ["交易次数", m.tradeCount, f.tradeCount, "—"]
  ];
  document.getElementById("compareTbody").innerHTML = rows.map(r =>
    `<tr><td>${r[0]}</td><td>${r[1]}</td><td>${r[2]}</td><td>${r[3]}</td></tr>`).join("");
}

function renderTrades(trades) {
  if (!trades || !trades.length) {
    document.getElementById("tradeTbody").innerHTML = '<tr><td colspan="7">无交易记录</td></tr>';
    return;
  }
  document.getElementById("tradeTbody").innerHTML = trades.map(t => {
    const tag = t.direction === "BUY" ? '<span class="tag buy">买入</span>'
      : t.direction === "SHORT" ? '<span class="tag sell">开空</span>'
      : t.direction === "COVER" ? '<span class="tag buy">平空</span>'
      : '<span class="tag sell">卖出</span>';
    const pnl = t.pnl ? `<span class="${cls(t.pnl)}">${t.pnl >= 0 ? "+" : ""}${t.pnl.toFixed(0)}</span>` : "—";
    return `<tr><td>${t.time}</td><td>${tag}</td><td>${t.price.toFixed(2)}</td><td>${t.quantity.toFixed(0)}</td><td>${t.commission.toFixed(0)}</td><td>${pnl}</td><td>${t.positionAfter.toFixed(0)}</td></tr>`;
  }).join("");
}

function renderWindows(windows) {
  const tbody = document.getElementById("windowTbody");
  if (!windows || !windows.length) {
    tbody.innerHTML = '<tr><td colspan="4">单模型回测，无滚动窗口</td></tr>';
    return;
  }
  tbody.innerHTML = windows.map((w, i) =>
    `<tr><td>${i + 1}</td><td>${w.trainStart} ~ ${w.trainEnd}</td><td>${w.predictStart} ~ ${w.predictEnd}</td><td>${num(w.bestFitness, 4)}</td></tr>`
  ).join("");
}

function renderIndicators(report) {
  const curves = report.indicatorCurves || [];
  const dates = report.outOfSampleKlines.map(k => k.date);
  const el = document.getElementById("indicatorChart");
  if (!curves.length) { el.innerHTML = '<p class="hint" style="padding:16px">无指标信号数据</p>'; return; }
  const chart = echarts.init(el);
  const series = curves.map(c => ({
    name: c.name, type: "line", data: c.scores, showSymbol: false, lineStyle: { width: 1.5 }
  }));
  chart.setOption({
    grid: { left: 54, right: 20, top: 40, bottom: 40 },
    legend: { data: curves.map(c => c.name), top: 0, type: "scroll" },
    xAxis: { type: "category", data: dates, axisLabel: { fontSize: 10 } },
    yAxis: { type: "value", min: -1, max: 1, name: "信号分" },
    tooltip: { trigger: "axis" },
    series
  });
}

function renderDrawdownDist(opt) {
  const dd = opt.drawdownCurve || [];
  const el = document.getElementById("drawdownDistChart");
  if (!dd.length) { el.innerHTML = '<p class="hint" style="padding:16px">无回撤数据</p>'; return; }
  const buckets = [
    { label: "0~1%", min: 0, max: 0.01, count: 0 },
    { label: "1~3%", min: 0.01, max: 0.03, count: 0 },
    { label: "3~5%", min: 0.03, max: 0.05, count: 0 },
    { label: "5~10%", min: 0.05, max: 0.10, count: 0 },
    { label: ">10%", min: 0.10, max: Infinity, count: 0 }
  ];
  for (const v of dd) {
    for (const b of buckets) {
      if (v >= b.min && v < b.max) { b.count++; break; }
    }
  }
  const chart = echarts.init(el);
  chart.setOption({
    grid: { left: 54, right: 20, top: 30, bottom: 40 },
    xAxis: { type: "category", data: buckets.map(b => b.label) },
    yAxis: { type: "value", name: "根数" },
    tooltip: { trigger: "axis" },
    series: [{
      type: "bar", data: buckets.map(b => b.count),
      itemStyle: { color: "#c84646" }, label: { show: true, position: "top" }
    }]
  });
}

function renderAnnual(annualReturns) {
  const tbody = document.getElementById("annualTbody");
  if (!annualReturns || !annualReturns.length) {
    tbody.innerHTML = '<tr><td colspan="2">无年度收益数据</td></tr>';
    return;
  }
  tbody.innerHTML = annualReturns.map(a =>
    `<tr><td>${a.year}</td><td><span class="${cls(a.returnRate)}">${pct(a.returnRate)}</span></td></tr>`
  ).join("");
}

(async function init() {
  try {
    const res = await fetch("/api/result");
    const data = await res.json();
    if (data.hasResult === false) {
      document.getElementById("subTitle").textContent = "尚未运行回测";
      showAlert('暂无回测结果，请先 <a href="/config">配置参数并运行回测</a>。', "info");
      return;
    }
    render(data);
  } catch (e) {
    showAlert("结果加载失败：" + e.message, "error");
  }
})();
