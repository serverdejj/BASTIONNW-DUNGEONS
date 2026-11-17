# Corrección: espectador 5 segundos dentro de la mazmorra

- El evento de respawn establece el punto de la mazmorra con prioridad alta.
- Durante los cinco segundos de espectador, bloquea teletransportes hacia otros mundos y corrige teletransportes externos al spawn en los ticks siguientes.
- Después de 100 ticks, elimina la protección temporal, teletransporta al spawn de retorno y restaura Survival.
- Se conservan los drops de inventario, el grupo y el resto de mecánicas.

Si otro plugin modifica el respawn, revisar también sus configuraciones. Probar en una copia del servidor antes de producción.
