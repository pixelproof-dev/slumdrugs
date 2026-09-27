#!/usr/bin/env python3
"""Deterministic modern 16x16 item art. Pixel geometry is the editable source of truth.
No anti-aliasing, gradients or downloaded art. Also authors item model definitions and names.
"""
from pathlib import Path
import json,re
from PIL import Image,ImageDraw,ImageFont
ROOT=Path(__file__).resolve().parent.parent
AS=ROOT/'neoforge/src/main/resources/assets/slumdrugs'
MAP=json.loads((ROOT/'tools/modern_item_names.json').read_text())
IDS=MAP['ids']; SUBS=MAP['substances']
COLORS={'daybreak':'#ffb03a','coldsnap':'#9be7ff','redline':'#ff3d3d','neon':'#d58cff','voltage':'#fff26b','blackout':'#3b2f63','riptide':'#2fd3b5','flatline':'#8d9b8a'}
INK='#182531';DARK='#354957';MID='#8199a5';LIGHT='#c7dbe0';WHITE='#edf4eb';GREEN='#76b68c';ORANGE='#e9a146'
ART={}

def shade(color,f):
    c=Image.new('RGB',(1,1),color).getpixel((0,0));return tuple(min(255,int(v*f)) for v in c)
class Sprite:
    def __init__(self): self.im=Image.new('RGBA',(16,16));self.d=ImageDraw.Draw(self.im)
    def box(self,b,c,outline=None): self.d.rectangle(b,fill=c,outline=outline);return self
    def poly(self,p,c,outline=INK): self.d.polygon(p,fill=c,outline=outline);return self
    def line(self,p,c,w=1):self.d.line(p,fill=c,width=w);return self
    def dot(self,p,c):self.d.point(p,fill=c);return self
    def save(self,name):
        ART[name]=self.im
        self.im.save(AS/'textures/item'/f'{name}.png')
        model={'parent':'minecraft:item/generated','textures':{'layer0':f'slumdrugs:item/{name}'}}
        (AS/'models/item'/f'{name}.json').write_text(json.dumps(model,indent=2)+'\n')
        (AS/'items'/f'{name}.json').write_text(json.dumps({'model':{'type':'minecraft:model','model':f'slumdrugs:item/{name}'}},indent=2)+'\n')
        return self

def pouch(color,sealed=False):
    s=Sprite();s.poly([(4,2),(11,2),(12,5),(13,13),(11,14),(3,14),(2,12),(3,5)],MID)
    s.box((4,3,10,3),WHITE).box((4,4,10,4),DARK)
    s.poly([(4,6),(11,6),(11,12),(4,12)],shade(color,.68),outline=DARK)
    s.box((5,7,10,10),color).line([(4,6),(4,11)],LIGHT)
    s.dot((6,7),WHITE).dot((9,10),shade(color,.5))
    if sealed:
        s.box((4,1,11,2),LIGHT,INK).box((5,8,10,11),WHITE)
        s.box((5,8,10,8),color).line([(6,10),(9,10)],DARK)
        s.box((3,13,11,13),LIGHT)
    return s

def bottle(color,label=True):
    s=Sprite().box((6,1,10,3),DARK,INK).line([(6,1),(9,1)],LIGHT)
    s.poly([(6,4),(10,4),(11,6),(12,12),(10,14),(5,14),(3,12),(4,6)],MID)
    s.box((5,7,10,12),color).line([(4,6),(4,11)],WHITE).line([(6,13),(10,13)],shade(color,.65))
    if label:s.box((6,8,10,10),WHITE).line([(7,9),(9,9)],DARK)
    return s

def sheet(accent=GREEN,clip=False):
    s=Sprite().poly([(3,1),(10,1),(13,4),(13,14),(3,14)],WHITE)
    s.poly([(10,1),(10,4),(13,4)],MID)
    s.box((4,5,11,6),accent).line([(5,8),(10,8)],MID).line([(5,10),(10,10)],MID).line([(5,12),(8,12)],MID)
    if clip:s.box((5,0,9,2),DARK).line([(6,1),(8,1)],LIGHT)
    return s

