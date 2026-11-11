// 著作権の切れた名画を、らくがきオークションの13色で「らくがき版」に描き直す。
// 実行: node tools/official-art/paintings.mjs  → src/main/resources/official/*.png と確認用の一覧
import fs from 'node:fs';
import path from 'node:path';
import {Canvas, C, rng, smooth, sheet} from './canvas.mjs';

const TAU = Math.PI * 2;

// ---------------------------------------------------------------- 葛飾北斎「神奈川沖浪裏」
function greatWave() {
    const cv = new Canvas(C.skin);
    const r = rng(11);
    cv.rect(0, 0, 512, 18, C.gray);
    cv.strokes(90, 30, 3, [C.gray], 0, (x, y) => y > 14 && y < 44, r, 0.1);
    // 遠くの海
    cv.poly([[0, 360], [512, 330], [512, 512], [0, 512]], C.blue);
    cv.strokes(160, 26, 3, [C.sky], 0, (x, y) => y > 345 && y < 512, r, 0.2);
    // 富士山
    cv.shape([[290, 400], [352, 334], [372, 332], [440, 400]], C.gray, C.black, 3, false);
    cv.poly([[333, 354], [352, 334], [372, 332], [392, 354], [378, 349], [366, 358], [352, 348], [342, 356]], C.white);
    // 右の小さな波
    const small = [[300, 512], [318, 452], [362, 416], [420, 410], [462, 428], [478, 452], [456, 446], [440, 456], [424, 446], [410, 470], [416, 512]];
    cv.shape(small, C.blue, C.black, 4);
    cv.curve([[330, 512], [342, 460], [380, 432], [430, 428]], 4, C.sky);
    // 大波
    const wave = [[-6, 512], [-6, 300], [26, 230], [76, 160], [138, 108], [208, 80], [282, 76], [342, 98], [384, 136], [398, 172], [380, 192], [356, 172], [326, 160], [300, 172], [290, 202], [306, 236], [280, 248], [246, 278], [220, 336], [208, 420], [236, 512]];
    cv.shape(wave, C.blue, C.black, 5);
    for (let k = 1; k <= 4; k++) {
        const s = k * 17;
        cv.curve([[10 + s, 512], [18 + s * 0.9, 340], [60 + s * 0.6, 230], [130 + s * 0.3, 160], [210, 118 + s * 0.55], [290, 116 + s * 0.6], [330, 140 + s * 0.3]], 4, C.sky);
    }
    // 波頭のしぶき（白い爪）
    const crest = smooth([[40, 210], [96, 142], [160, 104], [230, 84], [296, 86], [348, 110], [386, 146], [394, 176]]);
    cv.line(crest, 9, C.white);
    for (let i = 4; i < crest.length - 2; i += 5) {
        const [x, y] = crest[i];
        const [x2, y2] = crest[i + 1];
        const nx = (y2 - y), ny = -(x2 - x);
        const l = Math.hypot(nx, ny) || 1;
        const len = 12 + r() * 10;
        const tip = [x + nx / l * len, y + ny / l * len];
        cv.line([[x, y], tip], 6, C.white);
        cv.line([tip, [tip[0] + (x2 - x) * 0.8, tip[1] + (y2 - y) * 0.8]], 4, C.white);
    }
    for (let k = 0; k < 70; k++) {
        const t = 0.2 + r() * 0.75;
        const p = crest[Math.floor(t * (crest.length - 1))];
        cv.disk(p[0] + (r() - 0.3) * 40, p[1] - 10 - r() * 50, 2.5, C.white);
    }
    // 舟（細長い舟に、こぎ手が並ぶ）
    const boat = (cx, cy, ang, len) => {
        const ux = Math.cos(ang), uy = Math.sin(ang), nx = -uy, ny = ux;
        const pts = [];
        for (let k = 0; k <= 10; k++) {
            const t = k / 10 - 0.5;
            pts.push([cx + ux * t * len - nx * (1 - 4 * t * t) * 9, cy + uy * t * len - ny * (1 - 4 * t * t) * 9]);
        }
        for (let k = 10; k >= 0; k--) {
            const t = k / 10 - 0.5;
            pts.push([cx + ux * t * len + nx * 3, cy + uy * t * len + ny * 3]);
        }
        cv.shape(pts, C.yellow, C.black, 3, false);
        for (let k = -3; k <= 3; k++) {
            cv.disk(cx + ux * k * len / 9 - nx * 12, cy + uy * k * len / 9 - ny * 12, 4.5, C.blue);
        }
    };
    boat(120, 400, 0.6, 140);
    boat(440, 372, -0.08, 110);
    boat(372, 478, 0.1, 96);
    return cv;
}

