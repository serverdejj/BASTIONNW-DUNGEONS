# BASTION DUNGEONS 1.2.36 — Sin nombre duplicado en esqueletos

En EndlessXL los mobs SkeletalMinion, SkeletalKnight y SkeletonKing pueden mostrar el nombre nativo de MythicMobs debajo del holograma TextDisplay. Esta versión elimina el nombre nativo de la entidad cuando el holograma está habilitado, mientras conserva el nombre y la vida del holograma y los nombres configurables en `skeleton-mobs.*.name-format`.

## Configuración

En `plugins/BastionDungeons/config.yml`, dentro de `mob-hologram:`, agrega:

```yaml
  hide-native-name: true
```

Por defecto esta opción está activada para esqueletos, incluso si no la agregas al YAML viejo. Para permitir el nombre nativo de un esqueleto concreto, usa por ejemplo `skeleton-mobs.minion.hologram.hide-native-name: false`.

Requiere compilar desde GitHub Actions y reemplazar solo el JAR. Conserva el `config.yml`, mundos y demás archivos de datos. Haz una partida nueva de EndlessXL para comprobarlo. El efecto de otros plugins que generen hologramas independientes no se controla con esta opción.
