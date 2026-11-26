# BASTION DUNGEONS 1.2.43 — DRAGON ROJO FASEO / ATERRIZAJE

- Durante vuelo el dragon atraviesa paredes y techos por teletransporte controlado; NO destruye bloques.
- Busca el suelo bajo las coordenadas horizontales registradas con /dungeon dragon punto add y aterriza en la superficie.
- Ruta limitada a 30 bloques horizontales desde /dungeon dragon spawn; 10 s vuelo y 10 s detenido.
- Altura del arco de vuelo configurable con netherybreis.dragon.flight.fly-height-blocks (por defecto 8).
- Bolas de fuego durante vuelo y mientras esta detenido (configurable).
- Evita daño por sofocacion del dragon al atravesar estructuras.
- Logs de la ruta en consola para diagnostico de otros plugins que cancelan teletransportes.

IMPORTANTE: El paso a traves de bloques aplica solo al controlador del dragon, NO a sus proyectiles. Los proyectiles chocan con estructuras, como en Minecraft normal. El punto de spawn y los puntos de ruta se guardan en adventure.yml; conservarlo.

INSTALACION: subir proyecto completo a GitHub Actions; compilar; reemplazar .jar; fusionar nueva opcion en config.yml. No borrar los YAML del servidor.

VERIFICACION PENDIENTE: No se ha compilado contra Purpur 1.21.11 ni probado en el servidor. Compartir la primera excepcion [Dragon] de latest.log si sigue inmovil.