// ---------------------------------------------------------------- 葛飾北斎「凱風快晴」（赤富士）
function redFuji() {
    const cv = new Canvas(C.sky);
    const r = rng(22);
    cv.rect(0, 0, 512, 120, C.blue);
    cv.strokes(500, 22, 3, [C.blue, C.sky], 0, (x, y) => y > 100 && y < 190, r, 0.1);
    // うろこ雲
    for (let row = 0; row < 9; row++) {
        const y = 150 + row * 22;
        for (let x = (row % 2) * 20; x < 512; x += 40) {
            if (r() < 0.75) {
                cv.ellipse(x + r() * 10, y + r() * 6, 14 + r() * 4, 5, C.white);
            }
        }
    }
    const mountain = [[-10, 512], [40, 440], [120, 340], [196, 220], [236, 150], [282, 146], [318, 196], [380, 290], [450, 370], [522, 430], [522, 512]];
    cv.shape(mountain, C.red, C.black, 5, false);
    cv.poly([[196, 222], [236, 152], [282, 148], [318, 198], [300, 214], [284, 200], [266, 222], [246, 204], [226, 226], [210, 214]], C.brown);
    // 雪のすじ
    for (const [x, y, l] of [[244, 160, 40], [262, 156, 50], [278, 160, 36], [232, 170, 30], [292, 176, 34]]) {
        cv.line([[x, y], [x + (x - 262) * 0.25, y + l]], 3, C.white);
    }
    cv.strokes(260, 18, 3, [C.brown, C.orange], (x, y) => Math.PI / 2 + (x - 260) / 600, (x, y) => cv.get(x, y) === C.red, r, 0.2);
    // ふもとの森
    cv.poly([[-10, 512], [-10, 452], [70, 436], [160, 448], [260, 440], [380, 452], [522, 438], [522, 512]], C.green);
    cv.strokes(500, 9, 3, [C.black, C.green, C.green], -Math.PI / 2, (x, y) => y > 446, r, 0.3);
    return cv;
}

// ---------------------------------------------------------------- ゴッホ「星月夜」
function starryNight() {
    const cv = new Canvas(C.blue);
    const r = rng(33);
    // 空の筆あと（うずに沿って）
    const swirlAngle = (x, y) => Math.atan2(y - 170, x - 250) + Math.PI / 2;
    cv.strokes(1400, 16, 3, [C.sky, C.blue, C.sky, C.white], swirlAngle, (x, y) => y < 330, r, 0.5);
    cv.spiral(250, 168, 8, 70, 1.7, 9, C.sky, 0, 1);
    cv.spiral(250, 168, 14, 76, 1.7, 4, C.white, 0.4, 1);
    cv.spiral(150, 128, 6, 40, 1.4, 7, C.sky, 2, -1);
    cv.curve([[0, 110], [80, 90], [150, 96], [200, 140], [260, 120], [340, 90], [420, 100]], 8, C.sky);
    // 星
    for (const [x, y, s] of [[40, 60, 1], [130, 40, 0.8], [320, 52, 0.9], [410, 160, 1], [300, 268, 0.9], [70, 210, 0.8], [190, 236, 0.9], [380, 262, 0.8], [20, 300, 0.7], [470, 260, 0.7]]) {
        cv.disk(x, y, 26 * s, C.blue);
        cv.ring(x, y, 24 * s, 24 * s, 5, C.yellow);
        cv.ring(x, y, 16 * s, 16 * s, 3, C.white);
        cv.disk(x, y, 9 * s, C.yellow);
        cv.disk(x, y, 4 * s, C.white);
    }
    // 月
    cv.disk(448, 66, 46, C.blue);
    cv.ring(448, 66, 44, 44, 6, C.orange);
    cv.disk(448, 66, 30, C.yellow);
    cv.disk(462, 58, 22, C.orange);
    // 丘と村
    cv.blob([[-20, 350], [80, 320], [180, 336], [280, 318], [380, 330], [530, 300], [530, 420], [-20, 420]], C.purple);
    cv.strokes(260, 16, 3, [C.blue, C.sky], 0.1, (x, y) => cv.get(x, y) === C.purple, r, 0.4);
    cv.rect(0, 410, 512, 102, C.blue);
    cv.strokes(300, 14, 3, [C.green, C.sky, C.blue], -0.2, (x, y) => y > 412, r, 0.6);
    const house = (x, y, w, h) => {
        cv.shape([[x, y], [x + w, y], [x + w, y + h], [x, y + h]], C.sky, C.black, 3, false);
        cv.shape([[x - 4, y], [x + w / 2, y - h * 0.6], [x + w + 4, y]], C.blue, C.black, 3, false);
        cv.rect(x + w * 0.3, y + h * 0.35, w * 0.3, h * 0.3, C.yellow);
    };
    for (const [x, y, w, h] of [[200, 420, 34, 26], [250, 430, 30, 22], [330, 418, 36, 26], [380, 432, 30, 22], [430, 420, 34, 26], [150, 436, 30, 22], [290, 450, 30, 22], [360, 462, 32, 22], [230, 466, 30, 22]]) {
        house(x, y, w, h);
    }
    // 教会の塔
    cv.shape([[302, 452], [314, 452], [312, 380], [308, 350], [304, 380]], C.blue, C.black, 3, false);
    // 糸杉
    const cypress = [[40, 512], [30, 420], [44, 330], [40, 240], [62, 160], [70, 90], [86, 40], [100, 110], [118, 190], [126, 280], [150, 370], [160, 460], [176, 512]];
    cv.shape(cypress, C.black, C.black, 3);
    cv.strokes(320, 22, 4, [C.green, C.brown], -Math.PI / 2, (x, y) => cv.get(x, y) === C.black && x < 180, r, 0.5);
    return cv;
}

