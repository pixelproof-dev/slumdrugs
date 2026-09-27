"""Authors the station block models as code, the way the textures are: every model is a short
recipe of boxes, octagons and slanted slabs, so a change is a diff and the whole set stays in
one style. Writes the JSON the game reads; tools/render_models.py previews it.

Vanilla model rules honoured here: one rotation axis per element, at multiples of 22.5 up to
45; coordinates between -16 and 32; a round thing is an octagon made of four bars, the two
diagonal ones a hair shorter so no two top faces fight."""
import json, pathlib, math

OUT = pathlib.Path('neoforge/src/main/resources/assets/slumdrugs/models/block')
OUT.mkdir(parents=True, exist_ok=True)

T = 'slumdrugs:block/'
FACES = ('north', 'south', 'east', 'west', 'up', 'down')
DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
    "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
}


class Model:
    def __init__(self, particle):
        self.textures = {'particle': particle}
        self.elements = []

    def tex(self, key, path):
        self.textures[key] = path
        return '#' + key

    def box(self, frm, to, tex, faces=None, rot=None, uv_full=True, only=None, skip=(), shade=None):
        """A box. `faces` overrides the texture per face; `only` limits which faces exist."""
        el = {'from': [round(v, 3) for v in frm], 'to': [round(v, 3) for v in to], 'faces': {}}
        for f in FACES:
            if only and f not in only: continue
            if f in skip: continue
            t = (faces or {}).get(f, tex)
            face = {'texture': t}
            if uv_full: face['uv'] = [0, 0, 16, 16]
            el['faces'][f] = face
        if rot: el['rotation'] = rot
        if shade is not None: el['shade'] = shade
        self.elements.append(el)
        return el

    def octagon(self, cx, y0, y1, cz, width, tex, top=None, bottom=None, axis='y', eps=0.02, shade=None):
        """A regular octagonal prism from four bars: two straight, two at 45 degrees.

        `axis` is the prism's axis: 'y' for a drum or disc lying flat, 'x' or 'z' for a wheel
        standing up (then y0/y1 are the extent along that axis and cx, cz are the other two
        centre coordinates in the order x, z with the axis one skipped)."""
        w = width / 2
        a = width * math.tan(math.radians(22.5)) / 2
        caps = {}
        if top: caps['up' if axis == 'y' else ('east' if axis == 'x' else 'south')] = top
        if bottom: caps['down' if axis == 'y' else ('west' if axis == 'x' else 'north')] = bottom
        if axis == 'y':
            bars = [((cx - w, y0, cz - a), (cx + w, y1, cz + a)), ((cx - a, y0, cz - w), (cx + a, y1, cz + w))]
            origin = [cx, (y0 + y1) / 2, cz]
            def shrink(b): return ((b[0][0], b[0][1] + eps, b[0][2]), (b[1][0], b[1][1] - eps, b[1][2]))
        elif axis == 'x':
            # cx is the x-centre of the wheel's thickness; y0/y1 its x extent; cz its z centre.
            xc, yc, zc = (y0 + y1) / 2, cx, cz
            bars = [((y0, yc - w, zc - a), (y1, yc + w, zc + a)), ((y0, yc - a, zc - w), (y1, yc + a, zc + w))]
            origin = [xc, yc, zc]
            def shrink(b): return ((b[0][0] + eps, b[0][1], b[0][2]), (b[1][0] - eps, b[1][1], b[1][2]))
        else:
            xc, yc, zc = cx, cz, (y0 + y1) / 2
            bars = [((xc - w, yc - a, y0), (xc + w, yc + a, y1)), ((xc - a, yc - w, y0), (xc + a, yc + w, y1))]
            origin = [xc, yc, zc]
            def shrink(b): return ((b[0][0], b[0][1], b[0][2] + eps), (b[1][0], b[1][1], b[1][2] - eps))
        for frm, to in bars:
            self.box(frm, to, tex, faces=caps, shade=shade)
        for frm, to in bars:
            frm, to = shrink((frm, to))
            self.box(frm, to, tex, faces=caps, rot={'origin': origin, 'axis': axis, 'angle': 45}, shade=shade)

    def cross(self, cx, y0, y1, cz, width, tex):
        """Two crossed vertical planes, for plants and hanging bundles."""
        w = width / 2
        for angle in (45, -45):
            self.box((cx - w, y0, cz), (cx + w, y1, cz), tex, only=('north', 'south'),
                     rot={'origin': [cx, y0, cz], 'axis': 'y', 'angle': angle}, shade=False)

    def write(self, name, parent='minecraft:block/block'):
        obj = {'parent': parent, 'textures': self.textures, 'elements': self.elements, 'display': DISPLAY}
        (OUT / f'{name}.json').write_text(json.dumps(obj, indent=2) + '\n')
        return name


