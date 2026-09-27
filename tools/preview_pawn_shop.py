"""Pawn-shop previews, extending the general renderer for small furniture it approximates.
Does not modify the structure or the shared renderer. Text and lighting remain approximations.
"""
import render_structure as r
from PIL import Image,ImageDraw,ImageFont
tex=r.textures_for
shapes=r.cells_for

def texture(name,props):
    aliases={'polished_blackstone_brick_slab':'polished_blackstone_bricks','stone_button':'stone',
             'warped_wall_sign':'warped_planks','potted_cactus':'flower_pot','potted_fern':'flower_pot',
             'lectern':'lectern_front','grindstone':'grindstone_side'}
    if name in aliases:
        t=r.texture(aliases[name]);return t,t
    return tex(name,props)

def cells(name,props):
    full={(x,y,z) for x in range(8) for y in range(8) for z in range(8)}
    if name.endswith('_wall_sign'):
        south=props.get('facing')=='south'
        return {c for c in full if 2<=c[1]<=5 and (c[2]<=0 if south else c[2]>=7)}
    if name=='stone_button': return {c for c in full if 3<=c[0]<=4 and 3<=c[1]<=4 and (c[2]<=0 if props.get('facing')=='south' else c[2]>=7)}
    if name.startswith('potted_'): return shapes('flower_pot',{})
    if name=='daylight_detector': return {c for c in full if c[1]<3}
    if name=='grindstone': return {c for c in full if 2<=c[0]<=5 and 1<=c[2]<=6 and c[1]<6}
    if name=='lectern': return {c for c in full if c[1]>=5 or (2<=c[0]<=5 and 2<=c[2]<=5)}
    return shapes(name,props)

r.textures_for=texture;r.cells_for=cells
size,blocks=r.load(r.STRUCTURES/'pawn_shop.nbt')
for name,props in blocks.values():
    a,b=texture(name,props)
    assert a is not None or b is not None,('missing preview material',name)
for suffix,view,keep in [('front','se',blocks),('rear','nw',blocks),('interior','se',{p:v for p,v in blocks.items() if p[1]<6 and (p[2]<11 or p[1]<2)})]:
    w,h,buf=r.render(size,r.cells(keep),view=view)
    r.write_png(r.OUT/f'pawn_shop_{suffix}.png',w,h,buf)
    print('rendered',suffix)
# Presentation board, sourced from the actual generated NBT.
board=Image.new('RGB',(1600,960),(25,26,29));d=ImageDraw.Draw(board)
font=ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf',22)
heading=ImageFont.truetype('C:/Windows/Fonts/seguisb.ttf',34)
d.text((35,24),'PAWN SHOP / CUSTOM MODERN STRUCTURE',font=heading,fill=(226,226,210))
d.text((35,77),'17 x 15 footprint  |  broker + service hatch  |  furnished office  |  rooftop plant',font=font,fill=(147,177,181))
for fn,rect,title in [('front',(20,135,960,760),'STREET FRONT'),('interior',(995,180,590,600),'INTERIOR CUTAWAY')]:
    im=Image.open(r.OUT/f'pawn_shop_{fn}.png').convert('RGB')
    # Trim background to avoid the renderer's generous headroom.
    from PIL import ImageChops
    diff=ImageChops.difference(im,Image.new('RGB',im.size,im.getpixel((0,0))))
    bounds=diff.getbbox()
    if bounds: im=im.crop(bounds)
    im.thumbnail((rect[2],rect[3]),Image.Resampling.LANCZOS)
    board.paste(im,(rect[0]+(rect[2]-im.width)//2,rect[1]))
    d.text((rect[0]+15,rect[1]+rect[3]+5),title,font=font,fill=(177,193,196))
d.text((35,926),'Structure preview; Minecraft lighting and sign text are not simulated.',font=font,fill=(110,133,140))
board.save(r.OUT/'pawn_shop_overview.png')
