# BASTION DUNGEONS 1.2.39 — Dragón Rojo: entrada, BossBar y loot

Esta versión parte del ZIP `BASTIONNW-DUNGEONS-main (29).zip`.

## Funciones

- Conserva las oleadas y el countdown de 10 segundos de Netherybreis.
- Al acabar todas las zonas configuradas (o la primera si `boss-after-any-zone: true`), crea el mob MythicMobs `BastionRedDragon` **20 bloques por encima** de `/dungeon dragon spawn`, desciende durante 5 segundos, genera efectos de explosión sin destruir bloques, aplica daño de área configurable y empieza la ruta por puntos `/dungeon dragon punto add`.
- BossBar roja solo para participantes. Muestra vida real y se limpia al morir el jefe, cerrarse la instancia o apagarse el plugin.
- `/dungeon loot add BastionRedDragon <porcentaje>` usa el objeto de tu mano. `/dungeon loot list BastionRedDragon` y `/dungeon loot remove BastionRedDragon <id>`; también admite alias `dragon` para loot. Las probabilidades de la configuración de loot existente se siguen aplicando con su comportamiento previo.
- Loot físico se suelta en la posición de muerte mediante la lógica existente y se cancelan drops de entidades con `mob-drops.custom-loot-only: true`.

## Instalación segura

1. Compilar con GitHub Actions para Java 21/Purpur 1.21.11. **La compilación no ha sido ejecutada localmente por falta de dependencias disponibles**.
2. Reemplazar exclusivamente el JAR con el servidor detenido. No borrar `plugins/BastionDungeons`, `adventure.yml`, `loot.yml` ni los mundos.
3. Añadir las secciones `netherybreis.dragon.entrance` y `netherybreis.dragon.bossbar` del archivo `CONFIG_AGREGAR_DRAGON_ENTRADA_1.2.39.yml` a tu `config.yml` actual sin duplicar `netherybreis:`.
4. ModelEngine debe tener ya el blueprint V8 `bastion_red_dragon` y su paquete de recursos. El ZIP no altera el modelo.
5. Iniciar una **partida nueva** y terminar las oleadas. No requiere `/dungeon boss bloque`.

## Ajustes

- `entrance.height-above-spawn`, `entrance.descent-seconds`, `entrance.explosion-radius`, `entrance.explosion-damage`, `entrance.prevent-lethal`, `entrance.knockback`, `entrance.vertical-knockback`, `entrance.fire-particles`.
- `bossbar.title`, `bossbar.color` (RED, BLUE, GREEN, etc.), `bossbar.style` (SOLID, SEGMENTED_10, etc.). `title` admite `&` y `&#RRGGBB`, y variables `{health}` y `{max_health}`.

## Notas técnicas

- La BossBar utiliza la salud **real** de la entidad. Si aparece 1024/1024 pese a indicar 3500 en config, hay un límite de vida/atributos aplicado por el servidor; revisa logs/ajustes de atributos. No se simula un valor que no tenga realmente.
- Una hitbox 3D de ModelEngine y la capacidad de recibir golpes deben probarse en Purpur; un blueprint distinto no modifica automáticamente el atributo del mob base.
- Las partículas de fuego son visuales; no se crea fuego ni se modifican bloques.
- La altura y la trayectoria son configurables, pero evita spawn con techo de mazmorra cerrado: la entrada podría atravesarlo visualmente.
