(function(root){
    'use strict';
    function text(value,fallback,depth){
        depth=depth||0;fallback=fallback||'Источник не сообщил причину ошибки';
        if(depth>4||value==null)return fallback;
        if(typeof value==='string'){
            var result=value.replace(/<[^>]*>/g,' ').replace(/\s+/g,' ').trim();
            if(!result||result==='[object Object]')return fallback;
            if(/^[\[{]/.test(result)){try{return text(JSON.parse(result),fallback,depth+1);}catch(ignored){}}
            return result.slice(0,350);
        }
        if(Array.isArray(value)){var parts=value.map(function(v){return text(v,'',depth+1);}).filter(Boolean);return parts.slice(0,3).join('; ')||fallback;}
        if(typeof value==='object'){
            var keys=['message','error','errors','detail','description','text','reason','login_name','login_password','captcha'];
            for(var i=0;i<keys.length;i++)if(value[keys[i]]!=null){var result=text(value[keys[i]],'',depth+1);if(result)return result;}
            return typeof value.code==='string'||typeof value.code==='number'?'Код ошибки: '+value.code:fallback;
        }
        return fallback;
    }
    root.VladOSErrorText=text;
    if(typeof module!=='undefined')module.exports=text;
})(typeof window!=='undefined'?window:globalThis);
