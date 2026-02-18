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
let isPlaying = false;
let currentSessionId = null;
let currentPersonaId = null;

function getOrCreateUserId() {
    const storageKey = 'miyou-user-id';
    const existingUserId = localStorage.getItem(storageKey);

    if (existingUserId) {
        return existingUserId;
    }

    const newUserId = (window.crypto && window.crypto.randomUUID)
        ? window.crypto.randomUUID()
        : `${Date.now()}-${Math.random().toString(16).slice(2)}`;

    localStorage.setItem(storageKey, newUserId);
    return newUserId;
}

async function createSession(personaId) {
    try {
        const response = await fetch('/rag/dialogue/session', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                userId: getOrCreateUserId(),
                personaId: personaId
            })
        });

        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        const sessionData = await response.json();
        currentSessionId = sessionData.sessionId;
        currentPersonaId = sessionData.personaId;

        console.log('Session created:', sessionData);
        return sessionData;
    } catch (error) {
        console.error('Error creating session:', error);
        updateStatusText('세션 생성 실패');
        throw error;
    }
}

function showPersonaModal() {
    const modal = document.getElementById('personaModal');
    modal.classList.remove('hidden');
}

function hidePersonaModal() {
    const modal = document.getElementById('personaModal');
    modal.classList.add('hidden');
}

async function selectPersona(personaId) {
    hidePersonaModal();
    updateStatusText('세션 생성 중...');

    try {
        await createSession(personaId);
        updateStatusText('대화를 시작하려면 화면을 탭하세요');
    } catch (error) {
        showPersonaModal();
    }
}

function shuffleArray(array) {
    const shuffled = [...array];
    for (let i = shuffled.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
    }
    return shuffled;
}

function drawVisualizer() {
    if (!canvas || !canvasCtx) return;

    if (animationId) {
        requestAnimationFrame(drawVisualizer);
    }

    const width = canvas.width;
    const height = canvas.height;
    const centerX = width / 2;
    const centerY = height / 2;

    canvasCtx.clearRect(0, 0, width, height);

    const radius = 60;
    const barCount = 64;

    if (isPlaying && analyser) {
        analyser.getByteFrequencyData(dataArray);

        const avgIntensity = dataArray.reduce((sum, val) => sum + val, 0) / dataArray.length / 255;

        for (let i = 0; i < barCount; i++) {
            const angle = (i / barCount) * Math.PI * 2;
            const dataIndex = shuffledIndices[i];
            const barHeight = (dataArray[dataIndex] / 255) * 40 + 8;

            const x1 = centerX + Math.cos(angle) * radius;
            const y1 = centerY + Math.sin(angle) * radius;
            const x2 = centerX + Math.cos(angle) * (radius + barHeight);
            const y2 = centerY + Math.sin(angle) * (radius + barHeight);

            const intensity = dataArray[dataIndex] / 255;
            const grayValue = Math.floor(60 + intensity * 80);

            canvasCtx.strokeStyle = `rgb(${grayValue}, ${grayValue}, ${grayValue})`;
            canvasCtx.lineWidth = 3;
            canvasCtx.lineCap = 'round';

            canvasCtx.beginPath();
            canvasCtx.moveTo(x1, y1);
            canvasCtx.lineTo(x2, y2);
            canvasCtx.stroke();
        }

        const glowGradient = canvasCtx.createRadialGradient(centerX, centerY, 0, centerX, centerY, radius + 20);
        glowGradient.addColorStop(0, `rgba(80, 80, 80, ${0.1 + avgIntensity * 0.15})`);
        glowGradient.addColorStop(0.7, `rgba(80, 80, 80, ${0.05 + avgIntensity * 0.08})`);
        glowGradient.addColorStop(1, 'rgba(80, 80, 80, 0)');

        canvasCtx.beginPath();
        canvasCtx.arc(centerX, centerY, radius + 20, 0, Math.PI * 2);
        canvasCtx.fillStyle = glowGradient;
        canvasCtx.fill();

        const centerGradient = canvasCtx.createRadialGradient(centerX, centerY, 0, centerX, centerY, radius - 8);
        centerGradient.addColorStop(0, '#555');
        centerGradient.addColorStop(1, '#888');

        canvasCtx.beginPath();
        canvasCtx.arc(centerX, centerY, radius - 8, 0, Math.PI * 2);
        canvasCtx.fillStyle = centerGradient;
        canvasCtx.fill();
    } else {
        for (let i = 0; i < barCount; i++) {
            const angle = (i / barCount) * Math.PI * 2;
            const barHeight = 8;

            const x1 = centerX + Math.cos(angle) * radius;
            const y1 = centerY + Math.sin(angle) * radius;
            const x2 = centerX + Math.cos(angle) * (radius + barHeight);
            const y2 = centerY + Math.sin(angle) * (radius + barHeight);

            canvasCtx.strokeStyle = '#666';
            canvasCtx.lineWidth = 3;
            canvasCtx.lineCap = 'round';

            canvasCtx.beginPath();
            canvasCtx.moveTo(x1, y1);
            canvasCtx.lineTo(x2, y2);
            canvasCtx.stroke();
        }

        const centerGradient = canvasCtx.createRadialGradient(centerX, centerY, 0, centerX, centerY, radius - 8);
        centerGradient.addColorStop(0, '#555');
        centerGradient.addColorStop(1, '#888');

        canvasCtx.beginPath();
        canvasCtx.arc(centerX, centerY, radius - 8, 0, Math.PI * 2);
        canvasCtx.fillStyle = centerGradient;
        canvasCtx.fill();
    }
}

