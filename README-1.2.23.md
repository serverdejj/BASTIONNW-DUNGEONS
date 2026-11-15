# Bastion Dungeons 1.2.23-dev — salida de espectadores

- La victoria y derrota retornan a todos los participantes registrados, incluidos los que murieron y quedaron en espectador.
- Al retornar se desmonta NameTangs antes de teletransportar y se deja de seguir entidades en espectador.
- Si el teletransporte falla, se reintenta hasta cinco veces con un segundo de separación; no se elimina el mundo con jugadores dentro.
- La tarea de respawn solo activa Espectador si el jugador todavía está registrado en esa partida.
- Mantiene la mecánica de soltar inventario al morir.

**Nota:** es código fuente pendiente de compilar y probar. Guarda copia del JAR anterior. No reemplazar NameTangs.
