# BASTION DUNGEONS 1.2.37 — Netherybreis + Dragon Rojo

## Estado y requisitos

Este ZIP es CODIGO FUENTE para GitHub Actions (no un .jar precompilado). Basado en el proyecto que entregaste `BASTIONNW-DUNGEONS-main (28).zip`.

Requiere Purpur 1.21.11, WorldEdit, MythicMobs y ModelEngine 4.x. No reemplaces tus mundos, spawns ni configuracion existente.

**IMPORTANTE**: No se pudo compilar contra la API de Purpur/WorldEdit en este entorno. Debes comprobar que `Actions -> build` termine en verde y probarlo en el servidor. El modelo incluido es **PROTOTIPO editable de Blockbench**, no un modelo artístico final verificado en ModelEngine. Su escala visual real dependerá de su importación.

## INSTALACIÓN

1. Sube el contenido de este proyecto a tu repositorio `BASTIONNW-DUNGEONS`, compila en GitHub Actions, descarga el JAR y sustitúyelo con el servidor apagado.
2. Conserva tu `plugins/BastionDungeons/config.yml` actual. Copia/pega **solo** `netherybreis:` de `CONFIG_AGREGAR_NETHERYBREIS.yml` al final del config existente. NO elimines las otras mazmorras. Ejecuta `/dungeon reload`.
3. Copia `MythicMobs-BASTION-Nether.yml` como `plugins/MythicMobs/mobs/BastionNether.yml` (sin otros archivos que definan los mismos IDs). Ejecuta `/mm reload`.
4. Copia `ModelEngine-blueprint/bastion_red_dragon.bbmodel` a `plugins/ModelEngine/blueprints/` y ejecuta `/meg reload models` (o reinicia).
5. ModelEngine genera un resource pack. **Debes añadir sus assets al paquete de recursos de BASTION MC y hacer que los jugadores lo usen**. Si no, el dragón personalizado no será visible como se pretende.
6. Prueba primero invocando `/mm mobs spawn BastionNetherPiglin 1` y `/mm mobs spawn BastionNetherCrossbow 1`. No fuerces el dragón en mundos ajenos.

## Oleadas

Al entrar al mapa Netherybreis no se invocan enemigos. El OP debe registrar una o mas zonas de encuentro desde DENTRO de una partida:

`/dungeon radio 15 oleadas 3` crea una zona de radio 15 con **cuatro oleadas en total** (tres adicionales después de la primera). El numero de enemigos por oleada es configurable en `netherybreis.encounter.wave-counts` (por defecto 5,10,15,20). Las oleadas terminan al matar a todos los Piglins de la actual. Cada zona guardada funciona por orden, y solo despues de despejar la última aparece la cuenta regresiva.

`/dungeon mobs list` — listar cuantas zonas existen.
`/dungeon mobs clear` — borrar TODAS las zonas del mapa.

Los Piglins no sueltan equipamiento ni drops normales cuando se generan por BastionDungeons. Se usa exclusivamente el sistema de loot personalizado de `/dungeon loot add <ID> <porcentaje>`.

## Jefe Dragon Rojo: configuracion de spawn y vuelo

En una partida activa de Netherybreis, como OP:

- `/dungeon dragon spawn` — establece la posición de aparición (a la altura de tus pies, conviene colocarse en el aire en Creativo)
- `/dungeon dragon punto add` — guarda un punto del recorrido (ponte en el aire en Creativo)
- `/dungeon dragon punto list` — muestra la cantidad
- `/dungeon dragon punto remove 1` — elimina un punto
- `/dungeon dragon punto clear` — borra todos los puntos
- `/dungeon dragon info` — consulta spawn y puntos
- `/dungeon dragon test` — reintenta el jefe SI ya completaste todas las zonas, y tienes spawn y puntos

Puntos y spawn del dragón se guardan en `plugins/BastionDungeons/adventure.yml` asociados al schematic `netherybreis`; se conservan entre partidas y reinicios.

Tras completar la última zona, aparece un título 10 ... 0 segundos y se invoca `BastionRedDragon` de MythicMobs. Vuela durante 10 s hasta un punto, se detiene 10 s, ataca durante su reposo, y sigue al siguiente punto. La velocidad real depende de la distancia entre puntos. Los tiempos, radios, daño de aliento y estadisticas son configurables. Los ataques de aliento no destruyen bloques.

El jefe usa el holograma de vida de BastionDungeons. Al matarlo, la victoria y el loot siguen el sistema preexistente.

## Personalización

En `netherybreis.piglins.melee`, `.crossbow` y `.dragon` puedes modificar `health`, `damage`, `scale`, `name-format`, y `mythic-id`. El daño de ballesta es `projectile-damage`. La dificultad reduce / aumenta **solo vida y daño**. El ataque del dragón durante el reposo usa `netherybreis.dragon.attack.damage` y `radius`.

## Limitaciones actuales del prototipo

- La textura y la animación del modelo Blockbench incluido son una PRIMERA VERSION que debe verificarse en ModelEngine R4.1.1. Si ModelEngine no puede cargarlo, conservar el Java e instalar un `.bbmodel` válido con el ID `bastion_red_dragon`.
- El dragon usa un ZOMBIE como entidad base invisible al aplicar correctamente el modelo de ModelEngine. Por eso su caja de colisión no coincide necesariamente con el tamaño visual del dragon. Se requiere ajustar el hitbox en el modelo para una experiencia final pulida.
- `dragon.test` no salta etapas para proteger los encuentros; solo vuelve a intentar el spawn después de completar las zonas.
- Como en todas las versiones anteriores, se recomienda probar en un servidor de pruebas antes de producción.
