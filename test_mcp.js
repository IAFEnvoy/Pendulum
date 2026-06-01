const net=require("net");
const c=net.createConnection({host:"127.0.0.1",port:25566});
let buf="";
c.setEncoding("utf8");
c.on("data",d=>{
  buf+=d;
  const i=buf.indexOf("\n");
  if(i>=0){
    const l=buf.substring(0,i);
    buf=buf.substring(i+1);
    try{
      const r=JSON.parse(l);
      if(r.id===2){
        console.log(r.result?JSON.stringify(r.result.content):"ERR:"+JSON.stringify(r.error));
        c.end();
        process.exit(0);
      }
    }catch(e){}
  }
});
c.on("connect",()=>{
  c.write('{"jsonrpc":"2.0","id":0,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"c","version":"1"}}}\n');
  setTimeout(()=>{c.write('{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"pendulum_eval","arguments":{"code":"JSON.stringify(mc.getNearbyPlayers(64))"}}}\n')},500);
});
setTimeout(()=>{console.log("TIMEOUT");c.end();process.exit(1)},10000)
