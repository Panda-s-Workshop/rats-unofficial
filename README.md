# Rats: Unofficial 26.3 Port

> **This is not the official Rats mod.** It's an unofficial port to Minecraft 26.3 (NeoForge 26.3.0.89), made with
> the help of AI. The Rats team didn't make, check or support it. It's published with the Rats maintainer's
> permission, only for Minecraft versions the official mod doesn't support. When an official build exists for a
> version, use that one.
>
> Report problems with this port on [this repo's issues](https://github.com/Panda-s-Workshop/rats-unofficial/issues),
> not to the Rats team.

[Rats](https://github.com/AlexModGuy/Rats) is by AlexThe666 and GizmoTheMoonPig. The official mod was last released
for Minecraft 1.20.1. This port carries it to 26.3:

- Rats, tamed rats and their upgrades, the Cheese Staff, Rat Tubes, the Plague and Ratlantis.
- [Citadel](https://github.com/zoom-packers/Citadel-NeoForge/tree/zp5/26.3), which Rats needs, has no public 26.3
  build. Its 26.3 port is bundled inside the jar (jar-in-jar). A newer Citadel in your mods folder takes its place.

## Where it comes from

The port builds on several earlier ones:
- DearDanielr's and eba67's 1.21.1 fixes ([eba67/Rats-Fix-Port--1.21.1](https://github.com/eba67/Rats-Fix-Port--1.21.1)).
- JakeMalmrose's AI-written 26.1 port ([JakeMalmrose/Rats `port-26.1`](https://github.com/JakeMalmrose/Rats/tree/port-26.1),
  see [PORT_README.md](PORT_README.md)).
- Zoompack 5's 26.3 changes ([zoom-packers/Rats `zp5/26.3`](https://github.com/zoom-packers/Rats/tree/zp5/26.3), see
  [PORTING-26.3.md](PORTING-26.3.md)).

Most of the code changes in this line were written with AI and checked by people playing it.

## Download

[CurseForge](https://www.curseforge.com/minecraft/mc-mods/rats-unofficial-26-3-port) (NeoForge, Minecraft 26.3).

## Build

```bash
./gradlew build
```

The jar lands in `build/libs/`. Releases are built and uploaded to CurseForge by the Release workflow, when a `v*` tag
is pushed.

## License

[LGPL-3.0](LICENSE), like Rats and Citadel.