def legs(m, tex, inset=1, size=2.5, height=8, y0=0):
    for x in (inset, 16 - inset - size):
        for z in (inset, 16 - inset - size):
            m.box((x, y0, z), (x + size, height, z + size), tex)


# Modern, Minecraft-compatible models. Front is north (-Z); 16 units = one block.
import base64, uuid, shutil
ASSETS = OUT.parents[1]
PROJECTS = pathlib.Path('build/blockbench-modern')
WRITTEN = []

def station():
    m = Model(T+'modern_steel')
    for t in ('steel','shell','dark','rubber','silver','blue','orange','mint','cash','rockwool','leaf','dry_leaf','white','screen','screen_on','label','vent','mesh','led','led_off','gauge','hazard','glass','fan','rotor','rotor_spin','screen_run'):
        m.tex(t,T+'modern_'+t)
    return m

def box(m,a,b,t,**kw): return m.box(a,b,'#'+t,**kw)
def disc(m,x,y0,y1,z,w,t,**kw): m.octagon(x,y0,y1,z,w,'#'+t,**kw)
def panel(m,x,y,z,w,h,t): box(m,(x,y,z-.04),(x+w,y+h,z),t,only=('north',),shade=False)
def feet(m,top=9):
    for x in (1.2,13.5):
        for z in (1.4,13):
            box(m,(x,0,z),(x+1.2,top,z+1.2),'steel')
            box(m,(x-.2,0,z-.2),(x+1.4,.7,z+1.4),'rubber')
def table(m,accent='blue'):
    feet(m)
    box(m,(.3,9,.5),(15.7,10.3,15.5),'steel')
    box(m,(1.7,2.2,2),(14.3,2.8,14),'dark')
    box(m,(.35,9.05,.35),(15.65,9.55,.55),accent)