def device(color=GREEN,antenna=False):
    s=Sprite()
    if antenna:s.box((10,0,11,4),INK).dot((10,0),MID)
    s.poly([(5,2),(11,2),(12,4),(12,13),(10,15),(4,14),(3,12),(3,4)],DARK)
    s.box((5,4,10,8),INK).box((6,5,9,7),color).dot((6,5),WHITE)
    s.line([(4,4),(4,11)],MID)
    for x in (5,7,9):
        for y in (10,12):s.dot((x,y),LIGHT)
    return s

# Substance families: shared seed/packet silhouettes, distinct stage silhouettes and canonical labels.
for drug,c in COLORS.items():
    for stage in ('product','essence','package'):
        s=bottle(c) if stage=='essence' else pouch(c,stage=='package')
        s.save(stage+'_'+drug)
    if drug not in ('daybreak','coldsnap','redline'):continue
    s=Sprite()
    for x,y in ((3,9),(7,6),(10,9),(6,11),(11,5)):
        s.poly([(x,y-1),(x+2,y-1),(x+3,y+1),(x+2,y+2),(x,y+1)],shade(c,.65))
        s.dot((x+1,y),c)
    s.save('seed_'+drug)
    s=Sprite()
    if drug=='daybreak':
        s.line([(4,13),(10,4)],DARK,2)
        for points in [[(5,10),(2,7),(3,4),(7,8)],[(7,8),(7,3),(10,2),(9,7)],[(8,9),(12,5),(14,6),(11,10)]]:s.poly(points,GREEN)
        s.line([(4,6),(6,8)],'#bed894').dot((11,7),c)
    elif drug=='coldsnap':
        s.poly([(6,3),(10,3),(11,6),(9,10),(8,14),(6,11),(4,12),(5,8)],c)
        s.line([(7,4),(6,8),(7,10)],WHITE).line([(8,3),(6,1)],GREEN).line([(9,3),(12,1)],GREEN)
    else:
        s.poly([(5,4),(7,2),(10,3),(11,6),(13,8),(11,12),(7,14),(3,11),(2,8)],shade(c,.68))
        s.poly([(5,5),(8,3),(10,6),(8,10),(4,10)],c).line([(5,6),(7,4)],'#ffb18e')
    s.save('raw_'+drug)
    s=Sprite()
    for a,b in [((3,9),(7,4)),((6,12),(11,5)),((10,12),(13,8))]:
        x,y=a;xx,yy=b;s.poly([(x-1,y),(xx-1,yy),(xx+1,yy),(x+1,y+1)],shade(c,.55)).line([a,b],shade(c,.88))
    s.box((4,10,10,11),MID).dot((5,10),LIGHT).save('dried_'+drug)

# Cash: one bill, rolled notes, shrink-wrapped brick; bands do not replace the saved clean/dirty flag.
s=Sprite().poly([(2,5),(12,2),(14,9),(4,13)],'#649579')
s.poly([(4,6),(11,4),(12,8),(5,10)],'#b7cfa3',outline='#416752').poly([(7,6),(9,5),(10,7),(8,9),(6,8)],'#649579')
s.dot((4,7),WHITE).dot((11,7),WHITE).save('cash_note')
s=Sprite().poly([(4,3),(10,2),(13,5),(12,12),(6,14),(3,11)],'#769d79')
s.line([(5,4),(10,3),(12,5)],'#d4ddb2').line([(5,12),(10,10),(11,6)],'#41624c')
s.poly([(3,7),(12,4),(12,8),(5,11)],WHITE).line([(5,8),(10,6)],MID).save('cash_roll')
s=Sprite().poly([(2,5),(10,2),(14,5),(14,12),(6,15),(2,12)],'#628471')
s.poly([(3,5),(10,3),(13,5),(6,8)],'#c0d4a5').line([(6,9),(13,6)],'#d9e7be').line([(6,11),(13,8)],'#91ae8b').line([(6,13),(13,10)],'#91ae8b')
s.poly([(7,4),(9,3),(12,5),(10,6),(10,13),(8,14),(8,7)],WHITE).save('cash_brick')

