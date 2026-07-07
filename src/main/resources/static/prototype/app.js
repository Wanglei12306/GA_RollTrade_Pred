const priceCanvas = document.getElementById("priceChart");
const equityCanvas = document.getElementById("equityChart");

function scaleCanvas(canvas) {
  const ratio = window.devicePixelRatio || 1;
  const rect = canvas.getBoundingClientRect();
  canvas.width = Math.floor(rect.width * ratio);
  canvas.height = Math.floor(rect.height * ratio);
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
  ctx.fillStyle = "#7a8b9b";

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

function createMapper(values, width, height, padding) {
  const min = Math.min(...values);
  const max = Math.max(...values);
  const plotWidth = width - padding.left - padding.right;
  const plotHeight = height - padding.top - padding.bottom;
  return (value, index, total) => ({
    x: padding.left + (plotWidth * index) / Math.max(total - 1, 1),
    y: padding.top + plotHeight - ((value - min) / Math.max(max - min, 1)) * plotHeight
  });
}

function drawPriceChart() {
  const { ctx, width, height } = scaleCanvas(priceCanvas);
  const padding = { top: 24, right: 24, bottom: 34, left: 46 };
  const prices = makeSeries(92, 3180, 2.6, 8.5);
  const ma = movingAverage(prices, 20);
  const mapper = createMapper([...prices, ...ma], width, height, padding);
  const pricePoints = prices.map((value, index) => mapper(value, index, prices.length));
  const maPoints = ma.map((value, index) => mapper(value, index, ma.length));

  drawGrid(ctx, width, height, padding);

  ctx.fillStyle = "#7a8b9b";
  ctx.fillText("3600", 8, padding.top + 4);
  ctx.fillText("3300", 8, height / 2);
  ctx.fillText("3000", 8, height - padding.bottom);
  ctx.fillText("2024 Q4", padding.left, height - 10);
  ctx.fillText("2025 Q2", width - padding.right - 58, height - 10);

  drawLine(ctx, pricePoints, "#1769aa", 2.5);
  drawLine(ctx, maPoints, "#a66b00", 1.8);

  const signals = [
    { index: 12, type: "buy" },
    { index: 27, type: "sell" },
    { index: 46, type: "buy" },
    { index: 63, type: "sell" },
    { index: 74, type: "buy" }
  ];

  signals.forEach((signal) => {
    const point = pricePoints[signal.index];
    ctx.beginPath();
    ctx.arc(point.x, point.y, 6, 0, Math.PI * 2);
    ctx.fillStyle = signal.type === "buy" ? "#16875d" : "#c84646";
    ctx.fill();
    ctx.strokeStyle = "#ffffff";
    ctx.lineWidth = 2;
    ctx.stroke();
  });
}

function drawEquityChart() {
  const { ctx, width, height } = scaleCanvas(equityCanvas);
  const padding = { top: 18, right: 18, bottom: 28, left: 42 };
  const equity = makeSeries(80, 1, 0.006, 0.006).map((value) => value + 0.08);
  const mapper = createMapper(equity, width, height, padding);
  const points = equity.map((value, index) => mapper(value, index, equity.length));

  drawGrid(ctx, width, height, padding);

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

  ctx.fillStyle = "#7a8b9b";
  ctx.font = "12px Microsoft YaHei, Arial";
  ctx.fillText("1.42", 8, padding.top + 4);
  ctx.fillText("1.20", 8, height / 2);
  ctx.fillText("1.00", 8, height - padding.bottom);
}

function renderCharts() {
  drawPriceChart();
  drawEquityChart();
}

document.getElementById("crossRate").addEventListener("input", (event) => {
  document.getElementById("crossValue").textContent = Number(event.target.value).toFixed(2);
});

document.getElementById("mutationRate").addEventListener("input", (event) => {
  document.getElementById("mutationValue").textContent = Number(event.target.value).toFixed(2);
});

document.getElementById("runButton").addEventListener("click", () => {
  document.body.classList.add("is-running");
  const button = document.getElementById("runButton");
  button.textContent = "运行中";
  setTimeout(() => {
    button.textContent = "重新回测";
    document.body.classList.remove("is-running");
  }, 900);
});

window.addEventListener("resize", renderCharts);
window.addEventListener("load", renderCharts);
