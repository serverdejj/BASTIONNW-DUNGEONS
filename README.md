# BASTION DUNGEONS 1.1.0-dev

Prototipo para Purpur 1.21.11 y Java 21. **No es la versión final del minijuego.**

## Incluido
- Parties de hasta 4 jugadores: `/dungeon party add <nombre>`, `accept`, `leave`, `list`
- `/dungeon go`: elige un `.schem` al azar, crea mundo vacío y pega con WorldEdit/FAWE.
- `/dungeon leave`: vuelve al punto anterior.
- Muerte: inventario cae al suelo y jugador reaparece espectador.

## Pendiente antes de producción
- Integración real de MythicMobs, oleadas, boss, victoria/derrota y recompensas.
- Validación de zona segura de aparición; actualmente usa coordenadas 0,102,0 respecto al origen del schematic. Preparar schematics con origen y punto de entrada adecuados.
- Restaurar el modo de juego previo (ahora se fuerza Survival al salir).
- Limpieza robusta de instancias y recuperación tras desconexión/reinicio.
- Protección de drops contra despawn y mecanismos anti-duplicación.
- Gestión de partidas cuando se desconecta el líder o miembros.

## Compilación
Sube el contenido de este ZIP a la raíz del repositorio (incluyendo `.github/workflows/build.yml`). En GitHub: Actions > Build plugin > Run workflow. Descarga el artifact con el jar.

**Precaución:** probar en servidor de pruebas y con inventarios vacíos; no instalar todavía como plugin de producción.
