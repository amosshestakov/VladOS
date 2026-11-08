const fs=require('node:fs');const vm=require('node:vm');const path=require('node:path');
const sandbox={window:null,localStorage:{getItem:()=>null,setItem:()=>{}},document:{addEventListener:()=>{}},setTimeout:()=>1,clearTimeout:()=>{},VladCinema:{report:()=>{},request:(...a)=>sandbox.sent=a},XMLHttpRequest:function(){},console};sandbox.window=sandbox;vm.createContext(sandbox);vm.runInContext(fs.readFileSync(path.join(__dirname,'../app/src/main/assets/cinema/vlados.js'),'utf8'),sandbox);
let x=new sandbox.XMLHttpRequest(),loaded=0;x.open('POST','https://rezka.fi/ajax/login/',true);x.onload=()=>loaded++;x.send('fixture=1');sandbox.VladOSCinema.response(sandbox.sent[0],{status:200,text:'{"success":true}',type:'application/json'});if(x.status!==200||loaded!==1||x.responseText!=='{"success":true}')throw Error('Response delivery failed');
let a=new sandbox.XMLHttpRequest();a.open('GET','https://api.themoviedb.org/3/movie/1');a.send();let id=sandbox.sent[0];a.abort();sandbox.VladOSCinema.response(id,{status:200,text:'late'});if(a.status!==0)throw Error('Cancellation failed');console.log('Transport fixtures passed — no external requests');

sandbox.Lampa={Controller:{enabled:()=>({name:'content'}),toggle:()=>{}}};let plays=[],updates=[];sandbox.VladCinema.play=(value)=>plays.push(JSON.parse(value));sandbox.VladCinema.history=(value)=>updates.push(JSON.parse(value));let position=null;const first={url:'https://media.example/one.mp4',title:'One',card:{id:1,title:'One',unwanted:'omit'},timeline:{time:42,handler:(...a)=>position=a}};const second={url:'https://media.example/two.mp4',title:'Two',timeline:{time:0}};sandbox.VladOSCinema.play(first,[first,second]);if(plays[0].position!==42)throw Error('Resume position lost');sandbox.VladOSCinema.nativeProgress(50,100);if(position[1]!==50||updates[0].card.unwanted!==undefined)throw Error('History integration failed');sandbox.VladOSCinema.nativeClosed(true);if(plays[1].url!==second.url)throw Error('Next episode failed');console.log('Resume, history and episode queue fixtures passed');

vm.runInContext(fs.readFileSync(path.join(__dirname,'../app/src/main/assets/cinema/vender/qrcode/qrcode.js'),'utf8'),sandbox);
let qrMarkup='',opened=false;
const qrTarget={html:value=>{qrMarkup=value;return qrTarget;},find:()=>qrTarget,css:()=>qrTarget};
sandbox.$=()=>({find:()=>qrTarget});sandbox.Lampa.Modal={open:()=>{opened=true;}};
sandbox.VladOSCinema.showPhoneInput('http://192.0.2.1:1234/#'+'01'.repeat(32));
if(!opened||!qrMarkup.includes('<svg')||!qrMarkup.includes('<path'))throw Error('Phone QR rendering failed');
console.log('Phone QR renders with bundled generator');

let local=new sandbox.XMLHttpRequest();local.open('POST','http://127.0.0.1:8090/torrents');local.send('{"action":"list"}');
if(sandbox.sent[1]!=='http://127.0.0.1:8090/torrents'||sandbox.sent[3]!=='{"action":"list"}')throw Error('Local TorrServer must use native bridge');
sandbox.VladOSCinema.play({url:'http://127.0.0.1:8090/stream/film.mkv?link=abc&index=1&preload',title:'Local torrent'});
if(!plays.at(-1).url.endsWith('&play'))throw Error('Native player must receive playable stream, not preload request');
console.log('Local TorrServer native transport and playback URL passed');
