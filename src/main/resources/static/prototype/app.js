const priceCanvas = document.getElementById("priceChart");
const equityCanvas = document.getElementById("equityChart");

function scaleCanvas(canvas) {
  const ratio = window.devicePixelRatio || 1;
  const rect = canvas.getBoundingClientRect();
  // 防止容器隐藏时尺寸为 0 导致绘制异常
  canvas.width = Math.max(1, Math.floor(rect.width * ratio));
  canvas.height = Math.max(1, Math.floor(rect.height * ratio));
  const ctx = canvas.getContext("2d");
  ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
  return { ctx, width: rect.width, height: rect.height };
}

function makeSeries(length, start, drift, wave) {
  const values = [];
  let current = start;
  for (let i = 0; i < length; i += 1) {
    current += Math.sin(i / 6) * wave + Math.cos(i / 13) * wave * 0.7 + drift;
    values.push(Number(current.toFixed(2)));
  }
  return values;
}

function movingAverage(values, windowSize) {
  return values.map((_, index) => {
    const start = Math.max(0, index - windowSize + 1);
    const slice = values.slice(start, index + 1);
    return slice.reduce((sum, value) => sum + value, 0) / slice.length;
  });
}

function drawGrid(ctx, width, height, padding) {
  ctx.clearRect(0, 0, width, height);
  ctx.strokeStyle = "#e3ebf3";
  ctx.lineWidth = 1;
  ctx.font = "12px Microsoft YaHei, Arial";

  for (let i = 0; i <= 4; i += 1) {
    const y = padding.top + ((height - padding.top - padding.bottom) * i) / 4;
    ctx.beginPath();
    ctx.moveTo(padding.left, y);
    ctx.lineTo(width - padding.right, y);
    ctx.stroke();
  }

  for (let i = 0; i <= 5; i += 1) {
    const x = padding.left + ((width - padding.left - padding.right) * i) / 5;
    ctx.beginPath();
    ctx.moveTo(x, padding.top);
    ctx.lineTo(x, height - padding.bottom);
    ctx.stroke();
  }
}

// 根据实际数据最小/最大值在 5 条横向网格线上标注刻度，避免硬编码失真
function drawYLabels(ctx, min, max, padding, height, decimals) {
  const plotHeight = height - padding.top - padding.bottom;
  ctx.fillStyle = "#7a8b9b";
  ctx.font = "12px Microsoft YaHei, Arial";
  ctx.textAlign = "right";
  ctx.textBaseline = "middle";
  for (let i = 0; i <= 4; i += 1) {
    const value = max - ((max - min) * i) / 4;
    const y = padding.top + (plotHeight * i) / 4;
    ctx.fillText(value.toFixed(decimals), padding.left - 8, y);
  }
  ctx.textAlign = "left";
  ctx.textBaseline = "alphabetic";
}

function drawLine(ctx, points, color, width = 2) {
  ctx.beginPath();
  points.forEach((point, index) => {
    if (index === 0) {
      ctx.moveTo(point.x, point.y);
    } else {
      ctx.lineTo(point.x, point.y);
    }
  });
  ctx.strokeStyle = color;
  ctx.lineWidth = width;
  ctx.lineJoin = "round";
  ctx.lineCap = "round";
  ctx.stroke();
}

function drawSignals(ctx, points) {
  const signals = [
    { index: 12, type: "buy" },
    { index: 27, type: "sell" },
    { index: 46, type: "buy" },
    { index: 63, type: "sell" },
    { index: 74, type: "buy" }
  ];
  signals.forEach((signal) => {
    const point = points[signal.index];
    if (!point) return;
    ctx.beginPath();
    ctx.arc(point.x, point.y, 6, 0, Math.PI * 2);
    ctx.fillStyle = signal.type === "buy" ? "#16875d" : "#c84646";
    ctx.fill();
    ctx.strokeStyle = "#ffffff";
    ctx.lineWidth = 2;
    ctx.stroke();
  });
}

function createMapper(values, width, height, padding) {
  const min = Math.min(...values);
  const max = Math.max(...values);
  const plotWidth = width - padding.left - padding.right;
  const plotHeight = height - padding.top - padding.bottom;
  const map = (value, index, total) => ({
    x: padding.left + (plotWidth * index) / Math.max(total - 1, 1),
    y: padding.top + plotHeight - ((value - min) / Math.max(max - min, 1)) * plotHeight
  });
  return { map, min, max };
}