sheet(ORANGE).save('property_deed')
s=sheet('#7ebed4');s.box((8,10,11,13),ORANGE,INK).dot((9,11),WHITE).save('business_licence')
s=sheet(MID);s.line([(5,12),(6,11),(7,13),(10,11)],DARK).save('contract')
s=Sprite().box((2,4,13,12),LIGHT,INK).box((3,5,12,6),'#519baf').box((4,7,7,10),DARK)
s.box((5,7,6,8),'#c6a789').line([(9,8),(11,8)],MID).line([(9,10),(11,10)],MID).dot((11,5),WHITE).save('forged_id')
s=Sprite().box((3,2,12,14),DARK,INK).box((5,3,11,12),'#59899b').box((6,5,10,7),WHITE)
for y in (3,6,9,12):s.line([(2,y),(4,y)],LIGHT)
s.box((12,4,13,12),ORANGE).save('notebook')
s=pouch('#96b7bb',True);s.box((4,2,11,3),ORANGE).line([(5,8),(10,11)],DARK).save('evidence_bag')
s=sheet(ORANGE);s.box((5,7,10,11),DARK).box((7,7,8,8),MID).line([(6,10),(9,10)],MID).save('wanted_notice')
s=Sprite().poly([(2,4),(6,4),(7,2),(12,2),(14,5),(13,14),(2,14)],'#b49766')
s.box((4,5,11,12),WHITE).box((5,7,10,8),DARK).line([(5,10),(10,10)],MID).box((8,11,11,12),'#ad5847').save('informant_file')
device('#72d3a2',True).save('police_scanner')
s=device('#83dfd1');s.line([(6,6),(7,7),(9,5)],WHITE).box((6,3,9,3),MID).save('burner_phone')
s=Sprite().poly([(6,1),(10,1),(12,4),(13,13),(10,14),(3,14),(4,4)],DARK)
s.box((5,5,7,6),INK).box((9,5,11,6),INK).box((7,10,10,11),INK).line([(5,3),(6,2),(9,2)],MID).save('ski_mask')
s=Sprite().line([(3,13),(10,4),(12,4)],INK,3).line([(3,13),(10,4),(12,4)],LIGHT)
s.line([(6,14),(12,8),(12,6)],INK,3).line([(6,14),(12,8),(12,6)],MID).save('lockpicks')

