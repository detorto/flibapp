#!/bin/bash
set -euo pipefail

HOST="pmbot"
REMOTE_DIR="/opt/flibusta-backend"
SERVICE_NAME="flibusta-backend"
BINARY_NAME="flibusta-backend"

echo "=== Building Linux binary ==="
cd "$(dirname "$0")/backend"
CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -ldflags="-s -w" -o "../${BINARY_NAME}-linux" ./cmd/server
cd ..
echo "Binary size: $(du -h ${BINARY_NAME}-linux | cut -f1)"

echo "=== Uploading to ${HOST} ==="
ssh "$HOST" "mkdir -p ${REMOTE_DIR}"
scp "${BINARY_NAME}-linux" "${HOST}:${REMOTE_DIR}/${BINARY_NAME}.new"
rm "${BINARY_NAME}-linux"

echo "=== Installing on server ==="
ssh "$HOST" bash -s <<'REMOTE_SCRIPT'
set -euo pipefail

SERVICE_NAME="flibusta-backend"
REMOTE_DIR="/opt/flibusta-backend"
BINARY="${REMOTE_DIR}/flibusta-backend"

# Stop service if running
systemctl stop "$SERVICE_NAME" 2>/dev/null || true

# Swap binary
mv "${BINARY}.new" "$BINARY"
chmod +x "$BINARY"

# Create systemd service
cat > /etc/systemd/system/${SERVICE_NAME}.service <<EOF
[Unit]
Description=Flibusta OPDS Proxy Backend
After=network.target

[Service]
Type=simple
ExecStart=${BINARY}
WorkingDirectory=${REMOTE_DIR}
Restart=always
RestartSec=5

Environment=PORT=8080
Environment=FLIBUSTA_URL=https://flibusta.is
Environment=CACHE_TTL_MINUTES=60
Environment=RATE_LIMIT=60
Environment=RATE_LIMIT_WINDOW_SECONDS=60

# Security hardening
NoNewPrivileges=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=${REMOTE_DIR}

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable "$SERVICE_NAME"
systemctl start "$SERVICE_NAME"

sleep 2
if systemctl is-active --quiet "$SERVICE_NAME"; then
    echo "Service is running"
    curl -s http://localhost:8080/health
    echo ""
else
    echo "ERROR: Service failed to start"
    journalctl -u "$SERVICE_NAME" --no-pager -n 20
    exit 1
fi
REMOTE_SCRIPT

REMOTE_IP=$(ssh "$HOST" "hostname -I | awk '{print \$1}'" 2>/dev/null || echo "unknown")

echo ""
echo "=== Done! ==="
echo "Backend is running at http://${REMOTE_IP}:8080"
echo ""
echo "Test:  curl http://${REMOTE_IP}:8080/health"
echo "Logs:  ssh ${HOST} 'journalctl -u ${SERVICE_NAME} -f'"
