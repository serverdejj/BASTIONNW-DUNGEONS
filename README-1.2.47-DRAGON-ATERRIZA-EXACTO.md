# BASTION DUNGEONS 1.2.47 — altura exacta al aterrizar

El dragón mantiene el vuelo horizontal 6 bloques sobre el spawn. En cada punto
guardado de patrulla baja progresivamente hasta la coordenada Y del spawn
del dragón + 1.0 bloque, espera 10 segundos y vuelve a despegar.

## Cambio importante

Antes se escaneaban los bloques en una franja vertical próxima al spawn y
se elegía la superficie más alta. Eso podía hacerlo detenerse a 3 bloques
sobre el punto deseado. Ahora se utiliza la altura del spawn marcada por
`/dungeon dragon spawn` y la coordenada X/Z del punto de ruta.

## Configuración

Dentro de `netherybreis.dragon.flight`:

```yaml
cruise-height-above-spawn: 6.0
landing-height-above-spawn: 1.0
move-seconds: 4
descent-seconds: 2
takeoff-seconds: 2
stop-seconds: 10
```

Mantener los spawns y puntos persistidos; no hace falta volver a crearlos.
Nota: si el piso de algún punto de ruta está a una Y distinta del spawn
central, habrá que ajustar esa zona o adaptar el aterrizaje individual.
