# V13.7.6 — Ctrl long press opens animations

- Short press / normal hold on the crouch button keeps sending Left Ctrl exactly as before.
- Long press threshold: 650 ms.
- At the long-press threshold Left Ctrl is released first, then a clean Z key press is sent.
- This prevents Ctrl+Z and prevents Ctrl from remaining held after the animation menu opens.
- No engine patches, graphics presets, water, NG-GL4ES, or gameplay settings are changed in this update.
