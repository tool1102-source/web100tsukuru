// 「らくがきオークション」と同じ13色・512×512 の絵を、図形を組み合わせて描くための道具。
// 描いた絵は色の番号の並び（Uint8Array）で持ち、パレットつきの PNG に書き出す。
import zlib from 'node:zlib';

// Palette.java と同じ順・同じ色
export const PALETTE = [0xffffff, 0x222222, 0x9a9a9a, 0xe23b3b, 0xf28c28, 0xf7d038, 0x3fa34d, 0x4cc3e8, 0x2f5fd0, 0x8b4fc4, 0xf49ac1, 0x8a5a3b, 0xf6cfa8];
export const C = { white: 0, black: 1, gray: 2, red: 3, orange: 4, yellow: 5, green: 6, sky: 7, blue: 8, purple: 9, pink: 10, brown: 11, skin: 12 };
export const SIZE = 512;

export function rng(seed) {
    let a = seed >>> 0;
    return () => {
        a = (a + 0x6D2B79F5) >>> 0;
        let t = a;
        t = Math.imul(t ^ (t >>> 15), t | 1);
        t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
        return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
}

/** 点の列を通るなめらかな線（Catmull-Rom）。closed なら閉じた形 */
export function smooth(points, closed = false, steps = 12) {
    const out = [];
    const n = points.length;
    const at = i => closed ? points[(i + n) % n] : points[Math.max(0, Math.min(n - 1, i))];
    const last = closed ? n : n - 1;
    for (let i = 0; i < last; i++) {
        const p0 = at(i - 1), p1 = at(i), p2 = at(i + 1), p3 = at(i + 2);
        for (let s = 0; s < steps; s++) {
            const t = s / steps, t2 = t * t, t3 = t2 * t;
            out.push([
                0.5 * (2 * p1[0] + (-p0[0] + p2[0]) * t + (2 * p0[0] - 5 * p1[0] + 4 * p2[0] - p3[0]) * t2 + (-p0[0] + 3 * p1[0] - 3 * p2[0] + p3[0]) * t3),
                0.5 * (2 * p1[1] + (-p0[1] + p2[1]) * t + (2 * p0[1] - 5 * p1[1] + 4 * p2[1] - p3[1]) * t2 + (-p0[1] + 3 * p1[1] - 3 * p2[1] + p3[1]) * t3)
            ]);
        }
    }
    if (!closed) {
        out.push(points[n - 1]);
    }
    return out;
}

export class Canvas {
    constructor(bg = C.white) {
        this.a = new Uint8Array(SIZE * SIZE).fill(bg);
    }

    set(x, y, c) {
        x = Math.floor(x);
        y = Math.floor(y);
        if (x >= 0 && y >= 0 && x < SIZE && y < SIZE) {
            this.a[y * SIZE + x] = c;
        }
    }

    get(x, y) {
        return this.a[Math.floor(y) * SIZE + Math.floor(x)];
    }

    rect(x, y, w, h, c) {
        for (let j = Math.max(0, Math.floor(y)); j < Math.min(SIZE, y + h); j++) {
            for (let i = Math.max(0, Math.floor(x)); i < Math.min(SIZE, x + w); i++) {
                this.a[j * SIZE + i] = c;
            }
        }
        return this;
    }

    /** 多角形を塗る（偶奇の規則） */
    poly(points, c) {
        let minY = Infinity, maxY = -Infinity;
        for (const p of points) {
            minY = Math.min(minY, p[1]);
            maxY = Math.max(maxY, p[1]);
        }
        for (let y = Math.max(0, Math.floor(minY)); y <= Math.min(SIZE - 1, Math.ceil(maxY)); y++) {
            const yc = y + 0.5;
            const xs = [];
            for (let i = 0; i < points.length; i++) {
                const [x1, y1] = points[i];
                const [x2, y2] = points[(i + 1) % points.length];
                if ((y1 <= yc && y2 > yc) || (y2 <= yc && y1 > yc)) {
                    xs.push(x1 + (yc - y1) * (x2 - x1) / (y2 - y1));
                }
            }
            xs.sort((p, q) => p - q);
            for (let k = 0; k + 1 < xs.length; k += 2) {
                for (let x = Math.max(0, Math.ceil(xs[k] - 0.5)); x <= Math.min(SIZE - 1, Math.floor(xs[k + 1] - 0.5)); x++) {
                    this.a[y * SIZE + x] = c;
                }
            }
        }
        return this;
    }

    /** なめらかな閉じた形を塗る */
    blob(points, c) {
        return this.poly(smooth(points, true), c);
    }

    ellipse(cx, cy, rx, ry, c, rot = 0) {
        const cos = Math.cos(rot), sin = Math.sin(rot);
        const r = Math.max(rx, ry) + 1;
        for (let y = Math.floor(cy - r); y <= cy + r; y++) {
            for (let x = Math.floor(cx - r); x <= cx + r; x++) {
                const dx = x + 0.5 - cx, dy = y + 0.5 - cy;
                const u = dx * cos + dy * sin, v = -dx * sin + dy * cos;
                if ((u * u) / (rx * rx) + (v * v) / (ry * ry) <= 1) {
                    this.set(x, y, c);
                }
            }
        }
        return this;
    }

    disk(cx, cy, r, c) {
        return this.ellipse(cx, cy, r, r, c);
    }

    /** 太さ w の線（点の列をつなぐ） */
    line(points, w, c) {
        const r = w / 2;
        for (let i = 0; i < points.length; i++) {
            const a = points[i], b = points[Math.min(i + 1, points.length - 1)];
            const d = Math.hypot(b[0] - a[0], b[1] - a[1]);
            const n = Math.max(1, Math.ceil(d / 0.7));
            for (let k = 0; k <= n; k++) {
                const x = a[0] + (b[0] - a[0]) * k / n, y = a[1] + (b[1] - a[1]) * k / n;
                if (r <= 0.75) {
                    this.set(x, y, c);
                } else {
                    for (let j = Math.floor(y - r); j <= y + r; j++) {
                        for (let i2 = Math.floor(x - r); i2 <= x + r; i2++) {
                            if ((i2 + 0.5 - x) ** 2 + (j + 0.5 - y) ** 2 <= r * r) {
                                this.set(i2, j, c);
                            }
                        }
                    }
                }
            }
        }
        return this;
    }

    /** なめらかな線 */
    curve(points, w, c, closed = false) {
        const pts = smooth(points, closed);
        if (closed) {
            pts.push(pts[0]);
        }
        return this.line(pts, w, c);
    }

    /** 塗って、ふちを描く */
    shape(points, fill, edge = C.black, w = 4, round = true) {
        const pts = round ? smooth(points, true) : points;
        this.poly(pts, fill);
        if (edge !== null) {
            this.line([...pts, pts[0]], w, edge);
        }
        return this;
    }

    ring(cx, cy, rx, ry, w, c, rot = 0) {
        const pts = [];
        for (let k = 0; k <= 72; k++) {
            const t = k / 72 * Math.PI * 2;
            const u = Math.cos(t) * rx, v = Math.sin(t) * ry;
            pts.push([cx + u * Math.cos(rot) - v * Math.sin(rot), cy + u * Math.sin(rot) + v * Math.cos(rot)]);
        }
        return this.line(pts, w, c);
    }

    /** うず巻き */
    spiral(cx, cy, r0, r1, turns, w, c, start = 0, dir = 1) {
        const pts = [];
        const n = Math.ceil(turns * 60);
        for (let k = 0; k <= n; k++) {
            const t = k / n;
            const ang = start + dir * t * turns * Math.PI * 2;
            const r = r0 + (r1 - r0) * t;
            pts.push([cx + Math.cos(ang) * r, cy + Math.sin(ang) * r]);
        }
        return this.line(pts, w, c);
    }

    /** つながった同じ色の場所を塗る */
    fill(x, y, c) {
        x = Math.floor(x);
        y = Math.floor(y);
        const target = this.a[y * SIZE + x];
        if (target === c) {
            return this;
        }
        const stack = [[x, y]];
        while (stack.length) {
            const [px, py] = stack.pop();
            if (px < 0 || py < 0 || px >= SIZE || py >= SIZE || this.a[py * SIZE + px] !== target) {
                continue;
            }
            let l = px, r = px;
            while (l > 0 && this.a[py * SIZE + l - 1] === target) l--;
            while (r < SIZE - 1 && this.a[py * SIZE + r + 1] === target) r++;
            for (let i = l; i <= r; i++) {
                this.a[py * SIZE + i] = c;
                if (py > 0 && this.a[(py - 1) * SIZE + i] === target) stack.push([i, py - 1]);
                if (py < SIZE - 1 && this.a[(py + 1) * SIZE + i] === target) stack.push([i, py + 1]);
            }
        }
        return this;
    }

    /** 短い筆あとをたくさん置く（area(x,y) が true の場所だけ） */
    strokes(count, len, w, colors, angle, area, random, jitter = 0.4) {
        for (let k = 0; k < count; k++) {
            const x = random() * SIZE, y = random() * SIZE;
            if (area && !area(x, y)) {
                continue;
            }
            const a = (typeof angle === 'function' ? angle(x, y) : angle) + (random() - 0.5) * jitter;
            const l = len * (0.6 + random() * 0.8);
            const c = colors[Math.floor(random() * colors.length)];
            this.line([[x, y], [x + Math.cos(a) * l, y + Math.sin(a) * l]], w, c);
        }
        return this;
    }

    png() {
        return encodePng(this.a);
    }
}

const CRC = (() => {
    const t = new Uint32Array(256);
    for (let n = 0; n < 256; n++) {
        let c = n;
        for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
        t[n] = c >>> 0;
    }
    return t;
})();

function crc32(buf) {
    let c = 0xffffffff;
    for (const b of buf) c = CRC[(c ^ b) & 0xff] ^ (c >>> 8);
    return (c ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
    const len = Buffer.alloc(4);
    len.writeUInt32BE(data.length);
    const td = Buffer.concat([Buffer.from(type, 'ascii'), data]);
    const crc = Buffer.alloc(4);
    crc.writeUInt32BE(crc32(td));
    return Buffer.concat([len, td, crc]);
}

/** 色の番号の並びを、パレットつき PNG にする（w×h、既定は 512×512） */
export function encodePng(index, w = SIZE, h = SIZE) {
    const ihdr = Buffer.alloc(13);
    ihdr.writeUInt32BE(w, 0);
    ihdr.writeUInt32BE(h, 4);
    ihdr[8] = 8;
    ihdr[9] = 3;
    const plte = Buffer.alloc(PALETTE.length * 3);
    PALETTE.forEach((rgb, i) => {
        plte[i * 3] = (rgb >> 16) & 0xff;
        plte[i * 3 + 1] = (rgb >> 8) & 0xff;
        plte[i * 3 + 2] = rgb & 0xff;
    });
    const raw = Buffer.alloc((w + 1) * h);
    for (let y = 0; y < h; y++) {
        raw[y * (w + 1)] = 0;
        for (let x = 0; x < w; x++) raw[y * (w + 1) + 1 + x] = index[y * w + x];
    }
    return Buffer.concat([
        Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
        chunk('IHDR', ihdr), chunk('PLTE', plte), chunk('IDAT', zlib.deflateSync(raw, {level: 9})), chunk('IEND', Buffer.alloc(0))
    ]);
}

/** 何枚かを縮めて並べた一覧の PNG（確かめる用） */
export function sheet(canvases, cols = 4, cell = 256, gap = 8) {
    const rows = Math.ceil(canvases.length / cols);
    const w = cols * cell + (cols + 1) * gap, h = rows * cell + (rows + 1) * gap;
    const out = new Uint8Array(w * h).fill(C.gray);
    const scale = SIZE / cell;
    canvases.forEach((cv, i) => {
        const ox = gap + (i % cols) * (cell + gap), oy = gap + Math.floor(i / cols) * (cell + gap);
        for (let y = 0; y < cell; y++) {
            for (let x = 0; x < cell; x++) {
                out[(oy + y) * w + ox + x] = cv.a[Math.floor(y * scale) * SIZE + Math.floor(x * scale)];
            }
        }
    });
    return encodePng(out, w, h);
}