def write(m,name,aliases=()):
    # Retain only used textures, making both exported formats self-contained and small.
    used={f['texture'][1:] for el in m.elements for f in el['faces'].values()}
    m.textures={k:v for k,v in m.textures.items() if k in used or k=='particle'}
    m.write(name)
    for alias in aliases: m.write(alias)
    WRITTEN.append(name)
    # Native Blockbench project with embedded PNGs, named elements and editable cuboids.
    PROJECTS.mkdir(parents=True,exist_ok=True)
    texkeys=[k for k in m.textures if k!='particle']
    textures=[]
    for i,k in enumerate(texkeys):
        p=ASSETS/'textures'/(m.textures[k].split(':')[1]+'.png')
        data=p.read_bytes()
        import struct
        width,height=struct.unpack('>II',data[16:24])
        textures.append({'path':'','name':p.name,'folder':'block','namespace':'slumdrugs','id':str(i),'uuid':str(uuid.uuid5(uuid.NAMESPACE_URL,name+'/texture/'+k)),'source':'data:image/png;base64,'+base64.b64encode(data).decode(),'mode':'bitmap','width':width,'height':height,'uv_width':16,'uv_height':16,'render_mode':'default'})
    elems=[]
    for i,e in enumerate(m.elements):
        r=e.get('rotation',{}); rotation=[0,0,0]
        if r: rotation['xyz'.index(r['axis'])]=r['angle']
        faces={f:{'uv':d.get('uv',[0,0,16,16]),'texture':texkeys.index(d['texture'][1:])} if f in e['faces'] else {'uv':[0,0,0,0],'texture':None} for f in FACES for d in [e['faces'].get(f,{})]}
        elems.append({'name':name+' / '+next(iter(e['faces'].values()))['texture'][1:]+' '+str(i+1),'type':'cube','uuid':str(uuid.uuid5(uuid.NAMESPACE_URL,name+'/cube/'+str(i))),'from':e['from'],'to':e['to'],'origin':r.get('origin',[8,8,8]),'rotation':rotation,'rescale':False,'box_uv':False,'shade':e.get('shade',True),'faces':faces})
    obj={'meta':{'format_version':'4.10','model_format':'java_block','box_uv':False},'name':name,'model_identifier':'slumdrugs:'+name,'resolution':{'width':16,'height':16},'elements':elems,'outliner':[e['uuid'] for e in elems],'textures':textures,'display':DISPLAY,'ambientocclusion':True}
    (PROJECTS/(name+'.bbmodel')).write_text(json.dumps(obj,indent=2)+'\n')
    return name

def grow_tent(stage,lit=True):
    m=station()
    box(m,(.5,0,.5),(15.5,1,15.5),'rubber')
    # Open zipped frontage; reflective side/rear lining and rolled fabric door.
    box(m,(.5,1,14.6),(15.5,23,15.5),'dark',faces={'north':'#silver'})
    for x in (.5,14.6):
        box(m,(x,1,1),(x+.9,23,14.6),'dark',faces={'east':'#silver','west':'#silver'})
    box(m,(.5,22.5,.5),(15.5,23.5,15.5),'dark')
    for x in (1.3,13.9):
        box(m,(x,1,1.2),(x+.5,22.5,1.7),'steel')
        box(m,(x-.2,2,.9),(x+.6,21,1.15),'white')
    box(m,(1.9,20.5,.6),(13.9,22.1,2.2),'rubber')
    for x in (4,11): box(m,(x,20.3,.5),(x+.5,22.3,2.3),'orange')
    for x in (4,11): box(m,(x,19,7),(x+.25,22.5,7.25),'steel')
    box(m,(3,18.5,3),(13,19.1,12),'shell')
    for x in (4,7.5,11):
        box(m,(x,18.2,3.4),(x+.8,18.5,11.6),'led' if lit else 'led_off',shade=not lit)
    for x,z in ((5,5),(11,10)):
        box(m,(x-2,1,z-2),(x+2,3.4,z+2),'dark',faces={'up':'#rockwool'})
        if stage:
            plant=m.tex('plant',T+'plant_'+str(stage))
            m.cross(x,3.4,4+stage*2.8,z,3+stage*1.2,plant)
    panel(m,11.8,14,0.8,1.8,3,'screen_on' if lit else 'screen')
    box(m,(3.5,23.5,10),(8,25.5,14),'steel')
    panel(m,4,23.8,9.95,3.5,1.2,'vent')
    box(m,(.8,4,4),(1.8,8,8),'dark')
    return write(m,f'grow_tent_stage{stage}'+('' if lit else '_dark'),[f'forcing_frame_stage{stage}'+('' if lit else '_dark')])