function drawPriceChart() {
  const { ctx, width, height } = scaleCanvas(priceCanvas);
  if (width <= 0 || height <= 0) return;
  const padding = { top: 24, right: 24, bottom: 34, left: 52 };
  const prices = makeSeries(92, 3180, 2.6, 8.5);
  const ma = movingAverage(prices, 20);
  const { map, min, max } = createMapper([...prices, ...ma], width, height, padding);
  const pricePoints = prices.map((value, index) => map(value, index, prices.length));
  const maPoints = ma.map((value, index) => map(value, index, ma.length));

  drawGrid(ctx, width, height, padding);
  drawYLabels(ctx, min, max, padding, height, 0);

  ctx.fillStyle = "#7a8b9b";
  ctx.fillText("2024 Q4", padding.left, height - 10);
  ctx.fillText("2025 Q2", width - padding.right - 58, height - 10);

  drawLine(ctx, pricePoints, "#1769aa", 2.5);
  drawLine(ctx, maPoints, "#a66b00", 1.8);
  drawSignals(ctx, pricePoints);
}

function drawEquityChart() {
  const { ctx, width, height } = scaleCanvas(equityCanvas);
  if (width <= 0 || height <= 0) return;
  const padding = { top: 18, right: 18, bottom: 28, left: 48 };
  const equity = makeSeries(80, 1, 0.006, 0.006).map((value) => value + 0.08);
  const { map, min, max } = createMapper(equity, width, height, padding);
  const points = equity.map((value, index) => map(value, index, equity.length));

  drawGrid(ctx, width, height, padding);
  drawYLabels(ctx, min, max, padding, height, 2);

  const gradient = ctx.createLinearGradient(0, padding.top, 0, height - padding.bottom);
  gradient.addColorStop(0, "rgba(23, 105, 170, 0.22)");
  gradient.addColorStop(1, "rgba(23, 105, 170, 0)");

  ctx.beginPath();
  points.forEach((point, index) => {
    if (index === 0) {
      ctx.moveTo(point.x, point.y);
    } else {
      ctx.lineTo(point.x, point.y);
    }
  });
  ctx.lineTo(points[points.length - 1].x, height - padding.bottom);
  ctx.lineTo(points[0].x, height - padding.bottom);
  ctx.closePath();
  ctx.fillStyle = gradient;
  ctx.fill();

  drawLine(ctx, points, "#1769aa", 2.5);
}

function renderCharts() {
  drawPriceChart();
  drawEquityChart();
}

// 滑块实时回显
document.getElementById("crossRate").addEventListener("input", (event) => {
  document.getElementById("crossValue").textContent = Number(event.target.value).toFixed(2);
});

document.getElementById("mutationRate").addEventListener("input", (event) => {
  document.getElementById("mutationValue").textContent = Number(event.target.value).toFixed(2);
});

// 周期切换（日线/周线/月线）单选高亮
document.querySelectorAll(".segmented").forEach((group) => {
  group.addEventListener("click", (event) => {
    const target = event.target.closest("button");
    if (!target) return;
    group.querySelectorAll("button").forEach((b) => b.classList.remove("active"));
    target.classList.add("active");
  });
});

// 侧边导航高亮
document.querySelectorAll(".nav-item").forEach((item) => {
  item.addEventListener("click", () => {
    document.querySelectorAll(".nav-item").forEach((n) => n.classList.remove("active"));
    item.classList.add("active");
  });
});

// 开始回测：联动状态条演示 等待运行 → 运行中 → 结果生成
const runButton = document.getElementById("runButton");
const statusSteps = document.querySelectorAll(".status-step");

function setStep(index, state, label) {
  const step = statusSteps[index];
  if (!step) return;
  step.classList.remove("done", "current");
  if (state) step.classList.add(state);
  const strong = step.querySelector("strong");
  if (strong && label) strong.textContent = label;
}

runButton.addEventListener("click", () => {
  if (runButton.disabled) return;
  runButton.disabled = true;
  document.body.classList.add("is-running");
  runButton.textContent = "运行中…";
  setStep(2, "current", "运行中");
  setTimeout(() => {
    document.body.classList.remove("is-running");
    runButton.textContent = "重新回测";
    runButton.disabled = false;
    setStep(2, "done", "优化完成");
    setStep(3, "done", "结果已生成");
  }, 1200);
});

// 防抖重绘，避免拖拽窗口时高频刷新
let resizeTimer;
window.addEventListener("resize", () => {
  clearTimeout(resizeTimer);
  resizeTimer = setTimeout(renderCharts, 120);
});
window.addEventListener("load", renderCharts);
