# Cross-server identity

When you run several Bukkit servers (a hub, a survival, a minigames cluster, …)
you usually want a player who registered once to be recognised everywhere
without re-linking their Discord account on each server.

DILogin supports this out of the box: point every server at the **same MySQL
database** and the `user` table becomes the single source of truth for
Minecraft↔Discord links across the network.

## Requirements

- One reachable MySQL 8.x instance.
- DILogin **same plugin version** on every server in the network.
- Network connectivity from every Bukkit server to the MySQL host.

> A version mismatch can corrupt the schema. The plugin enforces a schema
> version check on boot (see *Safety guard* below) but rolling deploys still
> need to be quick.

## Configuration

In each server's `plugins/DILogin/config.yml` set:

```yml
database: mysql
database_host: db.example.internal
database_port: 3306
database_username: dilogin
database_password: <secret>
database_table: dilogin
database_autoReconnect: true
```

`database_table` is the **database name** (Maven historical naming). All
servers must point at the exact same value.

The first server to start creates the `user` and `di_schema` tables. The rest
detect the existing schema and join in.

## What is shared

| Data | Shared? |
|------|---------|
| Discord ↔ MC username link (`user` table) | yes |
| Schema version (`di_schema`) | yes |
| Login sessions (in-memory) | no — per-server |
| Prejoin codes / verified flags | no — per-server (cache lives in JVM) |
| AuthMe accounts | no — AuthMe handles its own DB |
| Roles / role-sync config | per-server (config-driven) |

So a player registered on server A is automatically recognised on server B
when they connect there: DI sees the existing row in `user`, skips the
register flow, and runs the standard login challenge instead.

## Safety guard

`SchemaController` runs on startup after the DB pool is up. It maintains a
single-row `di_schema` table:

| Stored vs current | Action |
|-------------------|--------|
| no row yet | Insert current version. |
| equal | Continue silently. |
| current is newer | Update the row, log a warning that other servers must be upgraded. |
| current is older | **Refuse to start** — the DB has been migrated by a newer plugin elsewhere; running this older plugin against it would risk data corruption. |

The version constant lives in
`di.dilogin.controller.SchemaController.CURRENT_SCHEMA_VERSION`. Bump it
whenever the shared schema changes, ship a migration in the same patch.

## Operational tips

- Keep the MySQL credentials out of git. Use Docker secrets, Infisical or
  similar.
- `bungeecord: true` in the proxy and `bungeecord: false` on the backends is
  unrelated to cross-server identity — it controls whether DI runs on the
  Bungee proxy or each backend Bukkit server independently. For cross-server
  identity you want every backend Bukkit running its own DI hooked to the
  shared DB.
- HikariCP is configured with `maximumPoolSize=10` per server. For large
  networks (≥ 20 backends) raise it in `DBConnectionMysqlImpl` or split into
  read replicas.
- Run **regular MySQL backups**. The `user` table is small but losing it
  forces every player to re-register.

## Sanity check after rollout

1. Register a test player on server A → row appears in `user`.
2. Connect with the same username on server B → DI prompts only for login,
   not registration.
3. `/userinfo <name>` on either server returns the same Discord user.

If any of these fail, check the server logs for `Schema check failed` or
`Database schema is at version` messages.