// ---------------------------------------------------------------- ゴッホ「ひまわり」
function sunflowers() {
    const cv = new Canvas(C.yellow);
    const r = rng(44);
    cv.strokes(400, 20, 3, [C.yellow, C.skin], () => r() * TAU, (x, y) => y < 390, r, 3);
    cv.rect(0, 392, 512, 120, C.orange);
    cv.line([[0, 392], [512, 392]], 4, C.blue);
    cv.strokes(200, 24, 3, [C.yellow, C.orange, C.brown], 0, (x, y) => y > 396, r, 0.2);
    // 花びんとくき
    const flowers = [[256, 200, 46], [160, 160, 40], [352, 156, 42], [104, 262, 36], [410, 258, 38], [204, 96, 32], [318, 84, 34], [256, 300, 34], [450, 140, 26], [62, 150, 26], [150, 330, 28], [370, 330, 30]];
    for (const [x, y] of flowers) {
        cv.curve([[x, y], [(x + 256) / 2, (y + 330) / 2 + 10], [256, 330]], 5, C.green);
    }
    cv.shape([[176, 318], [336, 318], [352, 380], [338, 448], [300, 480], [212, 480], [174, 448], [160, 380]], C.yellow, C.brown, 5);
    cv.poly(smooth([[166, 400], [346, 400], [338, 448], [300, 478], [212, 478], [174, 448]], true), C.orange);
    cv.line([[170, 318], [342, 318]], 6, C.brown);
    cv.curve([[232, 432], [244, 424], [254, 436], [266, 424], [280, 434]], 3, C.blue);
    // ひまわり
    const flower = (cx, cy, rad, k) => {
        const n = 14;
        for (let i = 0; i < n; i++) {
            const a = i / n * TAU + k;
            const px = cx + Math.cos(a) * rad * 0.72, py = cy + Math.sin(a) * rad * 0.72;
            cv.ellipse(px, py, rad * 0.42, rad * 0.16, i % 2 ? C.orange : C.yellow, a);
            cv.ring(px, py, rad * 0.42, rad * 0.16, 2, C.brown, a);
        }
        cv.disk(cx, cy, rad * 0.46, C.brown);
        cv.ring(cx, cy, rad * 0.46, rad * 0.46, 3, C.black);
        for (let i = 0; i < 12; i++) {
            const a = r() * TAU, d = r() * rad * 0.35;
            cv.disk(cx + Math.cos(a) * d, cy + Math.sin(a) * d, 2.5, i % 3 ? C.orange : C.black);
        }
    };
    flowers.forEach(([x, y, s], i) => flower(x, y, s, i * 0.37));
    return cv;
}

// ---------------------------------------------------------------- レオナルド・ダ・ヴィンチ「モナ・リザ」
function monaLisa() {
    const cv = new Canvas(C.green);
    const r = rng(55);
    // 霞んだ景色
    cv.rect(0, 0, 512, 170, C.sky);
    cv.strokes(500, 20, 3, [C.sky, C.green, C.white], 0, (x, y) => y < 190, r, 0.3);
    cv.blob([[-20, 150], [60, 130], [120, 170], [100, 260], [-20, 280]], C.brown);
    cv.blob([[400, 170], [470, 140], [530, 150], [530, 300], [430, 280]], C.brown);
    cv.curve([[20, 330], [70, 300], [40, 260], [90, 230]], 6, C.brown);
    cv.curve([[500, 330], [450, 300], [480, 250]], 6, C.sky);
    cv.strokes(300, 16, 3, [C.green, C.brown, C.green], 0.2, (x, y) => cv.get(x, y) === C.green, r, 0.6);
    // 髪
    cv.blob([[166, 280], [160, 170], [180, 100], [216, 66], [256, 58], [296, 66], [332, 100], [352, 170], [346, 280], [356, 340], [156, 340]], C.brown);
    // 体
    cv.shape([[50, 512], [80, 410], [140, 340], [196, 312], [316, 312], [372, 340], [432, 410], [462, 512]], C.black, C.black, 3);
    cv.strokes(120, 30, 4, [C.brown], 1.2, (x, y) => cv.get(x, y) === C.black && y > 330, r, 0.6);
    // 首と胸
    cv.poly(smooth([[228, 236], [284, 236], [292, 300], [338, 328], [256, 372], [174, 328], [220, 300]], true), C.skin);
    cv.curve([[176, 330], [216, 360], [256, 372], [296, 360], [336, 330]], 4, C.brown);
    // 顔
    cv.ellipse(256, 172, 60, 80, C.skin);
    cv.ring(256, 172, 60, 80, 3, C.brown);
    cv.curve([[198, 118], [230, 100], [256, 96], [282, 100], [314, 118]], 3, C.black);
    cv.ellipse(232, 160, 10, 4.5, C.black);
    cv.ellipse(280, 160, 10, 4.5, C.black);
    cv.curve([[256, 162], [252, 190], [248, 198], [258, 200]], 2.5, C.brown);
    cv.curve([[234, 214], [246, 220], [258, 220], [270, 218], [280, 211]], 3, C.brown);
    // 手
    cv.ellipse(222, 474, 54, 20, C.skin, 0.25);
    cv.ring(222, 474, 54, 20, 3, C.brown, 0.25);
    cv.ellipse(298, 460, 60, 22, C.skin, -0.2);
    cv.ring(298, 460, 60, 22, 3, C.brown, -0.2);
    for (let k = 0; k < 4; k++) {
        cv.line([[322 + k * 7, 452 - k * 2], [348 + k * 3, 446 - k * 3]], 2, C.brown);
    }
    return cv;
}

