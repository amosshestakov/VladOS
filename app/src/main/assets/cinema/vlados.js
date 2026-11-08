/* VladOS integration: local interface, bounded native networking, deterministic remote navigation. */
(function () {
    'use strict';
    var nativeXHR = window.XMLHttpRequest;
    var pending = {}, sequence = 0, ready = false;
    window.lampa_settings = {
        socket_use: false, account_use: false, account_sync: false,
        plugins_store: false, torrents_use: true, feed: false,
        geo: false, push_state: false, services: false,
        lang_use: false, youtube: false, blur_poster: false,
        disable_features:{ai:true,reactions:true,discuss:true,subscribe:true,ads:true,install_proxy:true,remote_configuration:true}
    };
    var defaults = {language:'ru', animation:'false', player:'inner', background:'false'};
    Object.keys(defaults).forEach(function (key) { if(localStorage.getItem(key) === null) localStorage.setItem(key,defaults[key]); });

    function handled(url) {
        if(/^http:\/\/127\.0\.0\.1:8090(?:\/(?:echo|settings|torrents|cache|viewed|stat|version|search|torznab\/search|gst\/echo))?\/?(?:\?|$)/.test(url))return true;
        return /^https:\/\/(?:api\.themoviedb\.org|apitmdb\.cub\.best|rezka\.fi|rezka\.ag|hdrezka\.ag|hdrezka\.me|hdrezka-home\.tv|filmixapp\.cyou|filmix\.gg|kodik-api\.com|kodik\.info|kodik\.cc|kodikdb\.com|plapi\.cdnvideohub\.com|api\.anilibria\.app|api\.anilibria\.tv|api\.apbugall\.org|api\.srvkp\.com|kinopoiskapiunofficial\.tech)(?::443)?\//i.test(url);
    }
    function Request() {
        this.headers={};this.readyState=0;this.status=0;this.responseText='';this.response='';this.timeout=20000;this.listeners={};this.withCredentials=false;this.onload=null;this.onerror=null;this.onabort=null;this.responseType='';
    }
    Request.prototype.open=function(method,url,async){
        VladCinema.report('STATE XHR '+method+' '+String(url).split('?')[0]);
        this.method=method.toUpperCase();this.url=url;this.async=async;this.readyState=1;
        if(!handled(url)){this.fallback=new nativeXHR();this.fallback.open(method,url,async===undefined?true:async);}
    };
    Request.prototype.setRequestHeader=function(key,value){this.headers[key]=value;if(this.fallback)this.fallback.setRequestHeader(key,value);};
    Request.prototype.addEventListener=function(name,fn){(this.listeners[name]||(this.listeners[name]=[])).push(fn);};
    Request.prototype.removeEventListener=function(name,fn){var list=this.listeners[name]||[];this.listeners[name]=list.filter(function(x){return x!==fn;});};
    Request.prototype.emit=function(name){var event={target:this,currentTarget:this,type:name};if(this['on'+name])this['on'+name](event);(this.listeners[name]||[]).forEach(function(fn){fn(event);});};
    Request.prototype.getAllResponseHeaders=function(){return 'Content-Type: '+(this.contentType||'text/plain')+'\r\n';};
    Request.prototype.getResponseHeader=function(name){return name.toLowerCase()==='content-type'?(this.contentType||'text/plain'):null;};
    Request.prototype.overrideMimeType=function(type){if(this.fallback)this.fallback.overrideMimeType(type);};
    Request.prototype.abort=function(){clearTimeout(this.timer);if(this.id)delete pending[this.id];if(this.fallback)this.fallback.abort();this.emit('abort');};
    Request.prototype.send=function(body){
        var self=this;
        if(this.fallback){
            var other=this.fallback;other.timeout=this.timeout;other.withCredentials=!!this.withCredentials;other.responseType=this.responseType||'';
            ['readystatechange','load','error','timeout','abort','loadend'].forEach(function(name){other.addEventListener(name,function(){self.readyState=other.readyState;self.status=other.status;self.responseText=(!other.responseType||other.responseType==='text')?other.responseText:'';self.response=other.response;self.contentType=other.getResponseHeader('Content-Type');self.emit(name);});});
            other.send(body);return;
        }
        this.id=String(++sequence);pending[this.id]=this;
        this.timer=setTimeout(function(){delete pending[self.id];self.status=0;self.readyState=4;self.emit('readystatechange');self.emit('timeout');self.emit('loadend');},Math.max(1000,this.timeout||20000));
        VladCinema.request(this.id,this.url,this.method,body==null?'':String(body),JSON.stringify(this.headers));
    };
    window.XMLHttpRequest=Request;

    function active(){return window.Lampa&&Lampa.Activity.active?Lampa.Activity.active():null;}
    var currentCard=null, nativePlayback=null, nativePlaylist=[],nativeIndex=0,nativeHistorySave=0,nativeController='content';
    function playNative(item){
        nativePlayback=item;
        if(typeof item.url==='function'){item.url(function(){playNative(item);});return;}
        var url=item.url;
        if(/^http:\/\/127\.0\.0\.1:8090\/stream\//.test(url))url=url.replace(/([?&])preload(?=&|$)/,'$1play');
        VladCinema.play(JSON.stringify({url:url,title:item.title,quality:item.quality||{},subtitles:item.subtitles||[],position:item.timeline?Math.floor(item.timeline.time||0):0}));
    }
    window.VladOSCinema={
        showPhoneInput:function(url){
            var panel=$('<div style="display:flex;align-items:center;gap:2em;padding:1em"><div class="vlados-phone-qr" style="background:white;padding:12px;border-radius:12px;flex-shrink:0;margin-right:1.4em"></div><div><p>Открой камеру телефона и отсканируй QR-код.</p><p>Телефон и ТВ должны быть в одной домашней сети.</p><p>Введи данные на телефоне, затем нажми «Войти в аккаунт» на ТВ.</p><p style="opacity:.6;font-size:.8em">Код действует 3 минуты. Пароль не отправляется в чат.</p></div></div>');
            Lampa.Modal.open({title:'Ввод с телефона',html:panel,size:'medium',onBack:function(){VladCinema.stopInput();Lampa.Modal.close();Lampa.Controller.toggle('settings_component');}});
            var code=qrcode(0,'M');code.addData(url);code.make();panel.find('.vlados-phone-qr').html(code.createSvgTag(4,16)).find('svg').css({width:'220px',height:'220px',display:'block'});
        },
        fillCredentials:function(login,password){
            window.VladOSCredentialDraft=password;Lampa.Storage.set('rezka_login',login);Lampa.Storage.set('rezka_password','');
            Lampa.Modal.close();Lampa.Controller.toggle('settings_component');Lampa.Noty.show('Данные получены. Нажми «Войти в аккаунт».');
            $('[data-name="rezka_login"] .settings-param__value').text(login);$('[data-name="rezka_password"] .settings-param__value').text('Введён с телефона');
        },
        play:function(item,playlist){nativeController=Lampa.Controller.enabled().name;nativePlaylist=playlist||[item];nativeIndex=Math.max(0,nativePlaylist.map(function(p){return p.url;}).indexOf(item.url));playNative(item);},
        nativeProgress:function(time,duration){
            if(!nativePlayback||!duration)return;
            var timeline=nativePlayback.timeline;if(timeline&&timeline.handler)timeline.handler(Math.round(time/duration*100),time,duration);
            if(nativePlayback.card&&Date.now()-nativeHistorySave>5000){nativeHistorySave=Date.now();var safe={};['id','title','name','original_title','original_name','poster_path','backdrop_path','release_date','first_air_date','media_type'].forEach(function(k){if(nativePlayback.card[k]!==undefined)safe[k]=nativePlayback.card[k];});VladCinema.history(JSON.stringify({card:safe,title:nativePlayback.title,time:time,duration:duration,updated:Date.now()}));}
        },
        nativeClosed:function(ended){
            if(ended&&nativeIndex+1<nativePlaylist.length){nativeIndex++;playNative(nativePlaylist[nativeIndex]);return;}
            nativePlayback=null;if(window.Lampa)Lampa.Controller.toggle(nativeController);
        },
        response:function(id,data){var xhr=pending[id];if(!xhr)return;delete pending[id];clearTimeout(xhr.timer);xhr.status=data.status;xhr.responseText=data.text||'';xhr.response=xhr.responseText;xhr.contentType=data.type;xhr.readyState=4;xhr.error=data.error||'';xhr.hasSession=data.hasSession===true;xhr.emit('readystatechange');xhr.emit(data.status?'load':'error');xhr.emit('loadend');},
        back:function(){
            if(!ready){VladCinema.exit();return;}
            var a=active(), controller=Lampa.Controller.enabled().name;
            if((controller==='content'||controller==='menu'||controller==='head')&&a&&(a.component==='main'||a.component==='category')&&(!Lampa.Activity.all||Lampa.Activity.all().length<=1)){VladCinema.exit();return;}
            Lampa.Controller.back();
        },
        pause:function(){var video=document.querySelector('video');if(video&&!video.paused)video.pause();},
        remember:function(movie){currentCard=movie;},
        ready:function(){
            if(ready)return;ready=true;
            VladCinema.report('STATE READY cors='+$.support.cors+' ajax='+$.support.ajax+' plugin='+!!window.rezka_plugin_started);
            document.documentElement.classList.add('vlados-cinema');
            if(window.Lampa){
                Lampa.Storage.set('language','ru');Lampa.Storage.set('player','inner');
                if(!Lampa.Storage.get('vlados_local_torrserver_v1',false)){
                    Lampa.Storage.set('torrserver_url','http://127.0.0.1:8090');
                    Lampa.Storage.set('torrserver_use_link','one');Lampa.Storage.set('internal_torrclient',true);
                    Lampa.Storage.set('parser_use',true);Lampa.Storage.set('parser_torrent_type','torrserver');
                    Lampa.Storage.set('torrserver_search_type','rutor');Lampa.Storage.set('player_torrent','inner');
                    Lampa.Storage.set('torrserver_preload',false);Lampa.Storage.set('torrserver_gts',false);
                    Lampa.Storage.set('vlados_local_torrserver_v1',true);
                }
                var queued=null,queuedList=null,playTimer;
                Lampa.Player.play=function(item){queued=item;queuedList=null;clearTimeout(playTimer);playTimer=setTimeout(function(){var movie=active();if(!queued.card&&movie&&movie.movie)queued.card=movie.movie;VladOSCinema.play(queued,queuedList||[queued]);queued=null;},0);};
                Lampa.Player.playlist=function(items){if(queued)queuedList=items;};
                Lampa.SettingsApi.addComponent({component:'vlados',name:'VladOS',icon:'<svg viewBox="0 0 24 24"><path d="M3 4L12 21 21 4H16L12 12 8 4Z" fill="currentColor"/></svg>'});
                Lampa.SettingsApi.addParam({component:'vlados',param:{name:'vlados_quality',type:'select',values:{'720p':'720p','1080p':'1080p','480p':'480p'},default:'720p'},field:{name:'Качество по умолчанию',description:'Если источник предлагает несколько вариантов'}});
                Lampa.SettingsApi.addParam({component:'vlados',param:{name:'vlados_test',type:'trigger',default:false},field:{name:'Проверить воспроизведение',description:'Короткий тест видео, звука и перемотки'},onChange:function(){VladOSCinema.play({url:'https://media.w3.org/2010/05/sintel/trailer.mp4',title:'Проверка воспроизведения · Sintel',timeline:Lampa.Timeline.view(Lampa.Utils.hash('vlados-sintel-test'))});}});
                Lampa.Listener.follow('full',function(e){if(e.type==='complite'&&e.data)currentCard=e.data.movie||e.data;});
                var playback=null,lastSave=0;
                Lampa.Player.listener.follow('start',function(data){playback=data;});
                Lampa.PlayerVideo.listener.follow('timeupdate',function(e){
                    if(!playback||!playback.card||!e.duration||Date.now()-lastSave<10000)return;
                    lastSave=Date.now();var source=playback.card,card={};
                    ['id','title','name','original_title','original_name','poster_path','backdrop_path','release_date','first_air_date','media_type'].forEach(function(k){if(source[k]!==undefined)card[k]=source[k];});
                    VladCinema.history(JSON.stringify({card:card,title:playback.title,time:e.current,duration:e.duration,updated:Date.now()}));
                });
                var logo=document.querySelector('.head__logo-icon');if(logo){logo.innerHTML='<span class="vlados-mark">V</span>';logo.setAttribute('aria-label','VladOS Кино');}
                var exit=$('<li class="menu__item selector"><div class="menu__ico"><svg viewBox="0 0 24 24"><path d="M4 10L12 3 20 10V21H15V14H9V21H4Z" fill="none" stroke="currentColor" stroke-width="1.6"/></svg></div><div class="menu__text">VladOS</div></li>');
                exit.on('hover:enter',function(){VladCinema.exit();});$('.menu .menu__list').first().prepend(exit);
            }
            VladCinema.ready();
            try{var launch=VladCinema.launchCard();if(launch){var movie=JSON.parse(launch);setTimeout(function(){Lampa.Activity.push({component:'rezka_online',movie:movie,resume:true,title:movie.title||movie.name});},100);}}catch(ignored){}
        }
    };
    var attempts=0;
    function boot(){
        if(window.appready&&window.Lampa){VladOSCinema.ready();return;}
        if(++attempts<300)setTimeout(boot,200);
    }
    boot();
    document.addEventListener('keydown',function(event){if(event.keyCode===27||event.keyCode===461){event.preventDefault();event.stopImmediatePropagation();VladOSCinema.back();}},true);
})();
