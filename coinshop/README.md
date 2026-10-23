# Dreamcoinshop 2.0

A cosmetics shop paid with **Coins**: chat colours, glows, tags, name gradients,
**country flags**, **22 particle effects**, perks and sellwands, plus a second currency
(**Orbs**) with its own shop. Dialog menus on Paper 1.21.6+, a chest menu everywhere else.
Both currencies can be renamed (Orbs → Shards, Gems, Tokens...), the shop can go on sale,
players can pay each other, and admins add or change items in-game.

## Runs on

| Server | Versions | Menus |
|---|---|---|
| Paper, Purpur, Leaf | 1.20 – 26.x | dialogs on 1.21.6+, chest menu below |
| Folia | 1.20 – 26.x | same (glow colours are white: Folia has no scoreboard teams) |
| Spigot / Bukkit | 1.20 – 26.x | chest menu |

Java 17+. Bedrock players (Geyser/Floodgate) get the chest menu by default.
Players on an older client through ViaVersion get the chest menu too.

## Commands

| Command | Who | What |
|---|---|---|
| `/coinshop` (`/cs`) | everyone | open the shop |
| `/coinshop flags` / `particles` | everyone | open the flags / particles list |
| `/coinshop gui` / `dialog` | everyone | open the chest menu / the dialog menu |
| `/coinshop settings` | `dreamcoinshop.settings` | change settings and prices in-game |
| `/coinshop sale <percent> <time> [sections]`, `/coinshop sale stop` | `dreamcoinshop.admin` | put the shop on sale (e.g. `/coinshop sale 20 2h`, `/coinshop sale 50 1d tags flags`) |
| `/coinshop reload` | `dreamcoinshop.admin` | reload config.yml, messages.yml, sounds.yml |
| `/orbshop` | everyone | open the Orbshop |
| `/coin balance [player]` | everyone | Coins balance |
| `/coin pay <player> <amount>` | `dreamcoinshop.pay` (everyone) | send Coins to another player (`pay:` tax, minimum, cooldown; Orbs pay is off by default) |
| `/coin give/remove/set <player> <amount>`, `/coin giveall <amount>` | `dreamcoinshop.admin` | change Coins (works for offline players and players on other servers) |
| `/orb ...` | same as `/coin` | the same for Orbs |

Permissions: `dreamcoinshop.use` and `dreamcoinshop.pay` (default: everyone),
`dreamcoinshop.settings` and `dreamcoinshop.admin` (default: op).

## Currency names

`currencies:` in config.yml renames Coins and Orbs everywhere (messages, titles, buttons,
settings). `currencies.orbs.commands: [shard]` adds `/shard` next to `/orb` (restart).

## Particles

22 effects: flame and soul trails, heart and angel halos, rainbow ring, snow and rain
clouds, music notes, enchanted, ender aura, emerald sparkle, cherry blossom, totem spiral,
witch magic, glow aura, electric, bubbles, lava drip, galaxy, angel and devil wings, sculk
souls. Each one is an item in `particles:` (price, name, icon, `effect`), so the same effect
can be sold twice. `particle-settings:` decides if others see them and how often they're drawn.

## Sales

`/coinshop sale 20 2h` or Settings → Sale: prices drop by the percent (the old price is shown
crossed out), the menus show a SALE banner and the chat announces it. A sale is saved in
the database, so every server of a network has it.

## Item editor

Settings → Edit items: add, change or delete chat colours, glows, tags, gradients,
particles, perks, sellwands, spawner wands and Orbshop items - name, price, icon (also
"use the item in your hand"), colours, tag text, effect, commands. Dialogs use text boxes;
the chest menu asks in chat.

## Chest menu layout

`gui:` in config.yml: border item (or none), main menu rows, a fixed slot for every main
menu button, the info item's slot, the section icons and the Back / Close / page icons.

## Flags

Every country (226) is for sale, 1000 coins each (`flags.price`). The equipped flag is
shown next to the tag in chat:

- Java 1.21.9+ clients on Paper see the real flag picture.
- Older clients and Spigot see the country code, e.g. `[IN]` (`flags.text-format`).
- Bedrock: the code, a flag glyph from a resource pack (`flags.bedrock: GLYPH`, works with
  the TaraashMC-Flags pack from Dreamtab), or nothing.

