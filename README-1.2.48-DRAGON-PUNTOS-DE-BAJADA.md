# BASTION DUNGEONS 1.2.48 — Corrección de puntos de bajada

- Cada `/dungeon dragon punto add` guarda X/Y/Z. El aterrizaje usa **la Y guardada del punto + 1 bloque**, no la Y del spawn principal.
- La altura de crucero permanece en spawn + 6 bloques (configurable).
- Desciende durante `descent-seconds`, permanece en el punto durante `stop-seconds` y vuelve a despegar durante `takeoff-seconds`.
- No elimina los puntos registrados ni cambia el archivo adventure.yml.
- En `config.yml`, bajo `netherybreis.dragon.flight`, usar `landing-height-above-point: 1.0`. La opción antigua `landing-height-above-spawn` ya no se utiliza.
- Si un punto se fijó bajo techo u obstáculo, la ruta no adapta el destino automáticamente: revisar los puntos para evitar colisiones visuales.
- Proyecto fuente listo para compilar con el workflow de GitHub Actions.
