let mediaSource;
let sourceBuffer;
let chunks = [];
let chunkCount = 0;

function showStatus(message, type) {
    const status = document.getElementById('status');
    status.textContent = message;
    status.className = `status ${type}`;
    status.style.display = 'block';
}

function updateStats(message) {
    document.getElementById('stats').textContent = message;
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
            } finally {
                sendBtn.disabled = false;
                btnText.textContent = '스트리밍 재생';
            }
        });

    } else {
        showStatus('이 브라우저는 Media Source Extensions를 지원하지 않습니다', 'error');
        sendBtn.disabled = false;
        btnText.textContent = '스트리밍 재생';
    }
}

document.addEventListener('DOMContentLoaded', () => {
    document.getElementById('queryText').addEventListener('keydown', function(e) {
        if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
            streamAudio();
        }
    });
});
