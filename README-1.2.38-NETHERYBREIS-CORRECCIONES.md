# BASTION DUNGEONS 1.2.38 - Correcciones de Netherybreis

1. **Piglins**: no recogen objetos (incluido oro) y no aceptan intercambios. El plugin les fija un objetivo jugador en sus instancias incluso si lleva armadura de oro.
2. **Nombres duplicados**: `mob-hologram.hide-native-name: true` ahora también oculta el nombre nativo de los Piglins sin borrar el holograma dinámico de vida.
3. **Oleadas**: desaparece el aviso de "puedes seguir avanzando" al finalizar cada encuentro de Netherybreis. Se usan mensajes de oleadas configurables.
4. **Dragón**: se activa automáticamente al completar las oleadas de la última zona registrada, sin `/dungeon boss bloque`. Si se quiere tras cualquier zona, añadir `netherybreis.encounter.boss-after-any-zone: true`.
5. Solo `/dungeon dragon spawn` es obligatorio. `/dungeon dragon punto add` es opcional; sin puntos no habrá ruta de vuelo, pero el boss puede aparecer.
6. Los títulos y temporizadores son configurables desde `netherybreis.dragon`.

**Actualización**: compilar fuente en GitHub Actions, reemplazar solo el JAR. Mantener todos los YAML existentes, especialmente `adventure.yml` con las zonas y el spawn. Añadir las nuevas opciones de `netherybreis.encounter` al config en el servidor. Para aplicar `/dungeon reload` e iniciar **una partida nueva**.

**Verificación pendiente**: compilación completa en GitHub Actions y prueba en Purpur 1.21.11 con MythicMobs/ModelEngine reales.
