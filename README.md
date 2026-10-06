# Spotlight

Overlays and sound alerts for pickpocketing Vyres in Darkmeyer, built for running several accounts at once. No automation - display only.

- **Blackout** - blacks out the game view except what needs clicking: valuable drops, the altar (and door) when prayer is low, the bank (and door) when 2 or fewer slots are left. Full blackout when prayer is off, you are "too AFK" (vyre noble clothes on / auto-retaliate off), or your Strength boost has run out.
- **Altar bar** - prayer bar that flashes below a set threshold.
- **Warnings** - big flashing text for full inventory, a blood shard nearby, too AFK, drink Strength.
- **Sounds** - place your own `.wav` files in `%userprofile%\.runelite\spotlight\`: `shard.wav`, `onyx.wav`, `yoink.wav` (someone else's shard), `prayer.wav`, `health.wav`, `regularDrop.wav`.
- **Panels** - big RSN, slots left, session profit and GP/hr, nearby valuable drops, alch-item counter, prayer time remaining.
- **Account tracker** - each client writes its status to `.runelite\spotlight\`; a tracker window shows all accounts, and any account low on prayer can show the altar on every client.

Build: `./gradlew jar` -> `build/libs/spotlight.jar`, copy into `.runelite/sideloaded-plugins`.