// ---------------------------------------------------------------- フェルメール「真珠の耳飾りの少女」
function pearlEarring() {
    const cv = new Canvas(C.black);
    // 服
    cv.shape([[96, 520], [124, 430], [196, 388], [306, 384], [376, 426], [424, 520]], C.brown, C.black, 3);
    cv.blob([[180, 392], [250, 372], [300, 384], [270, 410], [210, 412]], C.white);
    const rnd = rng(66);
    cv.strokes(160, 26, 4, [C.yellow, C.orange], 0.9, (x, y) => cv.get(x, y) === C.brown, rnd, 0.5);
    // 首
    cv.poly([[236, 320], [300, 320], [304, 392], [240, 396]], C.skin);
    // 顔
    cv.blob([[208, 196], [236, 160], [300, 160], [334, 206], [338, 268], [318, 320], [282, 348], [244, 338], [218, 300], [204, 250]], C.skin);
    cv.line(smooth([[208, 196], [204, 250], [218, 300], [244, 338], [282, 348], [318, 320], [338, 268]]), 3, C.brown);
    // ターバン（青）と、上から垂れる黄色い布
    cv.shape([[198, 214], [190, 150], [222, 100], [286, 86], [340, 108], [350, 160], [326, 182], [272, 168], [228, 184]], C.blue, C.black, 3);
    cv.curve([[214, 150], [260, 132], [320, 140]], 3, C.sky);
    cv.shape([[246, 92], [296, 70], [344, 86], [352, 120], [312, 112], [262, 106]], C.yellow, C.black, 3);
    cv.shape([[332, 132], [368, 168], [384, 256], [374, 336], [352, 328], [352, 246], [338, 188]], C.yellow, C.black, 3);
    // 目・鼻・口
    for (const [x, y] of [[246, 232], [300, 226]]) {
        cv.ellipse(x, y, 15, 9, C.white);
        cv.disk(x - 4, y, 6.5, C.brown);
        cv.disk(x - 4, y, 3, C.black);
        cv.curve([[x - 16, y - 5], [x, y - 11], [x + 16, y - 6]], 2.5, C.black);
    }
    cv.curve([[276, 236], [272, 272], [266, 282], [280, 284]], 2.5, C.brown);
    cv.ellipse(266, 306, 16, 8, C.red);
    cv.line([[252, 306], [282, 304]], 2, C.brown);
    // 真珠
    cv.disk(222, 312, 13, C.gray);
    cv.disk(220, 309, 10, C.white);
    cv.disk(224, 316, 5, C.gray);
    cv.disk(217, 305, 3, C.white);
    return cv;
}

