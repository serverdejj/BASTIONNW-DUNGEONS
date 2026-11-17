# BASTION DUNGEONS 1.2.26-dev (fuentes)

Cambios en esta revisión:
- Votación con 20 segundos máximos; cierre anticipado cuando todos votan; resultado visible 5 segundos; preparación 5 segundos. Abstenciones permitidas; sin votos gana Normal; empates aleatorios.
- Menú con número de votos, nombres de votantes, descripción por dificultad y advertencia de escalado multijugador. Cantidad de mobs sin cambios.
- Probabilidad de loot personalizado según dificultad: fácil 0.5x, normal 1x, difícil 2x, máximo 100%.
- Eliminar drops vanilla y XP de mobs de mazmorra identificados por el plugin.
- Subtítulo de dificultad bajo el título de inicio.
- Bloqueo de interacciones con bloques/entidades y ender pearls; protección vertical +10 Y desde el spawn.
- Menos actualizaciones de nombres de mobs en el tracker.

Configuración: config.yml, menus.yml, messages.yml, difficulties.yml, sounds.yml (reservado). Los textos existentes ajenos a esta revisión aún pueden estar escritos en Java; **no está implementada la personalización total de todos los mensajes y sonidos**. No se ha compilado ni probado en un servidor real.

Para aplicar los cambios de YAML ya existentes, conserva una copia de tu configuración y agrega las nuevas claves; `saveDefaultConfig` no reemplaza archivos previos. Los archivos nuevos se crean al primer inicio.
