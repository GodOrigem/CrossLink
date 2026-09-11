# Changelog

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
