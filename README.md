# Flibusta Reader

Cross-platform book search & download app for **Android**, **iOS**, **macOS**, and **Windows**.
Proxies requests to flibusta.is through an anonymous backend.

The client keeps the 10 most recent search queries locally. Repeating a query
moves it to the top; individual entries or the entire history can be removed.

## Architecture

```
Client App  ──HTTPS──►  Backend (Go)  ──Tor/Direct──►  flibusta.is
```

## Backend

Lightweight Go proxy that parses Flibusta HTML and exposes a clean JSON API.

### Run locally

```bash
cd backend
go run ./cmd/server
```

Env vars:
| Variable | Default | Description |
|---|---|---|
| `PORT` | `8080` | Server port |
| `FLIBUSTA_URL` | `https://flibusta.is` | Upstream URL |
| `TOR_PROXY` | *(empty)* | SOCKS5 proxy for Tor (e.g. `127.0.0.1:9050`) |
| `CACHE_TTL_MINUTES` | `60` | Search result cache TTL |
| `NEGATIVE_CACHE_TTL_MINUTES` | `10` | TTL for successful empty searches |
| `SEARCH_TIMEOUT_SECONDS` | `15` | Upstream timeout for book and author searches |
| `SERIES_SEARCH_TIMEOUT_SECONDS` | `8` | Upstream timeout for the slower series search |
| `RATE_LIMIT` | `60` | Requests per window per IP |
| `RATE_LIMIT_WINDOW_SECONDS` | `60` | Rate limit window |

### Docker

```bash
docker compose up --build
```

### API

```
GET /health
GET /api/v1/search?q=query&type=title|author
GET /api/v1/book/{id}
GET /api/v1/download/{id}/{format}
```

## Client

Kotlin Multiplatform + Compose Multiplatform.

### Desktop (macOS/Linux)

```bash
cd client
./gradlew :desktopApp:run
```

### Android

```bash
cd client
./gradlew :androidApp:assembleDebug
```

APK: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`

### iOS

Open `client/iosApp` in Xcode and build.

## Continuous integration and releases

GitHub Actions builds the client on every push and pull request:

- Android APKs on Linux
- macOS DMG on macOS
- Windows x64 EXE, MSI and portable ZIP on Windows

Build outputs are stored as workflow artifacts for 30 days. Pushing a tag such
as `v1.0.0` also creates a GitHub Release and attaches all three platform
packages.

The Windows job validates that the launcher and bundled JVM are both x64, then
starts the packaged application and checks that it stays running. This prevents
publishing an incomplete portable launcher or a mixed-architecture runtime.

Android release signing is optional. Without repository secrets, CI produces
an unsigned release APK plus a debug APK. To enable signing, add these GitHub
Actions secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

The website and locally generated binaries are intentionally not committed.

## Deploy Backend Anonymously

1. Get a VPS from a provider that accepts crypto (e.g. Njalla, 1984.is)
2. Put Cloudflare in front to hide the server IP
3. Deploy via Docker:
   ```bash
   docker compose up -d
   ```
4. Optionally enable Tor: set `TOR_PROXY=127.0.0.1:9050` and run Tor on the server
5. Share the Cloudflare domain with users — they enter it in app Settings

## License

MIT
