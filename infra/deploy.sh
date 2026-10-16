#!/usr/bin/env bash
set -euo pipefail
HOST="${1:?Usage: bash infra/deploy.sh EC2_PUBLIC_IP /path/to/key.pem}"
KEY="${2:?Provide EC2 SSH private key path}"
./gradlew bootJar
(cd frontend && npm ci && npm run build)
JAR=$(find build/libs -maxdepth 1 -name '*.jar' ! -name '*plain.jar' | head -n 1)
test -n "$JAR"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
cp "$JAR" "$TMP/app.jar"
tar -C frontend/dist -czf "$TMP/frontend.tar.gz" .
scp -o StrictHostKeyChecking=accept-new -i "$KEY" "$TMP/app.jar" "$TMP/frontend.tar.gz" "ec2-user@$HOST:/tmp/"
ssh -o StrictHostKeyChecking=accept-new -i "$KEY" "ec2-user@$HOST" <<'REMOTE'
set -euo pipefail
sudo install -o dealseal -g dealseal -m 0644 /tmp/app.jar /opt/dealseal/app.jar
sudo mkdir -p /opt/dealseal/frontend
sudo tar -xzf /tmp/frontend.tar.gz -C /opt/dealseal/frontend
sudo chown -R dealseal:dealseal /opt/dealseal
sudo systemctl restart dealseal nginx
sudo systemctl is-active --quiet dealseal
rm -f /tmp/app.jar /tmp/frontend.tar.gz
REMOTE
echo "DealSeal deployed to $HOST"
