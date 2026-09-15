package adris.altoclef.tasks.speedrun.testrun2;

/**
 * Gameplay advancements from Java 1.16.1 through 1.21.
 * Goals with since > running version are skipped at runtime.
 */
public final class AdvancementCatalog {

    public enum Kind {
        /** Covered by ModernSpeedrunTask / dragon route. */
        ROUTE,
        /** TaskCatalogue.getItemTask */
        ITEM,
        /** Dedicated factory in AaGrinders */
        GRIND,
        /** Needs a specialist we only attempt briefly then skip */
        HARD
    }

    public static final class Goal {
        public final String id;
        public final String title;
        public final String how;
        public final Kind kind;
        public final String item;
        public final int count;
        public final int seconds;
        public final int since;

        public Goal(String id, String title, String how, Kind kind, String item, int count, int seconds, int since) {
            this.id = id;
            this.title = title;
            this.how = how;
            this.kind = kind;
            this.item = item;
            this.count = count;
            this.seconds = seconds;
            this.since = since;
        }

        public boolean onThisVersion() {
            if (since > McCompat.gameMinor()) return false;
            if (item != null && kind == Kind.ITEM && since >= 17) {
                if (McCompat.item(item.toUpperCase()) == null) return false;
            }
            return true;
        }
    }

    private AdvancementCatalog() {}

    public static Goal[] all() {
        return ALL;
    }

    private static Goal r(String id, String title, String how) {
        return new Goal(id, title, how, Kind.ROUTE, null, 0, 45 * 60, 16);
    }

    private static Goal i(String id, String title, String how, String item, int n, int sec) {
        return new Goal(id, title, how, Kind.ITEM, item, n, sec, 16);
    }

    private static Goal g(String id, String title, String how, String item, int sec) {
        return new Goal(id, title, how, Kind.GRIND, item, 1, sec, 16);
    }

    private static Goal h(String id, String title, String how, int sec) {
        return new Goal(id, title, how, Kind.HARD, null, 0, sec, 16);
    }

    private static Goal iV(int since, String id, String title, String how, String item, int n, int sec) {
        return new Goal(id, title, how, Kind.ITEM, item, n, sec, since);
    }

    private static Goal gV(int since, String id, String title, String how, String item, int sec) {
        return new Goal(id, title, how, Kind.GRIND, item, 1, sec, since);
    }

    private static Goal hV(int since, String id, String title, String how, int sec) {
        return new Goal(id, title, how, Kind.HARD, null, 0, sec, since);
    }

