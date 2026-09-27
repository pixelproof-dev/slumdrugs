#!/usr/bin/env python3
"""Build the original modern pawn shop only; leaves every other structure untouched.
South-facing saved entrance, 17 x 11 x 15, ground offset 2. Vanilla blocks + one broker marker.
"""
from pathlib import Path
import json
from collections import deque
from build_structures import Build, marker, pane, stairs
import mcnbt

ROOT = Path(__file__).resolve().parent.parent

def door(b,x,y,z,facing='north'):
    for half,dy in (('lower',0),('upper',1)):
        b.set(x,y+dy,z,'dark_oak_door',{'facing':facing,'half':half,'hinge':'left','open':'false','powered':'false'})

def sign(b,x,y,z,lines,facing='north',color='yellow'):
    text={'messages':list(lines)+['']*(4-len(lines)),'color':color,'has_glowing_text':mcnbt.Int(1,'b')}
    b.set(x,y,z,'warped_wall_sign',{'facing':facing,'waterlogged':'false'},
          {'id':'minecraft:sign','front_text':text,'is_waxed':mcnbt.Int(1,'b')})

def build():
    b=Build(17,11,15)
    # Full earth footing, pavement, and enclosed air. Exterior void preserves surroundings.
    b.fill(0,0,0,16,0,14,'dirt')
    b.fill(0,1,0,16,1,14,'smooth_stone')
    b.fill(2,2,3,14,5,12,'air')
    for x in range(2,15):
        for z in range(3,13):
            b.set(x,1,z,'polished_andesite' if (x+z)%2 else 'smooth_stone')
    # Weathered brick shell with light concrete piers and a dark damp-proof course.
    b.fill(1,2,2,15,5,2,'bricks')
    b.fill(1,2,13,15,5,13,'bricks')
    for x in (1,15): b.fill(x,2,3,x,5,12,'bricks')
    for x in (1,6,10,15): b.fill(x,2,2,x,5,2,'light_gray_concrete')
    for x in range(1,16): b.set(x,2,13,'polished_deepslate')
    for z in range(3,13):
        b.set(1,2,z,'polished_deepslate');b.set(15,2,z,'polished_deepslate')
    # Two secured display windows, recessed entry and readable OPEN / trade signs.
    for a,c in ((2,5),(11,14)):
        b.fill(a,3,2,c,4,2,'light_blue_stained_glass')
        for x in range(a,c+1):
            for y in (3,4): b.set(x,y,1,'iron_bars',pane('x'))
        b.fill(a,2,1,c,2,1,'polished_deepslate_slab',{'type':'top','waterlogged':'false'})
    b.fill(7,2,2,9,4,2,'black_concrete')
    door(b,8,2,2)
    b.set(8,4,2,'light_blue_stained_glass')
    b.fill(7,2,0,9,2,1,'black_carpet')
    sign(b,7,3,1,['','BUY / SELL','TRADE / LOAN',''])
    sign(b,9,3,1,['','OPEN','9 - LATE',''],color='lime')
    # Thin striped canopy, with functional recessed entrance lighting.
    for x in range(1,16):
        b.set(x,5,1,'cyan_concrete' if x%3 else 'gray_concrete')
        b.set(x,5,0,'smooth_stone_slab',{'type':'top','waterlogged':'false'})
    for x in (6,10): b.set(x,5,1,'sea_lantern')
    # Flat roof and parapet. The big three-pixel lettering is actual geometry, not a preview overlay.
    b.fill(1,6,2,15,6,13,'gray_concrete')
    for x in range(1,16):
        b.set(x,7,13,'polished_deepslate_slab',{'type':'bottom','waterlogged':'false'})
    for z in range(2,14):
        for x in (1,15): b.set(x,7,z,'polished_deepslate_slab',{'type':'bottom','waterlogged':'false'})
    b.fill(0,6,1,16,10,1,'cyan_terracotta')
    b.fill(1,7,1,15,9,1,'black_concrete')
    letters={'P':['110','111','100'],'A':['010','101','111'],'W':['101','101','111'],'N':['101','111','101']}
    for i,ch in enumerate('PAWN'):
        for row,line in enumerate(letters[ch]):
            for col,on in enumerate(line):
                if on=='1': b.set(15-i*4-col,9-row,0,'ochre_froglight',{'axis':'z'})
    # Three golden pawn balls, a compact projecting side emblem.
    b.fill(16,6,4,16,6,8,'polished_blackstone_brick_slab',{'type':'top','waterlogged':'false'})
    for z in (4,6,8):
        b.set(16,5,z,'iron_chain',{'axis':'y','waterlogged':'false'})
        b.set(16,4,z,'gold_block')
    # Roof plant: condenser, vent grilles and duct, clear of the facade sign.
    b.fill(4,7,8,7,8,10,'light_gray_concrete')
    for x in (4,6): b.set(x,9,9,'daylight_detector',{'inverted':'false','power':'0'})
    for z in (8,9,10): b.set(3,8,z,'iron_trapdoor',{'facing':'east','half':'bottom','open':'true','powered':'false','waterlogged':'false'})
    b.fill(8,7,9,11,7,9,'smooth_stone_slab',{'type':'bottom','waterlogged':'false'})
    # Interior LED ceiling strips.
    for x in (4,8,12):
        for z in (5,9,12): b.set(x,6,z,'sea_lantern')
    # Back office partition with a usable staff door and serving window.
    b.fill(2,2,10,14,5,10,'light_gray_concrete')
    door(b,12,2,10)
    b.fill(6,3,10,9,4,10,'glass')
    sign(b,11,4,9,['','STAFF ONLY','',''],color='white')
    # Main L-shaped glass counter: broker has a clear aisle behind it and entry at east.
    b.fill(4,2,8,11,2,8,'polished_diorite')
    b.fill(4,3,8,11,3,8,'glass')
    # Open service hatch: a full two-high glass wall would block right-click trading.
    b.fill(7,3,8,8,3,8,'air')
    b.set(4,2,9,'polished_diorite'); b.set(4,3,9,'glass')
    for x in (5,8,11): b.set(x,2,8,'chiseled_quartz_block')
    # A terminal at the counter end, with a keyboard and receipt slab.
    b.set(11,4,8,'black_concrete')
    b.set(11,4,7,'stone_button',{'face':'wall','facing':'north','powered':'false'})
    # Tall reclaimed shelving and recognisable second-hand goods on the west wall.
    for z in (4,6,8):
        b.set(2,2,z,'barrel',{'facing':'east','open':'false'})
        b.set(2,4,z,'dark_oak_slab',{'type':'top','waterlogged':'false'})
    b.set(2,3,4,'note_block',{'instrument':'harp','note':'0','powered':'false'})
    b.set(2,3,6,'anvil',{'facing':'north'})
    b.set(2,3,8,'grindstone',{'face':'floor','facing':'east'})
    b.set(2,5,4,'potted_cactus')
    b.set(2,5,6,'amethyst_cluster',{'facing':'up','waterlogged':'false'})
    # East wall displays: glass sides protect goods; central aisle remains three blocks wide.
    for z,item in ((4,'amethyst_block'),(6,'jukebox')):
        b.set(14,2,z,'polished_diorite'); b.set(14,3,z,item)
        b.set(13,3,z,'glass_pane',pane('z'))
        b.set(14,4,z,'glass')
    # Window merchandise is visible from both street and sales floor.
    for x,item in ((3,'amethyst_block'),(4,'flower_pot'),(12,'jukebox'),(13,'potted_fern')):
        b.set(x,2,3,'dark_oak_slab',{'type':'top','waterlogged':'false'})
        b.set(x,3,3,item)
    # Office desk, chair, locked-looking storage, ledger shelves, and one modest loot chest.
    b.fill(5,2,12,8,2,12,'smooth_stone_slab',{'type':'top','waterlogged':'false'})
    b.set(7,3,12,'lectern',{'facing':'north','has_book':'false','powered':'false'})
    b.set(7,2,11,'dark_oak_stairs',stairs('south'))
    b.fill(2,2,11,3,3,12,'gray_concrete')
    b.set(3,3,10,'iron_trapdoor',{'facing':'south','half':'bottom','open':'true','powered':'false','waterlogged':'false'})
    b.set(14,2,12,'chest',{'facing':'north','type':'single','waterlogged':'false'},
          {'id':'minecraft:chest','LootTable':'slumdrugs:chests/pawn_shop'})
    b.set(14,3,11,'bookshelf')
    # Nobody is spawned inside furniture. Customers can approach the broker across glass.
    b.set(8,2,9,*marker('broker'))
    validate_layout(b)
    # Settlement and city placers treat SOUTH as the unrotated building frontage.
    # Author from the north for legibility, then rotate blocks and their state directions.
    turn={'north':'south','south':'north','east':'west','west':'east'}
    rotated={}
    for (x,y,z),(name,props,nbt) in b.blocks.items():
        props={turn.get(k,k):turn.get(str(v),v) for k,v in (props or {}).items()}
        if 'orientation' in props:
            props['orientation']='_'.join(turn.get(part,part) for part in props['orientation'].split('_'))
        rotated[(16-x,y,14-z)]=(name,props or None,nbt)
    b.blocks=rotated
    print(b.write('pawn_shop'))
    loot={'type':'minecraft:chest','pools':[{'rolls':{'type':'minecraft:uniform','min':2,'max':4},'entries':[
        {'type':'minecraft:item','name':'minecraft:iron_ingot','weight':5},
        {'type':'minecraft:item','name':'minecraft:gold_nugget','weight':5},
        {'type':'minecraft:item','name':'minecraft:redstone','weight':4},
        {'type':'minecraft:item','name':'minecraft:compass','weight':1},
        {'type':'minecraft:item','name':'minecraft:clock','weight':1},
        {'type':'minecraft:item','name':'minecraft:emerald','weight':2}]}]}
    p=ROOT/'neoforge/src/main/resources/data/slumdrugs/loot_table/chests/pawn_shop.json'
    p.write_text(json.dumps(loot,indent=2)+'\n')

def validate_layout(b):
    # Foot-level reachability including openable wooden doors; the broker needs two clear cells.
    def walk(x,z):
        if not (0<=x<17 and 0<=z<15): return False
        vals=[b.get(x,y,z).split(':')[-1] for y in (2,3)]
        return all(v in ('air','structure_void','jigsaw','dark_oak_door','black_carpet') for v in vals)
    todo=deque([(8,0)]);seen={(8,0)}
    while todo:
        x,z=todo.popleft()
        for q in ((x+1,z),(x-1,z),(x,z+1),(x,z-1)):
            if q not in seen and walk(*q): seen.add(q);todo.append(q)
    for spot in ((8,5),(8,7),(8,9),(12,11),(13,12)):
        assert spot in seen,('blocked route',spot)
    assert b.get(8,3,9)=='minecraft:air'
    assert b.get(8,1,9) not in ('minecraft:air','minecraft:structure_void')
    print('PASS: entrance, customer aisle, broker and office are connected; marker has headroom.')

if __name__=='__main__': build()