def drying_rack(n,dry=False):
    m=station()
    for x in (1,14):
        for z in (2,13):
            box(m,(x,0,z),(x+1,21,z+1),'steel')
            box(m,(x-.2,0,z-.2),(x+1.2,.7,z+1.2),'rubber')
    for level,y in enumerate((4,10,16)):
        box(m,(1.5,y,2),(14.5,y+.35,14),'mesh')
        for z in (1.8,13.5): box(m,(1,y-.4,z),(15,y+.6,z+.6),'steel')
        box(m,(6,y,1.3),(10,y+.55,1.8),'blue')
        if level<n:
            for x in (4,8,12):
                box(m,(x-1.3,y+.4,4),(x+1.3,y+1,11),'dry_leaf' if dry else 'leaf')
    box(m,(1,20,12.8),(15,21,14),'steel')
    box(m,(5.2,16.7,11.8),(10.8,22.3,13),'dark')
    panel(m,5.5,17,11.75,5,5,'fan')
    panel(m,1.2,17,1.95,1.5,2,'label')
    suffix=f'_{n}' if n<3 else ''
    ds='_dry' if dry else ''
    return write(m,'drying_rack'+suffix+ds,['drying_loft'+suffix+ds])

def pressing_bench():
    m=station(); table(m,'orange')
    for x in (2,12):
        box(m,(x,10.3,5),(x+2,24,11),'dark')
        box(m,(x+.3,11,4.9),(x+1.7,22,5),'steel')
    box(m,(1.5,22,4.5),(14.5,24,11.5),'orange')
    box(m,(6,18,6),(10,22,10),'dark')
    disc(m,8,14,19,8,1.6,'silver')
    box(m,(4,13.2,4),(12,14.2,12),'steel')
    box(m,(3,10.3,3.5),(13,11.4,12.5),'dark')
    panel(m,3,10.6,3.45,10,.7,'hazard')
    panel(m,10,22.2,4.45,2,1.6,'gauge')
    box(m,(13.8,11,8),(15,17,10),'orange')
    box(m,(14.1,16,6),(14.7,20,6.6),'steel',rot={'origin':[14.4,16,6.3],'axis':'x','angle':-22.5})
    box(m,(13.8,19.5,4.3),(15,20.5,5.7),'rubber')
    panel(m,2.5,6,1.3,3,1.5,'label')
    return write(m,'pressing_bench')

def vacuum_sealer():
    m=station(); table(m)
    box(m,(1,10.3,3),(12,12.5,11),'shell')
    box(m,(1.4,12.5,3.3),(11.6,13,10.7),'rubber')
    box(m,(1,13,4),(12,14.2,11),'shell',rot={'origin':[6.5,13,11],'axis':'x','angle':22.5})
    box(m,(2.5,14.3,4.5),(10.5,14.8,5.5),'dark',rot={'origin':[6.5,13,11],'axis':'x','angle':22.5})
    panel(m,2,10.8,2.95,3,1,'screen_on')
    box(m,(2,10.35,1),(10,10.55,3),'glass')
    for y in (10.4,10.9,11.4):
        box(m,(12, y, 2),(15,y+.35,7),'silver',faces={'up':'#label'})
    for x in (2,11): box(m,(x,10.3,12),(x+.6,14,14.5),'dark')
    m.octagon(12.8,2.6,11,13,2.3,'#white',axis='x')
    return write(m,'vacuum_sealer',['sealing_press'])

def storage_crate():
    m=station()
    for y,ox in ((0,0),(7.5,.5)):
        box(m,(1+ox,y,1),(14.5+ox,y+6.7,15),'blue')
        box(m,(.6+ox,y+6.4,.6),(14.9+ox,y+7.3,15.4),'dark')
        for x in (2.2,5.7,9.2,12.7):
            box(m,(x+ox,y+.7,.7),(x+.55+ox,y+6.2,1),'steel')
            box(m,(x+ox,y+.7,15),(x+.55+ox,y+6.2,15.3),'dark')
        for x in (1+ox,14.2+ox):
            for z in (3,6.5,10,13): box(m,(x-.2,y+.6,z),(x+.4,y+6,z+.4),'dark')
        panel(m,5+ox,y+4.7,.6,5,1,'rubber')
    panel(m,4.8,8.7,.55,6,3.1,'label')
    box(m,(1.1,14.8,1),(15.4,15.4,15),'blue')
    for z in (4,8,12): box(m,(1.4,15.4,z),(15.1,15.7,z+.45),'dark')
    return write(m,'storage_crate')

