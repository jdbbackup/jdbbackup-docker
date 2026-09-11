# TODO

## Plugin repository: avoid duplicating JSON files between versions

### Context
The image automatically downloads its plugins from a JSON descriptor hosted on GitHub Pages at `https://jdbbackup.github.io/web/repository/<version>.json` (see the [web repository](https://github.com/jdbbackup/web)).

Each time the image version changes, a new `<version>.json` file must be created in the `web` repository under `docs/repository/`. When the plugins haven't changed between two versions, the file is a verbatim copy of the previous version's file (e.g. `1.1.0.json` is identical to `1.0.0.json`).

### Problem
- GitHub Pages does not support HTTP redirects (30x), so we cannot redirect `1.1.0.json` to `1.0.0.json` at the server level.
- This leads to unnecessary duplication of JSON content across versions.

### Planned solution
Add an optional `redirect` attribute to the JSON descriptor. When present, the client should follow the redirect and download the plugins from the target URL instead of reading the rest of the file.

Example:
```json
{
  "repository": {
    "redirect": "https://jdbbackup.github.io/web/repository/1.0.0.json"
  }
}
```

This requires a client-side change in [jdbbackup-core](https://github.com/jdbbackup/jdbbackup-core) to handle the `redirect` attribute before parsing the `destinationManagers` and `sourceManagers` entries.

### Longer-term alternative
Migrate the plugin repository hosting to a platform that supports HTTP redirects (e.g. Cloudflare Pages, Netlify, or a custom server with Nginx/Caddy). This would allow clean 30x redirects without any client-side logic.
