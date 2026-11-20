# BASTION DUNGEONS 1.2.35 - Fix formato de hologramas

Corrige que `mob-hologram.lines` no tuviera efecto porque los valores por defecto integrados del JAR para cada mob prevalecian.

**Regla:** por defecto todos los mobs usan `mob-hologram.lines`; solo si anades `hologram.lines` expresamente dentro de un mob se utiliza el formato individual.

Ejemplo global en `plugins/BastionDungeons/config.yml`:

```yaml
mob-hologram:
  enabled: true
  lines:
    - '{name} &8| &#00FFFF&lVIDA: &f{health}/{max_health}'
```

Ejemplo de override individual opcional:

```yaml
labyrinth:
  mobs:
    mutant:
      hologram:
        lines:
          - '{name} &8| &dHP: &f{health}/{max_health}'
```

Subir proyecto a Github, compilar, cambiar solamente JAR en el servidor apagado. Conservar `plugins/BastionDungeons/config.yml`. Ejecutar `/dungeon reload` para cambios posteriores. Los enemigos existentes podrian conservar hologramas viejos hasta nueva partida.