def centrifuge(working=False):
    m=station(); table(m)
    box(m,(2,10.3,2),(14,16,14),'shell')
    box(m,(2.3,10.4,1.7),(13.7,12.6,2),'dark')
    panel(m,3,10.7,1.65,5,1.5,'screen_run' if working else 'screen')
    panel(m,10,11,1.65,1,1,'led' if working else 'led_off')
    box(m,(2,15.8,2),(14,16.25,14),'rubber')
    box(m,(2,16.25,2),(14,17.1,14),'shell')
    disc(m,8,17.1,17.3,8,9,'dark',top='#rotor_spin' if working else '#rotor')
    box(m,(6,17.3,2.8),(10,17.9,3.5),'blue')
    panel(m,2.4,13,1.95,3,1.8,'label')
    for z in (4,6,8,10,12): box(m,(14,11,z),(14.04,14,z+.7),'dark')
    return write(m,'centrifuge'+('_working' if working else ''))

def still(working=False):
    m=station()
    feet(m,5)
    box(m,(1,5,1),(15,6.2,15),'steel')
    box(m,(1.5,6.2,2),(10.5,7.4,12),'dark')
    panel(m,2,6.35,1.95,3, .8,'screen_run' if working else 'screen')
    disc(m,6,7.4,8.2,7,8,'rubber')
    disc(m,6,8.2,15,7,7.4,'steel')
    for y in (8.4,14.1): disc(m,6,y,y+.6,7,7.8,'silver')
    disc(m,6,15,16,7,6,'shell')
    disc(m,6,16,24,7,2.5,'steel')
    for y in (17,20,23): disc(m,6,y,y+.5,7,3.2,'dark')
    panel(m,5.35,18,5.55,1.3,1.8,'glass')
    panel(m,4.5,11,3.05,3,2.2,'gauge')
    box(m,(5.5,23.5,6.5),(12.5,24.5,7.5),'silver')
    box(m,(11.5,15,6.5),(12.5,24,7.5),'steel')
    disc(m,12,16,22,7,2.7,'shell')
    box(m,(11.7,12.8,6.7),(12.3,16,7.3),'steel')
    disc(m,12,6.2,7,7,3.4,'blue')
    disc(m,12,7,12.5,7,3,'glass')
    disc(m,12,7.2,9,7,2.5,'mint')
    box(m,(2,9,11),(3,15,12),'orange')
    panel(m,11,3,1.35,2,1.2,'label')
    return write(m,'still'+('_working' if working else ''))

def cutting_bench():
    m=station(); table(m)
    box(m,(1,10.3,11),(15,12,11.6),'steel')
    box(m,(2,10.3,3),(10,10.6,10),'white')
    box(m,(10.5,10.3,5),(15,11.1,10),'dark')
    box(m,(11,11.1,6),(14.5,11.5,9.6),'steel')
    panel(m,11,10.45,4.95,2.5,.5,'screen_on')
    box(m,(3,10.6,4),(6.5,10.8,6),'white',rot={'origin':[4.7,10.7,5],'axis':'y','angle':22.5})
    box(m,(4,10.6,8),(9,10.85,8.6),'steel')
    box(m,(7.5,10.85,8),(9.5,11.1,8.6),'dark')
    box(m,(1.5,10.3,12),(5,13.7,14.8),'shell')
    panel(m,2,11,11.95,2.5,1.5,'label')
    return write(m,'cutting_bench')

