# Changelog

## 1.1.0-beta.6

- **The periodic sweep no longer allocates.** It used to build a full state
  snapshot just to hash it and throw it away — 68 `ItemStack.clone()` calls per
  linked player per second, each deep copying item metadata. The hash is now
  computed directly over the live inventory. Players outside a linked group
  cost nothing either way.
- **`sync.armor` and `sync.offhand`** are now separate from `sync.inventory`,
  so you can share the backpack while keeping armour per account.
- **New config options are merged into an existing `config.yml` on startup.**
  `saveDefaultConfig()` only writes when the file is absent, so anyone updating
  the plugin never saw new options — they worked at their default value but
  stayed invisible. Existing comments and values are preserved.
- README gained badges, a Performance section explaining what the sweep
  actually costs, and the pet commands in the admin table.

## 1.1.0-beta.5

- `pets`, `claimpets` and the new `retargetpets` accept a player name, so they
  work **from the console**. The account standing next to the animal is usually
  the Bedrock one, which normally is not op and could not run them at all — the
  commands were unusable exactly where they were needed.

## 1.1.0-beta.4

- Pet ownership is now read through `getOwnerUniqueId()` where available.
  Resolving it via `getOwner()` returns an `OfflinePlayer` and can be null when
  the owner has not joined during this session — which silently skipped the
  transfer. The method is a Paper addition and does not exist in the Spigot
  API, so it goes through the compat layer.
- The world sweep iterates `getEntities()` and filters by type instead of
  relying on `getEntitiesByClass()` with an interface.
- **New: `/crosslink pets [radius]`** reports who owns each tameable around
  you, flagging any owner that is not in your group.
- **New: `/crosslink claimpets [radius]`** adopts nearby tamed animals into
  your group regardless of current owner. This exists because an animal tamed
  before the link — or while the server ran in offline mode, when the player
  had a different UUID — belongs to a UUID the group does not know, and the
  normal transfer correctly ignores it.

## 1.1.0-beta.3

- **Fixed: pets did not recognise the account that was playing.** Three
  separate faults: the transfer gave up whenever the other linked account was
  also online (so it never ran for someone playing on both), it only ran at
  join — when the player's chunks had not loaded yet, so the sweep found
  nothing — and nothing ever ran afterwards.
- Ownership now follows the **last active account** in the group, and is
  refreshed on join (after chunks load), on chunk load, and on interacting with
  the animal.

## 1.1.0-beta.2

- **Fixed: the Java skin was lost after relogging.** It was applied once, at
  link time, and nothing re-applied it — Geyser sets the Bedrock skin during
  login, so the first relog undid it. The skin is now re-applied on every join
  of the Bedrock account.
- The texture is cached in `groups.yml`, so re-applying is local and instant.
  Mojang is only queried in the background, to pick up skin changes on the Java
  account.
- **SkinsRestorer conflict handled.** Both plugins rewrite the player profile,
  which would make the skin flicker. CrossLink now detects SkinsRestorer and
  steps aside, logging why. Override with `link.skin-provider: native`.

## 1.1.0-beta.1

Widens the supported range and platforms. No behaviour changes for existing
setups — the data format is unchanged, groups and backups carry over.

- **Minecraft 1.18 → 26.2** in a single jar. Compiled to Java 17 bytecode and
  against the Spigot 1.18.2 API, so nothing newer is linked at compile time.
- **Spigot support.** Adventure components were replaced with legacy `§` codes,
  which work on every platform and version in range. Skin copying still needs
  Paper — the player profile API does not exist on Spigot, and the plugin says
  so instead of failing silently.
- **Folia support** declared and implemented via a scheduler abstraction. Folia
  removed the single main thread, so every task now goes through the right
  region scheduler. **Untested on an actual Folia server.**
- `GENERIC_MAX_HEALTH` / `MAX_HEALTH` is resolved by name at runtime — the
  constant was renamed in 1.21.3 and referencing either one directly would
  break the other end of the range.

Verified to load and enable on Paper 1.18.2 and Paper 26.2.

## 1.0.0 — first public release

Published as **CrossLink**. Everything below happened during internal
development on a private server, and is kept here because two of the entries
are data-loss bugs worth knowing about if you fork this.

### Fixed before release

- **Inventory wiped on link.** The sweep elected as source of truth any account
  whose state differed from the group state — and a freshly linked account
  always differs. An empty Bedrock inventory overwrote a full Java one. Each
  player now has its own previous snapshot, and only becomes the source when it
  changed relative to itself.
- **Duplication when removing the plugin.** Items lived in two player files at
  once. The secondary account now leaves with empty playerdata, so exactly one
  owner remains.
- **Bedrock form froze.** Labels also take an index in Cumulus responses, so
  `asInput(0)` read the label and threw `IllegalStateException`. The exception
  died inside the handler and the player never got a code.
- **Code form never showed.** It was sent at the same moment the previous form
  was closing, and the Bedrock client only displays one at a time.

### Features

- Shared inventory, ender chest and XP between linked accounts, with both
  online simultaneously
- Self-service linking with a native Bedrock form and cross-code confirmation
- Java skin copied to the Bedrock account on link
- Automatic prompt on first Bedrock join, once per account
- Primary account per group; secondaries mirror it
- Automatic backup before every destructive write, with `/crosslink backups`
  and `/crosslink restore`
- Pets recognise whichever linked account is online
- Admin commands under `/crosslink` (aliases `/clink`, `/plink`)
