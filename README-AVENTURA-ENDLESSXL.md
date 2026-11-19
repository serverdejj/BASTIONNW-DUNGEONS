# BASTION DUNGEONS - Modo aventura dungeon1-endlessxl

Exclusivo para el schematic `dungeon1-endlessxl.schem`. Los mapas restantes mantienen las rondas anteriores.

## Configuración por OP dentro de una instancia del mapa

1. Entra con `/dungeon go map dungeon1-endlessxl`.
2. Colócate en el centro de cada zona y usa `/dungeon mobs radio 10`. Repite para crear zonas en el orden del recorrido.
3. Usa `/dungeon mobs list` para ver el número de zonas. `/dungeon mobs clear` elimina todas (hazlo solo si quieres rehacerlas).
4. Al final, colócate ENCIMA de cada uno de los 3 bloques deseados y usa `/dungeon boss bloque` una vez por bloque.
5. Colócate en el sitio seguro de aparición del jefe y ejecuta `/dungeon boss spawn`.
6. Comprueba con `/dungeon boss list`. Si te equivocas, `/dungeon boss clear bloques` o `/dungeon boss clear spawn`.

**Persistencia:** `plugins/BastionDungeons/adventure.yml` se crea al utilizar comandos. No borres este archivo. Las coordenadas se guardan relativas a la altura de pegado; sirven para futuras instancias del mismo schematic. Los comandos solo funcionan para OP dentro de una partida del mapa especificado.

**Durante una partida:** cuando un jugador entra en el radio de una zona, aparecen mobs MythicMobs definidos en `config.yml` (por defecto 3 por jugador). No se puede avanzar más de 15 bloques del centro del encuentro mientras haya enemigos de esa zona; al derrotarlos se libera el camino. Cada zona se activa solo una vez por instancia. Al completar todas las zonas, cualquiera de los tres bloques marca el comienzo del jefe: el jugador activador queda inmóvil 5 segundos, el jefe aparece en su spawn y empieza a perseguirlo. El límite pasa a 100 bloques respecto del spawn del jefe; salir de la zona devuelve al jugador allí. Solo la muerte del jefe dispara victoria, con 10 segundos para recoger recompensas.

**Otros mapas:** ninguna de estas zonas, bloqueos de encuentros, activadores o finales por boss afecta a otros mapas; conservan sus rondas normales, sus spawns y sus reglas YAML.

**Recomendaciones:** crea primero el spawn del boss y sus 3 activadores. Asegúrate de que todos los puntos tengan suelo firme, de que MythicMobs contenga los IDs configurados y de que el radio normal del mapa permita llegar al final. Ajusta `map-rules.maps.dungeon1-endlessxl.radius-blocks` para permitir el recorrido (por ejemplo 100, si el schematic lo requiere). Durante la exploración el radio desde el spawn es `adventure.travel-radius-blocks: 256` y no se aplica el límite superior de altura; al empezar el jefe se cambia a 100 alrededor del spawn del jefe. Los demás mapas mantienen su radio y altura normales. No reemplaces `config.yml` ni `messages.yml` existentes sin conservar tus configuraciones previas; copia los bloques nuevos al final.

**Pendiente de prueba real:** fuente preparada pero no compilada ni probada en un servidor Purpur 1.21.11. Compila en Actions y prueba con una copia del mundo antes de producción.
