# Dreamcoinshop - Coinshop Dialog GUI

## Requirements
- **Paper 1.21.7 or newer** (the Dialog API is experimental and only exists from 1.21.7+, not just any 1.21.x build)
- Java 21
- pom.xml must have `paper-api` version `1.21.8-R0.1-SNAPSHOT` (or newer) as a dependency

## What changed in this update

### Bug fixes
- **Glow not updating on equip** - fixed. Glow (and Name Gradient) are now re-applied
  instantly the moment you click Equip/Unequip in the `/coinshop` menu, instead of only
  refreshing on next join. The old code also left you registered on the *previous* glow's
  scoreboard team when you switched colours or turned glow off, which is why it could look
  "stuck" or randomly wrong - that's fixed too (`Dreamcoinshop#refreshCosmetics`).
- **Name Gradient not always showing in tab / chat** - same root cause and same fix as above.
- **Tags now render on the right side of the name** (e.g. `Steve [VIP]: hello` instead of
  `[VIP] Steve: hello`), configurable via `settings.chat-name-format` in `config.yml`.
- The Tags menu now shows a live preview (`YourName [TAG]`) so it's obvious where the tag
  will sit before buying it.

### Perks removed (per request)
`/smithingtable`, `/craft`, `/trash`, `/anvil`, `/enderchest`, `/hat`, `/crawl`, `/spin`,
`/layback` were removed from the Perks menu entirely - you said another plugin already
handles those. Remaining perks: `/workbench`, `/repair`, `/heal`, `/feed`, `/kit` - these
still just grant a permission node, point it at whatever plugin provides the command.

### New: Spawner Sellwands
A separate category (own menu + own item, `NETHER_STAR`) from the normal chest Sellwands.
Tiers are 1.5x / 1.75x / 2x (`config.yml` -> `spawnerwands`). **This plugin only sells the
wand and gives the item** - it does not implement "right click a spawner to sell it" logic,
since you said your custom spawner plugin will handle that. The item is tagged with two
PersistentDataContainer keys your plugin can read:
- `dreamcoinshop:spawner_wand_multiplier` (Double)
- `dreamcoinshop:spawner_wand_uses` (Integer)

### New: Buy Coins dialog
A new main-menu category listing 5 real-money -> Coins packages (`config.yml` -> `buycoins`):
Rs.100->500, Rs.200->1100, Rs.300->1800, Rs.400->2600, Rs.500->3500 coins. **No payment is
processed by this plugin** - clicking a package sends the player a clickable chat link to
your store (Tebex / whatever you use). Set `store-url` per tier, or leave it blank to fall
back to `settings.store-url`.

### New: `/coinshop reload` (alias `/cs reload`)
Reloads `config.yml`, `messages.yml` and `sounds.yml` and re-applies every online player's
cosmetics - no restart needed. Requires `dreamcoinshop.admin`.

### New: `messages.yml`
Every player-facing message the plugin sends now lives here (MiniMessage formatted,
placeholders like `<player>`, `<amount>`, `<item>`). Reword anything without touching code.

### New: Orbs (second currency) + `/orbshop`
A separate currency from Coins, its own balance file (`orbs.yml`):
- Every online player automatically gets Orbs on a timer - default **1 orb every 5 minutes**,
  configurable via `config.yml` -> `orbs.interval-minutes` / `orbs.amount-per-interval`.
  Each player gets a chat message when this happens (`messages.yml` -> `orbs.received`,
  default: *"You received 1 orbs playing this server!"*).
- `/orbshop` opens a dialog menu (own title, `orbs.gui-title` in `config.yml`) listing
  purchasable items, paid for out of the Orbs balance.
- `/orb balance|give|giveall|remove|set|help` - admin command mirroring `/coin`, for
  giving/removing Orbs manually.
- **Orbshop items are placeholders for now** (`config.yml` -> `orbshop:`) - just
  `display`/`price`/`commands`. Leave `commands` empty for a pure placeholder purchase
  (deducts Orbs, sends a confirmation, gives nothing yet), or add console commands like
  `"give %player% diamond 1"` to actually hand out a reward - no code changes needed,
  it's all config.

### New: PlaceholderAPI support (optional, soft-dependency)
If PlaceholderAPI is installed, the plugin registers automatically (nothing to configure):
- `%dreamcoinshop_coins%` / `%dreamcoinshop_coins_exact%` - Coins balance
- `%dreamcoinshop_orbs%` / `%dreamcoinshop_orbs_exact%` - Orbs balance

If PlaceholderAPI isn't installed, the plugin just skips registering and works fine without it.

### Round 2 fixes (glow / tags / gradient / spawner wand / buy coins / /orbshop)
- **Glow always showing white regardless of colour picked** - the real cause: glow works by
  putting the player on a scoreboard Team and colouring that team, but the team was being
  registered on the **server's main scoreboard**. If you're running a custom tab/sidebar
  plugin (`dreamtab`) that assigns players their own `Scoreboard` object, that team is
  completely invisible to them, so the client falls back to the default (white) glow outline.
  Fixed by registering the glow team on **the player's own current scoreboard**
  (`player.getScoreboard()`) instead, plus a safety-net task that re-applies every ~5s in
  case that plugin ever resets a player's scoreboard object.