# Workshop consumables and handheld tools.
s=Sprite().poly([(4,2),(11,2),(13,4),(13,12),(11,14),(4,14),(2,12),(2,4)],DARK)
s.box((4,3,10,12),MID).box((5,4,9,11),INK)
for y in (5,7,9):s.line([(5,y),(9,y)],MID)
s.line([(4,3),(10,3)],LIGHT).save('carbon_filter')
pouch('#c8c8ab').save('filler')
bottle('#8cb8d3').save('solvent')
s=Sprite().poly([(3,4),(7,2),(11,3),(13,5),(13,10),(9,13),(4,12),(2,10)],LIGHT)
s.poly([(4,4),(7,3),(10,4),(10,6),(7,7),(4,6)],WHITE).box((6,4,8,5),DARK)
s.line([(11,6),(11,10),(7,12)],MID).poly([(4,9),(8,10),(8,14),(3,13)],'#a6c2ce').save('seal_film')
s=Sprite().poly([(3,3),(10,3),(13,6),(12,10),(8,10),(7,14),(4,14),(5,9),(2,7)],'#407e95')
s.box((4,4,9,6),LIGHT).box((9,7,13,9),WHITE).box((5,11,6,13),DARK).dot((11,8),DARK).save('label_gun')
s=Sprite().poly([(3,13),(5,14),(10,8),(9,6)],'#508f99').poly([(9,6),(11,2),(13,1),(12,5),(10,8)],LIGHT).line([(10,5),(12,2)],WHITE).save('clone_cutter')
s=pouch(GREEN);s.box((5,6,10,11),WHITE)
for p in ((6,8),(8,7),(9,9),(7,10)):s.dot(p,'#5c804c')
s.save('seed_pouch')
s=pouch('#83b97b');s.box((4,2,11,3),GREEN).box((5,8,10,11),WHITE).line([(7,10),(7,8),(8,9),(9,7)],GREEN).save('fertilizer')
s=bottle('#74c9b4');s.box((7,8,8,11),GREEN).box((6,9,9,10),GREEN).save('remedy')
s=Sprite().poly([(3,3),(11,1),(14,10),(6,14),(2,10)],MID).poly([(4,4),(10,3),(12,9),(6,12),(4,9)],'#aacdd5')
s.line([(5,6),(9,4)],WHITE).line([(6,9),(11,6)],LIGHT).save('mirror_glass')
pouch('#69748c').save('shadow_silt')
s=Sprite().poly([(4,3),(9,2),(13,5),(11,12),(6,14),(2,10)],DARK)
for x in range(4,11,2):s.line([(x,4),(x+2,5),(x+1,11),(x-1,10)],ORANGE)
s.line([(3,7),(1,7)],LIGHT).line([(12,9),(14,9)],LIGHT).save('coil_pack')
s=Sprite().box((6,1,9,2),MID).poly([(5,2),(10,2),(12,4),(12,12),(10,14),(4,14),(3,12),(3,4)],DARK)
s.box((4,4,10,10),'#649cad').box((6,5,8,8),WHITE).line([(7,5),(6,7),(8,7),(7,9)],ORANGE).line([(4,12),(10,12)],MID).save('battery_cell')
s=Sprite().poly([(3,12),(10,2),(12,3),(5,14)],DARK).line([(4,11),(10,3)],MID).line([(9,4),(11,5)],LIGHT).save('carbon_rod')
s=Sprite().poly([(2,7),(6,7),(6,3),(9,3),(9,7),(13,7),(13,10),(9,10),(9,14),(6,14),(6,10),(2,10)],MID)
s.box((5,2,10,4),'#b75d49',INK).dot((7,2),WHITE).line([(3,8),(5,8)],LIGHT).save('pump_valve')
s=Sprite().box((2,4,13,12),DARK,INK).box((4,5,11,9),MID).box((5,6,9,8),'#92d4b2').line([(5,7),(9,7)],DARK)
for x in (3,6,9,12):s.line([(x,2),(x,4)],LIGHT).line([(x,12),(x,14)],LIGHT)
s.save('line_stabiliser')
s=Sprite().poly([(4,2),(9,2),(12,5),(13,13),(3,14),(2,12),(2,5)],'#b56b3b')
s.box((5,3,8,4),INK).box((9,1,11,3),DARK).box((4,7,11,11),ORANGE).line([(5,8),(10,10)],'#814c33').line([(10,8),(5,10)],'#814c33').save('generator_fuel')

# Legacy texture/model copies remain modern too; existing packs referencing an old path still display correctly.
for old,new in IDS.items():
    if new in ART:
        ART[new].save(AS/'textures/item'/f'{old}.png')
        for folder in ('models/item','items'):
            source=AS/folder/f'{new}.json'
            (AS/folder/f'{old}.json').write_text(source.read_text())

