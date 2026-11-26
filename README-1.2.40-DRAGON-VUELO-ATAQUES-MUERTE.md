# BASTION DUNGEONS 1.2.40 — Dragón Rojo

Actualización basada en el ZIP 1.2.39 que compartiste. Se mantienen las tres mazmorras, los datos de aventura, las oleadas, la BossBar y el loot anterior.

## Correcciones

- Desactiva la gravedad del dragón para que no caiga mientras se mueve con IA desactivada.
- Ruta: entre cada punto, vuela durante `flight.move-seconds` (10 s); en el punto se detiene `flight.stop-seconds` (10 s) y repite los puntos indefinidamente.
- Si faltan puntos guardados, genera cuatro puntos provisionales alrededor del spawn y avisa en consola; estos no se escriben en adventure.yml.
- Ataques tanto durante el vuelo (cada 3 segundos) como durante la parada (cada 2 segundos), seleccionando al participante cercano dentro del rango horizontal Y vertical. Se dibuja una trayectoria de partículas desde el dragón y se aplica daño a participantes cerca del blanco. No rompe bloques.
- Al morir el dragón real, se registra su loot existente de inmediato. Se crea una segunda entidad de MythicMobs únicamente visual, invulnerable y sin IA, que asciende 4 s y estalla en partículas antes de retirarse. La victoria sigue el flujo normal (10 segundos para recoger recompensas).

## Configuración

- Cambia las opciones en `src/main/resources/config.yml` ANTES de compilar o fusiónalas en tu `plugins/BastionDungeons/config.yml` actual.
- Mira `CONFIG_AGREGAR_DRAGON_V1.2.40.yml` para el fragmento mínimo.
- No borres `adventure.yml`, tu `config.yml` personalizado ni datos del servidor.
- En una instancia del mapa, comprueba `/dungeon dragon punto list`, establece `spawn` y agrega dos o más puntos a altura suficiente con `/dungeon dragon punto add`.
- `/dungeon dragon test` permite probar el combate al finalizar las oleadas cuando esté permitido por el estado de la instancia.
- Requiere el mob MythicMobs `BastionRedDragon` y el modelo ya instalado de ModelEngine `bastion_red_dragon`.
- La animación de muerte intenta recrear un efecto estilo Ender Dragon: no es la animación vanilla exacta ni una nueva animación de huesos del modelo Blockbench.

## Validación

El código no se compiló localmente porque Gradle no está instalado en este entorno. Compilar y comprobar logs de GitHub Actions y probarlo con Purpur 1.21.11. Las versiones concretas de API/modelo pueden requerir ajustes.
