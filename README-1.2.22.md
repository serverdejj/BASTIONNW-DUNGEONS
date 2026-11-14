# BASTION DUNGEONS 1.2.22-dev

**Versión de código fuente, no compilada ni probada en Purpur.**

## Instalación de mapas

1. Detén el servidor y haz copia de seguridad.
2. Coloca cada mundo completo en `plugins/BastionDungeons/worlds/NOMBRE/`, con `level.dat` y `region/`.
3. El plugin crea un enlace simbólico desde la raíz del servidor al mapa. El hosting **debe permitir enlaces simbólicos**; si no los permite, verás un error en consola y el mapa no se cargará. No copies a ciegas mundos que ya existan en la raíz.
4. Inicia el servidor. Ejecuta `/dungeon worlds` para listar los mapas cargados.
5. Entra al mundo mediante el método externo que uses (no hay teletransporte automático en el plugin).
6. Desde el lugar de inicio, usa `/dungeon setspawn`. En posiciones seguras usa `/dungeon setmobspawn` (repite para varios puntos).
7. Todos los miembros de la party deben estar en el mismo mundo antes de `/dungeon go`.

**Sin schematics, FAWE, creación ni borrado de mundos temporales.** Se mantienen las oleadas, MythicMobs, recompensas, parties, cuenta regresiva y muerte con drops. Una partida por mundo a la vez. El plugin no mueve a jugadores al comenzar ni terminar. Los mundos permanecen en disco.

**Limitaciones:** Los cambios realizados en el mundo son persistentes. La protección contra romper/colocar bloques se aplica durante la partida. Se requiere acceso a mundos cargados para entrar; este plugin no incluye comando de viaje. Al iniciar el plugin, Bukkit puede necesitar que los mundos tengan formato compatible con la versión del servidor.
