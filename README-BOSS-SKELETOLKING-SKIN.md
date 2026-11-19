# BASTION DUNGEONS — SkeletolKing con skin y habilidades

Esta versión se creó a partir del ZIP `(20)` proporcionado por el usuario.

## Instalación
1. Compilar con GitHub Actions y reemplazar solamente el JAR anterior con el servidor apagado. **Conservar** `adventure.yml`, `spawns.yml`, `mobspawns.yml`, los mapas y todos los archivos YAML existentes.
2. Añadir la sección `boss-effects:` del `src/main/resources/config.yml` al `plugins/BastionDungeons/config.yml` actual (no reemplazarlo ni duplicar encabezados).
3. Copiar **SkeletolKing.png** de `SKIN-PARA-LIBSDISGUISES/` a `plugins/LibsDisguises/skins/SkeletolKing.png` en el servidor.
4. Con LibsDisguises operativo, **registrar** la skin desde el juego/console mediante `/grabskin BastionKingSkin SkeletolKing.png` y comprobar que el comando confirme su registro. Este proceso puede depender de MineSkin y de la configuración de LibsDisguises. Si el comando no existe, consultar `/grabskin` o su documentación para la versión instalada. **Copiar el PNG por sí solo no registra la skin.**
5. Reiniciar Dungeons para que vuelva a leer su configuración. Probar primero en una partida de prueba con el jefe.

## Funciones
- El jefe de MythicMobs identificado por `SkeletonKing` o `SkeletolKing` aparece 10 bloques por encima de su punto registrado. Al aterrizar produce partículas y sonidos, sin explosión destructiva ni daño explosivo.
- Sus golpes cuerpo a cuerpo empujan hacia arriba y lejos del jefe con tiempo de espera configurable.
- Teletransporte ocasional detrás de un jugador vivo, solo si la posición es segura; se puede modificar frecuencia y probabilidad.
- Escala del mob base configurable como 3.0. La escala visual del *disfraz de jugador* depende de la versión de LibsDisguises; puede requerir ModelEngine/BetterModel si no respeta la altura.
- El disfraz usa la skin previamente registrada con el alias `BastionKingSkin`. Si LibsDisguises no está activo, el jefe no desaparece: mantiene su apariencia de MythicMob y sus habilidades.

## Limitaciones
- La skin personalizada de jugador no se puede registrar solo con un archivo de resources ni basta con subir el PNG dentro de Dungeons. LibsDisguises necesita obtener/registrar sus propiedades de skin (a veces mediante MineSkin).
- Con techos bajos, una caída desde 10 bloques puede interceptar el techo; dejar espacio sobre el spawn del jefe.
- MythicMobs puede sobreescribir atributos, escala, orientación, daño o IA; revisar su archivo de configuración para conflictos.
- Todavía **no probado dentro del servidor**; validar build de GitHub Actions y probar primero en copia/partida de prueba.
