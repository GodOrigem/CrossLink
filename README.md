# CrossLink

Link a **Bedrock** and a **Java** account into one character: shared inventory,
ender chest, XP, pets and skin — **even with both accounts online at the same
time**.

> ### ⚠️ Status: not actively maintained
>
> This plugin was written for one specific server and is published because it
> may be useful to others. **There is no guarantee of updates, fixes or
> support.** Issues and pull requests may take a long time, or never be
> answered.
>
> The code is MIT: fork it, change it, publish your own version. If you keep an
> active fork, open an issue and I'll point people to it from here.
>
> Tested on **Paper 26.2** with **Floodgate 2.2.5** and **Geyser 2.11.2**.
> Other versions may need adjustments.

---

## Do you actually need this?

Floodgate already has native account linking, and **it is the better option if
you don't need both accounts online at once**: the Bedrock account *becomes*
the Java one, same UUID, everything shared with no plugin at all. If that fits
you, use Floodgate and ignore this project.

The catch is that "same UUID": one UUID is one session. Logging in with the
second account kicks the first with *"You logged in from another location"*.

CrossLink keeps the UUIDs separate and mirrors state between them. That's what
lets you, say, leave your character farming on the phone while playing the same
character on PC.

## Requirements

| | |
|---|---|
| Server | Paper 26.2 (or compatible) |
| Java | 25 |
| Floodgate | optional — without it, linking works through text commands only |

## Installation

1. Download `CrossLink-x.y.z.jar` from [Releases](../../releases)
2. Drop it into `plugins/`
3. Restart the server

No configuration is required. `config.yml` is generated on first boot.

## How players link

Self-service, no admin needed.

**On Bedrock**, a native form pops up on first join asking whether they want to
link. It shows **once per account**: anyone who declines is not bothered again
and only sees it again by running `/link`. An admin can reopen it with
`/crosslink resetprompt <player>`.

The flow has two steps on purpose — that's what proves the same person controls
both accounts:

```
On account A:  /link <name of account B>    → gets a 6-digit code
On account B:  /link <code>                 → linked
```

Without that cross-confirmation, anyone could link themselves to someone else's
inventory just by typing the victim's name.

| Command | What it does |
|---|---|
| `/link` | opens the form (Bedrock) or prints help (Java) |
| `/link <name>` | starts a link with that account |
| `/link <code>` | confirms a pending link |
| `/link status` | shows the current link |

### Why not Microsoft login

It would be the canonical way to prove ownership of the Java account, but it
requires registering an Azure AD application and getting access to the
Minecraft API scope — and **everyone hosting the plugin would have to register
their own**. The cross-code solves the same problem with no external
infrastructure.

## Admin commands

Require the `crosslink.admin` permission (default: op). Aliases: `/clink`,
`/plink`.

| Command | What it does |
|---|---|
| `/crosslink create <group>` | create an empty group |
| `/crosslink add <group> <player\|uuid>` | add someone to a group |
| `/crosslink remove <group> <player\|uuid>` | remove someone from a group |
| `/crosslink primary <group> <player>` | set the account that owns the data |
| `/crosslink sync <player>` | force this one as source of truth |
| `/crosslink backups <player>` | list available backups |
| `/crosslink restore <player> [file]` | restore a backup |
| `/crosslink resetprompt <player>` | make the Bedrock prompt show again |
| `/crosslink list` | list groups and members |
| `/crosslink delete <group>` | delete a group (nobody loses items) |
| `/crosslink reload` | reload config and groups |

Any number of groups, any number of members per group — it is not limited to
Bedrock/Java pairs.

### Adding an offline Bedrock account

`/crosslink add <group> .SomeBedrockName` **does not work while the player is
offline**, and that is deliberate. With `online-mode=true`, resolving a name
offline would query Mojang, where a Floodgate account does not exist: it would
return a wrong UUID and the link would silently point at nothing.

Ask them to join first, or pass the Floodgate UUID directly — those start with
`00000000-0000-0000-`.

## The primary account

Every group has a **primary account** — in practice the Java one, since it's
the only account that exists at Mojang. That's where the real playerdata lives;
the others mirror it.

Two rules follow from this, and both exist because of real bugs:

