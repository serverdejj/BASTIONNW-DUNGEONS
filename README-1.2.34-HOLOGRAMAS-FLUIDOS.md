# BASTION DUNGEONS 1.2.34 - Hologramas fluidos y sin nombre duplicado

- El seguimiento del holograma se actualiza cada tick (20 veces/s).
- Los TextDisplay interpolan el movimiento con teleportDuration=1.
- Los disfraces de MythicMobs incluyen `setNameVisible false` para ocultar el nombre de jugador; solo aparece el holograma de Dungeons.

## Instalacion
1. Compila el proyecto en GitHub Actions y sustituye el JAR anterior con el servidor apagado.
2. Conserva plugins/BastionDungeons/config.yml y los datos de las mazmorras.
3. Sustituye el YAML de los cuatro Slimes en plugins/MythicMobs/mobs/ por el archivo adjunto, **sin IDs duplicados**.
4. Ejecuta `/mm reload`; inicia una mazmorra nueva.

Los mobs ya generados no reciben retroactivamente la nueva configuracion de disfraz. Si ves todavia el nombre del jugador, comprueba en la consola si LibsDisguises acepta `setNameVisible false` en tu version.

No se han modificado la vida, dano, oleadas ni habilidades de los jefes.
