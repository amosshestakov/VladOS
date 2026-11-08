/* Strict source availability. Metadata never establishes that a film can be watched. */
(function (root) {
    'use strict';
    var cache = {}, pending = {}, queue = [], running = 0, installed = false;
    var STORE = 'vlados_availability_v1', LIMIT = 300;
    function once(fn) { var done=false; return function(value){if(!done){done=true;fn(value);}}; }
    function norm(s) { return String(s||'').toLowerCase().replace(/ё/g,'е').replace(/[^a-zа-я0-9]+/g,' ').trim(); }
    function names(movie) { return [movie.title,movie.name,movie.original_title,movie.original_name].filter(function(s,i,a){return s&&a.indexOf(s)===i;}); }
    function year(movie) { return String(movie.release_date||movie.first_air_date||'').slice(0,4); }
    function isCard(value) { return value&&value.id!=null&&(value.title||value.name)&&!value.known_for_department&&value.media_type!=='person'&&(value.media_type==='movie'||value.media_type==='tv'||value.release_date!==undefined||value.first_air_date!==undefined||value.original_title!==undefined||value.original_name!==undefined); }
    function scope() { try {return root.Lampa.Storage.get('rezka_domain','')+'|'+root.Lampa.Storage.get('rezka_login','')+'|'+root.Lampa.Storage.get('rezka_status','');}catch(e){return '';}}
    function key(movie) {return scope()+'|'+(movie.source||'tmdb')+'|'+(movie.media_type||(movie.first_air_date||movie.name?'tv':'movie'))+'|'+movie.id+'|'+names(movie).join('|')+'|'+year(movie);}
    function save() {
        var keys=Object.keys(cache).sort(function(a,b){return cache[b].checkedAt-cache[a].checkedAt;});
        keys.slice(LIMIT).forEach(function(k){delete cache[k];});
        try {root.localStorage.setItem(STORE,JSON.stringify(cache));}catch(e){}
    }
    try {cache=JSON.parse(root.localStorage.getItem(STORE)||'{}')||{};}catch(e){}
    function fresh(entry) {return entry&&Date.now()-entry.checkedAt<(entry.status==='available'?30*60000:entry.status==='missing'?10*60000:30000);}
    function json(url, callback) {
        var done=once(callback), xhr=new root.XMLHttpRequest();
        xhr.open('GET',url,true);xhr.timeout=8000;
        xhr.onload=function(){try{done(xhr.status===200?JSON.parse(xhr.responseText):null);}catch(e){done(null);}};
        xhr.onerror=xhr.ontimeout=xhr.onabort=function(){done(null);};xhr.send();
    }
    function matches(movie, video) {
        if(!video||video.is_hidden||video.is_deleted||video.is_locked||video.is_paid||video.is_audio||video.is_livestream)return false;
        // Serial completeness cannot be established from a single video.
        if(movie.media_type==='tv'||movie.first_air_date||movie.original_name)return false;
        var title=norm(video.title), y=year(movie);
        if(!/^\d{4}$/.test(y)||!new RegExp('\\b'+y+'\\b').test(title))return false;
        if(/трейлер|тизер|обзор|реакция|пересказ|отрывок|фрагмент|trailer|teaser|review/.test(title))return false;
        var duration=Number(video.duration), runtime=Number(movie.runtime);
        if(!isFinite(duration)||duration<(runtime?runtime*60*0.85:40*60))return false;
        if(runtime&&duration>runtime*60*1.2)return false;
        return names(movie).some(function(name){
            var n=norm(name), raw=String(video.title||'').toLowerCase().replace(/ё/g,'е');
            // The film title must start the video title; extra text must be a year or annotation.
            var prefix=raw.split(/[([|:]/)[0].trim();
            return n&&(norm(prefix)===n||norm(prefix)===n+' '+y||norm(prefix)===n+' полный фильм');
        });
    }
    function rutube(movie, callback) {
        var queries=names(movie).slice(0,2), failed=false;
        function next() {
            if(!queries.length)return callback({status:failed?'unknown':'missing'});
            json('https://rutube.ru/api/search/video/?query='+encodeURIComponent(queries.shift()),function(data){
                if(!data||!Array.isArray(data.results)){failed=true;return next();}
                var candidates=data.results.filter(function(v){return /^[a-f0-9]{32}$/.test(v.id||'')&&matches(movie,v);}).slice(0,3);
                function verify() {
                    if(!candidates.length)return next();
                    var video=candidates.shift();
                    json('https://rutube.ru/api/play/options/'+video.id+'/',function(options){
                        if(!options){failed=true;return verify();}
                        var streams=options.video_balancer||{};
                        if(options.has_video!==true||options.is_allowed===false||options.is_paid===true||options.errors&&Object.keys(options.errors).length||!Object.keys(streams).some(function(k){return /^https:\/\//.test(streams[k]);}))return verify();
                        callback({status:'available',provider:'rutube',url:'https://rutube.ru/play/embed/'+video.id+'/',title:video.title,duration:Number(video.duration)});
                    });
                }
                verify();
            });
        }
        if(movie.media_type==='tv'||movie.first_air_date||movie.original_name)return callback({status:'unknown'});
        next();
    }
    function pump() {while(running<3&&queue.length){running++;queue.shift()();}}
    function check(movie, callback, force) {
        var k=key(movie), stored=cache[k];
        if(!force&&fresh(stored))return callback(stored);
        if(pending[k]){pending[k].push(callback);return;}
        pending[k]=[callback];
        queue.push(function(){
            var answers=[], remaining=2, timer;
            var finish=once(function(result){
                clearTimeout(timer);result.checkedAt=Date.now();cache[k]=result;save();
                var listeners=pending[k]||[];delete pending[k];running--;pump();listeners.forEach(function(fn){fn(result);});
            });
            timer=setTimeout(function(){finish({status:'unknown'});},25000);
            function answer(result){
                result=result||{status:'unknown'};answers.push(result);remaining--;
                if(result.status==='available')return finish(result);
                if(!remaining)finish({status:answers.every(function(a){return a.status==='missing';})?'missing':'unknown'});
            }
            try {if(root.VladRezkaAvailability&&root.VladRezkaAvailability.check)root.VladRezkaAvailability.check(movie,once(answer));else answer({status:'unknown'});}catch(e){answer({status:'unknown'});}
            rutube(movie,once(answer));
        });pump();
    }
    function mapAsync(items, mapper, cb) {
        if(!items.length)return cb([]);
        var result=new Array(items.length), count=items.length;
        items.forEach(function(item,i){mapper(item,function(value){result[i]=value;if(!--count)cb(result.filter(function(v){return v!==null;}));});});
    }
    function filter(data, callback) {
        if(Array.isArray(data))return mapAsync(data,function(item,done){
            if(isCard(item))check(item,function(result){done(result.status==='available'?item:null);});else filter(item,done);
        },callback);
        if(!data||typeof data!=='object')return callback(data);
        var out={}, keys=Object.keys(data);keys.forEach(function(k){out[k]=data[k];});
        var children=keys.filter(function(k){return Array.isArray(data[k])||data[k]&&typeof data[k]==='object'&&['movie','tv','similar','recommendations','credits','collection'].indexOf(k)>=0;});
        mapAsync(children,function(k,done){filter(data[k],function(value){out[k]=value;done(k);});},function(){callback(out);});
    }
    function wrap(target,name,index) {
        if(!target||typeof target[name]!=='function'||target[name].availabilityWrapped)return;
        var original=target[name];
        var wrapped=function(){var args=Array.prototype.slice.call(arguments), cb=args[index], self=this;
            if(typeof cb==='function')args[index]=function(data){filter(data,cb);};
            var returned=original.apply(self,args);
            // Main/category return a loader for subsequent rows, not only the initial callback.
            if(typeof returned==='function')return function(done,empty){return returned(function(data){filter(data,done);},empty);};
            return returned;
        };wrapped.availabilityWrapped=true;target[name]=wrapped;
    }
    function install() {
        if(installed||!root.Lampa||!root.Lampa.Api)return;
        installed=true;
        var api=root.Lampa.Api;
        ['main','category','list','search','favorite','person','company','collections','full'].forEach(function(name){wrap(api,name,1);});
        // Discovery invokes source-specific search directly.
        Object.keys(api.sources||{}).forEach(function(k){var src=api.sources[k];wrap(src,'search',1);
            if(src.discovery){var old=src.discovery;src.discovery=function(){var obj=old.apply(this,arguments);wrap(obj,'search',1);return obj;};}
        });
        root.Lampa.Listener.follow('full',function(e){
            if(e.type!=='complite'||!e.object||!e.object.activity)return;
            var movie=e.data&&(e.data.movie||e.data)||e.object.movie;if(!movie)return;
            var view=e.object.activity.render(), buttons=view.find('.full-start-new__buttons');if(!buttons.length)buttons=view.find('.full-start__buttons');
            check(movie,function(result){
                if(result.status!=='available'||result.provider!=='rutube'||view.find('.view--rutube').length)return;
                var button=root.$('<div class="full-start__button selector view--rutube"><span>Смотреть · Rutube</span></div>');
                button.on('hover:enter',function(){check(movie,function(current){
                    if(current.status==='available'&&current.provider==='rutube'&&root.VladCinema&&root.VladCinema.openProvider)root.VladCinema.openProvider(current.url);
                    else root.Lampa.Noty.show('Источник больше не подтверждён. Обнови каталог.');
                },true);});buttons.prepend(button);
            });
        });
    }
    root.VladAvailability={check:check,filter:filter,install:install,matches:matches,rutube:rutube};
})(window);
