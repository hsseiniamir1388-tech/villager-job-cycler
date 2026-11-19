# Villager Job Cycler (Fabric, Minecraft 1.21.4, client-side)

Re-rolls a librarian's trades by breaking and re-placing its lectern until a wanted enchanted book
(enchantment, minimum level, maximum price) shows up. Pure client-side mod: nothing to install on the server.

## Build
Push this folder to GitHub. The workflow in `.github/workflows/build.yml` builds the jar
(Actions tab -> latest run -> Artifacts -> `villager-job-cycler`).
Local build: `gradle build` (Gradle 8.12+, JDK 21) -> `build/libs/villager-job-cycler-1.0.0.jar`.
Needs Fabric Loader 0.16.10+ and Fabric API 0.119.4+1.21.4.

## Use
1. Survival mode. Axe in main hand, lectern(s) in offhand (a stack of 2+ is best).
2. Put the villager in a cell next to the lectern spot (it must be able to reach the lectern).
3. Press `J` to open the menu: pick enchantment, min level, max price -> Add target.
4. Aim at the villager, press `K` (selects it and auto-detects a lectern nearby). If there is no lectern yet,
   aim at the block the lectern should go against and press `K` again.
5. `K` again or `ESC` stops. When a match is found the trade screen stays open.

## Safety notes
* Uses normal interaction packets, smooth camera rotation, vanilla reach, randomized delays (Speed: SAFE/NORMAL/FAST).
* Stops when you take damage, the villager is lost/too far, or trades are locked (already traded with).
* Some servers forbid any automation. Check the rules / ask staff before using it.
