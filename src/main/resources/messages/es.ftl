# Respuestas de comandos de Discord
whitelist-wrong-channel = El comando /whitelist no puede usarse en este canal.
changenick-wrong-channel = El comando /changenick no puede usarse en este canal.
command-guild-only = Este comando solo puede usarse en un servidor.
already-whitelisted = Ya has añadido a **{ $nickname }** a la lista blanca. Usa /changenick para cambiar tu nombre.
missing-required-role = No tienes el rol necesario para usar este comando.
no-whitelisting-role = No tienes un rol de Discord que permita el uso de la lista blanca.
changenick-not-enabled = La función de cambio de nombre no está habilitada.
no-existing-whitelist = Aún no has añadido a ningún jugador a la lista blanca. Usa /whitelist primero.
same-nickname = Ese ya es tu nombre en la lista blanca.

# Resultados de lista blanca
player-not-found = No se pudo encontrar al jugador de Minecraft **{ $nickname }**.
whitelist-success = **{ $nickname }** añadido a la lista blanca y asignado al grupo `{ $group }`.
whitelist-success-group-missing = **{ $nickname }** añadido a la lista blanca, pero el grupo de LuckPerms `{ $group }` no existe.
whitelist-success-group-error = **{ $nickname }** añadido a la lista blanca, pero no se pudo asignar el grupo de LuckPerms: `{ $error }`.

# Resultados de cambio de nombre
changenick-old-not-found = Se eliminó al jugador anterior de la lista blanca, pero no se pudo encontrar al jugador de Minecraft **{ $nickname }**.
changenick-success = Jugador cambiado de { $oldNickname } a { $newNickname } (grupo: { $group }).
changenick-success-group-missing = Nombre cambiado a { $nickname }, pero el grupo de LuckPerms { $group } no existe.
changenick-success-group-error = Nombre cambiado a { $nickname }, pero no se pudo asignar el grupo de LuckPerms: { $error }.

# Mensajes en el juego
reload-start = Recargando CelestialWhitelister...
reload-success = CelestialWhitelister recargado correctamente.
reload-failure = Error al recargar. Revisa la consola para más detalles.