**Whoever joins adopts, never overwrites.** An earlier version treated as
"source of truth" any account whose state differed from the group state — which
always includes a freshly linked account. The result was an empty Bedrock
inventory wiping a full Java one. Now an account only becomes the source when
it changed relative to **its own previous snapshot**.

**The secondary leaves with empty playerdata.** Without that, items would exist
in two player files at once, and simply removing the plugin would leave each
account holding a copy — duplicating everything. With `clear-secondary-on-quit`
enabled, removing the plugin leaves exactly one owner.

## Backups

Before any destructive write, the previous state is saved to
`plugins/CrossLink/backups/<uuid>/<timestamp>.yml` (10 most recent by default).

```
/crosslink backups Steve
/crosslink restore Steve
/crosslink restore Steve 1757600000000.yml
```

Restoring also creates a backup, so a restore can itself be undone.

## Configuration

```yaml
sync:
  inventory: true      # main inventory + armor + offhand
  ender-chest: true
  xp: true             # level, progress and total
  pets: true           # wolf, cat, horse, parrot
  health: false        # see warning below
  food: false

safety:
  clear-secondary-on-quit: true   # prevents duplication if the plugin is removed
  backups-to-keep: 10

link:
  prompt-on-first-join: true      # automatic Bedrock form
  prompt-delay-ticks: 60
  code-timeout-seconds: 300
  copy-java-skin: true

sweep-interval-ticks: 20          # safety sweep (20 = 1 second)
save-interval-ticks: 6000
```

## Skin

On link, the Java account's skin is applied to the Bedrock account. The texture
is fetched from Mojang's session server **with its signature**
(`unsigned=false`) — without the signature the client rejects the texture and
the player shows up with the default skin.

## Pets

Wolves, cats, horses and parrots start recognising whichever account is online.
A pet has exactly one owner, so the transfer only happens when **a single group
member is online** — with both connected there is no way to decide, and nothing
changes.

## How syncing works

Two paths feed the mirroring:

1. **Events** — inventory clicks, drops, pickups, item breaks, block placement,
   eating, XP, damage. These mark who acted, and the change propagates on the
   next tick, once the effect has landed.
2. **Periodic sweep** (1s by default) — compares each online member's snapshot
   against **its own previous snapshot** and propagates whoever changed. A
   safety net for anything no event covered.

An `applying` set prevents infinite loops: while the plugin writes to someone's
inventory, the events that triggers are ignored.

Death gets its own handling. The dead player's inventory is cleared *after* the
event, so the sync is delayed by one tick — without that the items would drop
on the ground **and** stay in the mirrored inventory, duplicating.

## Known limits

**Health and food are off by default.** With `health: true`, damage on one
shows up on the other instantly — and death kills both. Turn it on only if
that's genuinely what you want.

**Conflicting simultaneous actions can lose an item.** If both accounts touch
the inventory within the same sweep interval, one change wins and the other is
lost. Alternating between accounts never shows this; both sorting chests at the
same time does. It is inherent to a shared inventory.

**Not shared:** position, dimension, advancements, potion effects, gamemode.
Each account remains its own entity in the world.

## Building

```bash
./gradlew build
```

Requires JDK 25. The jar lands in `build/libs/`.

All dependencies are `compileOnly` — nothing third-party is bundled into the
jar.

### Layout

| File | Responsibility |
|---|---|
| `CrossLinkPlugin` | lifecycle, config, wiring |
| `SyncEngine` | captures, applies, decides who is the source of truth |
| `SyncListener` | events that mark "someone acted" |
| `LinkService` | the two-step linking flow |
| `LinkGroup` / `GroupManager` | group model and persistence |
| `SharedState` | what is shared, and how it serialises |
| `BedrockUi` | native forms (only loaded when Floodgate is present) |
| `SkinService` | fetches and applies the Java account's skin |
| `PromptTracker` | who has already seen the automatic prompt |
| `LinkCommand` / `PlayerLinkCommand` | `/crosslink` and `/link` |

Source comments are in Portuguese; user-facing messages are in English.

## License

MIT. See [LICENSE](LICENSE).

Built with the help of [Claude Code](https://claude.com/claude-code).
