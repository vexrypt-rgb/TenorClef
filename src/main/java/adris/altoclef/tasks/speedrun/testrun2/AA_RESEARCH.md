# 1.16.5 All Advancements — what each one needs

Run client: `:1.16.5:runClient`. This list is **1.16.5** (~80 gameplay advancements). 1.21 has more (axolotls, archaeology, etc.).

`@aa` order: dragon route → gear → magic → nether extras → farm → wander.

Legend: **BOT** = wired to a task now. **LATER** = needs a dedicated grinder.

## Story (16)

| ID | How | Bot |
|---|---|---|
| story/root | Crafting table in inventory | BOT — bootstrap |
| story/mine_stone | Cobble / blackstone | BOT — stone pick |
| story/upgrade_tools | Stone pickaxe | BOT |
| story/smelt_iron | Iron ingot | BOT — iron phase |
| story/obtain_armor | Any iron armor piece | BOT — AA GEAR |
| story/lava_bucket | Lava bucket | BOT — portal kit |
| story/iron_tools | Iron pickaxe | BOT |
| story/deflect_arrow | Block with a shield | partial — we craft shield, need a skeleton shot |
| story/form_obsidian | Obsidian in inventory | BOT |
| story/mine_diamond | Diamond | BOT — AA GEAR diamond tools |
| story/shiny_gear | Diamond armor piece | BOT — chestplate |
| story/enchant_item | Enchant anything | BOT — table + books, catalogue enchant is flaky |
| story/cure_zombie_villager | Weakness + golden apple | LATER |
| story/follow_ender_eye | Follow an eye | BOT — stronghold |
| story/enter_the_nether | Enter nether | BOT |
| story/enter_the_end | End portal | BOT |

## Nether (1.16)

| ID | How | Bot |
|---|---|---|
| nether/root | Enter nether | BOT |
| nether/return_to_sender | Kill ghast with its fireball | LATER |
| nether/find_fortress | Walk into fortress | BOT — blaze task |
| nether/obtain_blaze_rod | Rod in inventory | BOT |
| nether/get_wither_skull | Wither skeleton skull | LATER |
| nether/summon_wither | Place 4 soul sand + 3 skulls | LATER |
| nether/create_beacon | Place beacon | LATER |
| nether/create_full_beacon | Beacon on 164 mineral blocks | LATER |
| nether/brew_potion | Take a potion from a stand | BOT — brewing stand |
| nether/all_potions | Every max-effect potion | LATER (HDWGH sibling) |
| nether/all_effects | Every effect at once = HDWGH | LATER |
| nether/obtain_ancient_debris | Debris | BOT — catalogue |
| nether/netherite_armor | Full netherite | LATER (4 ingots + smithing) |
| nether/use_lodestone | Compass on lodestone | LATER |
| nether/obtain_crying_obsidian | Crying obsidian | BOT |
| nether/charge_respawn_anchor | Charge to max | LATER |
| nether/ride_strider | Warped fungus on a stick | LATER |
| nether/explore_nether | All nether biomes | LATER wander |
| nether/find_bastion | Enter bastion | LATER |
| nether/loot_bastion | Open bastion chest | LATER |
| nether/distract_piglin | Gold while unarmored gold | partial — we craft gold hat |
| nether/fast_travel | Nether travel 7km overworld | LATER |
| nether/uneasy_alliance | Kill a ghast in the overworld | LATER |

## End (9)

| ID | How | Bot |
|---|---|---|
| end/root | Enter end | BOT |
| end/kill_dragon | Dragon dies | BOT — testrun2 / beds |
| end/enter_end_gateway | Throw pearl into gateway | LATER |
| end/find_end_city | Walk into city | LATER |
| end/elytra | Pick up elytra | LATER |
| end/levitate | 50 blocks shulker levitation | LATER |
| end/respawn_dragon | Place 4 crystals | LATER |
| end/dragon_breath | Collect breath | LATER |
| end/dragon_egg | Egg in inventory | LATER (punch after kill) |

## Adventure (1.16)

| ID | How | Bot |
|---|---|---|
| adventure/root | Kill any mob or die to one | BOT — incidental |
| adventure/kill_a_mob | Kill one listed mob | BOT |
| adventure/kill_all_mobs | Every hostile type | LATER |
| adventure/shoot_arrow | Hit with bow/crossbow | LATER |
| adventure/sniper_duel | Kill skeleton 50m with projectile | LATER |
| adventure/bullseye | Target block bullseye from 30m | LATER |
| adventure/ol_betsy | Shoot a crossbow | LATER |
| adventure/whos_the_pillager_now | Kill pillager with crossbow | LATER |
| adventure/two_birds_one_arrow | Pierce 2 phantoms | LATER |
| adventure/arbalistic | Kill 5 unique mobs with one piercing shot | LATER |
| adventure/throw_trident | Throw trident | LATER |
| adventure/very_very_frightening | Lightning a villager | LATER |
| adventure/trade | Villager trade | LATER |
| adventure/summon_iron_golem | Build golem | LATER |
| adventure/sleep_in_bed | Sleep | BOT — bed in AA |
| adventure/adventuring_time | Every overworld biome | LATER |
| adventure/honey_block_slide | Slide on honey | LATER |
| adventure/voluntary_exile | Raid banner on head | LATER |
| adventure/hero_of_the_village | Win a raid | LATER |
| adventure/totem_of_undying | Pop a totem | LATER |
| adventure/honey_block_slide | Honey slide | LATER |

## Husbandry (1.16)

| ID | How | Bot |
|---|---|---|
| husbandry/root | Eat anything | BOT — food |
| husbandry/plant_seed | Plant a seed | BOT — wheat |
| husbandry/breed_an_animal | Breed one pair | LATER |
| husbandry/bred_all_animals | Two by Two | LATER |
| husbandry/tame_an_animal | Tame | LATER |
| husbandry/complete_catalogue | Every cat type | LATER |
| husbandry/fishy_business | Catch a fish | LATER |
| husbandry/tactical_fishing | Fish with a bucket | LATER |
| husbandry/balanced_diet | Eat every food | LATER |
| husbandry/safely_harvest_honey | Bottle honey, campfire under nest | LATER |
| husbandry/silk_touch_nest | Silk touch bee nest with 3 bees | LATER |
| husbandry/obtain_netherite_hoe | Netherite hoe | BOT — AA nether tab |

## What `@aa` runs today

1. `ModernSpeedrunTask` (story through dragon).
2. Iron armor set, shield, diamond pick/sword/chest, 14 obsidian.
3. Enchanting table, books, brewing stand, ender chest, bed.
4. 16 rods, 16 pearls, crying obsidian, debris, netherite ingot + hoe, gold helmet.
5. Wheat, bread, meats, hay.
6. Wander.

That is every **item-shaped** 1.16.5 advancement AltoClef already knows how to collect. The LATER rows are multi-hour specialists (raids, HDWGH, biomes, breeding book). They get their own tasks after the table loop and the 0-iron craft lock are stable.