// ---------------------------------------------------------------- ムンク「叫び」
function scream() {
    const cv = new Canvas(C.orange);
    const r = rng(77);
    // うねる夕焼けの空
    for (let k = 0; k < 14; k++) {
        const y0 = k * 16 - 10;
        const pts = [];
        for (let x = -10; x <= 522; x += 16) {
            pts.push([x, y0 + Math.sin(x / 70 + k * 0.6) * 14 + x * 0.06]);
        }
        cv.line(smooth(pts), 14, [C.red, C.orange, C.yellow, C.orange][k % 4]);
    }
    // フィヨルドと陸
    cv.blob([[120, 250], [220, 200], [380, 210], [530, 190], [530, 400], [300, 380], [180, 330]], C.blue);
    for (let k = 0; k < 7; k++) {
        cv.spiral(330 + k * 14, 290, 10, 70 - k * 6, 0.8, 4, k % 2 ? C.sky : C.blue, k, 1);
    }
    cv.blob([[-20, 200], [80, 190], [150, 240], [170, 330], [100, 330], [-20, 300]], C.brown);
    cv.blob([[430, 330], [530, 300], [530, 420], [420, 400]], C.green);
    cv.strokes(260, 20, 4, [C.green, C.brown, C.orange], 0.6, (x, y) => cv.get(x, y) === C.brown || cv.get(x, y) === C.green, r, 0.8);
    // 橋（左上から右下へ）
    cv.poly([[-10, 270], [40, 266], [470, 522], [-10, 522]], C.brown);
    cv.strokes(400, 30, 4, [C.orange, C.brown, C.black], Math.atan2(256, 430), (x, y) => cv.get(x, y) === C.brown && y > 260, r, 0.15);
    for (const off of [0, 14, 28]) {
        cv.line([[-10, 250 + off], [512, 522 - 60 + off]], 6, C.black);
    }
    cv.line([[-10, 244], [512, 456]], 8, C.brown);
    // 奥の2人
    for (const x of [40, 68]) {
        cv.ellipse(x, 226, 9, 26, C.black);
        cv.ellipse(x, 196, 10, 4, C.black);
    }
    // 叫ぶ人
    cv.shape([[200, 522], [214, 450], [236, 400], [262, 380], [292, 400], [306, 450], [330, 522]], C.blue, C.black, 3);
    cv.strokes(80, 26, 4, [C.black, C.blue], 1.4, (x, y) => cv.get(x, y) === C.blue && y > 380, r, 0.5);
    cv.blob([[226, 330], [232, 278], [262, 258], [292, 278], [296, 330], [284, 380], [262, 398], [240, 380]], C.skin);
    cv.line(smooth([[226, 330], [232, 278], [262, 258], [292, 278], [296, 330], [284, 380], [262, 398], [240, 380]], true), 3, C.black);
    // 頬に当てた手
    for (const side of [-1, 1]) {
        cv.curve([[262 + side * 40, 420], [262 + side * 44, 380], [262 + side * 38, 330], [262 + side * 32, 304]], 9, C.skin);
        cv.curve([[262 + side * 40, 420], [262 + side * 44, 380], [262 + side * 38, 330], [262 + side * 32, 304]], 2, C.black);
    }
    cv.ellipse(248, 312, 8, 11, C.black);
    cv.ellipse(276, 312, 8, 11, C.black);
    cv.ellipse(262, 340, 3, 5, C.black);
    cv.ellipse(262, 366, 9, 15, C.black);
    return cv;
}

// ---------------------------------------------------------------- モネ「印象・日の出」
function impressionSunrise() {
    const cv = new Canvas(C.sky);
    const r = rng(88);
    cv.strokes(900, 30, 4, [C.sky, C.gray, C.sky, C.white], 0, (x, y) => y < 270, r, 0.25);
    cv.strokes(160, 24, 4, [C.orange, C.pink], -0.2, (x, y) => y < 260 && Math.hypot(x - 310, y - 170) < 140, r, 0.4);
    // 霧の向こうの船やクレーン
    for (const [x, h] of [[40, 150], [70, 110], [120, 170], [160, 120], [410, 140], [450, 100]]) {
        cv.line([[x, 270], [x, 270 - h]], 4, C.gray);
        cv.line([[x - 18, 270 - h * 0.6], [x + 18, 270 - h * 0.6]], 3, C.gray);
    }
    cv.blob([[-10, 250], [80, 230], [200, 246], [240, 270], [-10, 280]], C.gray);
    // 水面
    cv.rect(0, 268, 512, 244, C.blue);
    cv.strokes(1100, 30, 4, [C.sky, C.blue, C.gray, C.sky], 0, (x, y) => y > 268, r, 0.15);
    // 太陽と、水に映った光
    cv.disk(310, 168, 22, C.orange);
    for (let y = 280; y < 440; y += 12) {
        const w = 26 - (y - 280) / 12;
        cv.line([[300 - w + (r() - 0.5) * 16, y], [320 + w + (r() - 0.5) * 16, y]], 6, C.orange);
    }
    // 小舟
    const boat = (x, y, s) => {
        cv.blob([[x - 46 * s, y], [x + 46 * s, y - 4 * s], [x + 30 * s, y + 12 * s], [x - 34 * s, y + 12 * s]], C.black);
        cv.line([[x - 10 * s, y], [x - 6 * s, y - 34 * s]], 6 * s, C.black);
        cv.line([[x - 30 * s, y + 18 * s], [x + 30 * s, y + 18 * s]], 4 * s, C.blue);
    };
    boat(190, 400, 1.2);
    boat(340, 330, 0.8);
    boat(440, 300, 0.55);
    return cv;
}

