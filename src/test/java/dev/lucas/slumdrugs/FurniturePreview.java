package dev.lucas.slumdrugs;
import dev.lucas.slumdrugs.pack.FurnitureModels;
import java.nio.file.*;

/** Standalone geometry preview, explicitly not a Minecraft screenshot. */
public final class FurniturePreview {
    public static void write(Path destination) throws Exception {
        String data=new com.google.gson.Gson().toJson(FurnitureModels.definitions());
        String html="""
                <!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
                <title>SlumDrugs furniture preview</title><style>
                *{box-sizing:border-box}body{margin:0;background:#141a20;color:#ede7d9;font:16px system-ui}main{max-width:1160px;margin:auto;padding:32px}
                h1{font-size:32px;margin:0 0 8px}p{color:#b6c2c4;max-width:800px;line-height:1.6}section{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:18px}
                article{background:#202a31;border:1px solid #38454a;border-radius:14px;padding:18px}canvas{width:100%;height:270px;touch-action:none;cursor:grab}h2{font-size:18px;margin:0 0 4px}
                label{display:block;color:#b6c2c4;margin:12px 0}input{accent-color:#9ac681}small{color:#b6c2c4}a{color:#c7e3ac}
                </style><main><h1>SlumDrugs · furniture</h1><p>Drag a model to rotate it. This preview uses the exact cuboid geometry exported in the resource pack, with representative colors. Minecraft applies its own block textures and lighting.</p><section id="models"></section>
                <p>In game: right-click to use · sneak + stick to rotate · sneak + left-click to pick up. Growbox: seeds, water bucket, fertilizer, then empty hand to harvest. Sneak + empty hand toggles its lamp.</p></main><script>
                const models=MODEL_DATA;
                const colors={wood:'#806143',iron:'#aebbbd',stone:'#7c8487',paper:'#e9dec8',parcel:'#92724b',rope:'#bcaa83',glass:'#84b7bc',soil:'#614533',lamp:'#d9f4bb',leaf:'#528148',stem:'#7e9846',bud:'#d2ac5b',amber:'#ce8738'};
                const titles={growbox:'Growbox',drying_rack:'Drying rack',processing_bench:'Processing bench',packaging_station:'Packaging station',storage_crate:'Storage crate'};
                for(const id of ['growbox','drying_rack','processing_bench','packaging_station','storage_crate']){
                  const card=document.createElement('article');card.innerHTML='<h2>'+titles[id]+'</h2><canvas width="520" height="540"></canvas>';
                  let stage=4,lit=true,angle=-.65; const canvas=card.querySelector('canvas'),ctx=canvas.getContext('2d');
                  function draw(){
                    ctx.clearRect(0,0,520,540);const boxes=models[id==='growbox'?'growbox_'+stage+(lit?'_on':'_off'):id];
                    const project=([x,y,z])=>{x-=8;y-=8;z-=8;const a=x*Math.cos(angle)-z*Math.sin(angle),b=x*Math.sin(angle)+z*Math.cos(angle);return [260+a*19,295-y*17+b*8,b-y*8/17]};
                    let faces=[];for(const b of boxes){let [x,y,z]=b.from,[X,Y,Z]=b.to;
                      const vertices=[[x,y,z],[X,y,z],[X,Y,z],[x,Y,z],[x,y,Z],[X,y,Z],[X,Y,Z],[x,Y,Z]];
                      for(const [v,light,n] of [[[0,1,2,3],.78,[0,0,-1]],[[4,5,6,7],.65,[0,0,1]],[[0,4,7,3],.7,[-1,0,0]],[[1,5,6,2],.9,[1,0,0]],[[3,2,6,7],1.12,[0,1,0]],[[0,1,5,4],.6,[0,-1,0]]]){
                        if(-n[0]*Math.sin(angle)+n[1]*8/17-n[2]*Math.cos(angle)<=0)continue;
                        const points=v.map(i=>project(vertices[i]));faces.push({points,depth:points.reduce((a,p)=>a+p[2],0)/4,texture:b.texture,light});
                      }
                    }
                    faces.sort((a,b)=>b.depth-a.depth);for(const f of faces){const hex=colors[f.texture];const rgb=[1,3,5].map(i=>Math.min(255,Math.round(parseInt(hex.slice(i,i+2),16)*f.light)));
                      ctx.beginPath();f.points.forEach((p,i)=>i?ctx.lineTo(p[0],p[1]):ctx.moveTo(p[0],p[1]));ctx.closePath();ctx.globalAlpha=f.texture==='glass'?.15:1;
                      ctx.fillStyle='rgb('+rgb.join(',')+')';ctx.fill();ctx.strokeStyle='rgba(10,15,16,.35)';ctx.lineWidth=1;ctx.stroke();ctx.globalAlpha=1;
                    }
                  }
                  let last=null;canvas.onpointerdown=e=>{last=e.clientX;canvas.setPointerCapture(e.pointerId)};canvas.onpointerup=()=>last=null;canvas.onpointermove=e=>{if(last===null)return;angle+=(e.clientX-last)*.012;last=e.clientX;draw()};
                  if(id==='growbox'){const label=document.createElement('label');label.textContent='Growth stage ';const slider=document.createElement('input');slider.type='range';slider.min=0;slider.max=4;slider.value=4;slider.oninput=()=>{stage=+slider.value;draw()};label.append(slider);card.append(label);
                    const toggle=document.createElement('label');toggle.innerHTML='<input type="checkbox" checked> Lamp on';toggle.firstChild.onchange=e=>{lit=e.target.checked;draw()};card.append(toggle)}
                  document.querySelector('#models').append(card);draw();
                }
                </script></html>
                """;
        Files.writeString(destination,html.replace("MODEL_DATA",data));
    }
}
