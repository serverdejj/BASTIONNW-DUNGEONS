# BASTION DUNGEONS 1.2.44 — corrección sustentada en latest.log

Causa de bloqueo de vuelo: a las 09:11:45, `dragonFireAttack` línea 1374 invocaba Particle.DRAGON_BREATH sin el dato Float requerido por Purpur 1.21.11. La excepción cancelaba la tarea de patrulla. Reemplazado por SOUL_FIRE_FLAME, que no requiere datos extra.

Causa del error repetitivo de muerte: a las 09:12:32 `Particle.FLASH` solicitaba org.bukkit.Color. Reemplazado por END_ROD.

El stalking genérico queda excluido para los jefes de Netherybreis, evitando teletransportar al Dragón Rojo detrás del jugador. Los ataques se protegen contra excepciones para no detener todo el vuelo.

El log prueba que había siete paradas guardadas. No es necesario eliminar adventure.yml ni cambiar los puntos.

## Cómo instalar

Subir el proyecto a GitHub, compilar con Actions, instalar el nuevo JAR y reiniciar. Mantener la carpeta actual del plugin, config.yml, adventure.yml y recompensas. No se requieren claves nuevas en config.yml.

## Verificación

Revisión estática del código y contraste con latest.log. La compilación y la prueba en un servidor Purpur 1.21.11 quedan pendientes.