// ---------------------------------------------------------------- モネ「睡蓮の池と日本の橋」
function waterLilyPond() {
    const cv = new Canvas(C.green);
    const r = rng(99);
    cv.strokes(1300, 18, 4, [C.green, C.yellow, C.green, C.black, C.sky], (x, y) => Math.PI / 2 + (r() - 0.5), (x, y) => y < 230, r, 0.6);
    // 水面
    cv.rect(0, 230, 512, 282, C.blue);
    cv.strokes(700, 26, 4, [C.green, C.sky, C.blue, C.green], Math.PI / 2, (x, y) => y > 230, r, 0.2);
    // 睡蓮の葉と花
    for (let k = 0; k < 34; k++) {
        const y = 250 + Math.pow(r(), 0.8) * 250;
        const x = r() * 512;
        const s = 0.5 + (y - 250) / 250;
        cv.ellipse(x, y, 30 * s, 9 * s, C.green);
        cv.ring(x, y, 30 * s, 9 * s, 2, C.black);
        cv.line([[x, y], [x + 12 * s, y - 3 * s]], 2, C.blue);
        if (r() < 0.55) {
            cv.ellipse(x - 6 * s, y - 4 * s, 9 * s, 5 * s, r() < 0.6 ? C.pink : C.white);
            cv.ellipse(x - 6 * s, y - 6 * s, 4 * s, 3 * s, C.yellow);
        }
    }
    // 太鼓橋
    const arch = x => 190 - Math.sin(Math.PI * (x + 20) / 552) * 80;
    const deck = [];
    for (let x = -20; x <= 532; x += 8) deck.push([x, arch(x)]);
    cv.line(deck, 22, C.black);
    cv.line(deck, 16, C.green);
    const rail = deck.map(([x, y]) => [x, y - 46]);
    cv.line(rail, 8, C.black);
    cv.line(rail, 4, C.green);
    for (let x = 0; x <= 512; x += 32) {
        cv.line([[x, arch(x) - 46], [x, arch(x) - 6]], 6, C.black);
        cv.line([[x, arch(x) - 44], [x, arch(x) - 8]], 3, C.green);
    }
    return cv;
}

// ---------------------------------------------------------------- クリムト「接吻」
function theKiss() {
    const cv = new Canvas(C.yellow);
    const r = rng(111);
    cv.strokes(900, 6, 4, [C.orange, C.brown, C.yellow], () => r() * TAU, null, r, 3);
    // 花の野原
    cv.blob([[-20, 400], [120, 380], [260, 410], [330, 450], [320, 530], [-20, 530]], C.green);
    for (let k = 0; k < 120; k++) {
        const x = r() * 330, y = 400 + r() * 120;
        if (cv.get(Math.min(511, x), Math.min(511, y)) === C.green) {
            cv.disk(x, y, 4, [C.red, C.pink, C.purple, C.white, C.yellow][k % 5]);
        }
    }
    // 金の衣（2人を包む）
    const robe = [[214, 40], [300, 34], [342, 110], [354, 280], [338, 400], [304, 470], [226, 476], [196, 400], [182, 250], [190, 116]];
    cv.shape(robe, C.yellow, C.black, 4);
    // 男の衣：黒と白の四角
    for (let k = 0; k < 40; k++) {
        const x = 196 + r() * 90, y = 120 + r() * 300;
        if (cv.get(x, y) === C.yellow && cv.get(x + 18, y + 30) === C.yellow) {
            cv.rect(x, y, 10 + r() * 8, 22 + r() * 16, k % 3 ? C.black : C.white);
            cv.ring(x + 8, y + 18, 9, 16, 1, C.black);
        }
    }
    // 女のドレス：色の丸
    for (let k = 0; k < 40; k++) {
        const x = 270 + r() * 80, y = 230 + r() * 230;
        if (cv.get(x, y) === C.yellow) {
            cv.disk(x, y, 7 + r() * 5, [C.red, C.blue, C.pink, C.purple, C.green][k % 5]);
            cv.ring(x, y, 9, 9, 2, C.black);
        }
    }
    // 男の頭（顔は向こう向き）とつたの冠
    cv.ellipse(240, 100, 44, 50, C.black, -0.3);
    for (let k = 0; k < 10; k++) {
        const a = Math.PI + k * 0.3;
        cv.ellipse(240 + Math.cos(a) * 44, 100 + Math.sin(a) * 46, 8, 4, C.green, a);
    }
    cv.poly([[262, 132], [296, 142], [290, 190], [262, 186]], C.skin);
    // 女の顔（横に傾けて目を閉じる）
    cv.ellipse(306, 196, 36, 26, C.skin, 0.2);
    cv.ring(306, 196, 36, 26, 3, C.brown, 0.2);
    cv.blob([[330, 160], [362, 176], [372, 214], [352, 240], [336, 222], [340, 190]], C.orange);
    for (let k = 0; k < 6; k++) {
        cv.disk(348 + r() * 18, 180 + r() * 50, 4, C.pink);
    }
    cv.curve([[290, 186], [296, 192], [304, 190]], 2.5, C.black);
    cv.curve([[312, 182], [318, 188], [326, 186]], 2.5, C.black);
    cv.ellipse(300, 214, 7, 4, C.red, 0.2);
    // 女の手
    cv.ellipse(276, 160, 14, 26, C.skin, 0.5);
    cv.ring(276, 160, 14, 26, 2, C.brown, 0.5);
    // 足
    cv.ellipse(290, 492, 22, 9, C.skin, 0.2);
    cv.ring(290, 492, 22, 9, 2, C.brown, 0.2);
    return cv;
}