    private static final Goal[] ALL = new Goal[] {
            // —— Story (16) ——
            r("story/root", "Minecraft", "Have a crafting table"),
            r("story/mine_stone", "Stone Age", "Pick up cobblestone"),
            r("story/upgrade_tools", "Getting an Upgrade", "Craft a stone pickaxe"),
            r("story/smelt_iron", "Acquire Hardware", "Smelt an iron ingot"),
            r("story/obtain_armor", "Suit Up", "Any iron armor piece"),
            r("story/lava_bucket", "Hot Stuff", "Fill a lava bucket"),
            r("story/iron_tools", "Isn't It Iron Pick", "Craft an iron pickaxe"),
            i("story/deflect_arrow", "Not Today, Thank You", "Block an arrow with a shield", "shield", 1, 180),
            r("story/form_obsidian", "Ice Bucket Challenge", "Obsidian in inventory"),
            i("story/mine_diamond", "Diamonds!", "Mine a diamond", "diamond", 1, 600),
            r("story/enter_the_nether", "We Need to Go Deeper", "Enter the Nether"),
            i("story/shiny_gear", "Cover Me With Diamonds", "Any diamond armor", "diamond_chestplate", 1, 600),
            i("story/enchant_item", "Enchanter", "Enchant any item", "enchanting_table", 1, 300),
            g("story/cure_zombie_villager", "Zombie Doctor", "Weakness potion + golden apple on zombie villager", "cure", 900),
            r("story/follow_ender_eye", "Eye Spy", "Follow an eye of ender"),
            r("story/enter_the_end", "The End?", "Enter the End portal"),

            // —— Nether (23) ——
            r("nether/root", "Nether", "Enter the Nether"),
            h("nether/return_to_sender", "Return to Sender", "Kill a ghast with its fireball", 180),
            r("nether/find_fortress", "A Terrible Fortress", "Walk into a fortress"),
            i("nether/obtain_blaze_rod", "Into Fire", "Pick up a blaze rod", "blaze_rod", 1, 600),
            i("nether/get_wither_skull", "Spooky Scary Skeleton", "Wither skeleton skull", "wither_skeleton_skull", 3, 1200),
            h("nether/uneasy_alliance", "Uneasy Alliance", "Kill a ghast in the Overworld", 180),
            i("nether/brew_potion", "Local Brewery", "Take a potion from a brewing stand", "brewing_stand", 1, 400),
            g("nether/summon_wither", "Withering Heights", "Place 4 soul sand + 3 skulls", "wither", 900),
            i("nether/create_beacon", "Bring Home the Beacon", "Place a beacon", "beacon", 1, 600),
            h("nether/create_full_beacon", "Beaconator", "Pyramid of 164 mineral blocks", 300),
            i("nether/obtain_ancient_debris", "Hidden in the Depths", "Mine ancient debris", "ancient_debris", 1, 800),
            g("nether/netherite_armor", "Cover Me in Debris", "Full netherite armor", "netherite", 1200),
            g("nether/use_lodestone", "Country Lode, Take Me Home", "Use a compass on a lodestone", "lodestone", 400),
            i("nether/obtain_crying_obsidian", "Who is Cutting Onions?", "Crying obsidian", "crying_obsidian", 1, 400),
            g("nether/charge_respawn_anchor", "Not Quite Nine Lives", "Charge a respawn anchor to max", "anchor", 400),
            g("nether/ride_strider", "This Boat Has Legs", "Ride a strider with warped fungus on a stick", "strider", 400),
            h("nether/explore_nether", "Hot Tourist Destinations", "Visit every Nether biome", 300),
            g("nether/find_bastion", "Those Were the Days", "Walk into a bastion", "bastion", 400),
            g("nether/loot_bastion", "War Pigs", "Open a bastion chest", "bastion_loot", 400),
            g("nether/distract_piglin", "Oh Shiny", "Throw gold while not wearing gold armor", "distract", 240),
            h("nether/fast_travel", "Subspace Bubble", "Nether-travel 7000 Overworld blocks", 180),
            g("nether/all_potions", "A Furious Cocktail", "Have every max-effect potion", "all_potions", 600),
            h("nether/all_effects", "How Did We Get Here?", "Every status effect at once", 120),

            // —— End (9) ——
            r("end/root", "The End", "Enter the End"),
            r("end/kill_dragon", "Free the End", "Kill the ender dragon"),
            i("end/dragon_egg", "The Next Generation", "Pick up the dragon egg", "dragon_egg", 1, 180),
            g("end/enter_end_gateway", "Remote Getaway", "Throw an ender pearl into a gateway", "gateway", 300),
            i("end/find_end_city", "The City at the End of the Game", "Walk into an end city", "elytra", 1, 600),
            i("end/elytra", "Sky's the Limit", "Find elytra", "elytra", 1, 600),
            h("end/levitate", "Great View From Up Here", "Levitate 50 blocks from a shulker", 180),
            g("end/respawn_dragon", "The End… Again…", "Place 4 end crystals on the fountain", "respawn", 300),
            i("end/dragon_breath", "You Need a Mint", "Collect dragon breath", "dragon_breath", 1, 240),

            // —— Adventure ——
            r("adventure/root", "Adventure", "Kill or be killed"),
            r("adventure/kill_a_mob", "Monster Hunter", "Kill one listed mob"),
            h("adventure/kill_all_mobs", "Monsters Hunted", "Kill every hostile type", 180),
            i("adventure/shoot_arrow", "Take Aim", "Hit a mob with a bow or crossbow", "bow", 1, 240),
            h("adventure/sniper_duel", "Sniper Duel", "Kill a skeleton from 50m", 120),
            h("adventure/bullseye", "Bullseye", "Hit a target block bullseye from 30m", 120),
            i("adventure/ol_betsy", "Ol' Betsy", "Shoot a crossbow", "crossbow", 1, 240),
            h("adventure/whos_the_pillager_now", "Who's the Pillager Now?", "Kill a pillager with a crossbow", 180),
            h("adventure/two_birds_one_arrow", "Two Birds, One Arrow", "Pierce two phantoms", 120),
            h("adventure/arbalistic", "Arbalistic", "Kill 5 unique mobs with one piercing bolt", 120),
            i("adventure/throw_trident", "A Throwaway Joke", "Throw a trident", "trident", 1, 400),
            h("adventure/very_very_frightening", "Very Very Frightening", "Strike a villager with lightning", 120),
            g("adventure/trade", "What a Deal!", "Trade with a villager", "trade", 400),
            g("adventure/summon_iron_golem", "Hired Help", "Build an iron golem", "golem", 400),
            i("adventure/sleep_in_bed", "Sweet Dreams", "Sleep in a bed", "white_bed", 1, 240),
            h("adventure/adventuring_time", "Adventuring Time", "Visit every Overworld biome", 300),
            g("adventure/honey_block_slide", "Sticky Situation", "Slide down a honey block", "honey", 240),
            g("adventure/voluntary_exile", "Voluntary Exile", "Wear an illager banner", "banner", 400),
            g("adventure/hero_of_the_village", "Hero of the Village", "Win a raid", "raid", 600),
            i("adventure/totem_of_undying", "Postmortal", "Pop a totem", "totem_of_undying", 1, 600),

            // —— Husbandry ——
            r("husbandry/root", "Husbandry", "Eat anything"),
            i("husbandry/plant_seed", "A Seedy Place", "Plant a seed", "wheat_seeds", 8, 240),
            g("husbandry/breed_an_animal", "The Parrots and the Bats", "Breed one pair", "breed", 400),
            g("husbandry/tame_an_animal", "Best Friends Forever", "Tame a wolf/cat/horse", "tame", 400),
            i("husbandry/fishy_business", "Fishy Business", "Catch a fish", "fishing_rod", 1, 300),
            i("husbandry/tactical_fishing", "Tactical Fishing", "Fish with a water bucket", "water_bucket", 1, 240),
            h("husbandry/bred_all_animals", "Two by Two", "Breed every breedable mob", 180),
            h("husbandry/complete_catalogue", "A Complete Catalogue", "Tame every cat variant", 180),
            g("husbandry/balanced_diet", "A Balanced Diet", "Eat every food type", "diet", 600),
            g("husbandry/safely_harvest_honey", "Bee Our Guest", "Bottle honey with a campfire under the nest", "honey_bottle", 300),
            g("husbandry/silk_touch_nest", "Total Beelocation", "Silk Touch a nest with 3 bees", "silk_nest", 300),
            i("husbandry/obtain_netherite_hoe", "Serious Dedication", "Netherite hoe", "netherite_hoe", 1, 800),

            // —— 1.17 Caves & Cliffs I ——
            gV(17, "husbandry/ride_a_boat_with_a_goat", "Whatever Floats Your Goat!", "Boat with a goat", "goat", 240),
            iV(17, "husbandry/wax_on", "Wax On", "Honeycomb on copper", "honeycomb", 1, 240),
            gV(17, "husbandry/wax_off", "Wax Off", "Axe a waxed copper block", "copper", 240),
            iV(17, "husbandry/axolotl_in_a_bucket", "The Cutest Predator", "Bucket an axolotl", "axolotl_bucket", 1, 300),
            gV(17, "husbandry/kill_axolotl_target", "The Healing Power of Friendship!", "Kill a mob a tamed axolotl is fighting", "axolotl_fight", 240),
            iV(17, "husbandry/make_a_sign_glow", "Glow and Behold!", "Glow ink sac on a sign", "glow_ink_sac", 1, 240),
            iV(17, "adventure/walk_on_powder_snow_with_leather_boots", "Light as a Rabbit", "Walk on powder snow in leather boots", "leather_boots", 1, 240),
            hV(17, "adventure/lightning_rod_with_villager_no_fire", "Surge Protector", "Lightning rod next to a villager", 180),
            iV(17, "adventure/spyglass_at_parrot", "Is It a Bird?", "Look at a parrot through a spyglass", "spyglass", 1, 240),
            iV(17, "adventure/spyglass_at_ghast", "Is It a Balloon?", "Look at a ghast through a spyglass", "spyglass", 1, 240),
            iV(17, "adventure/spyglass_at_dragon", "Is It a Plane?", "Look at the dragon through a spyglass", "spyglass", 1, 240),

            // —— 1.18 Caves & Cliffs II ——
            hV(18, "adventure/fall_from_world_height", "Caves & Cliffs", "Fall from world height to world depth", 180),
            hV(18, "nether/ride_strider_in_overworld_lava", "Feels Like Home", "Ride a strider on Overworld lava 50 blocks", 180),
            gV(18, "adventure/play_jukebox_in_meadows", "Sound of Music", "Jukebox in a meadow", "jukebox", 240),
            hV(18, "adventure/trade_at_world_height", "Star Trader", "Trade at build limit", 180),

            // —— 1.19 Wild ——
            gV(19, "husbandry/allay_deliver_item_to_player", "You've Got a Friend in Me", "Allay delivers an item", "allay", 300),
            gV(19, "husbandry/allay_deliver_cake_to_note_block", "Birthday Song", "Allay drops cake on a note block", "allay_cake", 300),
            iV(19, "husbandry/tadpole_in_a_bucket", "Bukkit Bukkit", "Bucket a tadpole", "tadpole_bucket", 1, 300),
            iV(19, "husbandry/froglights", "With Our Powers Combined!", "All three froglights", "ochre_froglight", 1, 400),
            gV(19, "husbandry/leash_all_frog_variants", "When the Squad Hops In", "Lead every frog variant", "lead", 300),
            gV(19, "adventure/kill_mob_near_sculk_catalyst", "It Spreads", "Kill a mob near a sculk catalyst", "sculk_catalyst", 300),
            hV(19, "adventure/avoid_vibration", "Sneak 100", "Sneak past a sculk sensor / warden", 180),

            // —— 1.20 Trails & Tales ——
            iV(20, "adventure/salvage_sherd", "Respecting the Remnants", "Brush a suspicious block", "brush", 1, 400),
            iV(20, "adventure/craft_decorated_pot_using_only_sherds", "Careful Restoration", "Pot from 4 sherds", "decorated_pot", 1, 300),
            gV(20, "adventure/trim_with_any_armor_pattern", "Crafting a New Look", "Apply any armor trim", "trim", 400),
            gV(20, "adventure/trim_with_all_exclusive_armor_patterns", "Smithing with Style", "Apply every exclusive trim", "trim_all", 300),
            iV(20, "husbandry/obtain_sniffer_egg", "Smells Interesting", "Sniffer egg in inventory", "sniffer_egg", 1, 400),
            iV(20, "husbandry/feed_snifflet", "Little Sniffs", "Feed a baby sniffer", "torchflower_seeds", 1, 300),
            iV(20, "husbandry/plant_any_sniffer_seed", "Planting the Past", "Plant a sniffer seed", "torchflower_seeds", 1, 300),

            // —— 1.20.5 / 1.21 Tricky Trials ——
            iV(21, "adventure/minecraft_trials_edition", "Minecraft: Trial(s) Edition", "Enter a trial chamber", "trial_key", 1, 400),
            gV(21, "adventure/crafters_crafting_crafters", "Crafters Crafting Crafters", "Watch a crafter craft a crafter", "crafter", 240),
            gV(21, "adventure/lighten_up", "Lighten Up", "Deoxidize a copper bulb", "copper_bulb", 240),
            iV(21, "adventure/who_needs_rockets", "Who Needs Rockets?", "Launch 8 blocks with a wind charge", "wind_charge", 1, 300),
            iV(21, "adventure/under_lock_and_key", "Under Lock and Key", "Open a vault with a trial key", "trial_key", 1, 400),
            iV(21, "adventure/revaulting", "Revaulting", "Open an ominous vault", "ominous_trial_key", 1, 400),
            hV(21, "adventure/blowback", "Blowback", "Kill a breeze with its own wind charge", 180),
            iV(21, "adventure/overoverkill", "Over-Overkill", "100 damage with a mace smash", "mace", 1, 400),
            iV(21, "adventure/brush_armadillo", "Isn't It Scute?", "Brush an armadillo", "brush", 1, 300),
            iV(21, "husbandry/remove_wolf_armor", "Shear Brilliance", "Shear wolf armor off", "wolf_armor", 1, 240),
            iV(21, "husbandry/repair_wolf_armor", "Good as New", "Repair wolf armor with scutes", "armadillo_scute", 1, 240),
            gV(21, "husbandry/whole_pack", "The Whole Pack", "Tame every wolf variant", "bone", 300),

            // —— 1.21.6 Chase the Skies / Garden Awakens leftovers ——
            iV(21, "husbandry/place_dried_ghast_in_water", "Stay Hydrated!", "Place a dried ghast in water", "dried_ghast", 1, 300),
            iV(21, "adventure/heart_transplanter", "Heart Transplanter", "Creaking heart between two pale oak logs", "creaking_heart", 1, 400),

            // —— 1.21.11 Mounts of Mayhem ——
            iV(21, "adventure/mob_kabob", "Mob Kabob", "Hit five mobs in one spear charge", "iron_spear", 1, 400),

            // —— 26.2 Chaos Cubed ——
            gV(26, "husbandry/uh_oh", "Uh Oh", "Feed TNT to a sulfur cube", "tnt", 240),
    };
}
