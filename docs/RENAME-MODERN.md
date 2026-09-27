# Rename map — gaslight to present day

Every id the mod registers, and what it becomes under the setting in MOD-GDD.md §1. This file
is the source the rename is executed from, so it has to stay exact: an id here is the spelling
in `ModItems`, `ModBlocks`, the lang file, the texture and model paths, the recipe files and the
structure NBT palettes. A rename that misses one of those is a missing texture in a player's
hand, which is the failure that keeps happening in this project.

Ids that are already modern are listed with no new name and are deliberately left alone —
`centrifuge`, `contract`, `lockpicks`, `filler` and the like read the same in either century.

## Substances

Renaming a substance renames six ids: `seed_`, `raw_`, `dried_`, `product_`, `essence_`,
`package_`. Only the three grown ones have the first three.

| was | ships as |
| --- | --- |
| sunleaf | daybreak |
| frostroot | coldsnap |
| emberbloom | redline |
| glowcap | neon |
| sparkshard | voltage |
| nightvein | blackout |
| tidecap | riptide |
| hollowcap | flatline |

## Money

The escalation stays three-deep; it is bills now rather than struck coin. Loose and stamped
coin — the dirty and clean money of §5 — become unmarked and banded.

| was | ships as |
| --- | --- |
| coin_penny | cash_note |
| coin_shilling | cash_roll |
| coin_sovereign | cash_brick |

## Stations

| was | ships as |
| --- | --- |
| forcing_frame | grow_tent |
| drying_loft | drying_rack |
| grafting_bench | cloning_bench |
| sealing_press | vacuum_sealer |
| counting_house | cash_counter |
| still | *unchanged* |
| centrifuge | *unchanged* |
| cutting_bench | *unchanged* |
| pressing_bench | *unchanged* |
| storage_crate | *unchanged* |

## Everything else

| was | ships as |
| --- | --- |
| charcoal_screen | carbon_filter |
| solvent_spirit | solvent |
| sealing_wax | seal_film |
| seal_stamp | label_gun |
| grafting_knife | clone_cutter |
| dynamo_coil | coil_pack |
| battery_glass | battery_cell |
| arc_carbon | carbon_rod |
| lamp_oil | generator_fuel |
| charter | business_licence |
| deed | property_deed |
| writ_of_pardon | forged_id |
| evidence_sack | evidence_bag |
| bounty_poster | wanted_notice |
| carved_mask | ski_mask |
| constable_whistle | police_scanner |
| informants_ledger | informant_file |
| journal | notebook |
| fertilizer, filler, contract, remedy, lockpicks, mirror_glass, line_stabiliser, pump_valve, seed_pouch, shadow_silt | *unchanged* |

## New

| id | what it is |
| --- | --- |
| burner_phone | Points at the nearest city. The reason a city may be as rare as a stronghold without being a punishment — see MOD-GDD.md §1. |
