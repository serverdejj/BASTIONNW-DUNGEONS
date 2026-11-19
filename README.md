# BASTION DUNGEONS 1.2.5-dev

Código fuente experimental para Purpur 1.21.11 + WorldEdit/FAWE + MythicMobs.

## Cambios

- `/dungeon go`: cuenta de 5 a 0 en lobby, mensaje MAZMORRAS con degradado HEX y mensaje ¡Todo Listo! antes del teletransporte.
- Cuenta de preparación en la mazmorra de 5 a 0, con títulos, sonidos y jugadores inmóviles.
- Cuenta de 5 a 0 antes del SkeletonKing.
- Al derrotar al jefe: título ¡VICTORIA!, sonido, espera de 5 segundos y retorno al lobby.
- Al terminar una partida se liberan las asociaciones de party para poder volver a invitar sin reiniciar.
- Los mundos temporales `bd_XXXXXXXX` se descargan y borran después de vaciarse, con reintentos y verificación del nombre.
- Al morir un enemigo: mensaje HEX `¡Uno menos!, ¡Quedan 9/10 enemigos!.` (contador dinámico).
- Mantiene `/dungeon setspawn`, oleadas de 10 mobs y enemigos cercanos.

## Instalación

Sube el contenido del ZIP al repositorio, conservando las rutas, y compila mediante GitHub Actions. Haz copia de seguridad antes de reemplazar el JAR. **No se ha comprobado en un servidor de Minecraft real**.

## Nota

El borrado automático solo afecta a mundos temporales nuevos administrados por esta ejecución del plugin. Los directorios `bd_` de ejecuciones anteriores deben revisarse por separado y borrarse solo con el servidor apagado y copia de seguridad.
