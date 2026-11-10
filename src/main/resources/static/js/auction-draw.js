/*
 * 「らくがきオークション」の描く画面。
 * 絵は 512×512 の「色の番号」の並びとして持ち、ペンは丸を押して線にする（にじまないので、保存した絵がそのまま13色になる）。
 * 描くたびに、今日来ている収集家が気に入りそうかを計り直す（計り方は ArtCollectors.java・Appraisal.java と同じ）。
 * 描きかけは、このブラウザに自動で残す（保存すると消す）。
 */
(() => {
    'use strict';
    const root = document.getElementById('draw');
    if (!root) {
        return;
    }
    const SIZE = 512;
    // Palette.java と同じ順・同じ色
    const PALETTE = [0xffffff, 0x222222, 0x9a9a9a, 0xe23b3b, 0xf28c28, 0xf7d038, 0x3fa34d, 0x4cc3e8, 0x2f5fd0, 0x8b4fc4, 0xf49ac1, 0x8a5a3b, 0xf6cfa8];
    const DRAFT_KEY = 'auction-draft-v1';
    const UNDO_LIMIT = 40;

    const canvas = document.getElementById('draw-canvas');
    const ctx = canvas.getContext('2d');
    const image = ctx.createImageData(SIZE, SIZE);
    const pixels = new Uint32Array(image.data.buffer);
    const little = new Uint8Array(new Uint32Array([1]).buffer)[0] === 1;
    const COLORS = PALETTE.map(rgb => {
        const r = (rgb >> 16) & 0xff, g = (rgb >> 8) & 0xff, b = rgb & 0xff;
        return (little ? ((0xff << 24) | (b << 16) | (g << 8) | r) : ((r << 24) | (g << 16) | (b << 8) | 0xff)) >>> 0;
    });

    let index = new Uint8Array(SIZE * SIZE);
    let color = 1;
    let size = 8;
    let mode = 'pen';
    const undoStack = [];
    const redoStack = [];

    // ---------- 描く ----------
    let dirty = null;
    let frame = 0;

    function markDirty(x0, y0, x1, y1) {
        if (!dirty) {
            dirty = [x0, y0, x1, y1];
        } else {
            dirty[0] = Math.min(dirty[0], x0);
            dirty[1] = Math.min(dirty[1], y0);
            dirty[2] = Math.max(dirty[2], x1);
            dirty[3] = Math.max(dirty[3], y1);
        }
        if (!frame) {
            frame = requestAnimationFrame(flush);
        }
    }

    function flush() {
        frame = 0;
        if (!dirty) {
            return;
        }
        const x0 = Math.max(0, dirty[0]), y0 = Math.max(0, dirty[1]);
        const x1 = Math.min(SIZE - 1, dirty[2]), y1 = Math.min(SIZE - 1, dirty[3]);
        dirty = null;
        if (x1 >= x0 && y1 >= y0) {
            ctx.putImageData(image, 0, 0, x0, y0, x1 - x0 + 1, y1 - y0 + 1);
        }
    }

    function repaintAll() {
        for (let i = 0; i < index.length; i++) {
            pixels[i] = COLORS[index[i]];
        }
        ctx.putImageData(image, 0, 0);
    }

    const disks = new Map();

    function disk(diameter) {
        if (disks.has(diameter)) {
            return disks.get(diameter);
        }
        const r = diameter / 2;
        const reach = Math.ceil(r);
        const offsets = [];
        for (let dy = -reach; dy <= reach; dy++) {
            for (let dx = -reach; dx <= reach; dx++) {
                if (dx * dx + dy * dy <= r * r) {
                    offsets.push(dx, dy);
                }
            }
        }
        disks.set(diameter, offsets);
        return offsets;
    }

    function stamp(cx, cy, c) {
        const offsets = disk(size);
        const ix = Math.round(cx), iy = Math.round(cy);
        const value = COLORS[c];
        for (let k = 0; k < offsets.length; k += 2) {
            const x = ix + offsets[k], y = iy + offsets[k + 1];
            if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) {
                continue;
            }
            const i = y * SIZE + x;
            index[i] = c;
            pixels[i] = value;
        }
        const reach = Math.ceil(size / 2) + 1;
        markDirty(ix - reach, iy - reach, ix + reach, iy + reach);
    }

    function line(a, b, c) {
        const dist = Math.hypot(b.x - a.x, b.y - a.y);
        // 細かく押すほど線のふちがなめらかになる
        const step = Math.max(1, size / 8);
        const n = Math.max(1, Math.ceil(dist / step));
        for (let k = 1; k <= n; k++) {
            stamp(a.x + (b.x - a.x) * k / n, a.y + (b.y - a.y) * k / n, c);
        }
    }

    // 塗りつぶし（同じ色がつながっているところを、横の列ごとに塗る）
    function fill(px, py, c) {
        const sx = Math.floor(px), sy = Math.floor(py);
        if (sx < 0 || sy < 0 || sx >= SIZE || sy >= SIZE) {
            return false;
        }
        const target = index[sy * SIZE + sx];
        if (target === c) {
            return false;
        }
        const value = COLORS[c];
        const stack = [sx, sy];
        while (stack.length) {
            const y = stack.pop();
            let x = stack.pop();
            let i = y * SIZE + x;
            while (x > 0 && index[i - 1] === target) {
                x--;
                i--;
            }
            let upOpen = false, downOpen = false;
            while (x < SIZE && index[i] === target) {
                index[i] = c;
                pixels[i] = value;
                if (y > 0) {
                    const up = index[i - SIZE] === target;
                    if (up && !upOpen) {
                        stack.push(x, y - 1);
                    }
                    upOpen = up;
                }
                if (y < SIZE - 1) {
                    const down = index[i + SIZE] === target;
                    if (down && !downOpen) {
                        stack.push(x, y + 1);
                    }
                    downOpen = down;
                }
                x++;
                i++;
            }
        }
        ctx.putImageData(image, 0, 0);
        return true;
    }

    // ---------- 戻す・やり直す ----------
    const undoButton = root.querySelector('[data-action="undo"]');
    const redoButton = root.querySelector('[data-action="redo"]');

    function pushUndo() {
        undoStack.push(index.slice());
        if (undoStack.length > UNDO_LIMIT) {
            undoStack.shift();
        }
        redoStack.length = 0;
    }

    function undo() {
        if (!undoStack.length) {
            return;
        }
        redoStack.push(index);
        index = undoStack.pop();
        repaintAll();
        changed();
    }

    function redo() {
        if (!redoStack.length) {
            return;
        }
        undoStack.push(index);
        index = redoStack.pop();
        repaintAll();
        changed();
    }

    function updateButtons() {
        undoButton.disabled = undoStack.length === 0;
        redoButton.disabled = redoStack.length === 0;
    }

    // ---------- ペンの動き ----------
    let drawingPointer = null;
    let last = null;

    function position(e) {
        const rect = canvas.getBoundingClientRect();
        return {
            x: (e.clientX - rect.left) * SIZE / rect.width,
            y: (e.clientY - rect.top) * SIZE / rect.height
        };
    }

    function currentColor() {
        return mode === 'eraser' ? 0 : color;
    }

    canvas.addEventListener('pointerdown', e => {
        if (drawingPointer !== null || (e.pointerType === 'mouse' && e.button !== 0)) {
            return;
        }
        e.preventDefault();
        const p = position(e);
        if (mode === 'fill') {
            pushUndo();
            if (fill(p.x, p.y, color)) {
                changed();
            } else {
                undoStack.pop();
            }
            return;
        }
        drawingPointer = e.pointerId;
        try {
            canvas.setPointerCapture(e.pointerId);
        } catch (ignored) {
            // 取れなくても描ける
        }
        pushUndo();
        last = p;
        stamp(p.x, p.y, currentColor());
    });

    canvas.addEventListener('pointermove', e => {
        if (e.pointerId !== drawingPointer) {
            return;
        }
        e.preventDefault();
        const events = typeof e.getCoalescedEvents === 'function' ? e.getCoalescedEvents() : [e];
        for (const ev of (events.length ? events : [e])) {
            const p = position(ev);
            line(last, p, currentColor());
            last = p;
        }
    });

    function endStroke(e) {
        if (e.pointerId !== drawingPointer) {
            return;
        }
        drawingPointer = null;
        last = null;
        flush();
        changed();
    }

    canvas.addEventListener('pointerup', endStroke);
    canvas.addEventListener('pointercancel', endStroke);

    // ---------- 色・太さ・道具 ----------
    function selectIn(group, button) {
        group.forEach(b => {
            const on = b === button;
            b.classList.toggle('is-active', on);
            b.setAttribute('aria-checked', on ? 'true' : 'false');
        });
    }

    const swatches = [...root.querySelectorAll('[data-color]')];
    const sizes = [...root.querySelectorAll('[data-size]')];
    const modes = [...root.querySelectorAll('[data-mode]')];
    swatches.forEach(b => b.addEventListener('click', () => {
        color = Number(b.dataset.color);
        selectIn(swatches, b);
        if (mode === 'eraser') {
            mode = 'pen';
            selectIn(modes, modes.find(m => m.dataset.mode === 'pen'));
        }
    }));
    sizes.forEach(b => b.addEventListener('click', () => {
        size = Number(b.dataset.size);
        selectIn(sizes, b);
    }));
    modes.forEach(b => b.addEventListener('click', () => {
        mode = b.dataset.mode;
        selectIn(modes, b);
        canvas.dataset.mode = mode;
    }));
    undoButton.addEventListener('click', undo);
    redoButton.addEventListener('click', redo);
    root.querySelector('[data-action="clear"]').addEventListener('click', () => {
        if (!hasInk() || !confirm('全部消しますか？（「戻す」で元に戻せます）')) {
            return;
        }
        pushUndo();
        index = new Uint8Array(SIZE * SIZE);
        repaintAll();
        changed();
    });
    document.addEventListener('keydown', e => {
        if (!(e.ctrlKey || e.metaKey) || e.target.closest('input, textarea')) {
            return;
        }
        const key = e.key.toLowerCase();
        if (key === 'z' && !e.shiftKey) {
            e.preventDefault();
            undo();
        } else if ((key === 'z' && e.shiftKey) || key === 'y') {
            e.preventDefault();
            redo();
        }
    });

    // ---------- 収集家の反応（ArtCollectors.java・Appraisal.java と同じ計り方） ----------
    const sat = x => Math.max(0, Math.min(1, x));
    const share = (f, list) => list.reduce((sum, c) => sum + f.share[c], 0);
    const TASTES = {
        akasaka: f => 0.4 + 1.6 * sat(share(f, [3, 4, 10]) / 0.5),
        aoyagi: f => 0.4 + 1.6 * sat(share(f, [7, 8, 9]) / 0.5),
        morino: f => 0.4 + 1.6 * sat(share(f, [6, 11]) / 0.5),
        kanenari: f => 0.4 + 1.6 * sat((f.colors - 1) / 6) * Math.sqrt(sat(f.ink / 0.4)),
        hosokawa: f => 0.4 + 1.6 * sat(f.detail / 0.1),
        shirai: f => 0.4 + 1.6 * sat(1 - Math.max(0, f.ink - 0.12) / 0.25) * sat(1 - (f.colors - 2) / 5),
        sumida: f => 0.4 + 1.6 * sat((share(f, [1, 2]) - 0.3) / 0.5),
        himari: f => 0.4 + 1.6 * sat(share(f, [5, 10, 7, 4]) / 0.5)
    };
    const TRENDS = {red: [3, 10], warm: [4, 5, 12], green: [6, 11], blue: [7, 8], purple: [9], mono: [1, 2]};
    const trendColors = TRENDS[root.dataset.trend] || [];

    function measure() {
        const counts = new Array(PALETTE.length).fill(0);
        let edges = 0;
        for (let y = 0; y < SIZE; y++) {
            const row = y * SIZE;
            for (let x = 0; x < SIZE; x++) {
                const i = row + x;
                const c = index[i];
                counts[c]++;
                if ((x + 1 < SIZE && index[i + 1] !== c) || (y + 1 < SIZE && index[i + SIZE] !== c)) {
                    edges++;
                }
            }
        }
        const total = SIZE * SIZE;
        const inked = total - counts[0];
        const shares = counts.map((n, c) => c === 0 || inked === 0 ? 0 : n / inked);
        const colors = shares.filter((s, c) => c > 0 && s >= 0.02).length;
        return {ink: inked / total, detail: edges / total, colors, share: shares};
    }

    function hasInk() {
        for (let i = 0; i < index.length; i++) {
            if (index[i] !== 0) {
                return true;
            }
        }
        return false;
    }

    const meters = [...root.querySelectorAll('[data-guest]')];
    const trendValue = document.getElementById('trend-value');
    const qualityValue = document.getElementById('quality-value');
    let measureTimer = 0;

    function showMeters() {
        const f = measure();
        const empty = f.ink < 0.005;
        meters.forEach(li => {
            const out = li.querySelector('.meter-value');
            const taste = TASTES[li.dataset.guest];
            li.classList.remove('is-none', 'is-1', 'is-2', 'is-3');
            if (empty || !taste) {
                out.textContent = '—';
                return;
            }
            const t = taste(f);
            if (t < 0.8) {
                out.textContent = '興味なし';
                li.classList.add('is-none');
            } else if (t < 1.2) {
                out.textContent = '★ 少し気になる';
                li.classList.add('is-1');
            } else if (t < 1.6) {
                out.textContent = '★★ ほしい';
                li.classList.add('is-2');
            } else {
                out.textContent = '★★★ ぜひほしい';
                li.classList.add('is-3');
            }
        });
        if (trendValue) {
            trendValue.textContent = Math.round(share(f, trendColors) * 100) + '%';
        }
        if (qualityValue) {
            // 絵のでき（Appraisal.base と同じ。塗った広さ・細かさ・色の数）
            const q = 0.35 * sat(f.ink / 0.3) + 0.35 * sat(f.detail / 0.08) + 0.3 * sat((f.colors - 1) / 5);
            const pips = empty ? 0 : Math.max(1, Math.round(q * 5));
            qualityValue.textContent = '●'.repeat(pips) + '○'.repeat(5 - pips);
            qualityValue.setAttribute('aria-label', '5段階の' + pips);
        }
    }

    // ---------- 描きかけを残す ----------
    const titleInput = document.getElementById('draw-title');
    let draftTimer = 0;

    function encodeDraft() {
        const parts = [];
        let run = 1;
        for (let i = 1; i <= index.length; i++) {
            if (i < index.length && index[i] === index[i - 1]) {
                run++;
            } else {
                parts.push(index[i - 1].toString(36) + '.' + run.toString(36));
                run = 1;
            }
        }
        return parts.join(',');
    }

    function decodeDraft(text) {
        const out = new Uint8Array(SIZE * SIZE);
        let at = 0;
        for (const part of text.split(',')) {
            const [c, n] = part.split('.');
            const value = parseInt(c, 36), count = parseInt(n, 36);
            if (!(value >= 0 && value < PALETTE.length) || !(count > 0) || at + count > out.length) {
                return null;
            }
            out.fill(value, at, at + count);
            at += count;
        }
        return at === out.length ? out : null;
    }

    function saveDraft() {
        clearTimeout(draftTimer);
        draftTimer = setTimeout(() => {
            try {
                if (hasInk()) {
                    localStorage.setItem(DRAFT_KEY, JSON.stringify({index: encodeDraft(), title: titleInput.value}));
                } else {
                    localStorage.removeItem(DRAFT_KEY);
                }
            } catch (ignored) {
                // 残せないブラウザでも描ける
            }
        }, 400);
    }

    function restoreDraft() {
        try {
            const saved = JSON.parse(localStorage.getItem(DRAFT_KEY) || 'null');
            const restored = saved && decodeDraft(saved.index);
            if (restored) {
                index = restored;
                if (saved.title && !titleInput.value) {
                    titleInput.value = saved.title;
                }
                return true;
            }
        } catch (ignored) {
            // 読めなければ白紙から
        }
        return false;
    }

    function clearDraft() {
        try {
            localStorage.removeItem(DRAFT_KEY);
        } catch (ignored) {
            // なにもしない
        }
    }

    function changed() {
        updateButtons();
        clearTimeout(measureTimer);
        measureTimer = setTimeout(showMeters, 120);
        saveDraft();
    }

    titleInput.addEventListener('input', saveDraft);

    // ---------- お題 ----------
    const promptButton = document.getElementById('prompt-button');
    const promptHint = document.getElementById('prompt-hint');
    const prompts = (promptButton.dataset.prompts || '').split(',').filter(Boolean);
    let lastPrompt = '';
    promptButton.addEventListener('click', () => {
        let p = lastPrompt;
        while (prompts.length > 1 && p === lastPrompt) {
            p = prompts[Math.floor(Math.random() * prompts.length)];
        }
        if (!titleInput.value || titleInput.value === lastPrompt) {
            titleInput.value = p;
        }
        lastPrompt = p;
        promptHint.textContent = 'お題は「' + p + '」。題名はあとで変えられます。';
        saveDraft();
    });

    // ---------- 保存 ----------
    const form = document.getElementById('draw-form');
    const errorBox = document.getElementById('draw-error');
    const submit = form.querySelector('.draw-submit');

    function showError(message) {
        errorBox.textContent = message;
        errorBox.hidden = false;
    }

    form.addEventListener('submit', e => {
        e.preventDefault();
        errorBox.hidden = true;
        const title = titleInput.value.trim();
        if (measure().ink < 0.005) {
            showError('ほとんど白紙です。もう少し描いてから保存してください。');
            return;
        }
        if (!title) {
            showError('題名を付けてください。');
            titleInput.focus();
            return;
        }
        submit.disabled = true;
        submit.textContent = '保存しています…';
        flush();
        canvas.toBlob(blob => {
            const body = new FormData();
            body.append('title', title);
            body.append('image', blob, 'drawing.png');
            fetch('/auction/artworks', {method: 'POST', body, headers: {'X-Requested-With': 'fetch'}, credentials: 'same-origin'})
                .then(res => res.json().then(json => ({ok: res.ok, json})))
                .then(({ok, json}) => {
                    if (ok && json.url) {
                        clearDraft();
                        location.href = json.url;
                        return;
                    }
                    throw new Error(json.error || '保存できませんでした。');
                })
                .catch(err => {
                    showError(err && err.message && !/fetch|network|JSON/i.test(err.message)
                        ? err.message : '保存できませんでした。通信のようすを確かめて、もう一度お試しください。');
                    submit.disabled = false;
                    submit.textContent = '保存して出品へ進む';
                });
        }, 'image/png');
    });

    // ---------- はじめ ----------
    if (restoreDraft()) {
        promptHint.textContent = '前に描きかけた絵を出しています。白紙から描くなら「全部消す」を押してください。';
    }
    repaintAll();
    changed();
})();
