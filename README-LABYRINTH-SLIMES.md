# BASTION DUNGEONS 1.2.30 - THE LABYRINTH

## Cambios
- The Labyrinth funciona con encuentros progresivos y jefe como EndlessXL (sin modificar sus esqueletos).
- OP: en una instancia de the-labyrinth usa `/dungeon radio 15 oleadas 2` desde el centro de cada encuentro. `2` son **dos oleadas adicionales**, de modo que hay tres en total: 5, 10, 15.
- Si prefieres la sintaxis antigua: `/dungeon mobs radio 15` utiliza los valores predeterminados del mapa.
- Marca hasta 3 activadores finales con `/dungeon boss bloque` y posición de aparición con `/dungeon boss spawn`. Los datos se guardan en `adventure.yml` por schematic.
- Solo en dúo/equipo se traen compañeros al encuentro. El jugador que activa el encuentro nunca se teletransporta. En solitario no hay teletransporte de inicio.
- Mutant, Giant, Moon (1% por cada aparición elegida, configurable) y King, IDs de MythicMobs personalizables.
- Vida/dano normal configurables; fácil los divide por dos y difícil los duplica. Escala y multiplicador de velocidad no cambian.
- `height-blocks` ajusta la hitbox a la altura aproximada solicitada (0.75/1.5/0.9/3 bloques). Las skins/disfraces pueden tener altura visual diferente. `speed-multiplier` es un factor relativo de la velocidad base del MythicMob; no es bloques/segundo.
- Loot configurable vía `/dungeon loot add BastionSlimeMoon <porcentaje>` sosteniendo el objeto deseado. 1% es la probabilidad de aparición del Moon, NO la probabilidad de su drop legendario.
- OP: `/dungeon reload` o `/dungeons reload`, recarga config.yml, menus.yml, messages.yml, difficulties.yml y sounds.yml. No reinicia partidas.

## Instalación
1. Compila con Java 21 y la acción GitHub ya incluida en el repositorio; coloca el JAR actualizado en plugins.
2. **No borres** la carpeta `plugins/BastionDungeons/`; incorpora la sección `labyrinth:` del `src/main/resources/config.yml` a TU `config.yml` existente (Bukkit no reemplaza configs antiguos).
3. Incorpora el archivo de ejemplo de MythicMobs en `plugins/MythicMobs/Mobs/`, revisa que los cuatro IDs existan y recarga MythicMobs. Estos son mobs de SLIME provisionales: faltan las skins/modelos que enviarás.
4. Agrega `admin-reload` a `messages.yml` si deseas editar el texto del comando.
5. Entrar al schematic exacto `the-labyrinth` o `the_labyrinth`, marca zonas y jefe, luego prueba fácil/normal/difícil.
6. Para daño de habilidades de MythicMobs (en lugar de ataque cuerpo a cuerpo), ajusta los Skills de MythicMobs por dificultad; el multiplicador configurado aquí controla golpes directos. Usa MythicMobs como origen de habilidades especiales.

## Atención
- La vida de 2000 del Rey Slime usa el mecanismo actual de Dungeons para exceder la salud vanilla mediante absorción.
- Este ZIP incluye código fuente, no un `.jar` precompilado.
- Los enemigos de otros mapas permanecen sin cambios.
- Si los MythicMobs no están definidos, los encuentros NO podrán empezar correctamente. Revisa la consola.
