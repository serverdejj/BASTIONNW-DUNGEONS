# BASTION DUNGEONS 1.2.32 — vida, daño, tamaño y nombre de mobs

En `plugins/BastionDungeons/config.yml`, agrega `skeleton-mobs` y `mob-nameplate` desde `src/main/resources/config.yml` de este proyecto. Para los Slimes ya existe `labyrinth.mobs.<tipo>` con `health`, `damage`, `height-blocks`, `speed-multiplier`, `name-format`.

Los tres esqueletos se configuran individualmente en `skeleton-mobs.minion`, `.knight` y `.king` con `health`, `damage`, `scale` y `name-format`. La velocidad del caballero es opcional (`speed`).

Dificultad: FACIL multiplica vida y daño por 0.5; NORMAL por 1; DIFICIL por 2. Tamaño y velocidad no cambian. Para los esqueletos el daño configurado aplica a golpes directos; habilidades de MythicMobs y proyectiles configurados aparte podrían tener daño propio. El valor de `scale` es el atributo de la entidad; los disfraces de LibsDisguises podrían no reflejarlo exactamente.

Los nombres custom de los Slimes disfrazados pueden ser ocultados por LibsDisguises; `mob-nameplate.format` configura el nombre nativo de Bukkit, no garantiza un holograma extra. Para ello hace falta una integración adicional.

Ejecuta `/dungeon reload` después de editar. Las estadísticas nuevas se aplican a mobs que aparezcan después de la recarga; no alteran enemigos ya vivos.

No borres los archivos existentes de tus mapas, spawns ni loot.
