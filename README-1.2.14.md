# BASTION DUNGEONS 1.2.14-dev

- `/dungeon go` crea su propio mundo temporal `bd_...` sin DeepVoid.
- Carga chunks y pega el `.schem` con WorldEdit antes de teletransportar al equipo.
- Comprueba el resultado de cada teletransporte y registra errores en consola.
- Devuelve a los jugadores y programa el borrado del mundo al terminar o fallar.

Requiere WorldEdit/FAWE y MythicMobs. Compilar en GitHub Actions y probar en un servidor de pruebas antes de producción.