NAMES={
'cash_note':('Cash Note','Geldschein'),'cash_roll':('Cash Roll','Geldrolle'),'cash_brick':('Cash Brick','Geldpaket'),
'carbon_filter':('Carbon Filter','Aktivkohlefilter'),'filler':('Filler','Füllstoff'),'solvent':('Solvent','Lösungsmittel'),
'seal_film':('Sealing Film','Versiegelungsfolie'),'label_gun':('Label Gun','Etikettiergerät'),'property_deed':('Property Deed','Eigentumsurkunde'),
'business_licence':('Business Licence','Gewerbeschein'),'contract':('Contract','Vertrag'),'forged_id':('Forged ID','Gefälschter Ausweis'),
'line_stabiliser':('Line Stabiliser','Leitungsstabilisator'),'clone_cutter':('Clone Cutter','Stecklingsmesser'),'seed_pouch':('Seed Pouch','Saatgutbeutel'),
'coil_pack':('Coil Pack','Spulenpaket'),'pump_valve':('Pump Valve','Pumpenventil'),'battery_cell':('Battery Cell','Batteriezelle'),
'carbon_rod':('Carbon Rod','Kohlestab'),'generator_fuel':('Generator Fuel','Generatorkraftstoff'),'evidence_bag':('Evidence Bag','Beweismittelbeutel'),
'wanted_notice':('Wanted Notice','Fahndungsplakat'),'ski_mask':('Ski Mask','Sturmhaube'),'lockpicks':('Lockpicks','Dietriche'),
'police_scanner':('Police Scanner','Polizeifunkscanner'),'mirror_glass':('Mirror Glass','Spiegelglas'),'informant_file':('Informant File','Informantenakte'),
'shadow_silt':('Shadow Silt','Schattenschlick'),'fertilizer':('Fertilizer','Dünger'),'remedy':('Recovery Medicine','Entzugsmedikament'),
'notebook':('Notebook','Notizbuch'),'burner_phone':('Burner Phone','Prepaid-Handy')}
BLOCKS={'grow_tent':('Grow Tent','Growzelt'),'drying_rack':('Drying Rack','Trockenregal'),'cloning_bench':('Cloning Bench','Stecklingsbank'),
'vacuum_sealer':('Vacuum Sealer','Vakuumierer'),'cash_counter':('Cash Counter','Geldzähler'),'pressing_bench':('Hydraulic Press','Hydraulikpresse'),
'cutting_bench':('Cutting Bench','Schneidetisch'),'storage_crate':('Storage Crates','Lagerkisten'),'centrifuge':('Centrifuge','Zentrifuge'),'still':('Column Still','Kolonnendestille')}
for name in ART:
    if name in NAMES:continue
    stage,drug=name.split('_',1);title=drug.title()
    en={'seed':title+' Seeds','raw':'Fresh '+title,'dried':'Dried '+title,'product':title,'essence':title+' Concentrate','package':'Sealed '+title}
    de={'seed':title+'-Samen','raw':'Frisches '+title,'dried':'Getrocknetes '+title,'product':title,'essence':title+'-Konzentrat','package':'Versiegeltes '+title}
    NAMES[name]=(en[stage],de[stage])
for idx,lang in enumerate(('en_us','de_de')):
    p=AS/'lang'/f'{lang}.json';d=json.loads(p.read_text(encoding='utf-8'))
    for name,titles in NAMES.items():d['item.slumdrugs.'+name]=titles[idx]
    for name,titles in BLOCKS.items():d['block.slumdrugs.'+name]=titles[idx]
    for old,new in IDS.items():
        typ='block' if new in BLOCKS else 'item'
        if f'{typ}.slumdrugs.{new}' in d:d[f'{typ}.slumdrugs.{old}']=d[f'{typ}.slumdrugs.{new}']
    # Substance mentions in dialogue and advancements follow the visible names, while keys stay save-compatible.
    for k,v in d.items():
        for old,new in SUBS.items():v=re.sub(re.escape(old),new.title(),v,flags=re.I)
        d[k]=v
    p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
# Contact sheet uses nearest-neighbour enlargement only; actual files remain 16x16.
preview=ROOT/'build/item-previews';preview.mkdir(parents=True,exist_ok=True)
cols=10;cellw=145;cellh=140;rows=(len(ART)+cols-1)//cols
board=Image.new('RGB',(cols*cellw,rows*cellh+105),'#1b232d');draw=ImageDraw.Draw(board)
font=ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf',13)
head=ImageFont.truetype('C:/Windows/Fonts/seguisb.ttf',25)
draw.text((25,20),'SLUMDRUGS / MODERN ITEM ART',font=head,fill='#e2e9e6')
draw.text((25,61),'65 pixel sprites · canonical colours · modern packaging, cash, tools and electronics',font=font,fill='#99b4c0')
for i,(name,im) in enumerate(ART.items()):
 x=(i%cols)*cellw;y=(i//cols)*cellh+105
 draw.rectangle((x+8,y+3,x+cellw-8,y+cellh-8),fill='#253240')
 board.paste(im.resize((80,80),Image.Resampling.NEAREST),(x+32,y+10),im.resize((80,80),Image.Resampling.NEAREST))
 label=NAMES[name][0]
 words=label.split();lines=['']
 for w in words:
  if len(lines[-1])+len(w)>18:lines.append(w)
  else:lines[-1]+=(' ' if lines[-1] else '')+w
 for j,l in enumerate(lines):draw.text((x+10,y+94+j*17),l,font=font,fill='#d3e1e5')
board.save(preview/'modern-items-overview.png')
assert len(ART)==65,len(ART)
print('Wrote',len(ART),'modern sprites, model definitions, legacy asset copies and EN/DE names.')
