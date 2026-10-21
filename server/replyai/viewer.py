"""Static viewer; the short-lived read-only grant is supplied in a URL fragment."""
PAGE = r'''<!doctype html>
<html lang="ru"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="referrer" content="no-referrer"><title>Zamolvi — вход в Telegram</title>
<style nonce="__NONCE__">
*{box-sizing:border-box}body{margin:0;background:#f3f6f5;color:#192c2b;font:17px system-ui,sans-serif;display:grid;place-items:center;min-height:100vh;padding:24px}
main{background:white;max-width:580px;width:100%;padding:30px;border:1px solid #d8e4e0;border-radius:24px;text-align:center}
.tag{font-size:12px;letter-spacing:.15em;color:#087f78;font-weight:700}h1{font-size:30px;line-height:1.1}p{line-height:1.5}canvas{display:block;image-rendering:pixelated;width:min(100%,360px);height:auto;margin:20px auto;background:white}small{display:block;color:#586b68;line-height:1.5}#status{font-weight:600}#timer{color:#087f78;min-height:26px}.hidden{display:none!important}
</style></head><body><main><div class="tag">ZAMOLVI / TELEGRAM</div><h1>Подключите свой Telegram</h1>
<p>На телефоне откройте Telegram → Настройки → Устройства → Подключить устройство и отсканируйте этот QR.</p>
<canvas id="qr" class="hidden" aria-label="QR входа в ваш сервер Zamolvi"></canvas>
<div id="timer"></div><p id="status" role="status">Получаем QR…</p>
<small>Подтверждайте только вход в свой сервер Zamolvi. Пароль двухэтапной проверки вводится в приложении Zamolvi на телефоне. Страница закроет доступ к QR через несколько минут.</small>
</main><script nonce="__NONCE__">
'use strict';
const canvas=document.getElementById('qr'), status=document.getElementById('status'), timer=document.getElementById('timer');
let key='',running=true,expires=0,generation=0,pollTimer=null;
function clear(){canvas.classList.add('hidden');canvas.width=1;canvas.height=1;timer.textContent='';expires=0;}
function draw(rows){
 if(!Array.isArray(rows)||rows.length<25||rows.length>129||!rows.every(r=>typeof r==='string'&&r.length===rows.length&&/^[01]+$/.test(r)))throw Error();
 const size=rows.length;canvas.width=canvas.height=size*8;const c=canvas.getContext('2d');c.fillStyle='#fff';c.fillRect(0,0,canvas.width,canvas.height);c.fillStyle='#000';
 rows.forEach((r,y)=>{for(let x=0;x<size;x++)if(r[x]==='1')c.fillRect(x*8,y*8,8,8);});canvas.classList.remove('hidden');
}
async function refresh(cycle){
 if(!running||cycle!==generation)return;
 try{
  if(!/^[A-Za-z0-9_-]{43}$/.test(key)){status.textContent='Откройте полную ссылку, скопированную из Zamolvi.';clear();return;}
  const response=await fetch('/qr/state',{method:'POST',headers:{Authorization:'Bearer '+key},cache:'no-store',credentials:'omit',redirect:'error',referrerPolicy:'no-referrer'});
  if(cycle!==generation)return;
  if(!response.ok){clear();status.textContent='Ссылка истекла или вход отменён. Создайте новую ссылку в Zamolvi.';return;}
  const data=await response.json();if(cycle!==generation)return;status.textContent=data.message||'Проверьте состояние входа в приложении.';
  if(data.stage==='qr'&&data.qr_rows){draw(data.qr_rows);expires=Date.now()+Math.max(0,Math.min(300,Number(data.expires_in)||0))*1000;}
  else clear();
  running=data.stage==='qr';
 }catch(_){if(cycle!==generation)return;clear();status.textContent='Нет связи с сервером. Повторяем подключение…';}
 if(running&&cycle===generation)pollTimer=setTimeout(()=>refresh(cycle),2000);
}
function openGrant(){
 key=location.hash.slice(1);history.replaceState(null,'',location.pathname);
 generation++;running=true;clearTimeout(pollTimer);clear();refresh(generation);
}
addEventListener('hashchange',openGrant);
setInterval(()=>{if(expires){const left=Math.max(0,Math.ceil((expires-Date.now())/1000));timer.textContent=left?'QR обновится через '+left+' с':'Обновляем QR…';if(!left)canvas.classList.add('hidden');}},500);
openGrant();
</script></body></html>'''
