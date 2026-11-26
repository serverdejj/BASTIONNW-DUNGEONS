# BASTION DUNGEONS — corrección del Dragón Rojo

- Altura de patrulla: 6 bloques por encima del Y del spawn del dragón.
- Cada parada tiene cuatro fases: despegue (2 s), vuelo (4 s), descenso (2 s), descanso sobre el terreno (10 s).
- El terreno se busca alrededor de la cota del spawn, sin explorar cuevas profundas.
- Si no existe suelo seguro en la zona de búsqueda, el jefe se queda a la cota de spawn en esa parada.
- Los puntos de vuelo y spawn registrados en adventure.yml siguen intactos.
- Las velocidades y alturas se configuran en `netherybreis.dragon.flight`.
- Si ya tienes config.yml propio, añade las claves de CONFIG_DRAGON_ATERRIZA_6_BLOQUES.yml; el config.yml interno no sobrescribe automáticamente el del servidor.

## Instalación
Compila con Java 21 y Gradle (GitHub Actions incluido), y sustituye solo el .jar. No borres `adventure.yml`.
No se pudo verificar la compilación ni el resultado en un servidor Purpur desde este entorno.