- **Tags polish** - default tag styles in `config.yml` now use bold brackets/text for a more
  premium look. Purely cosmetic, edit `tags:` in `config.yml` however you like.
- **Name Gradient showing in tab but not chat** - our chat listener now runs at `HIGHEST`
  priority so it renders last; if another plugin also sets a chat renderer, ours wins.
- **Spawner Sellwands menu** - now display-only as requested: shows the 3 tiers/prices, but
  clicking one does not charge coins or give an item (sends a "coming soon" message instead).
  Wire up the real purchase + selling logic in your own custom spawner plugin whenever ready.
- **Buy Coins redesigned** - now shows the 5 price tiers as plain info text plus two buttons:
  **Visit Store** (sends a clickable link to `settings.store-url`, now set to
  `https://store.slapmc.fun`) and **Back**.
- **`/orb` and `/coin` tab-completion** - typing `/orb ` now suggests `balance/give/giveall/
  remove/set/help`, and a player name for the ones that need it.
- **`/orbshop` removed** - per request, you're building this menu yourself in DeluxeMenus.
  The Orbs currency itself (`/orb` command, the automatic Orbs-over-time, `orbs.yml`,
  `%dreamcoinshop_orbs%`) all still work exactly the same - only the dialog GUI was removed.

### New: `sounds.yml`
A sound effect for basically every action - opening/closing menus, buying, equipping,
unequipping, selling, running out of uses, buying coins, etc. Set `settings.sounds-enabled:
false` in `config.yml` to mute the whole plugin, or blank out an individual sound's `sound:`
value in `sounds.yml` to mute just that one.

### More config options
- `settings.menu-titles` / `settings.menu-descriptions` - every screen's title and every
  main-menu button's description is now editable from `config.yml`.
- `settings.chat-name-format` - controls tag placement relative to the name in chat.
- `settings.store-url` - fallback link for the Buy Coins dialog.

## What's included (unchanged from before)
- `/coin balance|give|giveall|remove|set|help`
- `/coinshop` (alias `/cs`) - opens the native Minecraft **Dialog** menu.
  - **Chat Colour**, **Glow**, **Perks**, **Tags**, **Name Gradient**, **Sellwands**,
    **Spawner Sellwands**, **Buy Coins**.
- Every colour / glow / tag / gradient works the same way: buy once (unlocks permanently),
  click again to equip, click again to turn off and go back to vanilla default.
- `/orb balance|give|giveall|remove|set|help` and `/orbshop` - see "New: Orbs" above.

## Config files
- `config.yml` - prices, colours, gradients, perks, tags, sell prices, menu titles/descriptions.
- `messages.yml` - every chat message the plugin sends.
- `sounds.yml` - every sound effect the plugin plays.

## Notes / things you should sanity-check
- `CoinsManager` is a small standalone economy (`coins.yml`). If you already have a working
  Coins balance system elsewhere in your project, swap the internals of `CoinsManager`'s
  methods to call into it instead - the rest of the plugin only calls
  `getBalance/add/remove/setBalance`, so nothing else needs to change.
- I couldn't compile-test this here (no internet access in this environment to pull the
  Paper/Adventure dependencies), so please do a `mvn clean package` / rebuild in IntelliJ and
  fix any small signature mismatches if Paper's Dialog API changed slightly between builds -
  it's still marked **Experimental** by PaperMC.
- Sellwand selling only affects materials you've listed under `sell-prices` in `config.yml`.
- Buy Coins does not charge anyone - it only links to your store. Wire up your actual
  payment/Tebex webhook to call `/coin give <player> <amount>` (or `CoinsManager#add`
  directly) when a purchase completes.

### Round 3 fixes (stale messages.yml/sounds.yml + true double-sound cause)
- **`buycoins.tier-line` / `buycoins.visit-store` showing as "Missing message"** - same root
  cause as the Round 2 config.yml bug: your server already had an old `messages.yml` on disk
  (from before those keys existed), and the plugin was only ever creating the file if it was
  missing entirely - never adding new keys to an existing one. Fixed the same way: `messages.yml`
  and `sounds.yml` now both auto-merge in new keys on every load too (see `DefaultsMergingConfig`),
  exactly like `config.yml` already did.
- **Still hearing a double sound on every click** - the actual cause turned out to be different
  from Round 2's fix: Minecraft's own client already plays a click sound for every dialog button
  press, completely independent of anything the server sends. Our `menu-open`/`menu-back` sounds
  were both mapped to `UI_BUTTON_CLICK` - the same sound the client already plays - so every
  navigation click produced two overlapping copies of the same sound. Fixed by removing sounds
  from pure navigation entirely (opening a menu, clicking Back) - Minecraft's native click is
  the only sound for those now. Real actions (buy, equip, unequip, sell, etc) still play their
  own distinct sound since the client doesn't give feedback for those on its own.
  **Note:** `unequip` also used to be mapped to `UI_BUTTON_CLICK` (now `BLOCK_LEVER_CLICK`) -
  since that key already existed in your old `sounds.yml`, the auto-merge won't change its
  *value* (it only adds missing keys, never overwrites what you've already got) - either edit
  that one line yourself or delete `sounds.yml` and let it regenerate fresh.
