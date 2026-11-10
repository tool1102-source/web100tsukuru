/*
 * 「らくがきオークション」の競りの画面。
 * 開いて見ている間だけ、2秒ごとに /state を読んで値段・残り時間・入札の記録を出し直す（裏に回ると止める）。
 * 入札はページを移らずに送る（JavaScript が動かなければ、ふつうのフォームとして送られる）。
 */
(() => {
    'use strict';
    const root = document.getElementById('lot');
    if (!root) {
        return;
    }
    const POLL_MS = 2000;
    const stateUrl = root.dataset.state;
    const bidUrl = root.dataset.bid;
    const planned = Number(root.dataset.planned);
    let status = root.dataset.status;
    let endAt = Number(root.dataset.end);
    let offset = Number(root.dataset.now) - Date.now();
    let lastPrice = null;
    let lastBidId = null;
    let timer = 0;
    let busy = false;

    const $ = id => document.getElementById(id);
    const priceEl = $('price');
    const priceLabel = $('price-label');
    const topEl = $('top');
    const clockEl = $('clock');
    const extendedEl = $('extended');
    const mineEl = $('mine');
    const stampEl = $('stamp');
    const bidsEl = $('bids');
    const bidsEmpty = $('bids-empty');
    const form = $('bid-form');
    const amount = $('amount');
    const message = $('bid-message');
    const available = $('available');
    const quick = $('bid-quick');
    let minBid = amount ? Number(amount.min) : 0;
    const submitButton = form ? form.querySelector('button[type="submit"]') : null;
    let edited = false;

    const yen = n => Number(n).toLocaleString('ja-JP');

    function el(tag, className, text) {
        const e = document.createElement(tag);
        if (className) {
            e.className = className;
        }
        if (text !== undefined && text !== null) {
            e.textContent = text;
        }
        return e;
    }

    function whoLink(w) {
        const a = el('a', 'who');
        a.href = w.href;
        const icon = el('span', 'who-icon', w.initial);
        icon.style.backgroundColor = w.color;
        icon.setAttribute('aria-hidden', 'true');
        a.append(icon, el('span', 'who-name', w.name));
        return a;
    }

    function clockText(ms) {
        if (ms <= 0) {
            return '締め切り中…';
        }
        const s = Math.ceil(ms / 1000);
        if (s >= 86400) {
            return Math.floor(s / 86400) + '日' + Math.floor((s % 86400) / 3600) + '時間';
        }
        const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), sec = s % 60;
        const two = n => String(n).padStart(2, '0');
        return h > 0 ? h + ':' + two(m) + ':' + two(sec) : m + ':' + two(sec);
    }

    function tick() {
        if (!clockEl || status !== 'OPEN') {
            return;
        }
        const left = endAt - (Date.now() + offset);
        clockEl.textContent = clockText(left);
        root.classList.toggle('is-final', left > 0 && left <= 30000);
        if (left <= 0 && !busy) {
            poll();
        }
    }

    function renderBids(bids) {
        if (!bids.length) {
            return;
        }
        if (bids[0].id === lastBidId) {
            return;
        }
        const fresh = lastBidId !== null;
        lastBidId = bids[0].id;
        bidsEl.replaceChildren(...bids.map((b, i) => {
            const li = el('li', 'bid-line');
            if (b.who.npc) {
                li.classList.add('is-npc');
            }
            if (b.auto) {
                li.classList.add('is-auto');
            }
            if (b.win) {
                li.classList.add('is-win');
            }
            if (fresh && i === 0) {
                li.classList.add('is-new');
            }
            const icon = el('span', 'who-icon', b.who.initial);
            icon.style.backgroundColor = b.who.color;
            icon.setAttribute('aria-hidden', 'true');
            const body = el('span', 'bid-body');
            const head = el('span', 'bid-who');
            const name = el('a', null, b.who.name);
            name.href = b.who.href;
            const time = el('time', null, new Date(b.at).toLocaleTimeString('ja-JP', {hour: 'numeric', minute: '2-digit', second: '2-digit'}));
            head.append(name, ' ', el('small', null, b.win ? '落札' : (b.auto ? '自動で入札' : '入札')), ' ', time);
            body.append(head);
            if (b.line) {
                body.append(el('span', 'bid-say', '「' + b.line + '」'));
            }
            li.append(icon, body, el('b', 'bid-amount', yen(b.amount)));
            return li;
        }));
        if (bidsEmpty) {
            bidsEmpty.hidden = true;
        }
    }

    function renderMine(me, s) {
        if (!mineEl) {
            return;
        }
        mineEl.className = 'lot-mine';
        if (me && me.seller) {
            const note = document.getElementById('seller-note');
            if (note && s.status !== 'OPEN') {
                note.textContent = s.status === 'SOLD'
                    ? '売れました。手数料を引いたコインが入り、残りは ' + yen(me.coins) + ' 枚になりました。'
                    : 'だれも入札しませんでした。絵は手元に戻りました。もう一度出品できます。';
            }
        }
        if (!me || me.seller) {
            mineEl.hidden = true;
            if (me) {
                const balance = document.getElementById('coin-balance');
                if (balance) {
                    balance.textContent = yen(me.coins);
                }
            }
            return;
        }
        if (s.status === 'OPEN') {
            if (me.top) {
                mineEl.textContent = 'あなたがいちばん高い値を付けています（上限 ' + yen(me.max) + ' コイン）。';
                mineEl.classList.add('is-top');
                mineEl.hidden = false;
            } else if (me.outbid) {
                mineEl.textContent = 'ほかの人に抜かれました。もう一度入札できます。';
                mineEl.classList.add('is-outbid');
                mineEl.hidden = false;
            } else {
                mineEl.hidden = true;
            }
        } else if (s.status === 'SOLD' && me.won) {
            mineEl.textContent = 'おめでとうございます。この絵はあなたのものです。';
            mineEl.classList.add('is-top');
            mineEl.hidden = false;
        } else {
            mineEl.hidden = true;
        }
        if (available) {
            available.textContent = yen(me.available);
        }
        const balance = document.getElementById('coin-balance');
        if (balance) {
            balance.textContent = yen(me.coins);
        }
    }

    function render(s) {
        status = s.status;
        if (s.endAt !== endAt) {
            endAt = s.endAt;
        }
        offset = s.now - Date.now();
        if (extendedEl) {
            extendedEl.hidden = !(endAt > planned + 500);
        }
        if (lastPrice !== null && s.price !== lastPrice) {
            priceEl.classList.remove('is-bump');
            void priceEl.offsetWidth;
            priceEl.classList.add('is-bump');
        }
        lastPrice = s.price;
        priceEl.textContent = yen(s.price);
        const ended = s.status !== 'OPEN';
        priceLabel.textContent = ended ? (s.status === 'SOLD' ? '落札価格' : '開始価格') : (s.top ? 'いまの値段' : '開始価格');
        topEl.replaceChildren();
        if (s.top) {
            topEl.append(el('span', null, ended ? '落札' : 'いちばん高い'), ' ', whoLink(s.top));
        } else {
            topEl.append(el('span', null, ended ? 'だれも入札しませんでした' : 'まだ入札はありません'));
        }
        renderBids(s.bids);
        renderMine(s.me, s);
        // いちばん高い人が上限を上げるときは、今の上限より上から
        const leading = s.me && s.me.top;
        minBid = leading ? Math.max(s.minBid, s.me.max + 10) : s.minBid;
        if (submitButton) {
            submitButton.textContent = leading ? '上限を上げる' : '入札する';
        }
        if (amount) {
            amount.min = String(minBid);
            if (!edited || Number(amount.value) < minBid) {
                amount.value = String(minBid);
                edited = false;
            }
        }
        if (ended) {
            root.classList.remove('is-final');
            root.classList.add('is-ended');
            if (stampEl) {
                stampEl.textContent = s.status === 'SOLD' ? '落札' : '売れず';
                stampEl.classList.toggle('is-unsold', s.status !== 'SOLD');
                stampEl.hidden = false;
            }
            if (clockEl) {
                clockEl.parentElement.textContent = '終了しました';
            }
            if (form) {
                form.hidden = true;
            }
            stop();
        }
    }

    function poll() {
        if (busy || status !== 'OPEN') {
            return;
        }
        busy = true;
        fetch(stateUrl, {credentials: 'same-origin', cache: 'no-store'})
            .then(res => res.ok ? res.json() : Promise.reject(res.status))
            .then(render)
            .catch(() => { /* 次の回でまた読む */ })
            .finally(() => {
                busy = false;
            });
    }

    function start() {
        if (timer || status !== 'OPEN') {
            return;
        }
        poll();
        timer = setInterval(() => {
            if (document.visibilityState === 'visible') {
                poll();
            }
        }, POLL_MS);
    }

    function stop() {
        clearInterval(timer);
        timer = 0;
    }

    document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'visible') {
            poll();
        }
    });

    if (form && amount) {
        amount.addEventListener('input', () => {
            edited = true;
        });
        if (quick) {
            quick.addEventListener('click', e => {
                const b = e.target.closest('[data-quick]');
                if (!b) {
                    return;
                }
                const q = b.dataset.quick;
                const base = minBid;
                const value = q === 'min' ? base : Math.ceil(base * Number(q) / 10) * 10;
                amount.value = String(value);
                edited = q !== 'min';
                amount.focus();
            });
        }
        form.addEventListener('submit', e => {
            e.preventDefault();
            const value = Number(amount.value);
            if (!(value >= minBid)) {
                message.textContent = yen(minBid) + ' コイン以上で入札してください。';
                message.className = 'bid-message is-error';
                return;
            }
            const button = form.querySelector('button[type="submit"]');
            button.disabled = true;
            const body = new URLSearchParams({amount: String(value)});
            fetch(bidUrl, {method: 'POST', body, credentials: 'same-origin', headers: {'X-Requested-With': 'fetch'}})
                .then(res => res.json())
                .then(json => {
                    message.textContent = json.message;
                    message.className = 'bid-message ' + (json.ok ? 'is-ok' : 'is-error');
                    edited = false;
                })
                .catch(() => {
                    message.textContent = '送れませんでした。通信のようすを確かめて、もう一度お試しください。';
                    message.className = 'bid-message is-error';
                })
                .finally(() => {
                    button.disabled = false;
                    busy = false;
                    poll();
                });
        });
    }

    setInterval(tick, 250);
    tick();
    start();
})();
