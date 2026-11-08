const fs=require('fs'),vm=require('vm'),assert=require('assert');
const source=fs.readFileSync(require('path').join(__dirname,'../app/src/main/assets/cinema/rezka.js'),'utf8').replace('    bootstrap();','    window.testRezka={component:component,decode:decodeTrash,playlist:parsePlaylist};');
const requests=[],elements=[],filters=[];
function element(){const obj={events:{},append(){return this},empty(){return this},remove(){},text(v){this.value=v;return this},html(){return String(this.value).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;')},on(name,cb){this.events[name]=cb;return this}};elements.push(obj);return obj;}
const store=new Map();
const Lampa={Storage:{get:(k,d)=>store.has(k)?store.get(k):d,set:(k,v)=>store.set(k,v)},Reguest:function(){this.timeout=()=>{};this.silent=(url,ok,fail,body)=>requests.push({url,ok,fail,body});this.clear=()=>{};},Scroll:function(){this.minus=()=>{};this.render=()=>({});this.append=()=>{};},Explorer:function(){this.appendFiles=()=>{};this.appendHead=()=>{};this.render=()=>({});},Filter:function(){this.render=()=>({});this.set=(key,items)=>{this.items=items};filters.push(this)},Controller:{enable:()=>{},toggle:()=>{},add:()=>{},enabled:()=>({name:'content'})},Utils:{},Select:{},Empty:function(){this.render=()=>({})}};
const context={window:{},Lampa,$:element,document:{createElement:()=>({set innerHTML(v){this.source=v},querySelectorAll(){return [{getAttribute:()=>'/films/action/123-fixture-2026.html',querySelector:()=>({textContent:'Fixture'}),textContent:'Fixture (2026)'}]}})},console,setTimeout:()=>{},btoa:s=>Buffer.from(s).toString('base64'),atob:s=>Buffer.from(s,'base64').toString()};
vm.runInNewContext(source,context);const api=context.window.testRezka;
const component=new api.component({movie:{id:42,title:'Local name',original_title:'Fixture',release_date:'2026-01-01'}});component.activity={loader(){},toggle(){}};component.create();
assert.equal(requests.length,1,'Opening component must start source search');assert(requests[0].url.includes('q=Fixture'),'Search should use original title');
requests[0].ok('<a>fixture</a>');assert.equal(requests.length,2,'Search selection must fetch film page');
requests[1].ok('initCDNMoviesEvents(123, 7, 0, 0, 0)');assert(elements.some(e=>e.events['hover:enter']),'Film page must render playable row without Lampa.Utils.escape');
assert.equal(filters[0].items[0].items[0].index,0);
const decoded=api.decode('#h'+Buffer.from('[720p]https://media.example/movie.mp4').toString('base64'));assert.equal(api.playlist(decoded)[0].file,'https://media.example/movie.mp4');
console.log('PASS: source search, film page, playable row, voice choices, encoded playlist.');
