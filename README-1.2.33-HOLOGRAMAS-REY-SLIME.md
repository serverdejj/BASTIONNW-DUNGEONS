# BASTION DUNGEONS 1.2.33 — Hologramas, 10 activadores, Rey Slime

### Instalación
1. Compila el proyecto con GitHub Actions (Java 21, Purpur 1.21.11).
2. Sustituye el `.jar` con el servidor apagado. NO borres `plugins/BastionDungeons/`.
3. En tu `plugins/BastionDungeons/config.yml` añade `mob-hologram` y `labyrinth.king-jump` de `src/main/resources/config.yml`. Para personalizar cada enemigo, copia sus secciones `hologram` en `labyrinth.mobs` o `skeleton-mobs`.
4. Ejecuta `/dungeon reload` para actualizar la configuración de nuevos mobs. Los hologramas actuales también leen los cambios, pero para reiniciar la habilidad del jefe inicia otra partida.

### Hologramas
- Son TextDisplay de Minecraft, no requieren FancyHolograms.
- Para una sola línea usa `lines: ['{name} &7| &c❤ {health}/{max_health}']`.
- Para dos líneas usa `lines: ['{name}', '&c❤ {health}/{max_health}']`.
- `mob-hologram` establece valores globales. Puedes sobrescribir por tipo en `labyrinth.mobs.mutant.hologram`, `.giant`, `.moon`, `.king` y `skeleton-mobs.minion.hologram`, `.knight`, `.king`.
- `line-settings.1.scale`, `line-settings.1.height` controlan la primera línea, índices desde 1.
- `background-opacity: 0` quita el fondo.
- Colores `&` y `&#RRGGBB` con degradados escritos carácter por carácter.
- Se eliminan al morir el enemigo, al terminar la instancia y al apagar el plugin.
- Nota: LibsDisguises puede mostrar **además** un nombre de jugador propio en ciertos disfraces. Si aparece duplicado, habrá que ocultar ese nombre desde la configuración de LibsDisguises; los TextDisplay son independientes.

### Rey Slime
En `labyrinth.king-jump`:
- `first-delay-seconds: 10` primer salto tras aparecer el jefe.
- `repeat-delay-seconds: 5` siguiente salto desde el aterrizaje anterior.
- `jump-velocity: 1.25` fuerza vertical del salto.
- `explosion-radius: 5.0` alcance en bloques.
- `explosion-damage: 14.0` daño base (puntos de vida) en Normal. Fácil /2, Difícil x2.
- `prevent-lethal: true` la explosión no mata directamente (deja al menos 1 punto de vida).
- `knockback`, `vertical-knockback` controlan el empuje.
- Efecto visual y sonido, sin romper bloques.

### Activadores
`/dungeon boss bloque` ahora admite hasta **10** bloques activadores. Usa `/dungeon boss list` y `/dungeon boss clear bloques` en el mapa.

### Pruebas necesarias
El código y la sintaxis del YAML están revisados, pero el funcionamiento real en Purpur/LibsDisguises y la compilación de Gradle deben comprobarse con GitHub Actions y en una partida nueva.
