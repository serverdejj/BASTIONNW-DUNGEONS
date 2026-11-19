# BASTION DUNGEONS - mapas OP y control de mobs (Minecraft 1.21.11)

## Selección manual OP
- `/dungeon go` - mantiene el mapa aleatorio para jugadores.
- `/dungeon go map` - OP: muestra los mapas `.schem` disponibles.
- `/dungeon go map <nombre>` - OP: selecciona el mapa e inicia la votación de dificultad y la cuenta regresiva normales.
- `/dungeon go <nombre>` - forma abreviada para OP.
- El OP debe ser líder de su party.
- Solo se admiten mapas existentes en `plugins/BastionDungeons/schematics/`.
- La selección manual permite al OP iniciar fuera del horario, sin quitar los controles normales de partidas duplicadas.
- La selección NO cambia el mapa aleatorio de otros jugadores ni el flujo de votación.
- Mensajes de selección editables en `messages.yml`.

## Mobs
- En mundos de instancia creados por BastionDungeons se desactiva la aparición natural.
- Se cancelan otras apariciones de criaturas, salvo invocaciones CUSTOM hechas por el propio plugin mediante MythicMobs.
- No afecta a survival/spawn/otros mundos.
- Opciones: `dungeon-mobs.block-natural-spawns` y `dungeon-mobs.allow-other-custom-spawns`.
- Si un jefe de MythicMobs necesita invocar minions con habilidades internas fuera del spawn del plugin, hay que adaptar la excepción; por defecto se bloquean para cumplir el requisito de solo mobs configurados.

## Instalación
1. Haz una copia del JAR anterior y de `plugins/BastionDungeons/`.
2. Sube los archivos de este proyecto al repositorio de construcción correcto.
3. Ejecuta GitHub Actions / Build y descarga el JAR del artifact.
4. Apaga el servidor, reemplaza el JAR y vuelve a encenderlo.
5. Si conservas config.yml/messages.yml anteriores, agrega las claves nuevas siguiendo los YAML incluidos; los mensajes tienen fallback.

**No se ha probado este cambio en el servidor Minecraft; requiere compilación y prueba.**
