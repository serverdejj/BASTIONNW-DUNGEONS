# BASTION DUNGEONS 1.2.18-dev — integración SchemFlow

## Qué hace
`/dungeon go` llama a la API `provisionRoundWorld` de SchemFlow para crear un mundo vacío, pegar el mapa remoto, iluminarlo y entregarlo listo. Cuando termina la partida, llama a `disposeWorld` para descargarlo y borrarlo. No usa DeepVoid ni Multiverse.

## REQUISITO IMPORTANTE
SchemFlow NO provisiona automáticamente desde `plugins/BastionDungeons/schematics/*.schem`: la API de provisioning usa un mapa publicado en su almacenamiento S3/MinIO. Hay que configurar SchemFlow y publicar el mapa allí. Conservar el .schem local en BastionDungeons para lectura de dimensiones, spawns y combate.

## Preparación
1. Instala SchemFlow (versión con API `provisionRoundWorld`), FAWE/WorldEdit y MythicMobs.
2. Configura el endpoint, bucket y credenciales S3/MinIO en `plugins/SchemFlow/config.yml`.
3. Publica `bohemiancastle` en el grupo `default` mediante `saveSelectionAsMap` o `saveRegionAsMap`, preservando sus coordenadas originales. Si el mapa no se exportó con coordenadas originales, el spawn guardado puede no coincidir.
4. Conserva `plugins/BastionDungeons/schematics/bohemiancastle.schem`.
5. Ajusta `plugins/BastionDungeons/config.yml`: `schemflow.maps.bohemiancastle_schem: bohemiancastle`.
6. Prueba `/dungeon go`.

## Advertencias
- `paste-at-origin: true` presupone que el mapa remoto conserva las mismas coordenadas que el schematic local pegado a (0,100,0) en versiones anteriores. Si no coincide, reexporta el mapa o vuelve a configurar los puntos.
- Un `PlayerTeleportEvent` cancelado por otro plugin sigue impidiendo el teletransporte. La integración SchemFlow no evita cancelaciones.
- No se ha ejecutado un test de integración real con SchemFlow; revisar consola y hacer copia de seguridad.