function startVisualizer() {
    isPlaying = true;
    if (!audioContext) {
        const audioElement = document.getElementById('audio');
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
}

function stopVisualizer() {
    isPlaying = false;
}

function updateStatusText(message) {
    const statusText = document.getElementById('statusText');
    statusText.textContent = message;
}

function toggleInputOverlay() {
    const overlay = document.getElementById('inputOverlay');
    const isActive = overlay.classList.toggle('active');

    if (isActive) {
        updateStatusText('');
    } else {
        const audioElement = document.getElementById('audio');
        if (!audioElement.src || audioElement.ended) {
            updateStatusText('대화를 시작하려면 화면을 탭하세요');
        }
    }
}

function toggleVoice() {
    const voiceBtn = document.getElementById('voiceBtn');
    voiceBtn.classList.toggle('active');
}

async function streamAudio() {
    const queryText = document.getElementById('queryText').value.trim();
    const voiceEnabled = document.getElementById('voiceBtn').classList.contains('active');

    if (!queryText) {
        return;
    }

    updateStatusText(queryText);

    const overlay = document.getElementById('inputOverlay');
    overlay.classList.remove('active');

    const sendBtn = document.getElementById('sendBtn');
    sendBtn.disabled = true;

    if (voiceEnabled) {
        await streamWithVoice(queryText, sendBtn);
    } else {
        await streamTextOnly(queryText, sendBtn);
    }
}

async function streamTextOnly(queryText, sendBtn) {
    if (!currentSessionId) {
        updateStatusText('세션이 없습니다. 페르소나를 선택하세요.');
        showPersonaModal();
        return;
    }

    try {
        const response = await fetch('/rag/dialogue/text', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                sessionId: currentSessionId,
                text: queryText,
                requestedAt: new Date().toISOString()
            })
        });

        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder('utf-8');
        let fullResponse = '';
        let buffer = '';

        while (true) {
            const { done, value } = await reader.read();

            if (done) break;

            buffer += decoder.decode(value, { stream: true });

            const lines = buffer.split('\n');
            buffer = lines.pop() || '';

            for (const line of lines) {
                if (line.startsWith('data:')) {
                    const data = line.substring(5);
                    console.log('Raw data:', JSON.stringify(data));
                    if (data.length > 0) {
                        const token = data.startsWith(' ') ? data.substring(1) : data;
                        console.log('Processed token:', JSON.stringify(token));
                        fullResponse += token;
                        updateStatusText(fullResponse);
                    }
                }
            }
        }

    } catch (error) {
        console.error('Streaming error:', error);
        updateStatusText('오류가 발생했습니다');
    } finally {
        sendBtn.disabled = false;
        document.getElementById('queryText').value = '';
    }
}

async function streamWithVoice(queryText, sendBtn) {
    if (!currentSessionId) {
        updateStatusText('세션이 없습니다. 페르소나를 선택하세요.');
        showPersonaModal();
        return;
    }

    startVisualizer();

    const audioElement = document.getElementById('audio');

    if ('MediaSource' in window) {
        mediaSource = new MediaSource();
        audioElement.src = URL.createObjectURL(mediaSource);

        mediaSource.addEventListener('sourceopen', async () => {
            try {
                sourceBuffer = mediaSource.addSourceBuffer('audio/mpeg');
                chunks = [];
                chunkCount = 0;

                const response = await fetch('/rag/dialogue/audio?format=mp3', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                    },
                    body: JSON.stringify({
                        sessionId: currentSessionId,
                        text: queryText,
                        requestedAt: new Date().toISOString()
                    })
                });

                if (!response.ok) {
                    throw new Error(`HTTP error! status: ${response.status}`);
                }

                const reader = response.body.getReader();

                audioElement.play();

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
                        break;
                    }

                    chunkCount++;

                    if (sourceBuffer.updating || chunks.length > 0) {
                        chunks.push(value);
                    } else {
                        sourceBuffer.appendBuffer(value);
                    }
                }

            } catch (error) {
                console.error('Streaming error:', error);
                updateStatusText('오류가 발생했습니다');
                stopVisualizer();
            } finally {
                sendBtn.disabled = false;
                document.getElementById('queryText').value = '';
            }
        });

    } else {
        updateStatusText('브라우저가 지원되지 않습니다');
        sendBtn.disabled = false;
        stopVisualizer();
    }
}

document.addEventListener('DOMContentLoaded', () => {
    canvas = document.getElementById('visualizer');
    canvasCtx = canvas.getContext('2d');

    animationId = 1;
    drawVisualizer();

    // Persona card click events
    document.querySelectorAll('.persona-card').forEach(card => {
        card.addEventListener('click', () => {
            const personaId = card.getAttribute('data-persona');
            selectPersona(personaId);
        });
    });

    document.querySelector('.voice-container').addEventListener('click', (e) => {
        if (!currentSessionId) {
            showPersonaModal();
            return;
        }
        const clickY = e.clientY;
        const windowHeight = window.innerHeight;

        if (clickY > windowHeight - 300) {
            const overlay = document.getElementById('inputOverlay');
            if (!overlay.classList.contains('active')) {
                toggleInputOverlay();
                const textarea = document.getElementById('queryText');
                setTimeout(() => {
                    textarea.focus();
                }, 300);
            }
        }
    });

    document.getElementById('inputPanel').addEventListener('click', (e) => {
        const overlay = document.getElementById('inputOverlay');
        if (!overlay.classList.contains('active')) {
            e.stopPropagation();
            toggleInputOverlay();
        }
    });

    document.getElementById('audio').addEventListener('ended', () => {
        stopVisualizer();
        updateStatusText('대화를 시작하려면 화면을 탭하세요');
    });

    document.getElementById('queryText').addEventListener('keydown', function(e) {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            streamAudio();
        }
    });
});
