# 🐴 BetterHorses – Advanced Horse Genetics, Abilities & Upgrades

**BetterHorses** expands the vanilla mount system with **genetics, mutations, gender, growth, training, special traits, permanent upgrades, veterinary tools, virtual record books, tandem riding, and summon horns**.

The plugin is designed for survival and RPG servers while keeping most systems configurable through `config.yml` and all player-facing text editable through `language.yml`.

![1fbf8a99-e548-4b89-934b-9b5479828b7d](https://github.com/user-attachments/assets/45bc889b-419c-4bce-8957-99d857c79a00)

---

## ✨ Features

### 🧬 Genetics, Mutation & Breeding

Horses pass their base **Health, Speed, and Jump Strength** to their offspring.

A configurable mutation factor adds controlled random variation around the parents' average stats. Maximum values can be configured separately for normal horses and other supported mount types.

Purchased upgrades and training bonuses are **not genetic** and are not inherited by foals.

### 👫 Gender System

Every supported mount receives a gender when it spawns or is born.

By default:

- male and female horses can breed;
- same-gender pairs cannot breed;
- same-gender breeding can be enabled in `config.yml`;
- breeding cooldowns are configurable;
- male horses can optionally ignore the breeding cooldown.

### 🦴 Growth System

Young horses grow gradually instead of instantly changing from a foal into a full-sized adult.

You can configure:

- whether growth is enabled;
- how long it takes to reach adulthood;
- the maximum adult scale;
- which supported mount types use the growth system.

### 🎓 Horse Training

Horses can improve through normal gameplay:

- **Riding** increases movement speed;
- **Brushing** increases jump strength;
- **Feeding** increases maximum health.

Each category can be enabled, disabled, and balanced independently. Training progress is shown in the horse record book and can also be included in horse-item lore.

### ✂️ Castration System

Players obtain special glowing **Veterinary Shears** with:

```text
/horse neuter
```

To castrate a horse:

1. Hold the plugin-issued Veterinary Shears.
2. Sneak.
3. Right-click a tamed horse.

Castration:

- works regardless of the horse's gender;
- affects only the selected horse;
- is permanent;
- prevents that horse from breeding;
- is stored against the horse's permanent identity;
- can optionally be restricted to the horse owner.

Ordinary shears do nothing. The Veterinary Shears can have a configurable name, price, number of uses, and optional `CustomModelData`. When the inventory is full, the item is dropped next to the player.

### 📖 Horse Record Book & Statistics

Players can inspect a horse in two ways:

- use `/horse stats` while riding it;
- obtain a glowing Horse Record Book with `/horse book`, then Sneak + Right-click a horse.

Only plugin-issued record books work. The physical inspection book is not consumed.

The plugin opens a temporary virtual written book:

- **Page 1** — gender, health, speed, jump strength, growth, training, trait, and castration status;
- **Page 2** — passive ability, active ability used with `F`, and permanent upgrade abilities.

If a category has no ability, the book displays `None`.

The virtual book is never added to the player's inventory and disappears immediately after the interface is closed.

### 🔥 Horse Traits

Horses may be born or created with one configurable genetic trait.

#### Passive and automatic traits

- `Fireheart` — grants fire resistance to the horse and rider;
- `Feather Hooves` — gives the horse and rider slow falling;
- `Frost Hooves` — freezes water below the horse;
- `Skyburst` — launches nearby entities when the horse lands after a jump;
- `Heaven Hooves` — replaces the normal jump with a stronger forward leap and slow falling.

#### Active traits

Active traits are triggered while riding by pressing **F**, or the configured swap-hands key:

- `Hellmare` — creates a temporary trail of fire and grants fire resistance;
- `Dash Boost` — temporarily increases movement speed;
- `Kickback` — knocks nearby living entities away;
- `Ghost Horse` — temporarily makes the horse and rider invisible;
- `Revenant Curse` — temporarily punishes attackers with strong debuffs.

Trait chances, particles, durations, cooldowns, radiuses, strengths, and individual availability can be configured in `config.yml`.

### 🛠 Permanent Horse Upgrades

Permanent upgrades belong to one exact horse. They do not modify its genetic base stats and are not inherited by foals.

Players can open the upgrade guide for the horse they are currently riding:

```text
/horse upgrades
```

The virtual book contains:

- the horse's overall upgrade progress;
- a separate page for every enabled upgrade;
- descriptions and effects;
- current and maximum levels;
- money costs;
- required items;
- required `CustomModelData`;
- the command used to buy the next level.

To purchase the next level:

```text
/horse upgrade <upgrade>
```

Each level can require both Vault money and one or more item types. Item requirements can match either a normal material or an exact custom resource-pack item through `CustomModelData`.

Implemented upgrades:

- `stable_speed` — increases movement speed;
- `vitality` — increases maximum health;
- `jump_training` — increases jump strength;
- `sure_footed` — reduces or completely prevents fall damage for the horse and riders;
- `tandem_seat` — unlocks a second passenger seat;
- `horn_mastery` — reduces the summon-horn cooldown for that horse;
- `loyal_mount` — allows only the owner to ride the horse.

Upgrade transactions are validated before payment. If applying an upgrade fails, the plugin restores the withdrawn money and items.

### 👥 Tandem Riding

A horse with the `tandem_seat` upgrade can carry two players.

- The first rider controls the horse normally.
- The second player right-clicks the occupied horse.
- The second passenger uses Sneak to dismount.

The second seat uses a lightweight Bukkit/Paper implementation. Seat height, rear offset, and movement prediction can be configured to improve visual positioning without scanning the world or processing unrelated horses.

### 📯 Summon Horn

Players can bind the configured Goat Horn to a horse using **Sneak + Right-click**.

Using the bound horn calls the exact horse associated with it, including when the horse is located in an unloaded chunk.

The summon system supports:

- permanent horse and horn UUIDs;
- last known world and coordinates stored in SQLite;
- asynchronous chunk loading;
- configurable cooldowns;
- permission-based cooldown overrides;
- configurable horn uses;
- permission-based use-count overrides;
- configurable chat and action-bar notifications;
- disabled worlds;
- automatic removal of the horn after its final use;
- a message explaining that the horse needs a new horn;
- rebinding a horse to a new horn if the previous horn was lost or destroyed.

Only one horn is active for a horse at a time. Binding a new horn invalidates older horns without preventing future rebinding.

### 🐎 Supported Mount Types

The plugin is built around horses and can also be configured to support:

- skeleton horses;
- zombie horses;
- camels.

Availability of additional mount types, genetics, growth, and maximum stats can be controlled separately in `config.yml`.

### 🏳️ Language & Display Customization

All player-facing messages can be changed in `language.yml`.

Additional customization includes:

- MiniMessage and legacy color support;
- configurable horse-item lore layout;
- PlaceholderAPI processing when PlaceholderAPI is installed;
- configurable trait particles;
- configurable names and models for special tools;
- configurable descriptions, names, effects, prices, and item requirements for upgrades.

---

## ⚙️ Configuration Examples

The plugin generates a complete `config.yml`. The examples below show the most important custom systems.

### Veterinary Shears

```yaml
neutering:
  require-owner: true

  tool:
    name: "&cVeterinary Shears"
    price: 0.0
    uses: 1

    custom-model:
      enabled: false
      data: 10001
```

### Horse Record Book

```yaml
statistics:
  book:
    name: "&6Horse Record Book"
    price: 0.0

    custom-model:
      enabled: false
      data: 10002
```

### Upgrade Cost

```yaml
upgrades:
  stable_speed:
    enabled: true
    display-name: "&bStable Speed Training"
    description:
      - "&0Professional conditioning permanently increases movement speed."
      - "&8The bonus does not change genetics and is not inherited by foals."

    max-level: 3

    levels:
      1:
        money: 500.0
        effect: 0.05
        effect-description: "&0+5% movement speed"

        items:
          - material: SUGAR
            amount: 64
            custom-model-data: -1
            display-name: "&fSugar"
```

For upgrade item requirements:

```text
custom-model-data: -1
```

accepts any item of the configured material.

A value of `0` or greater requires an exact `CustomModelData` match:

```yaml
items:
  - material: PAPER
    amount: 1
    custom-model-data: 12001
    display-name: "&6Tandem Seat Kit"
```

### Tandem Seat Positioning

```yaml
upgrades:
  tandem_seat:
    passenger-offset: 0.65
    seat-height: 1.15
    movement-prediction-ticks: 1.0
```

### Summon Horn

```yaml
horse-summon:
  enabled: true
  require-owner: true
  prevent-call-with-player-passenger: true
  auto-mount: false

  cooldown-seconds: 300
  default-uses: 5

  notifications:
    mode: ACTION_BAR
    repeat-cooldown-ticks: 40

  disabled-worlds:
    - world_nether
    - world_the_end

  load-unloaded-chunk: true
  unloaded-search-radius-chunks: 1

  tracking:
    enabled: true
    update-interval-seconds: 30

  horn:
    material: GOAT_HORN
    custom-model-data: -1
```

---

## 📚 Commands

| Command | Description | Permission |
|---|---|---|
| `/horse spawn` | Spawns a BetterHorses mount from the item in the player's main hand. | `betterhorses.base` |
| `/horse stats` | Opens the statistics and abilities book for the horse currently being ridden. | `betterhorses.base` |
| `/horse book` | Gives or sells a glowing Horse Record Book. | `betterhorses.book` |
| `/horse neuter` | Gives or sells glowing Veterinary Shears. | `betterhorses.neuter` |
| `/horse upgrades` | Opens the upgrade guide for the horse currently being ridden. | `betterhorses.upgrade.use` |
| `/horse upgrade <upgrade>` | Purchases the next configured level of an upgrade. | `betterhorses.upgrade.use` |
| `/horse upgrade set <upgrade> <level>` | Sets an upgrade level without payment for the ridden horse. | `betterhorses.upgrade.admin` |
| `/horse upgrade remove <upgrade>` | Removes an upgrade from the ridden horse. | `betterhorses.upgrade.admin` |
| `/horse reload` | Reloads `config.yml` and `language.yml`. | `betterhorses.reload` |
| `/horse info` | Displays debug information when debug mode is enabled. | Debug mode only |
| `/horsecreate <health> <speed> <jump> [gender] [name] [trait] [growth] [mounttype] [neutered]` | Creates a custom BetterHorses mount item. | `betterhorses.create` |

> `/horse despawn` is intentionally no longer available. Players cannot turn a live horse back into an item through a command.

### `/horsecreate` Examples

```text
/horsecreate 40 0.25 0.8 male Zeus
```

```text
/horsecreate 40 0.25 0.8 female Flare hellmare 10 horse false
```

---

## 🔐 Permissions

| Permission | Description | Default |
|---|---|---|
| `betterhorses.base` | Allows `/horse spawn` and `/horse stats`. | Everyone |
| `betterhorses.book` | Allows obtaining and using the Horse Record Book. | Everyone |
| `betterhorses.neuter` | Allows obtaining and using Veterinary Shears. | Everyone |
| `betterhorses.upgrade.use` | Allows viewing and purchasing upgrades. | Everyone |
| `betterhorses.upgrade.admin` | Allows setting or removing upgrade levels without payment. | Operators |
| `betterhorses.create` | Allows `/horsecreate`. | Operators |
| `betterhorses.reload` | Allows `/horse reload`. | Operators |
| `betterhorses.bypass` | Bypasses owner-based riding restrictions. | Permission plugin |
| `betterhorses.summon` | Allows binding and using summon horns. | Everyone |
| `betterhorses.summoncooldown.<seconds>` | Overrides the horn cooldown. The lowest granted value wins. | Permission plugin |
| `betterhorses.summonuses.<amount>` | Overrides the number of uses given to a newly bound horn. The highest granted value wins. | Permission plugin |

Examples:

```text
betterhorses.summoncooldown.60
betterhorses.summonuses.10
```

---

## 🧩 Requirements

### Required

- **Paper 1.20.6 or newer**
- **Java 21 or newer**

Paper 1.21.x is recommended for the current development version.

### Optional Integrations

- **Vault + an economy provider**  
  Required only when Veterinary Shears, Horse Record Books, or upgrades have a money price above `0`.

- **ProtocolLib 5.3+**  
  Enables the complete Ghost Horse visual effect, including hiding equipment from viewers.

- **PlaceholderAPI**  
  Allows PlaceholderAPI placeholders to be processed inside language messages.

SQLite support for the summon-horn system is loaded automatically through the plugin's declared libraries.

---

## 🚀 Installation

1. Download the latest BetterHorses JAR from the repository or the Releases section.
2. Place the JAR in the server's `plugins/` directory.
3. Start or restart the server.
4. Configure `plugins/BetterHorses/config.yml`.
5. Edit `plugins/BetterHorses/language.yml` when custom messages or translations are required.
6. Install Vault and an economy plugin only when paid tools or upgrades are enabled.
7. Run `/horse reload` after supported configuration changes, or restart the server.

---

## 🧠 Plugin API

BetterHorses provides wrappers, factory methods, and cancellable events for integrations with other plugins.

### Create a Horse Item

```java
ItemStack horseItem = BetterHorsesAPI.createHorseItem(
        40.0,                         // health
        0.25,                         // speed
        0.8,                          // jump strength
        "male",                       // gender: male or female
        "Zeus",                       // name
        player,                       // owner
        player.getInventory(),        // target inventory
        true,                         // drop next to owner when inventory is full
        "hellmare",                   // trait, "none", or null for random
        false,                        // castrated
        10,                           // growth stage
        SupportedMountType.HORSE      // mount type
);
```

### Access a Live BetterHorses Mount

```java
BetterHorsesAPI.getBetterHorse(horse).ifPresent(betterHorse -> {
    betterHorse.setOtherAbilityLevel("stable_speed", 2);
    betterHorse.setOtherAbilityLevel("tandem_seat", 1);

    int speedLevel = betterHorse.getOtherAbilityLevel("stable_speed");
    boolean hasTandemSeat = betterHorse.hasOtherAbility("tandem_seat");
});
```

Available wrapper operations include:

- reading and changing health, speed, and jump strength;
- reading and changing traits;
- reading and changing growth stage;
- adding and removing permanent ability keys;
- reading and setting permanent ability levels.

### Events

The API includes events for:

- breeding;
- spawning;
- despawning through API/internal conversion;
- item-based neutering compatibility;
- live-entity neutering;
- trait ability activation.

---

## 🗺️ Roadmap

- [ ] Persistent cargo bags
- [ ] Strong-swimmer upgrade
- [ ] Additional configurable upgrade effects
- [ ] More mount-specific traits and animations

---

## 🙏 Credits

The tandem-passenger concept was inspired by **DualHorse** by GeeVeeDee and was adapted to the current BetterHorses architecture using Paper/Bukkit APIs.