The Flags button on the main menu keeps changing to another country
(`flags.rotate`). The flags list has pages and a search box (dialogs). `flags.sold`
limits the countries for sale, `flags.featured` puts some first. Under the main menu the
next few flags are drawn small, so their pictures are already downloaded when the button
shows them.

## Leaderboards

Top Coins and Orbs Top (from the Orbshop) show the top 10 (`top.size`): every row has the
player's head, their equipped flag, their name and amount, and you see your own rank.
**View full leaderboard** shows the top 50 (`top.full-size`, up to 100).

## Settings in-game

`/coinshop settings`: 31 switches (every shop section on/off, chat formatting, tags and
flags in chat, chat colours, name gradient in tab / display name, the changing flag,
Orbs payout and its message, sounds, equip on purchase, purchase log, balance line,
particles, sale announcements, /coin pay and /orb pay), values (flag price, Orbs interval
and amount, flag change speed, top list sizes, click cooldown, particle speed, pay tax and
minimum, currency names, menu style, Bedrock menu style, Bedrock flags, store link), a
**price editor**, the **item editor** and the **Sale** screen. Everything is saved to
config.yml at once.

## Storage and cross-server sync

`storage.type` in config.yml:

- `SQLITE` (default) - `plugins/Dreamcoinshop/data.db`, one server, nothing to set up.
- `MYSQL` / `MARIADB` / `POSTGRESQL` / `REDIS` - a database server. Every server with the
  same database and `table-prefix` shares Coins, Orbs and unlocks, and a change on one
  server shows up on the others right away (Redis: instantly, SQL: within `sync-poll-ms`).

Balance changes are stored as "+ amount", so two servers can never overwrite each other.
The server thread never waits for the database; every change is written straight away on
the plugin's own database thread and retried if the database is briefly gone.

**Updating from 1.x:** the old `coins.yml`, `orbs.yml` and `playerdata/` are imported
into the database once and renamed to `*.imported` (kept as a backup). The old
`config.yml` is upgraded to the new layout (saved as `config-old.yml`); prices, items,
titles and the store link are kept. The India head hunt event was removed.

## PlaceholderAPI

`%dreamcoinshop_coins%`, `%dreamcoinshop_coins_exact%`, `%dreamcoinshop_orbs%`,
`%dreamcoinshop_orbs_exact%`, `%dreamcoinshop_gradient_name%` (MiniMessage, used by
Dreamtab's tab list), `%dreamcoinshop_gradient_name_legacy%`, `%dreamcoinshop_tag%`,
`%dreamcoinshop_tag_mini%`, `%dreamcoinshop_flag%` (`[IN]`), `%dreamcoinshop_flag_code%`,
`%dreamcoinshop_flag_name%`, `%dreamcoinshop_coins_formatted%` (1,234), `%dreamcoinshop_particle%`.

Leaderboards for holograms: `%dreamcoinshop_top_coins_<n>_name%`, `..._amount%`,
`..._amount_formatted%`, `..._flag%`, `..._flag_code%`, `..._flag_name%`, `..._uuid%` (same for
`top_orbs`), `%dreamcoinshop_rank_coins%`, `%dreamcoinshop_rank_orbs%`. Sale:
`%dreamcoinshop_sale_active%`, `%dreamcoinshop_sale_percent%`, `%dreamcoinshop_sale_time%`.
Names: `%dreamcoinshop_coins_name%`, `%dreamcoinshop_orbs_name%`.

If another plugin formats chat, set `chat.enabled: false` and use the placeholders there.

## Other plugins

- Sellwands keep the same item keys as 1.x (`dreamcoinshop:wand_multiplier`,
  `dreamcoinshop:wand_uses`), so wands bought before the update still work.
- Spawner Sellwands and the Orbshop run console commands (`spawnerwand-give-command`,
  `orbshop.*.commands`); if a command doesn't run, the buyer gets their money back.

## Files

- `config.yml` - everything above, plus all items and prices.
- `messages.yml` - every message and menu text (MiniMessage). New keys are added
  automatically after an update.
- `sounds.yml` - a sound for each action (`enabled: false` mutes the plugin).

## License

The license is checked against https://dreamscube.fun (product id `dreamcoinshop`). A
key from BuiltByBit is filled in automatically on first start.

## Build

```
mvn clean package
```

`target/dreamcoinshop.jar`.
