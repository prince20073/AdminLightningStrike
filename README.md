# AdminLightningStrike

Server-side Fabric mod for Minecraft Java 26.3. Clients do NOT need it.

## Command
`/strike <player>` (OP level 2+)

- Visual-only lightning every 0.5 s (10 ticks): thunder + flash, **no fire**.
- Each strike removes 1 heart of real health directly, clamped so health never drops below 2.0 HP.
- Stops automatically at exactly 2 HP. It can never kill.
- The mod never heals the target or touches max health; natural regen, food and golden apples work normally.
- While the attack runs, all other damage to the target is ignored (only an explicit `/kill` passes).
- Creative/spectator targets are refused. Disconnect/death cancels the attack.

## Build (needs Java 25 + Gradle 9.x, internet)
    gradle build
The jar is `build/libs/AdminLightningStrike-1.0.0.jar` (not the `-sources` one).

No PC? Push this folder to a GitHub repo; the included Actions workflow builds the jar
(Actions tab > latest run > Artifacts).

## Aternos install
1. Server > Software: choose **Fabric**, version **26.3**.
2. Server > Mods: upload **Fabric API 0.160.5+26.3** (or newer for 26.3) and **AdminLightningStrike-1.0.0.jar**.
3. Start the server. In the Aternos console run `op YourName`.
4. In game: `/strike PlayerName`.

## Build configuration
The workflow uses Java 25 and Gradle. Compilation and Minecraft 26.3 API compatibility still need to be confirmed by a successful Actions run.
