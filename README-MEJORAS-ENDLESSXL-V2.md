# BASTION DUNGEONS 1.2.28 – Mejoras en dungeon1-endlessxl

Proyecto fuente para compilar con GitHub Actions; no es el JAR.

## Cambios exclusivos del modo aventura

- **Hojas:** se cancela la descomposición natural de hojas (LeavesDecayEvent) en las instancias de `dungeon1-endlessxl`. No se modifica Survival ni el schematic original.
- **Dos oleadas por zona:** aparecen 4 enemigos por cada integrante original del equipo en la primera oleada (1/2/3/4 jugadores = 4/8/12/16). La segunda oleada sale al derrotarlos; solo después de terminar ambas se desbloquea el siguiente tramo. Configurable en `config.yml` con `adventure.encounter.*`.
- **Parkour:** el OP se coloca en un suelo seguro del mapa y ejecuta `/dungeon setspawn parkour`. Se persiste en `plugins/BastionDungeons/adventure.yml` para ese schematic. Si un jugador toca agua dentro de `water-detection-radius-blocks` del checkpoint, vuelve allí y queda inmóvil por 5 segundos. **El radio es configurable** para no activar otras zonas con agua; ajústalo a la distancia real entre el checkpoint y el agua del parkour. No borres `adventure.yml`.
- **Voto obligatorio para cerrar:** durante la votación activa, si el usuario cierra el menú sin haber votado, se reabre al siguiente tick. Después de votar podrá cerrarlo. No modifica los resultados ni el cronómetro original. Configurable en `difficulty-voting.require-vote-before-close`.

## Nuevos valores para agregar al config.yml existente

```yaml
difficulty-voting:
  require-vote-before-close: true

adventure:
  encounter:
    mobs-per-player: 4
    waves: 2
    next-wave-delay-seconds: 0
  leaves:
    prevent-decay: true
  parkour:
    enabled: true
    water-detection-radius-blocks: 40
    freeze-seconds: 5
```

**NO dupliques** las secciones `difficulty-voting:` o `adventure:`. Solo añade sus claves faltantes con la indentación correcta. El `config.yml` dentro de este proyecto contiene todos los valores de ejemplo.

## Nuevos mensajes opcionales para messages.yml

```yaml
adventure-encounter-wave: '&#FF5555¡Emboscada! &fOleada {wave}/{total}: &#FF5555{count} &fenemigos.'
adventure-parkour-saved: '&#55FFFFCheckpoint del parkour guardado para futuras partidas.'
adventure-parkour-return: '&#55FFFF¡Caíste al agua! &fEspera {seconds}s para volver a intentarlo.'
vote-must-vote: '&eVota una dificultad antes de cerrar este menú.'
```

## Instalación

Sube el contenido del proyecto al repositorio de BASTION DUNGEONS y compila con GitHub Actions. Apaga el servidor, haz copia de seguridad del JAR y la carpeta del plugin, reemplaza solo el JAR y agrega las opciones a tus YAML existentes. Conserva `adventure.yml`, `spawns.yml`, `mobspawns.yml`, `worlds/` y tus mapas. Luego reinicia y prueba con una partida en `dungeon1-endlessxl`.

No se han realizado pruebas en ejecución con un servidor Purpur; el ZIP solo contiene los cambios de código y YAML.