// ---------------------------------------------------------------- 東洲斎写楽「三代目大谷鬼次の奴江戸兵衛」
function sharaku() {
    const cv = new Canvas(C.gray);
    const r = rng(122);
    cv.strokes(400, 8, 3, [C.gray, C.black, C.white], () => r() * TAU, null, r, 3);
    cv.rect(0, 0, 512, 512, C.gray);
    cv.strokes(260, 6, 2, [C.black], () => r() * TAU, null, r, 3);
    // 着物
    cv.shape([[40, 530], [70, 400], [170, 340], [256, 330], [350, 344], [440, 400], [470, 530]], C.black, C.black, 3);
    cv.curve([[190, 340], [240, 420], [256, 530]], 10, C.white);
    cv.curve([[330, 346], [290, 420], [272, 530]], 10, C.white);
    for (const [x, y] of [[110, 440], [400, 450]]) {
        cv.ring(x, y, 20, 20, 4, C.gray);
        cv.disk(x, y, 7, C.gray);
    }
    // 顔
    const face = [[176, 170], [196, 116], [256, 104], [316, 116], [336, 170], [334, 236], [304, 296], [256, 316], [208, 296], [178, 236]];
    cv.shape(face, C.white, C.black, 4);
    // 髪とさかやき・まげ
    cv.shape([[176, 176], [178, 120], [212, 84], [256, 74], [304, 84], [336, 120], [338, 176], [316, 128], [256, 112], [198, 128]], C.black, C.black, 3);
    cv.blob([[214, 104], [256, 92], [298, 104], [256, 116]], C.sky);
    cv.shape([[236, 74], [276, 72], [300, 40], [292, 30], [266, 54], [240, 58]], C.black, C.black, 2, false);
    // 眉・目（にらむ）
    cv.line([[200, 160], [246, 172]], 10, C.black);
    cv.line([[266, 172], [312, 158]], 10, C.black);
    cv.ellipse(226, 188, 16, 6, C.white, 0.15);
    cv.ring(226, 188, 16, 6, 3, C.black, 0.15);
    cv.disk(234, 188, 4, C.black);
    cv.ellipse(288, 188, 16, 6, C.white, -0.15);
    cv.ring(288, 188, 16, 6, 3, C.black, -0.15);
    cv.disk(280, 188, 4, C.black);
    cv.curve([[196, 200], [214, 208], [236, 204]], 2.5, C.red);
    cv.curve([[278, 204], [300, 208], [318, 200]], 2.5, C.red);
    // 鼻・口（への字）
    cv.curve([[258, 190], [262, 226], [272, 238], [254, 244]], 4, C.black);
    cv.curve([[222, 272], [240, 262], [258, 266], [276, 262], [292, 272]], 5, C.black);
    cv.line([[222, 272], [226, 280]], 3, C.black);
    cv.line([[292, 272], [288, 280]], 3, C.black);
    // 突き出した両手
    const hand = (x, y, dir) => {
        cv.ellipse(x, y, 30, 22, C.white, dir * 0.3);
        cv.ring(x, y, 30, 22, 3, C.black, dir * 0.3);
        for (let k = 0; k < 4; k++) {
            const a = -Math.PI / 2 + dir * (0.2 + k * 0.32);
            const tip = [x + Math.cos(a) * 52, y + Math.sin(a) * 46];
            cv.line([[x + Math.cos(a) * 20, y + Math.sin(a) * 16], tip], 13, C.black);
            cv.line([[x + Math.cos(a) * 20, y + Math.sin(a) * 16], tip], 8, C.white);
        }
    };
    hand(190, 420, -1);
    hand(330, 430, 1);
    return cv;
}

