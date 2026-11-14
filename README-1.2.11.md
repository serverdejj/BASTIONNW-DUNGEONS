# BASTION DUNGEONS 1.2.11-dev

Cambios respecto a 1.2.10.1-dev:

- Nombres visibles de SkeletalMinion, SkeletalKnight y SkeletonKing con colores y degradados definidos para BASTION MC; se conserva el contador de vida.
- Límite horizontal de 33 bloques desde el spawn de cada instancia: los jugadores que lo superen vuelven al spawn; se permite subir y bajar pisos.
- Los mobs de la partida persiguen al jugador activo más cercano y, si se quedan atascados durante unos 4 segundos, se teletransportan cerca de él a una posición segura. Se incluye al jefe.
- Se conservan las recompensas personalizadas y la corrección del loot de 1.2.10.1.

Compilar con GitHub Actions (`.github/workflows/build.yml`). Verificar en un servidor de pruebas antes de reemplazar la versión estable.
