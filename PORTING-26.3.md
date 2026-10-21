# Rats for Minecraft 26.3 / NeoForge 26.3.0.89 (unofficial port)

This branch (`zp5/26.3`) ports Rats to Minecraft 26.3 on stable NeoForge 26.3.0.89. The work was done for the Zoompack 5
modpack, but the branch contains **no modpack-specific changes**: the content, configs and behaviour are those of the
26.1 port it starts from. It is unofficial and not endorsed by Alexthe666 or GizmoTheMoonPig. Rats is © Alexthe666 and
contributors, licensed under the GNU LGPL v3 (`LICENSE`). This port keeps that licence, and every change here is
LGPL v3 too.

Checked on 2026-10-21 against MC 26.3, NeoForge 26.3.0.89, ModDevGradle 2.0.147 and JDK 25.

| | |
|---|---|
| Base | [JakeMalmrose/Rats](https://github.com/JakeMalmrose/Rats) `port-26.1` at `53fa767` (MC 26.1.2, NeoForge 26.1.2.22-beta), itself a 100% AI-generated port of the 1.21.1 community port (see `PORT_README.md`) |
| Jar | `build/libs/Rats-26.3-9.0.0-zp5.1.jar` (`./gradlew build`) |
| Dependency | Citadel `com.github.alexthe666:citadelneoforge:26.3-1.0.0-zp5.1` (required), from the `zp5/26.3` branch of the Citadel-NeoForge fork (astryxion's `NeoForge-26.3` plus a standalone build and three NeoForge 26.3.0.89 fixes). Build and `publishToMavenLocal` it first |

**This port was also written by AI** (Claude, Anthropic), supervised by the Zoompack 5 maintainer. Four agents each took
one area (client, worldgen and data, inventory and blocks, server), and their branches were merged and checked
together. Review it with that in mind.

## What 26.3 changed that matters here

26.1 → 26.3 is a large step, with about 4,000 vanilla source files changed. The parts that hit Rats:

- **World generation was rewritten.** Density functions moved to `levelgen.densityfunction`. Surface rules became
  material rules. `ConfiguredFeature`, `FeatureConfiguration` and `FeaturePlaceContext` are gone: features are records
  implementing `Feature` with their config inline, and `worldgen/configured_feature` became `worldgen/feature`.
  Processors and carvers are interfaces or records with `MapCodec` registries. Biome mob spawns and spawn probability
  are environment attributes.
- **Item, fluid and energy handlers.** NeoForge removed `IItemHandler`, `ItemStackHandler`, `SlotItemHandler`,
  `SidedInvWrapper`, `ItemHandlerHelper`, `IFluidHandler(Item)` and the old `FluidUtil`. Everything uses the transfer API
  (`ResourceHandler<ItemResource>`, transactions).
- **Advancements and loot.** `advancements.criterion` split into `advancements.triggers` and
  `advancements.predicates`, and triggers take a `LootItemCondition` holder. Loot conditions use `type`, and global loot
  modifiers take a single optional `condition`.
- **Rendering and client.** `PoseStack.mulPose` is now `rotate`. `Gui` became `Hud`. Screens open through
  `Minecraft.gui`. The `submitModel` arity changed, and crumbling is a separate submit. The entity and armour glint render
  types are gone. `GameRenderer` no longer holds a settable post effect. Signs are baked block models. Input uses SDL.
- **Gameplay APIs.** Block codecs, `AxeItem` and `HoeItem` are gone; tools are plain items with a
  `minecraft:block_transformer`. `ComposterBlock.add` is now the `minecraft:compostable` item component. `FlowerPotBlock`
  registers itself. `EntityType.X` became `EntityTypes.X`. `swing`, `drop` and `knockback` have new signatures.
  `isBaby()` is final. `ModConfig.Type.COMMON` became `LOCAL`.
- **`blocksMotion()` is gone.** Suffocation, the motion-blocking heightmaps, fluid flow and teleport targets read the
  `minecraft:blocks_motion` tag family.

## Removed or behaviour-changed

Nothing was stubbed out. These are the places where behaviour isn't byte-for-byte 26.1:

| What | Why | Effect |
|---|---|---|
| Ratlantis terrain layout | 26.3's noise seeding and `sloped_cheese` differ | A seed gives a different Ratlantis than on 26.1, with the same style of terrain. The ore veins were already disabled and are dropped |
| `RatsCaveCarver` | `CaveWorldCarver` is a final record | A delegate record keeps the `rats:ratlantis_caves` id. The shipped JSON uses `minecraft:cave`, so caves are unchanged |
| Silk-touch and shears loot checks (6 blocks) | They used the pre-1.20.5 `"enchantments"` predicate field, which 26.1 ignored | Fixed: cheese, gem and oratchalcum ore, marbled cheese grass, pirat leaves and garbage piles need silk touch again, as in the original mod |
| 288 item predicates with `"tag"` (recipe unlocks and others) | Ignored since 1.20.5 | Now `"items": "#tag"`, so recipe advancements unlock on the intended items |
| Biome foliage and grass colours | 26.1 used dead `*_override` keys | Ratlantis gets its custom colours back |
| 17 stonecutting recipes | The top-level `count` was ignored | Slabs give 2 again |
| Pirat log and wood stripping | `AxeItem.STRIPPABLES` is gone, and the axe no longer fires `BlockToolModificationEvent` | A `neoforge:transformables` data map entry |
| Ratlantis axe, shovel and hoe | The tool classes are gone | Plain items built with `Properties.axe/shovel/hoe`. The hoe's 3×3 tilling reads its `minecraft:block_transformer` per block |
| `DemonRat` babies | `isBaby()` is final | `canBeABaby()` returns false. Demon rats no longer save their age |
| Synesthesia shader | No settable post effect on `GameRenderer` | Rides on the local player's active post-effect list each tick |
| Glint render types | Removed in 26.3 | Mapped to vanilla's shield and trimmed-armour glints, which have the same texture and texturing. Rats' own glints are forced into the solid model phase |
| Pirat signs | 26.3 signs are block models | Blockstates and models like vanilla oak's. The 32×32 block textures were converted face by face from the old entity textures (the mapping reproduces all 12 vanilla woods pixel for pixel). The renderers only draw text, and the flat edit-screen texture is `textures/gui/signs/pirat.png` |
| Trim palettes | 26.3 reads `textures/palettes/trim/` | Moved there. The item trim permutations live in the items atlas, keyed on `minecraft:trim_base` |
| Crafting table and tamed rat capabilities | TODOs in 26.1 | Exposed again (`Capabilities.Item.BLOCK`, with the result slot below; `Capabilities.Item.ENTITY`), as in the 21.x port |
| Auto Curdler hand interaction | `IFluidHandlerItem` is gone | Same result through `ItemAccess` and `ResourceHandlerUtil.move`. It no longer loses milk when the tank only partly fits |
| `blocks_motion` | `blocksMotion()` is gone | The 47 Rats blocks that 26.1 treated as solid are in `minecraft:blocks_motion_no_leaves` |
| Dead tags | `minecraft:snow_golem_melts` and `without_patrol_spawns` are now biome attributes; `dead_bush_may_place_on`, `c:tools/bows` and `#c:shears` were renamed | Converted, so the Rats entries take effect again |

## Changes by area

**Client.** `mulPose` became `rotate` (110 sites). Screens go through `Minecraft.gui`, and the HUD through
`Minecraft.gui.hud`; the plague hearts read its fields, opened by the access transformer. The `submitModel` helper puts
the crumbling overlay in its own `order(1).submitCrumblingOverlay`, and `submitMovingBlock` takes the outline colour.
SDL `InputConstants` replaces GLFW. `getPixelARGB`, `ItemStackRenderState` in place of `ItemInHandRenderer`, the name tag
distance attribute and the `ParticleRenderType` shorthand round out the changes. The Citadel bridge is unchanged.

**Worldgen and data.** Rats' features (`RatlantisRuinFeature`, `MarblePileFeature`), the copy-state processor and the
cave carver are 26.3 records and interfaces, with their codecs in `worldgen/feature_type`, `worldgen/structure_processor`
and `worldgen/carver_type`. `RatsFeatureContext` replaces `FeaturePlaceContext`. The JSON was converted with The
Bumblezone's 26.3 migration scripts plus two Rats-specific ones in `scripts/port-26.3/`:

| Script | What it converted |
|---|---|
| `criteria-migrate.py` | 354 advancements, 117 loot tables, 3 loot modifiers |
| `features-migrate.py` | 9 features |
| `structures-migrate.py` | 12 block states, 1 spawn override |
| `rats-dim-migrate.py` (new) | Noise settings to 26.3 density functions, aquifers and `material_rule/ratlantis_noise_gen`; the biome to attributes; the carver |
| `rats-data-fixups.py` (new) | Item-predicate tags, enchantment predicates, stonecutting counts, biome-modifier counts, trim palettes, advancement icons |

The datagen bootstraps were ported so they compile and mirror the JSON. They are still excluded from the build.

**Inventory, transfer and blocks.**
- **Crafting table.** Its buffer, matrix and result handlers are `RatsItemStackHandler`, an `ItemStacksResourceHandler`
  that keeps the old stack helpers (`getStackInSlot`, `setStackInSlot`, `insertItem`, `extractItem`, `insertItemStacked`).
  Its menu slots use `RatsHandlerSlot`, a vanilla `Slot`, because NeoForge's `ResourceHandlerSlot` makes
  `set`/`setChanged` final.
- **Capabilities.** `RatsCapabilities` exposes the crafting table, the tamed rat, the quarry, the curdler and the
  combiner (`WorldlyContainerWrapper`, created lazily), the curdler tank, and the cage wheel's energy.
- **Auto Curdler.** It drains milk containers through `Capabilities.Fluid.ITEM`.
- **Signatures.** Blocks lost `codec()`. `BonemealableBlock` takes a `BonemealSource`, and `Player.drop` takes a
  `Prediction`.

**Server.**
- **Entity API renames.** `swing`/`drop` take `SwingAnimation`/`Prediction`; `knockback` takes a damage source;
  invulnerability and swing state sit behind accessors; `EntityTypes.*`; the PushReaction values were renamed.
- **Tools, compost and pots.** Tools are plain items with block transformers. Compost chances are set in
  `ModifyDefaultComponentsEvent`. Flower pots register themselves.
- **Triggers and loot.** The Black Death trigger lives in `advancements.triggers`. The add-item loot modifier takes one
  optional condition.
- **Mixins.** Both mixin targets (`PiglinAi.isWearingSafeArmor`, `Warden.canTargetEntity`) are unchanged.

**Build.**
- MC 26.3, NeoForge 26.3.0.89 and ModDevGradle 2.0.147. javac runs with `-XDshould-stop.ifError=FLOW`.
- The access transformer lost the entries whose targets are gone (`ComposterBlock.add`, `AxeItem.STRIPPABLES`,
  `NoiseRouterData.SLOPED_CHEESE_AMPLIFIED`, the four-int `Model.renderToBuffer`). It moved the heart fields from `Gui`
  to `Hud` and points `NoiseRouterData.getFunction` at the new package.
- `neoforge.mods.toml` requires `[26.3,)` for Minecraft, NeoForge and Citadel.

## Verification (2026-10-21)

- `./gradlew build` passes. There are no GameTests in this mod.
- **Dedicated server (`runServer`), Rats and Citadel only, default config (Ratlantis datapack off):**
  - It boots with no errors.
  - Every living Rats entity and mount summons and ticks for 20 seconds without an error: all of rats, Plague, Pied
    Piper, Black Death, Rat King, demon rats, every Ratlantis mob and boss, all six mounts, the Rattling Gun and the
    Pirat Boat.
  - The rat, Black Death, Rat King and Pied Piper loot tables roll.
- **Dedicated server with the Ratlantis datapack on:**
  - It boots with no codec, datapack, recipe, loot or registry errors, and loads 2,263 advancements.
  - `rats:ratlantis` generates, read back block by block over RCON: ocean at sea level 63, sand, dirt and stone, a
    bedrock floor, and jungle islands up to y≈90 with cheese ore and vanilla ores.
  - `locate` finds `baron_runway` and `dutchrat_ship`.
  - `place structure` works for both, and `place template rats:plague_doctor_hut` works.
  - Every Ratlantis feature places.
  - Chunk-generation spawns come from the new biome attributes.
  - The pet shop and Dutchrat ship chest loot rolls.
- **Static checks on the data.**
  - A key-path diff against vanilla 26.3's data, by type, finds no unexplained paths.
  - Every referenced id and tag resolves, and all JSON parses.
- **Not verified:**
  - The client: every renderer, the GUIs, signs, glints, trims, the synesthesia shader and the plague hearts were only
    compiled.
  - Natural spawning, taming, rat jobs and upgrades, the crafting table GUI, hoppers or pipes reaching the machines, the
    portal and loading a 26.1 world.
