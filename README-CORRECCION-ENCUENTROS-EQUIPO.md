# BASTION DUNGEONS — encuentro de equipo y mobs (EndlessXL)

## Cambios

- Al iniciar la emboscada del mapa `dungeon1-endlessxl`, todos los compañeros vivos dentro de esa instancia se reúnen cerca del jugador que activó el encuentro. No afecta a espectadores ni a otros mundos.
- Durante un encuentro, los enemigos que salgan de los 15 bloques del centro se devuelven a un lugar seguro cerca del centro. El rastreador se ejecuta una vez por segundo; con el retraso predeterminado, vuelven aproximadamente en 1–2 segundos.
- Los `SkeletalKnight` (y el identificador alternativo `SkeletalKingt`) invocados como enemigos del encuentro reciben velocidad de movimiento base 0.32. MythicMobs puede modificarla mediante sus propias habilidades.
- El resto de mapas y sus jefes conservan sus reglas.

## Opciones nuevas en config.yml

Añade bajo `adventure: -> encounter:` (sin duplicar `adventure` ni `encounter`):

```yaml
    teleport-party-on-start: true
    mob-return-delay-ticks: 20
    skeletal-knight-speed: 0.32
```

Las opciones tienen valores predeterminados en el código, por lo que no es obligatorio reemplazar tu `config.yml` actual.

## Texto nuevo en messages.yml

```yaml
adventure-party-gathered: '&#55CCFFTu equipo inició una emboscada. &fHas sido reunido con tus compañeros.'
```

## Instalación

1. Haz respaldo del proyecto y del JAR actual.
2. Descomprime el ZIP y sube los archivos al repositorio **BASTIONNW-DUNGEONS**.
3. Compila con GitHub Actions. Este ZIP contiene **código fuente, NO JAR**.
4. Con el servidor apagado, reemplaza solo el JAR. Conserva `spawns.yml`, `adventure.yml` y el resto de datos.
5. Prueba con dos jugadores: activación, teleport del compañero, mob fuera de los 15 bloques y velocidad del Knight.

No se ha compilado ni probado en el servidor en esta entrega.
