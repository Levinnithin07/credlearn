(() => {
  'use strict';
  const root=document.getElementById('chat'); if(!root)return;
  const api=`/api/sessions/${root.dataset.session}/chat`, user=Number(root.dataset.user);
  const list=document.getElementById('messages'), empty=document.getElementById('chat-empty'), error=document.getElementById('chat-error');
  const form=document.getElementById('message-form'), input=document.getElementById('message-content'), older=document.getElementById('load-older'), connection=document.getElementById('connection-status');
  const headers={'Content-Type':'application/json',[document.querySelector('meta[name="_csrf_header"]').content]:document.querySelector('meta[name="_csrf"]').content};
  let first=0,last=0,lastRead=0,busy=false,again=false,stopped=false,source,timer,pending=null;
  const polling=root.dataset.transport==='poll';
  const seen=new Set();
  function fail(message){error.textContent=message;error.hidden=false;}
  async function request(path='',body){
    const r=await fetch(api+path,{method:body?'POST':'GET',headers:body?headers:{},body:body?JSON.stringify(body):undefined,cache:'no-store'});
    if(r.status===401||r.redirected){stopped=true;source?.close();throw Error('Your login expired. Sign in again in another tab, then reload this conversation. Your unsent text is still here.');}
    if(!r.ok){const value=await r.json().catch(()=>({}));throw Error(value.error||'The request could not be completed. Check your connection and try again.');}return r.json();
  }
  function render(messages,prepend=false){
    const fragment=document.createDocumentFragment();
    for(const m of messages){if(seen.has(m.id))continue;seen.add(m.id);first=first?Math.min(first,m.id):m.id;last=Math.max(last,m.id);
      const item=document.createElement('article');item.className='message '+(m.senderId===user?'mine':'theirs');item.dataset.messageId=m.id;
      const name=document.createElement('strong');name.className='message-sender';name.textContent=m.senderId===user?'You':m.sender;
      const text=document.createElement('p');text.textContent=m.content;
      const time=document.createElement('time');time.dateTime=m.sentAt;time.textContent=new Intl.DateTimeFormat('en-IN',{dateStyle:'medium',timeStyle:'short',timeZone:'Asia/Kolkata'}).format(new Date(m.sentAt))+' IST';
      item.append(name,text,time);fragment.append(item);
    }
    empty.hidden=seen.size>0;empty.textContent='Say hello. Your shared learning starts here.';
    if(prepend)list.prepend(fragment);else list.append(fragment);
  }
  async function read(){if(last>lastRead&&!document.hidden){try{const through=last;await request('/read',{through});lastRead=through;}catch(e){fail(e.message);}}}
  async function refresh(){
    if(stopped)return;if(busy){again=true;return;}busy=true;
    try{let count;do{const data=await request(last?`?after=${last}`:'');const bottom=list.scrollHeight-list.scrollTop-list.clientHeight<100||!last;
      render(data.messages);count=data.messages.length;input.disabled=!data.writable;form.querySelector('button').disabled=!data.writable;
      document.getElementById('chat-session-status').textContent=data.status;
      if(!data.writable)document.getElementById('chat-policy').textContent='This conversation is read-only after completion or cancellation.';
      if(bottom)list.scrollTop=list.scrollHeight;
      if(seen.size>=50)older.hidden=false;
    }while(count===50);await read();error.hidden=true;if(polling)connection.textContent='Connected · Automatic updates';}catch(e){fail(e.message);if(polling)connection.textContent=stopped?'Sign in to reconnect':'Reconnecting… Your messages are saved.';}finally{busy=false;if(again){again=false;refresh();}}
  }
  older.addEventListener('click',async()=>{older.disabled=true;try{const height=list.scrollHeight;const data=await request(`?before=${first}`);render(data.messages,true);list.scrollTop+=list.scrollHeight-height;older.hidden=data.messages.length<50;}catch(e){fail(e.message);}finally{older.disabled=false;}});
  form.addEventListener('submit',async e=>{e.preventDefault();if(!input.value.trim())return;const text=input.value;
    if(!pending||pending.content!==text)pending={content:text,clientMessageId:crypto.randomUUID()};
    form.querySelector('button').disabled=true;
    try{await request('',pending);if(input.value===text)input.value='';pending=null;await refresh();list.scrollTop=list.scrollHeight;error.hidden=true;}
    catch(err){fail(err.message+' Your draft has been kept.');}finally{form.querySelector('button').disabled=input.disabled;}
  });
  if(polling){
    // Short authenticated requests work across instances and container restarts.
    timer=setInterval(()=>{if(!document.hidden)refresh();},3000);
  }else{
    source=new EventSource(api+'/events');
    source.addEventListener('connected',()=>{connection.textContent='Connected · Live updates';refresh();});
    source.addEventListener('changed',refresh);
    source.onerror=()=>{connection.textContent=stopped?'Sign in to reconnect':'Reconnecting… Your messages are saved.';refresh();};
  }
  document.addEventListener('visibilitychange',()=>{if(!document.hidden)refresh();});
  window.addEventListener('pagehide',()=>{source?.close();clearInterval(timer);});
  window.addEventListener('pageshow',e=>{if(e.persisted)location.reload();});
  refresh();
})();
