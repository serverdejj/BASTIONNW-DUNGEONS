# BASTION DUNGEONS 1.2.41 – Vuelo y bolas de fuego del Dragón Rojo

- Corrige entrada desde el cielo cuando el techo impide iniciar a altura completa.
- Registra advertencia en consola cuando otro plugin cancele el teletransporte del mob.
- Controla el desplazamiento cada tick hacia puntos de `adventure.yml`.
- Lanza bolas de fuego reales (`LargeFireball`) tipo Ghast durante el vuelo hacia jugadores participantes.
- Las bolas no incendian ni destruyen bloques (yield=0 y defensa global de explosiones).
- Daño de área propio configurable.
- Conserva BossBar, loot y animación de muerte de la versión anterior.

## Instalación
Compilar en GitHub Actions. Reemplazar solo JAR generado; conservar `adventure.yml` y demás datos.
Fusionar solo `netherybreis.dragon.fireballs` del config predeterminado, sin sobrescribir valores personalizados.

## Diagnóstico si sigue inmóvil
Revisar la consola: `DRAGON ... ruta iniciada` y `DRAGON: teleport CANCELADO`.
Verificar `/dungeon dragon info` y `/dungeon dragon punto list` dentro de instancia.
Si el modelo visual no sigue al zombie invisible, revisar sincronización de ModelEngine.
