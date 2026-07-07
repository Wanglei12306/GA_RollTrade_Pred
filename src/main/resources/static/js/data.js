const alertBox = document.getElementById("alertBox");

function showAlert(message, type) {
  const cls = type === "error" ? "alert-error" : (type === "success" ? "alert-success" : "alert-info");
  alertBox.innerHTML = '<div class="alert ' + cls + '">' + message + "</div>";
}

function pct(v) { return (v * 100).toFixed(2) + "%"; }

function renderPreview(p) {
  document.getElementById("previewPanel").style.display = "";
  document.getElementById("previewTitle").textContent = p.dataName || "行情数据";
  document.getElementById("summaryGrid").innerHTML = [
    metric("数据行数", p.rowCount),
    metric("起始时间", p.startDate || "—"),
    metric("结束时间", p.endDate || "—")
  ].join("");

  const tbody = document.getElementById("previewTbody");
  tbody.innerHTML = (p.headRows || []).map(k =>
    `<tr><td>${k.date}</td><td>${k.open}</td><td>${k.high}</td><td>${k.low}</td><td>${k.close}</td><td>${k.volume}</td></tr>`
  ).join("");

  const chart = echarts.init(document.getElementById("priceChart"));
  chart.setOption({
    grid: { left: 50, right: 20, top: 20, bottom: 30 },
    xAxis: { type: "category", data: p.priceDates, axisLabel: { fontSize: 10 } },
    yAxis: { type: "value", scale: true },
    tooltip: { trigger: "axis" },
    series: [{ name: "收盘价", type: "line", data: p.priceCloses, showSymbol: false, lineStyle: { width: 2, color: "#1769aa" } }]
  });
}

function metric(label, value) {
  return `<div class="metric"><span>${label}</span><strong>${value}</strong></div>`;
}

async function postUpload() {
  const file = document.getElementById("fileInput").files[0];
  if (!file) { showAlert("请先选择 CSV 文件", "error"); return; }
  const fd = new FormData();
  fd.append("file", file);
  try {
    const res = await fetch("/api/upload", { method: "POST", body: fd });
    const data = await res.json();
    if (!res.ok) { showAlert(data.error || "上传失败", "error"); return; }
    showAlert("上传成功，共 " + data.rowCount + " 行数据", "success");
    renderPreview(data);
  } catch (e) { showAlert("上传失败：" + e.message, "error"); }
}

async function loadSample() {
  try {
    const res = await fetch("/api/sample");
    const data = await res.json();
    if (!res.ok) { showAlert(data.error || "加载失败", "error"); return; }
    showAlert("已加载示例数据，共 " + data.rowCount + " 行数据", "success");
    renderPreview(data);
  } catch (e) { showAlert("加载失败：" + e.message, "error"); }
}

document.getElementById("uploadBtn").addEventListener("click", postUpload);
document.getElementById("sampleBtn").addEventListener("click", loadSample);

(async function init() {
  try {
    const res = await fetch("/api/preview");
    const data = await res.json();
    if (data.hasData === false) return;
    renderPreview(data);
  } catch (e) { /* 忽略 */ }
})();
