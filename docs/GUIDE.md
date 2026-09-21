# SlumDrugs: a player's guide

From the first seed to the Workshop. Everything in this mod is invented: the plants, what they
do, the coin. The one real thing is that a quarter notices what you do in it.

Also available in German: [GUIDE.de.md](GUIDE.de.md).

## Your first day

You arrive with ten shillings in your purse and a journal. The journal (right-click) tells you
where you stand on the ladder and what the next step costs. Read it whenever you are unsure.

Find the trader in the village: the farmer with a name over his head. He sells seed and
compost and buys raw harvest. Buy sunleaf seed. Sunleaf forgives mistakes; the other two
plants do not.

You need two things to start, and both are open to you from the first day:

| Station | Recipe (3x3) | What it does |
| --- | --- | --- |
| Forcing frame | glass pane x3 / planks, air, planks / planks, dirt, planks | Grows one crop under glass |
| Drying loft | string x3 / log, air, log / log, air, log | Dries the harvest on three rails |

Put the frame on **farmland** if you can (rooted dirt is better, mud, podzol or moss best).
Right-click it with a seed. Five visible stages; ten minutes to ripe. The lantern inside
burns while the crop is comfortable in that spot. A dark lantern over a growing crop means
the climate is wrong for it, and the harvest will be poorer.

- **Sunleaf** wants a temperate meadow: mild warmth, a little damp.
- **Frostroot** wants cold and wet: snow, water nearby, rain.
- **Emberbloom** wants heat and dry ground: a desert, or a furnace burning next to it.

Warmth counts the biome plus anything burning within two blocks; damp counts water within two
blocks, rain, and whether the biome rains at all. `/slum frame info` at a frame tells you the
numbers if you are allowed to run it.

Right-click a growing crop with compost (up to three doses) for more and better harvest. When
it is ripe, right-click with an empty hand: raw harvest and one or two seeds drop out.

**Keep your best seed.** Each harvest returns seed near the quality of the crop it came off.
Plant the best one, every time. That is the whole long game.

## Drying and pressing

Hang raw harvest on the loft (right-click with it). Ninety seconds later it is dried; the
bundles turn brown when everything hanging is ready. Take it down with an empty hand within
three minutes of done for a small bonus. Left hanging much longer, it slowly goes to dust.

You can sell raw harvest to the trader and dried to nobody. To make product you need the
Workshop tier, which means the pressing bench. Until then, sell raw to the trader to build
your coin, or take a hint from the next section.

## Selling

There are two ways to sell product, and they are not the same.

**Regulars.** The villagers with no profession and a name are customers. Each one wants one
substance, always the same one, and has a quality they insist on. Right-click them with product
in hand: they take a handful (four units) and pay by the batch's quality and by how much the
street has already seen of it. Good goods at a fair price make them warmer over time; a devoted
regular takes a double hand at a premium. Goods below their floor cool them; cut goods make
them sick and they know why. A regular you have lost buys nothing and mentions to the Watch
that you asked.

**The broker.** The weaponsmith with a name. He trades through the ordinary trading screen, at
a fixed price for standard quality, so he cannot pay you for a good batch. He does pay more per
unit for sealed parcels, because the seal is what he is buying. He also sells the two
substances nobody grows, dear.

Every sale drains the street's demand for that substance a little; it comes back over time.
Flood the street and the prices drop everywhere at once, for you and for the broker alike.

## Coin

Twelve pence to the shilling, twenty shillings to the sovereign. Sales pay in loose coin.
Loose coin buys most things, but not everything: the healer and the magistrate keep books and
take **stamped** coin only.

The counting house desk (recipe: gold ingot, book, iron ingot / planks x3 / planks, air,
planks; open from the Backroom) turns emeralds into stamped shillings one for one, and loose
coin into stamped coin less a tenth. Sneak and click it with a coin to get change.

## The ladder

| Tier | Opens at | What it unlocks |
| --- | --- | --- |
| Hand to mouth | The start | Forcing frame, drying loft |
| Backroom | 20 units sold | Counting house desk |
| Workshop | 240 shillings earned (12 sovereigns) | Pressing bench, sealing press, storage crate, centrifuge, still, cutting bench, grafting bench |

