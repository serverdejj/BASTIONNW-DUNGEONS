# BASTION DUNGEONS 1.2.10-dev

- Vida: SkeletalMinion 60 HP; SkeletalKnight 150 HP; SkeletonKing 600 HP.
- Victoria: movimiento libre y 10 segundos para explorar y recoger objetos antes de volver al lobby.
- Loot personalizado persistente por tipo de enemigo, con probabilidades independientes de 0.01% a 100%, conservando metadatos de ItemStack.

## Comandos (solo OP)

Sostén el objeto en la mano principal (se guarda la cantidad que sostengas):

`/dungeon loot add SkeletonKing 5`

`/dungeon loot list SkeletonKing`

`/dungeon loot remove SkeletonKing <id>`

Tipos: `SkeletalMinion`, `SkeletalKnight`, `SkeletonKing`.

Se guarda en `plugins/BastionDungeons/loot.yml`. El loot se añade a los drops normales; si MythicMobs tiene drops configurados, también podrían caer. Las probabilidades se evalúan independientemente por objeto en cada muerte. El loot está asociado al tipo de enemigo, no al mapa.

**Compilación:** sube el contenido de este ZIP a GitHub y revisa Actions. Este paquete es código fuente, no un JAR compilado ni probado en servidor.