def cloning_bench():
    m=station(); table(m,'mint')
    box(m,(1.5,10.3,4),(13,11.1,13.5),'dark')
    for x in (3.5,7,10.5):
        for z in (6,10.5):
            box(m,(x-1,11.1,z-1),(x+1,12.5,z+1),'rockwool')
            box(m,(x-.15,12.5,z-.15),(x+.15,14.1,z+.15),'leaf')
            box(m,(x-.9,13.3,z-.6),(x+.9,13.55,z+.6),'leaf',rot={'origin':[x,13.3,z],'axis':'z','angle':22.5})
    # Clear lid with bevelled shoulders; open front keeps seedlings visible at game scale.
    for x in (1.5,12.7): box(m,(x,11.1,4),(x+.3,15,13.5),'glass')
    box(m,(1.5,11.1,13.3),(13,15,13.6),'glass')
    box(m,(2.3,15,4),(12.2,15.3,13.5),'glass')
    for x in (1.5,12.2): box(m,(x,14.7,4),(x+.8,15.2,13.5),'silver')
    box(m,(6,15.3,8),(9,15.7,9),'mint')
    box(m,(2,10.3,1.8),(9,10.6,2.2),'steel')
    box(m,(2,10.3,1.7),(4,10.8,2.3),'mint')
    box(m,(13.5,10.3,10),(15,14,12),'white')
    box(m,(13.8,14,10.5),(15.2,14.5,11.2),'blue')
    return write(m,'cloning_bench',['grafting_bench'])

def cash_counter():
    m=station(); table(m,'mint')
    box(m,(1.5,10.3,5),(10.5,13,13.5),'shell')
    box(m,(2,13,9),(10,16.2,13),'dark')
    box(m,(2,13,5),(10,14.4,7),'shell')
    panel(m,3,11.3,4.95,4,1.1,'screen_on')
    box(m,(3,13.1,7),(9,13.4,9),'rubber')
    for x in (3,8): box(m,(x,13.2,7),(x+.8,14.1,9),'steel')
    box(m,(3,14,10),(9,16,12.5),'cash')
    box(m,(3,10.5,2),(9,11,4.8),'cash')
    for y in (10.3,11.8):
        box(m,(11,y,3),(15,y+1.3,8),'cash')
        box(m,(12.4,y-.02,2.98),(13.2,y+1.32,8.02),'white')
    disc(m,13,10.3,10.7,12.5,3,'dark')
    box(m,(12.7,10.7,12.2),(13.3,18,12.8),'steel')
    box(m,(10,17.5,9),(14,18.5,13),'dark')
    box(m,(10.3,17.3,9.3),(13.7,17.5,12.7),'led',shade=False)
    panel(m,2,5,1.3,3,1.5,'label')
    return write(m,'cash_counter',['counting_house'])

def modern_blockstates():
    pairs={'forcing_frame':'grow_tent','drying_loft':'drying_rack','grafting_bench':'cloning_bench','sealing_press':'vacuum_sealer','counting_house':'cash_counter'}
    for old,new in pairs.items():
        src=ASSETS/'blockstates'/(old+'.json')
        if src.exists():
            (ASSETS/'blockstates'/(new+'.json')).write_text(src.read_text().replace(old,new))
    bases={'grow_tent':'grow_tent_stage0','drying_rack':'drying_rack','cloning_bench':'cloning_bench','vacuum_sealer':'vacuum_sealer','cash_counter':'cash_counter'}
    for name,model in bases.items():
        (ASSETS/'models/item'/(name+'.json')).write_text(json.dumps({'parent':'slumdrugs:block/'+model},indent=2)+'\n')
        (ASSETS/'items'/(name+'.json')).write_text(json.dumps({'model':{'type':'minecraft:model','model':'slumdrugs:item/'+name}},indent=2)+'\n')

if __name__=='__main__':
    for s in range(5):
        grow_tent(s); grow_tent(s,False)
    for n in range(4): drying_rack(n)
    for n in range(1,4): drying_rack(n,True)
    pressing_bench(); vacuum_sealer(); storage_crate(); centrifuge(); centrifuge(True)
    still(); still(True); cutting_bench(); cloning_bench(); cash_counter()
    modern_blockstates()
    print('Wrote',len(WRITTEN),'modern models and embedded-texture Blockbench projects; legacy IDs retained for the current game build.')
