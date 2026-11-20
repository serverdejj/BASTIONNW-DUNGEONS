BASTION DUNGEONS - AJUSTE DE TAMANOS DE SLIMES (The Labyrinth)

Problema: SLIME puede tener varios tamanos; el viejo calculo de escala partia
         de su bounding box actual y producia disfraces de tamanos desiguales.

Cambios:
- El codigo calcula la escala usando referencia fija de 1.8 bloques
  (config: labyrinth.player-model-reference-height), sin multiplicar la
  escala preexistente de MythicMobs.
- Si un mob viejo todavia es SLIME, su tamano base se normaliza a 1.
- La nueva plantilla MythicMobs usa ZOMBIE como base humanoide y aplica las
  skins de LibsDisguises. Los nombres/IDs conservan BastionSlime*.
- Alturas: mutant 0.75, moon 0.9, giant 1.5, king 3.0 bloques.
- Altura/velocidad constantes al cambiar dificultad; vida/dano como antes.

INSTALACION:
1. Compilar el repositorio con GitHub Actions y sustituir solo el JAR.
2. Conservar los archivos existentes plugins/BastionDungeons/ (no borrarlos).
3. Reemplazar el YAML anterior de los cuatro Slimes en
   plugins/MythicMobs/mobs/ por BastionLabyrinthSlimes_tamanos_fijos.yml.
   IMPORTANTE: no dejar otro YAML con IDs BastionSlime* duplicados.
4. Verificar skins registradas con LibsDisguises; ejecutar /mm reload.
5. Iniciar una nueva partida. Los mobs ya generados no se redimensionan.
6. Si tu config.yml es antigua, agrega bajo labyrinth:
     player-model-reference-height: 1.8
   Y conserva las claves labyrinth.mobs.*.height-blocks.

NOTA: El atributo SCALE ajusta las entidades; LibsDisguises puede no reflejar
la escala visual del disfraz en todas sus versiones. En ese caso es necesaria
una integracion especifica con la API del disfraz. No es una garantia visual
hasta probarlo con la version de LibsDisguises del servidor.
