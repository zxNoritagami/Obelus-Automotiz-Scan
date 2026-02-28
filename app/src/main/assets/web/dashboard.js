// Obelus Web Dashboard - SSE Client Logic

const UI = {
    rpmValue: document.getElementById('rpm-value'),
    speedValue: document.getElementById('speed-value'),
    rpmProgress: document.getElementById('rpm-progress'),
    speedProgress: document.getElementById('speed-progress'),
    voltageValue: document.getElementById('voltage-value'),
    tempValue: document.getElementById('temp-value'),
    statusBadge: document.getElementById('status'),
    sseStatus: document.getElementById('sse-status'),
    chartContainer: document.getElementById('chart-container'),
    loginOverlay: document.getElementById('login-overlay'),
    otpInput: document.getElementById('otp-input'),
    loginButton: document.getElementById('login-button'),
    loginError: document.getElementById('login-error')
};

let authToken = localStorage.getItem('obelus_web_token') || "";

const MAX_RPM = 8000;
const MAX_SPEED = 240;
const CHART_MAX_POINTS = 60;
let rpmHistory = new Array(CHART_MAX_POINTS).fill(0);
let eventSource = null;
let reconnectTimeout = null;

// Initialize Chart Bars
function initChart() {
    UI.chartContainer.innerHTML = '';
    for (let i = 0; i < CHART_MAX_POINTS; i++) {
        const bar = document.createElement('div');
        bar.className = 'chart-bar';
        bar.style.height = '2px';
        UI.chartContainer.appendChild(bar);
    }
}

function updateChart(rpm) {
    rpmHistory.shift();
    rpmHistory.push(rpm);

    const bars = UI.chartContainer.children;
    for (let i = 0; i < CHART_MAX_POINTS; i++) {
        if (bars[i]) {
            const h = Math.max((rpmHistory[i] / MAX_RPM) * 100, 2);
            bars[i].style.height = `${h}%`;
        }
    }
}

function updateGauge(element, value, max) {
    if (!element) return;
    const radius = 70;
    const circumference = 2 * Math.PI * radius;
    // Evitar valores NaN
    const safeValue = isNaN(value) ? 0 : value;
    const percentage = Math.min(Math.max(safeValue / max, 0), 1);
    const offset = circumference - (percentage * circumference);

    element.style.strokeDasharray = circumference;
    element.style.strokeDashoffset = offset;
}

function setDisconnectedState() {
    UI.statusBadge.className = 'status-badge disconnected';
    UI.statusBadge.innerHTML = '<span class="status-dot"></span> SIN CONEXIÓN OBD';

    UI.rpmValue.innerText = '----';
    UI.speedValue.innerText = '----';
    UI.voltageValue.innerText = '--.-V';
    UI.tempValue.innerText = '--°C';

    updateGauge(UI.rpmProgress, 0, MAX_RPM);
    updateGauge(UI.speedProgress, 0, MAX_SPEED);
    updateChart(0);
}

function updateUI(data) {
    if (!data.obdConnected) {
        setDisconnectedState();
        return;
    }

    // OBD Connected
    UI.statusBadge.className = 'status-badge connected';
    UI.statusBadge.innerHTML = '<span class="status-dot"></span> EN LÍNEA';

    const rpm = data.rpm || 0;
    const speed = data.speed || 0;

    UI.rpmValue.innerText = Math.round(rpm);
    UI.speedValue.innerText = Math.round(speed);
    UI.voltageValue.innerText = (data.voltage || 0).toFixed(1) + 'V';
    UI.tempValue.innerText = (data.temp || 0) + '°C';

    updateGauge(UI.rpmProgress, rpm, MAX_RPM);
    updateGauge(UI.speedProgress, speed, MAX_SPEED);
    updateChart(rpm);
}

function connectSSE() {
    if (eventSource) {
        eventSource.close();
    }

    if (!authToken) return;

    UI.sseStatus.innerText = 'Conectando...';
    UI.sseStatus.className = 'mono-text status-text disconnected';

    // NanoHttpdServer now supports ?auth= query param for SSE
    eventSource = new EventSource(`/api/events?auth=${encodeURIComponent(authToken)}`);

    eventSource.onopen = () => {
        console.log("SSE Conectado");
        UI.sseStatus.innerText = 'Conectado (Live)';
        UI.sseStatus.className = 'mono-text status-text connected';
        if (reconnectTimeout) clearTimeout(reconnectTimeout);
    };

    eventSource.onmessage = (event) => {
        try {
            const data = JSON.parse(event.data);
            updateUI(data);
        } catch (e) {
            console.error("Error parseando SSE json", e);
        }
    };

    eventSource.onerror = (err) => {
        console.error("SSE Error de conexión", err);
        UI.sseStatus.innerText = 'Reconectando...';
        UI.sseStatus.className = 'mono-text status-text disconnected';
        eventSource.close();
        setDisconnectedState();

        // Exponential backoff or static 3s reconnect
        reconnectTimeout = setTimeout(connectSSE, 3000);
    };
}

// --- Auth Handling ---

async function attemptLogin() {
    const password = UI.otpInput.value.trim();
    if (!password) return;

    UI.loginButton.innerText = "VERIFICANDO...";
    UI.loginButton.disabled = true;
    UI.loginError.style.display = 'none';

    try {
        const response = await fetch('/auth', {
            method: 'POST',
            body: JSON.stringify({ password })
        });

        if (response.ok) {
            authToken = password;
            localStorage.setItem('obelus_web_token', authToken);
            showDashboard();
        } else {
            throw new Error("Invalid password");
        }
    } catch (e) {
        UI.loginError.style.display = 'block';
        UI.loginButton.innerText = "DESBLOQUEAR TERMINAL";
        UI.loginButton.disabled = false;
    }
}

function showDashboard() {
    UI.loginOverlay.style.display = 'none';
    initChart();
    setDisconnectedState();
    connectSSE();
}

UI.loginButton.addEventListener('click', attemptLogin);
UI.otpInput.addEventListener('keypress', (e) => {
    if (e.key === 'Enter') attemptLogin();
});

// Start
if (!authToken) {
    UI.loginOverlay.style.display = 'flex';
} else {
    showDashboard();
}

// Theming from URL if desired
const urlParams = new URLSearchParams(window.location.search);
if (urlParams.get('theme') === 'sport') {
    document.documentElement.style.setProperty('--primary', '#FF3333');
    document.documentElement.style.setProperty('--secondary', '#FFAA00');
}