// ---------------------------------------------------------------- ミレー「落穂拾い」
function gleaners() {
    const cv = new Canvas(C.sky);
    const r = rng(133);
    cv.strokes(400, 26, 4, [C.sky, C.white, C.sky], 0, (x, y) => y < 170, r, 0.2);
    // 遠くの畑と、積みわら
    cv.rect(0, 160, 512, 110, C.skin);
    cv.strokes(300, 22, 3, [C.yellow, C.skin], 0, (x, y) => y > 160 && y < 270, r, 0.1);
    for (const [x, y, s] of [[360, 190, 1], [420, 196, 0.8], [470, 186, 1.1], [300, 200, 0.6]]) {
        cv.blob([[x - 30 * s, y + 20 * s], [x - 22 * s, y - 14 * s], [x, y - 26 * s], [x + 22 * s, y - 14 * s], [x + 30 * s, y + 20 * s]], C.yellow);
        cv.line(smooth([[x - 30 * s, y + 20 * s], [x - 22 * s, y - 14 * s], [x, y - 26 * s], [x + 22 * s, y - 14 * s], [x + 30 * s, y + 20 * s]]), 2, C.brown);
    }
    for (let k = 0; k < 9; k++) {
        cv.ellipse(150 + k * 26, 214 + (k % 3) * 4, 3, 8, C.brown);
    }
    // 手前の畑
    cv.rect(0, 268, 512, 244, C.yellow);
    cv.strokes(700, 24, 3, [C.brown, C.orange, C.yellow], -0.15, (x, y) => y > 270, r, 0.4);
    // 3人
    const woman = (x, y, bend, cap, top, skirt) => {
        // スカート
        cv.shape([[x - 40, y + 110], [x - 30, y + 40], [x + 30, y + 30], [x + 50, y + 110]], skirt, C.black, 3);
        // 上半身（かがむほど前に倒れる）
        const hx = x - 70 * bend, hy = y - 60 + 70 * bend;
        cv.shape([[x - 26, y + 50], [x + 26, y + 40], [hx + 20, hy + 6], [hx - 10, hy - 6]], top, C.black, 3, false);
        // 頭と帽子
        cv.disk(hx - 6, hy, 18, C.skin);
        cv.ring(hx - 6, hy, 18, 18, 2, C.black);
        cv.ellipse(hx - 2, hy - 8, 20, 14, cap, 0.4 * bend);
        // 腕（地面へ）
        cv.line([[hx + 10, hy + 10], [hx - 6, y + 104]], 9, C.skin);
        cv.line([[hx + 10, hy + 10], [hx - 6, y + 104]], 2, C.black);
        cv.ellipse(x + 40, y + 120, 34, 8, C.brown);
    };
    woman(150, 330, 0.95, C.blue, C.blue, C.brown);
    woman(286, 320, 0.8, C.red, C.gray, C.blue);
    woman(420, 290, 0.45, C.skin, C.brown, C.brown);
    return cv;
}

// ---------------------------------------------------------------- 長谷川等伯「松林図屏風」
function pineTrees() {
    const cv = new Canvas(C.white);
    const r = rng(144);
    // 霧のかかった葉のかたまり（短い線をたくさん重ねる）
    const clump = (cx, cy, w, h, ink, n) => {
        for (let k = 0; k < n; k++) {
            const u = r() * 2 - 1, v = r() * 2 - 1;
            if (u * u + v * v > 1) continue;
            const x = cx + u * w, y = cy + v * h;
            const a = -Math.PI / 2 + (r() - 0.5) * 1.4;
            const l = 7 + r() * 9;
            cv.line([[x, y], [x + Math.cos(a) * l, y + Math.sin(a) * l]], 2, ink);
        }
    };
    const pine = (x, base, top, lean, ink, scale) => {
        const trunk = [];
        for (let k = 0; k <= 8; k++) {
            const t = k / 8;
            trunk.push([x + lean * t * t * 50 + Math.sin(t * 4) * 6, base - (base - top) * t]);
        }
        cv.line(smooth(trunk), 5 * scale, ink);
        const at = t => trunk[Math.min(8, Math.round(t * 8))];
        const [tx, ty] = at(1);
        clump(tx, ty - 6, 40 * scale, 16 * scale, ink, 260 * scale);
        for (let k = 0; k < 4; k++) {
            const [bx, by] = at(0.45 + k * 0.13);
            const dir = k % 2 ? 1 : -1;
            const len = (40 + r() * 20) * scale;
            cv.line([[bx, by], [bx + dir * len * 0.8, by - 8 * scale]], 3 * scale, ink);
            clump(bx + dir * len * 0.6, by - 12 * scale, len * 0.6, 13 * scale, ink, 170 * scale);
        }
    };
    pine(110, 520, 220, -0.4, C.gray, 0.8);
    pine(180, 520, 110, 0.5, C.black, 1.25);
    pine(360, 520, 70, -0.6, C.black, 1.35);
    pine(440, 520, 230, 0.3, C.gray, 0.8);
    cv.strokes(60, 50, 3, [C.gray], 0, (x, y) => y > 470, r, 0.05);
    return cv;
}

export const PAINTINGS = [
    ['great-wave', greatWave],
    ['red-fuji', redFuji],
    ['starry-night', starryNight],
    ['sunflowers', sunflowers],
    ['mona-lisa', monaLisa],
    ['pearl-earring', pearlEarring],
    ['scream', scream],
    ['impression-sunrise', impressionSunrise],
    ['water-lily-pond', waterLilyPond],
    ['the-kiss', theKiss],
    ['sharaku', sharaku],
    ['gleaners', gleaners],
    ['pine-trees', pineTrees],
];

const outDir = path.resolve('src/main/resources/official');
fs.mkdirSync(outDir, {recursive: true});
const drawn = [];
for (const [key, draw] of PAINTINGS) {
    const cv = draw();
    fs.writeFileSync(path.join(outDir, key + '.png'), cv.png());
    drawn.push(cv);
}
fs.writeFileSync(process.argv[2] || 'C:/Claude/note-images/official-sheet.png', sheet(drawn, 4, 300, 8));
console.log('描いた枚数', drawn.length);
