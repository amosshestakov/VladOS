/*!
 * HDREZKA plugin for Lampa
 * --------------------------------------------------------
 *  - Online-balanser source (button "HDREZKA" on the card)
 *  - Account login (login + password) via /ajax/login/
 *  - Configurable mirror domain (default: rezka.fi)
 *  - Settings panel: domain / login / password / login button / status
 *
 *  Tested against the public HDREZKA site engine (DLE-based).
 *  Author: generated for the user via Perplexity Computer.
 *  License: MIT
 */
(function () {
    'use strict';

    if (window.rezka_plugin_ready) return;
    window.rezka_plugin_ready = true;

    /* ====================================================
     *  Plugin manifest (shown in Lampa "Extensions" panel)
     * ==================================================== */
    var manifest = {
        type: 'video',
        version: '1.0.1',
        name: 'HDREZKA',
        description: 'Просмотр фильмов и сериалов с HDREZKA по личному аккаунту',
        component: 'rezka_online'
    };

    /* ====================================================
     *  Storage keys / defaults
     * ==================================================== */
    var STORAGE = {
        domain:   'rezka_domain',
        login:    'rezka_login',
        password: 'rezka_password',
        cookie:   'rezka_cookie',     // dle_user_id=...; dle_password=...
        status:   'rezka_status',     // 'logged' | 'guest' | 'error:<msg>'
        proxy:    'rezka_proxy_url'   // optional CORS proxy
    };

    /* Безопасный вызов Lampa.Storage.add — в разных билдах Lampa
       сигнатура разная. Используем get(name, default) — это работает везде.
       Сразу и инициализируем по умолчаниям через set(…, default), если пусто. */
    function ensureDefaults() {
        var defaults = {};
        defaults[STORAGE.domain]   = 'https://rezka.fi';
        defaults[STORAGE.login]    = '';
        defaults[STORAGE.password] = '';
        defaults[STORAGE.cookie]   = '';
        defaults[STORAGE.status]   = 'guest';
        defaults[STORAGE.proxy]    = '';
        Object.keys(defaults).forEach(function (k) {
            try {
                var cur = Lampa.Storage.get(k, '__none__');
                if (cur === '__none__' || cur === null || cur === undefined) {
                    Lampa.Storage.set(k, defaults[k]);
                }
            } catch (err) {}
        });
    }

    /* ====================================================
     *  Helpers
     * ==================================================== */
    function getDomain() {
        var d = (Lampa.Storage.get(STORAGE.domain) || 'https://rezka.fi').trim();
        if (!/^https?:\/\//i.test(d)) d = 'https://' + d;
        return d.replace(/\/+$/, '');
    }

    function getCookie() {
        return (Lampa.Storage.get(STORAGE.cookie) || '').trim();
    }

    function isLoggedIn() {
        return Lampa.Storage.get(STORAGE.status) === 'logged' && !!getCookie();
    }

    function buildHeaders(extra) {
        var headers = {
            'User-Agent': 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
            'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
            'Accept-Language': 'ru,en;q=0.9',
            'Referer': getDomain() + '/',
            'X-Requested-With': 'XMLHttpRequest'
        };
        var ck = getCookie();
        // Cookies are managed by Android CookieManager, never exposed to JavaScript.
        if (extra) for (var k in extra) headers[k] = extra[k];
        return headers;
    }

    /**
     * Pass URL through optional CORS proxy if user configured one.
     */
    function proxify(url) {
        var p = (Lampa.Storage.get(STORAGE.proxy) || '').trim();
        if (!p) return url;
        // Lampac-style: <proxy>/<url>
        if (p.slice(-1) !== '/') p += '/';
        return p + url;
    }

    /**
     * HDREZKA "trash list" decoder for video URL (#h<base64-with-trash> payload).
     * 1:1 with the canonical algorithm used in nb557/online_mod and HdRezkaApi.
     */
    function decodeTrash(data) {
        if (!data || typeof data !== 'string') return data;
        if (data.charAt(0) !== '#') return data;

        // Сервер вставляет base64-версии этих строк, предварённых //_//
        var trashList = ['$$!!@$$@^!@#$$@', '@@@@@!##!^^^', '####^!!##!@@', '^^^!@##!!##', '$$#!!@#!@##'];

        // Аналог браузерного btoa() для UTF-8 строк
        function enc(str) {
            return btoa(encodeURIComponent(str).replace(/%([0-9A-F]{2})/g,
                function (m, p1) { return String.fromCharCode(parseInt(p1, 16)); }));
        }
        function dec(str) {
            return decodeURIComponent(atob(str).split('').map(function (c) {
                return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
            }).join(''));
        }
        function escapeRegex(s) { return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'); }

        var x = data.substring(2); // убираем ведущие два символа (обычно "#h")
        trashList.forEach(function (t) {
            var token = '//_//' + enc(t);
            // все вхождения, а не только первое
            x = x.split(token).join('');
        });
        try { return dec(x); } catch (e) {
            try { return atob(x); } catch (e2) { return ''; }
        }
    }

    /**
     * Parse the HDREZKA playlist string returned by /ajax/get_cdn_series/.
     * Format (after decode): [1080p Ultra]https://x/1080.mp4 or https://x/1080.mp4,[720p]...
     */
    function parsePlaylist(str) {
        if (!str) return [];
        var result = [];
        var parts = str.split(',');
        parts.forEach(function (part) {
            var m = part.match(/\[([^\]]+)\](.+)/);
            if (!m) return;
            var label = m[1].trim();
            var urls = m[2].split(' or ');
            // last URL is usually the highest-quality / mp4 fallback
            var file = urls[urls.length - 1].trim();
            result.push({ label: label, file: file });
        });
        return result;
    }

    /**
     * Network helper – wraps Lampa.Reguest / native fetch.
     */
    function request(opts, success, error) {
        var network = new Lampa.Reguest();
        network.timeout(15000);
        network.silent(opts.url, function (resp) {
            success(resp);
        }, function (a, b) {
            error(a, b);
        }, opts.post || false, {
            dataType: opts.dataType || 'text',
            headers: {} 
        });
        return network;
    }

    /* ====================================================
     *  Auth: login to HDREZKA
     * ==================================================== */
    function authenticate(login, password, cb) {
        var url = proxify(getDomain() + '/ajax/login/?t=' + Date.now());
        var post = 'login_name=' + encodeURIComponent(login) +
                   '&login_password=' + encodeURIComponent(password) +
                   '&login_not_save=0';

        var net = new Lampa.Reguest();
        net.timeout(15000);

        // We need raw response WITH Set-Cookie. Lampa.Reguest does not always
        // expose headers, so we fall back to XHR directly.
        try {
            var xhr = new XMLHttpRequest();
            xhr.open('POST', url, true);
            xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
            xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
            
            xhr.withCredentials = true;
            xhr.timeout = 15000;
            xhr.onload = function () {
                var ok = false, msg = '';
                try {
                    var json = JSON.parse(xhr.responseText);
                    ok = json.success===true||json.success===1||json.success==='true';
                    msg = VladOSErrorText(json.message || json.error || json.errors,'Не удалось войти');
                    if(ok&&xhr.hasSession!==true){ok=false;msg='Источник не сохранил сессию. Возможно, требуется проверка в браузере.';}
                } catch (e) { msg = 'Некорректный ответ сервера'; }

                if (ok) {
                    // Try to read Set-Cookie. In a browser this is blocked;
                    // we therefore read document.cookie which the server set
                    // (the request was same-origin via the proxy / direct).
                    var cookieStr = 'native-session';
                    try {
                        cookieStr = 'native-session';
                    } catch (e) {}
                    // Filter to dle_* cookies only
                    var dle = cookieStr.split(';').map(function (s) { return s.trim(); })
                        .filter(function (s) { return /^dle_(user_id|password|hash|forum_sessions)=/.test(s); })
                        .join('; ');

                    Lampa.Storage.set(STORAGE.cookie, 'native-session');
                    Lampa.Storage.set(STORAGE.password, '');
                    Lampa.Storage.set(STORAGE.status, 'logged');
                    cb(true, 'Успешный вход');
                } else {
                    Lampa.Storage.set(STORAGE.status, 'error:' + (msg || 'login failed'));
                    cb(false, msg || 'Не удалось войти');
                }
            };
            xhr.onerror = function () {
                Lampa.Storage.set(STORAGE.status, 'error:network');
                cb(false, VladOSErrorText(xhr.error,'Сетевая ошибка: источник недоступен')); 
            };
            xhr.ontimeout = function () {
                Lampa.Storage.set(STORAGE.status, 'error:timeout');
                cb(false, 'Превышено время ожидания');
            };
            xhr.send(post);
        } catch (e) {
            cb(false, 'Ошибка: ' + e.message);
        }
    }

    function logout() {
        if(window.VladCinema) VladCinema.clearSession();
        Lampa.Storage.set(STORAGE.password, '');
        Lampa.Storage.set(STORAGE.cookie, '');
        Lampa.Storage.set(STORAGE.status, 'guest');
    }

    /* ====================================================
     *  Search on HDREZKA
     *  GET /engine/ajax/search.php?q=<title>
     * ==================================================== */
    function searchRezka(query, year, cb) {
        var url = proxify(getDomain() + '/engine/ajax/search.php?q=' + encodeURIComponent(query));
        request({ url: url }, function (html) {
            // <li><a href="..."><span class="enty">Title</span> (Original, 2023)<span class="rating">8.50</span></a></li>
            var div = document.createElement('div');
            div.innerHTML = html;
            var items = [];
            div.querySelectorAll('a').forEach(function (a) {
                var href = a.getAttribute('href');
                if (!href || href.indexOf('search') !== -1) return;
                // Основное название — из .enty (русский вариант)
                var entyEl = a.querySelector('.enty');
                var title = entyEl ? (entyEl.textContent || '').trim() : '';
                // Полный текст (содержит и оригинал, и год, и рейтинг)
                var fullText = (a.textContent || '').trim();
                if (!title) {
                    // без .enty — берём всю строку до «(слово, год)»
                    title = fullText.replace(/\s*\([^)]*\d{4}\)[\s\S]*$/, '').trim();
                }
                // Год — из любого вхождения (4 цифры 19xx/20xx)
                var ym = fullText.match(/\b(19|20)\d{2}\b/);
                items.push({
                    url: /^https:\/\//i.test(href)?href:getDomain()+'/'+href.replace(/^\//,''),
                    title: title,
                    year: ym ? ym[0] : '',
                    original: (fullText.match(/\(([^,()]+)/)||[])[1]||''
                });
            });
            // если знаем год — предпочитаем точное совпадение
            if (year) {
                var exact = items.filter(function (i) { return i.year == String(year); });
                if (exact.length) items = exact;
            }
            cb(items);
        }, function () { cb([], 'network'); });
    }

    /* ====================================================
     *  Parse film page → translators / seasons / film_id
     * ==================================================== */
    function fetchFilmPage(filmUrl, cb, err) {
        request({ url: proxify(filmUrl), dataType: 'text' }, function (str) {
            var info = {
                film_id: '',
                is_series: false,
                favs: '',
                voice: [],   // [{name, id, is_camrip, is_ads, is_director}]
                season: [],  // [{name, id}]
                episode: [], // [{name, season_id, episode_id}]
                page_url: filmUrl
            };

            // film id
            var seriesMatch=str.match(/initCDNSeriesEvents\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)/);
            var idm = seriesMatch||str.match(/initCDNMoviesEvents\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*(?:,\s*(\d+))?/);
            if (idm) {
                info.film_id = idm[1];
                var defVoiceId = idm[2];
                info.is_series = !!seriesMatch;
                var camrip = seriesMatch?'0':idm[3], ads = seriesMatch?'0':idm[4], director = idm[5] || '0';

                // favs hash
                var fm = str.match(/var\s+sof\s*=.*?\.send\([^,]+,\s*'([^']+)'/);
                if (!fm) fm = str.match(/data-favs="([^"]+)"/);
                if (fm) info.favs = fm[1];

                // translators block
                var tm = str.match(/<ul[^>]+(?:id="translators-list"|class="b-translator__list")[\s\S]*?<\/ul>/);
                if (tm) {
                    var d = document.createElement('div');
                    d.innerHTML = tm[0];
                    d.querySelectorAll('.b-translator__item').forEach(function (li) {
                        info.voice.push({
                            name: (li.getAttribute('title') || li.textContent || '').trim(),
                            id: li.getAttribute('data-translator_id') || defVoiceId,
                            is_camrip:   li.getAttribute('data-camrip')   || camrip,
                            is_ads:      li.getAttribute('data-ads')      || ads,
                            is_director: li.getAttribute('data-director') || director
                        });
                    });
                }
                if (!info.voice.length) {
                    var defName = '';
                    var dn = str.match(/<h2>В переводе<\/h2>:[\s\S]*?<td[^>]*>(.*?)<\/td>/);
                    if (dn) {
                        var dd = document.createElement('div'); dd.innerHTML = dn[1];
                        defName = (dd.textContent || '').trim();
                    }
                    info.voice.push({
                        name: defName || 'Оригинал',
                        id: defVoiceId,
                        is_camrip: camrip, is_ads: ads, is_director: director
                    });
                }

                if (info.is_series) {
                    var sm = str.match(/<ul[^>]+class="b-simple_seasons__list"[\s\S]*?<\/ul>/);
                    if (sm) {
                        var ds = document.createElement('div'); ds.innerHTML = sm[0];
                        ds.querySelectorAll('.b-simple_season__item').forEach(function (li) {
                            info.season.push({
                                name: (li.textContent || '').trim(),
                                id: li.getAttribute('data-tab_id')
                            });
                        });
                    }
                    var em = str.match(/<ul[^>]+class="b-simple_episodes__list"[\s\S]*?<\/ul>/g);
                    if (em) {
                        em.forEach(function (block) {
                            var de = document.createElement('div'); de.innerHTML = block;
                            de.querySelectorAll('.b-simple_episode__item').forEach(function (li) {
                                info.episode.push({
                                    name: (li.textContent || '').trim(),
                                    season_id: li.getAttribute('data-season_id'),
                                    episode_id: li.getAttribute('data-episode_id')
                                });
                            });
                        });
                    }
                }
                cb(info);
            } else {
                var waiting=/Ожидаем[^<]{0,160}(?:качеств|выход)/i.test(str);
                err && err(waiting?'На HDRezka пока нет полного видео: источник ожидает выход фильма. Вход в аккаунт это не меняет.':/<title>[^<]*Вход/i.test(str)?'HDRezka запрашивает вход заново. Открой настройки HDRezka и повтори вход на выбранном домене.':'Плагин не смог получить данные видео. Можно проверить страницу HDRezka кнопкой ниже.',waiting?'missing':'unknown');
            }
        }, function () { err && err('Сетевая ошибка'); });
    }

    /* ====================================================
     *  Get direct stream url
     *  POST /ajax/get_cdn_series/  (action=get_movie | get_stream)
     * ==================================================== */
    function getStream(info, voice, season, episode, cb, err) {
        var url = proxify(getDomain() + '/ajax/get_cdn_series/?t=' + Date.now());
        var post;
        if (info.is_series && season && episode) {
            post = 'id=' + encodeURIComponent(info.film_id) +
                   '&translator_id=' + encodeURIComponent(voice.id) +
                   '&season=' + encodeURIComponent(season.id) +
                   '&episode=' + encodeURIComponent(episode.episode_id) +
                   '&favs=' + encodeURIComponent(info.favs || '') +
                   '&action=get_stream';
        } else {
            post = 'id=' + encodeURIComponent(info.film_id) +
                   '&translator_id=' + encodeURIComponent(voice.id) +
                   '&is_camrip=' + encodeURIComponent(voice.is_camrip || 0) +
                   '&is_ads=' + encodeURIComponent(voice.is_ads || 0) +
                   '&is_director=' + encodeURIComponent(voice.is_director || 0) +
                   '&favs=' + encodeURIComponent(info.favs || '') +
                   '&action=get_movie';
        }

        try {
            var xhr = new XMLHttpRequest();
            xhr.open('POST', url, true);
            xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
            xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
            
            xhr.withCredentials = true;
            xhr.timeout = 15000;
            xhr.onload = function () {
                try {
                    var json = JSON.parse(xhr.responseText);
                    if (!json.success) { err && err(json.message || 'Сервер вернул ошибку'); return; }
                    var decoded = decodeTrash(json.url);
                    var items = parsePlaylist(decoded);
                    if (!items.length) { err && err('Пустой плейлист'); return; }
                    var qualities = {};
                    items.forEach(function (it) { qualities[it.label] = it.file; });
                    var premium=!!json.premium_content,previous='';
                    items.forEach(function(it){if(it.label!=='1080p Ultra'){if(previous&&previous!==it.file)premium=false;previous=it.file;}});
                    if(premium){err&&err('Выбранная озвучка доступна только с HDRezka Premium. Попробуй другую озвучку. Обычного входа в аккаунт недостаточно.');return;}
                    cb({
                        title: '',
                        file: qualities[Lampa.Storage.get('vlados_quality','720p')] || qualities['720p'] || qualities['1080p'] || items[0].file,
                        quality: qualities,
                        subtitles: parseSubtitles(json.subtitle)
                    });
                } catch (e) { err && err('Не удалось разобрать ответ'); }
            };
            xhr.onerror = function () { err && err('Сетевая ошибка'); };
            xhr.ontimeout = function () { err && err('Таймаут'); };
            xhr.send(post);
        } catch (e) { err && err(e.message); }
    }

    function parseSubtitles(s) {
        if (!s || typeof s !== 'string') return [];
        // format: "[lang]url,[lang2]url2,..."
        return s.split(',').map(function (part) {
            var m = part.match(/\[([^\]]+)\](.+)/);
            return m ? { label: m[1], url: m[2] } : null;
        }).filter(Boolean);
    }

    /* ====================================================
     *  Lampa Online component
     * ==================================================== */
    function component(object) {
        var network = new Lampa.Reguest();
        var scroll = new Lampa.Scroll({ mask: true, over: true });
        var files = new Lampa.Explorer(object);
        var filter = new Lampa.Filter(object);
        var html = $('<div></div>');

        var state = {info:null, choice:Lampa.Storage.get('vlados_choice_'+object.movie.id,{voice:0,season:0})};

        this.create = function () {
            scroll.minus();
            files.appendFiles(scroll.render());
            files.appendHead(filter.render());

            filter.onSearch = function (value) {
                Lampa.Activity.replace({ component: 'rezka_online', movie: object.movie, search: value, clarification: true });
            };
            filter.onBack = function () { self.start(); };
            this.initialize();

            return this.render();
        };

        this.render = function () { return files.render(); };

        var self = this;

        this.start = function () {
            if (Lampa.Activity.active().activity !== this.activity) return;
            Lampa.Background.immediately(Lampa.Utils.cardImgBackgroundBlur(object.movie));
            Lampa.Controller.add('content', {
                toggle: function () {
                    Lampa.Controller.collectionSet(scroll.render(), files.render());
                    Lampa.Controller.collectionFocus(false, scroll.render());
                },
                up: function () {
                    if (Navigator.canmove('up')) Navigator.move('up');
                    else Lampa.Controller.toggle('head');
                },
                down: function () { Navigator.move('down'); },
                left: function () { Lampa.Controller.toggle('menu'); },
                right: function () { Navigator.move('right'); },
                back: this.back
            });
            Lampa.Controller.toggle('content');
        };

        this.pause = function () {};
        this.stop = function () {};
        this.back = function () { Lampa.Activity.backward(); };
        this.destroy = function () {
            network.clear();
            scroll.destroy();
            files.destroy();
            filter.destroy();
            html.remove();
        };

        // search & render flow ---------------------------------
        function showError(msg, pageUrl) {
            var buttons=pageUrl&&window.VladCinema?[{title:'Открыть плеер HDRezka',onEnter:function(){VladCinema.openProvider(pageUrl);}}]:[];var empty = new Lampa.Empty({ title:'HDRezka', descr:msg, text:msg,noicon:true,buttons:buttons });
            html.append(empty.render());
            scroll.append(html);
        }

        function buildList() {
            html.empty();
            if (!state.info) return;
            var info = state.info;

            var voice = info.voice[state.choice.voice] || info.voice[0];
            if(!voice){showError('У источника нет доступных озвучек');return;}
            var season = info.season[state.choice.season];

            var items = [];
            if (info.is_series && season) {
                items = info.episode.filter(function (e) { return String(e.season_id) === String(season.id); });
            } else {
                items = [{ name: 'Смотреть фильм', episode_id: 0, season_id: 0, _movie: true }];
            }

            items.forEach(function (ep) {
                var item = $('<div class="online selector"><div class="online__title">' + $('<span>').text(ep.name).html() +
                    '</div><div class="online__quality">' + $('<span>').text(voice.name).html() + '</div></div>');
                item.on('hover:enter', function () {
                    Lampa.Storage.set('vlados_episode_'+object.movie.id,String(ep.episode_id));
                    Lampa.Modal.open({
                        title: 'HDREZKA',
                        html: $('<div style="padding:1em">Получаем ссылку…</div>'),
                        size: 'small',
                        onBack: function () { Lampa.Modal.close(); Lampa.Controller.toggle('content'); }
                    });
                    getStream(info, voice,
                        info.is_series ? season : null,
                        info.is_series ? ep : null,
                        function (data) {
                            Lampa.Modal.close();
                            var card=object.movie;
                            var key='rezka:'+card.id+':'+(info.is_series?season.id+':'+ep.episode_id:'movie');
                            var timeline=Lampa.Timeline.view(Lampa.Utils.hash(key));
                            var playback={url:data.file,title:(card.title||card.name||'')+(info.is_series?' · '+ep.name:''),quality:data.quality,subtitles:data.subtitles,card:card,timeline:timeline};

                            var playlist=items.map(function(entry){
                                var entryKey='rezka:'+card.id+':'+(info.is_series?season.id+':'+entry.episode_id:'movie');
                                var item={title:(card.title||card.name||'')+(info.is_series?' · '+entry.name:''),card:card,timeline:Lampa.Timeline.view(Lampa.Utils.hash(entryKey))};
                                if(entry===ep){item.url=data.file;item.quality=data.quality;item.subtitles=data.subtitles;}
                                else item.url=function(done){getStream(info,voice,info.is_series?season:null,info.is_series?entry:null,function(next){item.url=next.file;item.quality=next.quality;item.subtitles=next.subtitles;done();},function(message){Lampa.Noty.show(message);Lampa.Player.close();});};
                                return item;
                            });
                            if(window.VladOSCinema)VladOSCinema.play(playback,playlist);
                            else {Lampa.Player.play(playback);Lampa.Player.playlist(playlist);}
                        },
                        function (msg) {
                            Lampa.Modal.close();
                            Lampa.Noty.show('HDREZKA: ' + msg);
                        });
                });
                html.append(item);
                if(object.resume&&(ep._movie||String(ep.episode_id)===String(Lampa.Storage.get('vlados_episode_'+object.movie.id,'')))){object.resume=false;setTimeout(function(){item.trigger('hover:enter');},200);}
            });
            scroll.append(html);
            Lampa.Controller.enable('content');
        }

        function buildFilter() {
            if (!state.info) return;
            var info = state.info;
            var f = {
                voice: info.voice.map(function (v) { return v.name; })
            };
            if (info.is_series) {
                f.season = info.season.map(function (s) { return s.name; });
            }
            filter.set('filter', Object.keys(f).map(function (key) {
                return { title: key === 'voice' ? 'Перевод' : 'Сезон', subtitle: f[key][state.choice[key]] || '—', stype: key, items:f[key].map(function(name,index){return {title:name,index:index,selected:index===state.choice[key]};}) };
            }));
            filter.onSelect = function (type, a, b) {
                if (type === 'filter' && a.stype && b) {
                    state.choice[a.stype] = b.index;
                    Lampa.Storage.set('vlados_choice_'+object.movie.id,state.choice);
                    buildFilter();
                    buildList();
                }
            };
        }

        this.initialize = function () {
            this.activity.loader(true);

            var movie = object.movie || {};
            var title = object.search || movie.original_title || movie.original_name || movie.title || movie.name || '';
            if(window.VladOSCinema) VladOSCinema.remember(movie);
            var year = (movie.release_date || movie.first_air_date || '').slice(0, 4);

            searchRezka(title, year, function (results, failure) {
                if (!results.length) {
                    self.activity.loader(false);
                    showError(failure?'Не удалось связаться с HDRezka. Проверь подключение и повтори попытку.':'Ничего не найдено на HDREZKA');
                    self.activity.toggle();
                    return;
                }
                function chooseFilm(candidate){fetchFilmPage(candidate.url, function (info) {
                    state.info = info;
                    self.activity.loader(false);
                    buildFilter();
                    buildList();
                    self.activity.toggle();
                }, function (msg) {
                    self.activity.loader(false);
                    showError(msg,candidate.url);
                    self.activity.toggle();
                });}
                if(results.length===1)chooseFilm(results[0]);
                else {self.activity.loader(false);Lampa.Select.show({title:'Выбери фильм',items:results.map(function(r){return {title:r.title+' · '+r.year,result:r};}),onSelect:function(item){Lampa.Select.hide();self.activity.loader(true);chooseFilm(item.result);},onBack:function(){Lampa.Select.hide();Lampa.Activity.backward();}});}
            });
        };
    }

    /* ====================================================
     *  Register component & online source button
     * ==================================================== */
    window.VladRezkaAvailability={check:function(movie,cb){
        var year=String(movie.release_date||movie.first_air_date||'').slice(0,4);
        var names=[movie.original_title,movie.original_name,movie.title,movie.name].filter(Boolean);
        function norm(v){return String(v||'').toLowerCase().replace(/ё/g,'е').replace(/[^a-zа-я0-9]+/g,'');}
        var normalized=names.map(norm),index=0,done=false;
            function finish(status,url){if(done)return;done=true;cb({status:status,provider:'rezka',url:url||'',checkedAt:Date.now()});}
        function next(){
            if(index>=names.length||!year){finish('unknown');return;}
            searchRezka(names[index++],year,function(results,failure){
                if(failure){finish('unknown');return;}
                var match=results.filter(function(r){return r.year===year&&(normalized.indexOf(norm(r.title))>=0||normalized.indexOf(norm(r.original))>=0);})[0];
                if(!match){next();return;}
                fetchFilmPage(match.url,function(info){
                    var voice=info.voice[0],season=info.season[0],episode=info.episode.filter(function(ep){return season&&String(ep.season_id)===String(season.id);})[0];
                    if(!voice||(info.is_series&&(!season||!episode))){finish('unknown',match.url);return;}
                    getStream(info,voice,season,episode,function(stream){finish(/^https:\/\//i.test(stream.file)?'available':'unknown',match.url);},function(){finish('unknown',match.url);});
                },function(message,status){finish(status==='missing'?'missing':'unknown',match.url);});
            });
        }
        next();
    }};
    function registerComponent() {
        if (Lampa.Component && Lampa.Component.add) {
            Lampa.Component.add('rezka_online', component);
        }
    }

    function openRezka(movie) {
        Lampa.Activity.push({
            url: '',
            title: 'HDREZKA - ' + (movie.title || movie.name || ''),
            component: 'rezka_online',
            movie: movie,
            page: 1
        });
    }

    function addOnlineSource() {
        // Регистрация в Lampa.Online (источник в стандартной кнопке «Онлайн»)
        var source = {
            title: 'HDREZKA',
            search: function (movie, oncomplite) {
                openRezka(movie);
                oncomplite && oncomplite([]);
            },
            onContextMenu: function () { return { name: 'HDREZKA' }; }
        };
        if (Lampa.Online && Lampa.Online.register) {
            Lampa.Online.register('rezka', source);
        }
    }

    /* ====================================================
     *  Кнопка «HDREZKA» прямо в карточке фильма (рядом со «Смотреть»)
     *  Срабатывает на событии full → complite, когда карточка отрисована.
     * ==================================================== */
    function addCardButton() {
        // Inline-стили для кнопки (Lampa использует свои классы, мы
        // дополнительно добавляем минимальный CSS на случай отсутствия темы)
        var styleId = 'rezka-plugin-style';
        if (!document.getElementById(styleId)) {
            var st = document.createElement('style');
            st.id = styleId;
            st.innerHTML =
                '.full-start__button.view--rezka{background:linear-gradient(135deg,#1d8a3a,#0f5e25);color:#fff}' +
                '.full-start-new__buttons .view--rezka,.full-start__buttons .view--rezka{order:-1}' +
                '.view--rezka .button__icon{margin-right:.4em}';
            document.head.appendChild(st);
        }

        Lampa.Listener.follow('full', function (e) {
            if (e.type !== 'complite') return;

            var root = e.object.activity.render();
            // Поддерживаем разные темы карточки: новую и классическую
            var btnContainer = root.find('.full-start-new__buttons');
            if (!btnContainer.length) btnContainer = root.find('.full-start__buttons');
            if (!btnContainer.length) return;
            if (root.find('.view--rezka').length) return; // уже добавлена

            var label = '<svg class="button__icon" width="22" height="22" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">' +
                '<path d="M9 8l6 4-6 4V8z" fill="currentColor"/></svg>' +
                '<span>HDRezka</span>';

            var btn = $(
                '<div class="full-start__button selector view--online view--rezka">' + label + '</div>'
            );

            btn.on('hover:enter', function () { openRezka(e.data.movie); });

            // Помещаем кнопку ПЕРВОЙ — слева от «Смотреть» / «Онлайн»
            btnContainer.prepend(btn);

            // Делаем эту кнопку активной по умолчанию (фокус под указателем).
            // Lampa перестроит навигацию после prepend, поэтому нужно немного подождать.
            setTimeout(function () {
                try {
                    if (Navigator && Navigator.focused) {
                        Navigator.focused(btn[0]);
                    } else {
                        Lampa.Controller.collectionFocus(btn[0], root);
                    }
                } catch (err) {
                    // На некоторых сборках Navigator может быть недоступен
                    btn.addClass('focus');
                }
            }, 50);
        });
    }

    /* ====================================================
     *  Settings panel: domain / login / password / login
     * ==================================================== */
    function statusLabel() {
        var s = Lampa.Storage.get(STORAGE.status) || 'guest';
        if (s === 'logged') return '🟢 Вы вошли в аккаунт';
        if (s.indexOf('error:') === 0) return '🔴 Ошибка: ' + VladOSErrorText(s.substring(6), 'Не удалось войти. Повтори вход.');
        return '⚪ Не авторизованы';
    }

    function addSettings() {
        Lampa.SettingsApi.addComponent({
            component: 'rezka',
            name: 'HDREZKA',
            icon: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">' +
                '<path d="M4 4h16v16H4z" stroke="currentColor" stroke-width="2"/>' +
                '<path d="M9 8l6 4-6 4V8z" fill="currentColor"/></svg>'
        });

        Lampa.SettingsApi.addParam({
            component: 'rezka',
            param: { name: STORAGE.domain, type: 'input', values: '', default: 'https://rezka.fi' },
            field: { name: 'Домен HDREZKA', description: 'По умолчанию rezka.fi. Можно сменить на рабочее зеркало.' },
            onChange: function () { Lampa.Storage.set(STORAGE.status, 'guest'); }
        });

        Lampa.SettingsApi.addParam({component:'rezka',param:{name:'rezka_phone_input',type:'button'},field:{name:'Ввести с телефона',description:'QR-код для ввода в домашней сети'},onChange:function(){VladCinema.phoneInput();}});
        Lampa.SettingsApi.addParam({
            component: 'rezka',
            param: { name: STORAGE.login, type: 'input', values: '', default: '' },
            field: { name: 'Логин / E-mail', description: 'Email или имя пользователя HDREZKA' }
        });

        Lampa.SettingsApi.addParam({
            component: 'rezka',
            param: { name: 'rezka_password_hidden', type: 'button' },
            field: { name: 'Пароль', description: 'Значение скрыто. Ввести с телефона через QR-код' },
            onChange: function () { VladCinema.phoneInput(); }
        });

        Lampa.SettingsApi.addParam({
            component: 'rezka',
            param: { name: 'rezka_login_button', type: 'trigger' },
            field: { name: 'Войти в аккаунт', description: statusLabel() },
            onChange: function () {
                var login = Lampa.Storage.get(STORAGE.login);
                var pwd   = window.VladOSCredentialDraft || Lampa.Storage.get(STORAGE.password);
                window.VladOSCredentialDraft='';
                if (!login || !pwd) {
                    Lampa.Noty.show('Введите логин и пароль');
                    return;
                }
                Lampa.Noty.show('Авторизация на HDREZKA…');
                authenticate(login, pwd, function (ok, msg) {
                    Lampa.Noty.show((ok ? '✓ ' : '✗ ') + msg);
                    // refresh description
                    $('[data-name="rezka_login_button"] .settings-param__descr').text(statusLabel());
                });
            }
        });

        Lampa.SettingsApi.addParam({
            component: 'rezka',
            param: { name: 'rezka_logout_button', type: 'trigger' },
            field: { name: 'Выйти из аккаунта', description: 'Удалить сохранённую сессию' },
            onChange: function () {
                logout();
                Lampa.Noty.show('Сессия HDREZKA очищена');
                $('[data-name="rezka_login_button"] .settings-param__descr').text(statusLabel());
            }
        });

        Lampa.SettingsApi.addParam({
            component: 'rezka',
            param: { name: STORAGE.proxy, type: 'input', values: '', default: '' },
            field: {
                name: 'CORS-прокси (опц.)',
                description: 'Если HDREZKA блокируется CORS, можно указать прокси, например https://your-proxy.com/'
            }
        });
    }

    /* ====================================================
     *  Bootstrap
     * ==================================================== */
    function registerManifest() {
        try {
            if (!Lampa.Manifest) Lampa.Manifest = {};
            // Старые билды: plugins — объект; новые: массив.
            if (Array.isArray(Lampa.Manifest.plugins)) {
                var already = Lampa.Manifest.plugins.some(function (p) {
                    return p && p.component === manifest.component;
                });
                if (!already) Lampa.Manifest.plugins.push(manifest);
            } else if (typeof Lampa.Manifest.plugins === 'object' && Lampa.Manifest.plugins) {
                Lampa.Manifest.plugins[manifest.component] = manifest;
            } else {
                // Поле отсутствует — создаём как объект (наиболее совместимый вариант)
                var box = {};
                box[manifest.component] = manifest;
                Lampa.Manifest.plugins = box;
            }
        } catch (err) {
            console.log('REZKA', 'manifest register failed', err && err.message);
        }
    }

    function startPlugin() {
        if (window.rezka_plugin_started) return;
        window.rezka_plugin_started = true;
        try {
            ensureDefaults();
            registerManifest();
            registerComponent();
            addSettings();
            addOnlineSource();
            addCardButton();
            console.log('REZKA', 'plugin started OK');
        } catch (err) {
            console.log('REZKA', 'startPlugin error:', err && (err.stack || err.message));
            if (typeof Lampa !== 'undefined' && Lampa.Noty && Lampa.Noty.show) {
                Lampa.Noty.show('HDREZKA: ошибка старта — ' + (err && err.message));
            }
        }
    }

    function bootstrap() {
        if (typeof Lampa === 'undefined') {
            // Lampa ещё не загружена — ждём
            return setTimeout(bootstrap, 200);
        }
        if (window.appready) {
            startPlugin();
        } else if (Lampa.Listener && Lampa.Listener.follow) {
            Lampa.Listener.follow('app', function (e) {
                if (e.type === 'ready') startPlugin();
            });
            // Страховка: если событие уже прошло до нашей подписки — попробуем через секунду
            setTimeout(function () { if (window.appready) startPlugin(); }, 1000);
        } else {
            // Нет Listener — запускаем в лоб
            startPlugin();
        }
    }

    bootstrap();
})();
