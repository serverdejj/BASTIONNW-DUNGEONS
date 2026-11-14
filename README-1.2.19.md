# BASTION DUNGEONS 1.2.19-dev

- Crea mundos temporales `bd_XXXXXXXX` con un generador de vacío interno (sin DeepVoid, Multiverse, VoidGen ni SchemFlow).
- Carga chunks en lotes asíncronos y pega el `.schem` usando FAWE/WorldEdit.
- Al terminar, descarga y borra el mundo temporal después de sacar a los jugadores.
- Conserva las mecánicas de la 1.2.17.

## Requisitos
Purpur 1.21.11, Java 21 y FastAsyncWorldEdit/WorldEdit compatible. MythicMobs para enemigos.

## Atención: teletransportes
El servidor previamente rechazó `teleportAsync` y `teleport` hacia mundos temporales. Este cambio crea un mundo verdaderamente vacío pero NO garantiza solucionar cancelaciones de teleport por otros plugins. Revisar consola buscando `Teleport EVENTO FINAL (cancelado=true)` si falla.

## Instalación
Compilar en GitHub Actions y dejar un solo JAR de BastionDungeons en `plugins/`. Mantener el `.schem` en `plugins/BastionDungeons/schematics/`. Hacer backup antes de probar.
