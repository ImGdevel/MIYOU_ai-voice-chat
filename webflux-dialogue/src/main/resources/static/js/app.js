let mediaSource;
let sourceBuffer;
let chunks = [];
let chunkCount = 0;
let audioContext;
let analyser;
let animationId;
let dataArray;
let canvas;
let canvasCtx;
let shuffledIndices = [];

function showStatus(message, type) {
    const status = document.getElementById('status');
    status.textContent = message;
    status.className = `status ${type}`;
    status.style.display = 'block';
}

function updateStats(message) {
    document.getElementById('stats').textContent = message;
}

function shuffleArray(array) {
    const shuffled = [...array];
    for (let i = shuffled.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
    }
    return shuffled;
}

function initVisualizer() {
    canvas = document.getElementById('visualizer');
    canvasCtx = canvas.getContext('2d');

    const audioElement = document.getElementById('audio');

    if (!audioContext) {
        audioContext = new (window.AudioContext || window.webkitAudioContext)();
        analyser = audioContext.createAnalyser();
        const source = audioContext.createMediaElementSource(audioElement);
        source.connect(analyser);
        analyser.connect(audioContext.destination);

        analyser.fftSize = 128;
        const bufferLength = analyser.frequencyBinCount;
        dataArray = new Uint8Array(bufferLength);

        shuffledIndices = shuffleArray([...Array(bufferLength).keys()]);
    }

    drawVisualizer();
}

function drawVisualizer() {
    animationId = requestAnimationFrame(drawVisualizer);

    analyser.getByteFrequencyData(dataArray);

    const width = canvas.width;
    const height = canvas.height;
    const centerX = width / 2;
    const centerY = height / 2;
    const radius = 80;
    const barCount = dataArray.length;

    canvasCtx.clearRect(0, 0, width, height);

    for (let i = 0; i < barCount; i++) {
        const angle = (i / barCount) * Math.PI * 2;
        const dataIndex = shuffledIndices[i];
        const barHeight = (dataArray[dataIndex] / 255) * 60 + 10;

        const x1 = centerX + Math.cos(angle) * radius;
        const y1 = centerY + Math.sin(angle) * radius;
        const x2 = centerX + Math.cos(angle) * (radius + barHeight);
        const y2 = centerY + Math.sin(angle) * (radius + barHeight);

        const gradient = canvasCtx.createLinearGradient(x1, y1, x2, y2);
        gradient.addColorStop(0, 'rgba(255, 255, 255, 0.8)');
        gradient.addColorStop(1, 'rgba(255, 255, 255, 0.3)');

        canvasCtx.strokeStyle = gradient;
        canvasCtx.lineWidth = 4;
        canvasCtx.lineCap = 'round';

        canvasCtx.beginPath();
        canvasCtx.moveTo(x1, y1);
        canvasCtx.lineTo(x2, y2);
        canvasCtx.stroke();
    }

    canvasCtx.beginPath();
    canvasCtx.arc(centerX, centerY, radius - 10, 0, Math.PI * 2);
    canvasCtx.fillStyle = 'rgba(255, 255, 255, 0.2)';
    canvasCtx.fill();
}

function showVoiceIndicator() {
    const indicator = document.getElementById('voiceIndicator');
    indicator.classList.add('active');
    initVisualizer();
}

function hideVoiceIndicator() {
    const indicator = document.getElementById('voiceIndicator');
    indicator.classList.remove('active');
    if (animationId) {
        cancelAnimationFrame(animationId);
        animationId = null;
    }
}

function updateVoiceStatus(message) {
    const status = document.querySelector('.voice-status');
    status.textContent = message;
}

async function streamAudio() {
    const queryText = document.getElementById('queryText').value.trim();

    if (!queryText) {
        showStatus('질문을 입력해주세요', 'error');
        return;
    }

    const sendBtn = document.getElementById('sendBtn');
    const btnText = document.getElementById('btnText');

    sendBtn.disabled = true;
    btnText.innerHTML = '<span class="spinner"></span>스트리밍 중...';

    showStatus('MP3 스트리밍 시작...', 'info');
    showVoiceIndicator();
    updateVoiceStatus('음성 응답 준비 중...');

    const audioElement = document.getElementById('audio');

    if ('MediaSource' in window) {
        mediaSource = new MediaSource();
        audioElement.src = URL.createObjectURL(mediaSource);

        mediaSource.addEventListener('sourceopen', async () => {
            try {
                sourceBuffer = mediaSource.addSourceBuffer('audio/mpeg');
                chunks = [];
                chunkCount = 0;

                const response = await fetch('http://localhost:8081/rag/dialogue/audio/mp3', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                    },
                    body: JSON.stringify({
                        text: queryText,
                        requestedAt: new Date().toISOString()
                    })
                });

                if (!response.ok) {
                    throw new Error(`HTTP error! status: ${response.status}`);
                }

                const reader = response.body.getReader();
                let totalBytes = 0;

                document.getElementById('audioPlayer').style.display = 'block';
                audioElement.play();
                updateVoiceStatus('음성 응답 중...');

                sourceBuffer.addEventListener('updateend', () => {
                    if (chunks.length > 0 && !sourceBuffer.updating) {
                        sourceBuffer.appendBuffer(chunks.shift());
                    }
                });

                while (true) {
                    const { done, value } = await reader.read();

                    if (done) {
                        if (chunks.length === 0 && !sourceBuffer.updating) {
                            mediaSource.endOfStream();
                        }
                        showStatus('스트리밍 완료', 'success');
                        break;
                    }

                    chunkCount++;
                    totalBytes += value.byteLength;
                    updateStats(`청크: ${chunkCount}, 총 크기: ${(totalBytes / 1024).toFixed(2)} KB`);
                    updateVoiceStatus(`음성 응답 중... (청크 ${chunkCount})`);

                    if (sourceBuffer.updating || chunks.length > 0) {
                        chunks.push(value);
                    } else {
                        sourceBuffer.appendBuffer(value);
                    }

                    showStatus(`스트리밍 중... (청크 ${chunkCount})`, 'success');
                }

            } catch (error) {
                console.error('Streaming error:', error);
                showStatus(`스트리밍 오류: ${error.message}`, 'error');
                hideVoiceIndicator();
            } finally {
                sendBtn.disabled = false;
                btnText.textContent = '스트리밍 재생';
            }
        });

    } else {
        showStatus('이 브라우저는 Media Source Extensions를 지원하지 않습니다', 'error');
        sendBtn.disabled = false;
        btnText.textContent = '스트리밍 재생';
        hideVoiceIndicator();
    }
}

document.getElementById('audio').addEventListener('ended', () => {
    hideVoiceIndicator();
});

document.addEventListener('DOMContentLoaded', () => {
    document.getElementById('queryText').addEventListener('keydown', function(e) {
        if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
            streamAudio();
        }
    });
});
