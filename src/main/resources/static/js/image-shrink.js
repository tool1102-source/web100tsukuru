// 画像を選んだら、送る前にブラウザの中で長い辺 1600px までに縮めて JPEG にする
// （スマホの大きな写真でもすぐ送れるように。サーバーでも同じように縮め直す：service/ImageProcessor）
// 使い方：<input type="file" data-shrink data-max="選べる枚数" data-previews="見本を並べる ol の id">
(function () {
    const LONG_SIDE = 1600;
    if (!window.DataTransfer || !HTMLCanvasElement.prototype.toBlob) {
        return;
    }

    function shrink(file) {
        return new Promise(function (resolve) {
            const url = URL.createObjectURL(file);
            const img = new Image();
            img.onload = function () {
                const scale = Math.min(1, LONG_SIDE / Math.max(img.naturalWidth, img.naturalHeight));
                const canvas = document.createElement('canvas');
                canvas.width = Math.max(1, Math.round(img.naturalWidth * scale));
                canvas.height = Math.max(1, Math.round(img.naturalHeight * scale));
                const ctx = canvas.getContext('2d');
                ctx.fillStyle = '#fff';
                ctx.fillRect(0, 0, canvas.width, canvas.height);
                ctx.imageSmoothingQuality = 'high';
                ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
                URL.revokeObjectURL(url);
                canvas.toBlob(function (blob) {
                    const name = file.name.replace(/\.[^.]*$/, '') + '.jpg';
                    resolve(blob ? new File([blob], name, { type: 'image/jpeg' }) : file);
                }, 'image/jpeg', 0.85);
            };
            // ブラウザで開けない画像は、そのまま送ってサーバーの判断に任せる
            img.onerror = function () {
                URL.revokeObjectURL(url);
                resolve(file);
            };
            img.src = url;
        });
    }

    document.querySelectorAll('input[type="file"][data-shrink]').forEach(function (input) {
        const max = Number(input.dataset.max || 1);
        const previews = document.getElementById(input.dataset.previews);
        const submit = input.form ? input.form.querySelector('button[type="submit"]') : null;

        input.addEventListener('change', async function () {
            const files = Array.from(input.files);
            if (previews) {
                previews.replaceChildren();
            }
            if (files.length > max) {
                alert('画像は' + max + '枚までです。選び直してください。');
                input.value = '';
                return;
            }
            if (files.length === 0) {
                return;
            }
            const label = submit ? submit.textContent : '';
            if (submit) {
                submit.disabled = true;
                submit.textContent = '画像を準備しています…';
            }
            const shrunk = [];
            for (const file of files) {
                shrunk.push(await shrink(file));
            }
            const transfer = new DataTransfer();
            shrunk.forEach(function (file) { transfer.items.add(file); });
            input.files = transfer.files;
            if (previews) {
                shrunk.forEach(function (file, i) {
                    const item = document.createElement('li');
                    const thumb = document.createElement('img');
                    thumb.src = URL.createObjectURL(file);
                    thumb.alt = '';
                    item.append(thumb);
                    if (max > 1) {
                        const caption = document.createElement('span');
                        caption.textContent = (i + 1) + '枚目';
                        item.append(caption);
                    }
                    previews.append(item);
                });
            }
            if (submit) {
                submit.disabled = false;
                submit.textContent = label;
            }
        });
    });
})();
