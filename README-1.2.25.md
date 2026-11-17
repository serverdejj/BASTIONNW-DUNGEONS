# BASTION DUNGEONS 1.2.25-dev

- `/dungeon` (líder) inicia votación de dificultad; `/dungeon go` conserva el mismo inicio.
- Todos los integrantes reciben GUI y pueden votar Fácil (50% vida), Normal (100%), Difícil (200%). Se muestran los votos y nombres en cada opción.
- Cuando todos votan, 5 segundos de visualización; se cierra GUI y empieza la cuenta atrás existente de 5 segundos antes de crear/teletransportar a la mazmorra.
- Empates resueltos aleatoriamente entre las opciones con más votos, con igual probabilidad.
- Cada 10 segundos de combate, un enemigo activo se teletransporta detrás del jugador participante más cercano a él; se elige el enemigo cuya distancia a su jugador más cercano sea la mayor. Incluye jefes, sin crear mobs.
- Mantiene el número de mobs por jugador y los drops originales.
- Autocompletado TAB para `/dungeon` y sus subcomandos, incluyendo loot.
- Configuración en `config.yml`: difficulty.*.health-multiplier y enemy-stalking.*.

**Requiere** WorldEdit, MythicMobs y Purpur 1.21.11 (Java 21). Compilar con GitHub Actions. **No se ha probado en un servidor real**.
