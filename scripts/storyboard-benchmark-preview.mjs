import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
const root=path.resolve('frontend/dist');
const types={'.html':'text/html; charset=utf-8','.js':'application/javascript','.css':'text/css','.svg':'image/svg+xml','.png':'image/png','.jpg':'image/jpeg','.woff2':'font/woff2','.json':'application/json'};
http.createServer((req,res)=>{
 if(req.url==='/__benchmark_asset.svg'||/^\/api\/projects\/52\/ai-image-results\/[12]\/thumbnail(?:\?|$)/.test(req.url)){res.writeHead(200,{'Content-Type':'image/svg+xml'});res.end('<svg xmlns="http://www.w3.org/2000/svg" width="64" height="64"><rect width="64" height="64" fill="#a78bfa"/></svg>');return;}

 if(req.url.startsWith('/api/')||req.url.startsWith('/actuator/')){
  const proxy=http.request({hostname:'127.0.0.1',port:18189,path:req.url,method:req.method,headers:{...req.headers,host:'127.0.0.1:18189'}},up=>{res.writeHead(up.statusCode,up.headers);up.pipe(res);});
  proxy.on('error',()=>{res.writeHead(502);res.end('Local benchmark backend unavailable');});req.pipe(proxy);return;
 }
 const pathname=decodeURIComponent(new URL(req.url,'http://127.0.0.1').pathname);
 let file=path.resolve(root,'.'+pathname);
 if(!file.startsWith(root+path.sep)&&file!==root){res.writeHead(404);res.end();return;}
 if(!fs.existsSync(file)||fs.statSync(file).isDirectory())file=path.join(root,'index.html');
 res.setHeader('Content-Type',types[path.extname(file)]||'application/octet-stream');
 res.setHeader('Cache-Control',file.endsWith('index.html')?'no-store':'public,max-age=86400');
 fs.createReadStream(file).on('error',()=>{res.writeHead(404);res.end();}).pipe(res);
}).listen(18190,'127.0.0.1',()=>console.log('PREVIEW_READY http://127.0.0.1:18190'));