The tiers above the Workshop wait on standing and turf, which the mod does not have yet.

## The Workshop

| Station | Recipe (3x3) | What it does |
| --- | --- | --- |
| Pressing bench | air, iron, air / iron x3 / planks, smooth stone, planks | Dried into product. Load it, pull the screw five times with an empty hand |
| Sealing press | copper, iron, honeycomb / planks x3 / planks, air, planks | Product plus honeycomb into parcels of eight under your seal. Batches of any quality pool |
| Storage crate | log, planks, log / planks, air, planks / log, planks, log | A chest, 27 slots |
| Centrifuge | copper, bucket, copper / copper, iron, copper / iron, redstone, iron | Dried into product, three quarters of the volume, stronger. Charcoal on top, coal at the side to run faster |
| Still | air, copper, air / copper, copper, glass bottle / iron, furnace, iron | Dried into essence, half the volume, hitting nearly twice as hard. Sugar on top, coal at the side |
| Cutting bench | iron, sugar, air / planks x3 / planks, air, planks | Stretches product with sugar or bone meal. More units, worse, marked. Customers notice |
| Grafting bench | flower pot, shears, flower pot / planks x3 / planks, air, planks | Crosses two seeds of one plant into one seed near their mean |

The centrifuge and the still run on their own and show it: the still's firebox glows and its
neck steams, the centrifuge's band spins. Everything else moves only when you move it.
Breaking any station drops what it held.

## Lines

Every seed carries four traits, 0 to 100: **potency** (worth and strength), **vigour**
(speed and yield), **hardiness** (how far outside its comfort it still grows) and
**subtlety** (how little a sale is noticed). Hover a seed to read them. Harvest seed drifts
a little from its parent each generation; a line that stays close to where it started for five
generations can be **named**: rename a seed of it at an anvil and plant it. A named line sells
for a tenth more to regulars who know it. Stray too far and the line is a new line, with no
name and no history.

The grafting bench crosses two seeds toward something: put one in each pot, pull, and take one
seed near the parents' mean, spread a little either way. Both parents are spent.

## The Watch

Every sale is noticed a little. Loose sales less, sealed parcels more, because a seal has a
name on it. Cut goods count double, because sick customers talk. A subtle line is noticed
less. Suspicion fades on its own, half a point a minute.

Four levels: noticed, watched, hunted, and the bell. When the bell rings you have two minutes.
Lay low until you are below hunted and the Watch loses interest; stay out and they arrive,
take every unit of contraband you carry, and if a constable is near you, take you too. The
cell is wherever you were caught, for three minutes; walk off and you are walked back. Two
sovereigns of stamped coin is bail.

A constable will take a bribe: loose coin in his hand, a point off your suspicion per
shilling, up to fifteen a time. Every bribe is a name in a book somewhere.

The constables are the armourers with names. They stir when you are watched and say so.

## Crews

The toolsmiths and masons with names belong to crews. Each crew has a standing with you, from
-100 to 100, and it decides their mood: calm, wary, demanding, hostile. Hit one and the crew
thinks less of you; kill one and the whole crew inherits his anger. Tribute, coin pressed into
a crew member's hand, buys standing back and calms the one who took it. The crews come in
opposed pairs: rising with one drops you with its rival.

## You

Product is used like food. It does what it does for a while, and it adds to your tolerance
and your dependence. Tolerance blunts the next dose; dependence brings withdrawal when you
stop. The bar at the bottom of the screen shows where you stand.

- The **remedy draught** (from the healer, the cleric with a name, five shillings) holds
  withdrawal off for five minutes and takes a little dependence and tolerance with it. It
  will not work while the last one still is.
- The healer's **treatment** (twelve shillings, stamped) takes twenty off dependence, ten off
  tolerance and holds withdrawal off for ten minutes.
- Three days without a use lowers your tolerance ceiling by ten, once per streak, down to
  fifty. That is the permanent part, and the only one.

The way out is always to stop. Nothing in the mod prevents it.

## The street talks

Pass close to anyone with a name and they may say something. What they say is what they can
see of you: a regular's loyalty, a broker's view of the street, a constable's view of you, a
crew member's mood. They are worth listening to.
