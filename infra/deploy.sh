#!/usr/bin/env bash
# Deploy DealSeal via private S3 artifacts and AWS Systems Manager (no SSH).
set -euo pipefail

PROFILE="${AWS_PROFILE:-dealseal-admin}"
REGION="${AWS_REGION:-us-west-2}"
INSTANCE_ID="${DEALSEAL_INSTANCE_ID:-i-0b4a755d6410e6699}"
BUCKET="${DEALSEAL_DEPLOY_BUCKET:-dealseal-deploy-152867773442-1792135604}"
AWS=(aws --profile "$PROFILE" --region "$REGION")
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

for cmd in aws python3 npm tar curl; do command -v "$cmd" >/dev/null || { echo "Missing: $cmd" >&2; exit 1; }; done
echo "Building backend..."
./gradlew clean bootJar
echo "Building frontend..."
(cd frontend && npm ci && npm run build)
JAR="$(find build/libs -maxdepth 1 -name '*.jar' ! -name '*plain.jar' | head -n 1)"
test -s "$JAR" || { echo "Backend JAR missing" >&2; exit 1; }
test -f frontend/dist/index.html || { echo "Frontend build missing" >&2; exit 1; }

TMP="$(mktemp -d)"
DEPLOY_ID="$(date -u +%Y%m%dT%H%M%SZ)-$$"
PREFIX="deployments/$DEPLOY_ID"
cleanup() {
  "${AWS[@]}" s3 rm "s3://$BUCKET/$PREFIX/" --recursive >/dev/null 2>&1 || true
  rm -rf "$TMP"
}
trap cleanup EXIT
cp "$JAR" "$TMP/app.jar"
tar -C frontend/dist -czf "$TMP/frontend.tar.gz" .
echo "Uploading temporary artifacts to S3..."
"${AWS[@]}" s3 cp "$TMP/app.jar" "s3://$BUCKET/$PREFIX/app.jar" --only-show-errors
"${AWS[@]}" s3 cp "$TMP/frontend.tar.gz" "s3://$BUCKET/$PREFIX/frontend.tar.gz" --only-show-errors

# Presigned downloads work even if the EC2 instance role has no S3 permissions.
# The URLs are short-lived; don't print the SSM request or its parameters.
BUCKET_REGION="$("${AWS[@]}" s3api get-bucket-location --bucket "$BUCKET" --query LocationConstraint --output text)"
if [[ "$BUCKET_REGION" == "None" || "$BUCKET_REGION" == "null" ]]; then BUCKET_REGION="us-east-1"; fi
if [[ "$BUCKET_REGION" == "EU" ]]; then BUCKET_REGION="eu-west-1"; fi
JAR_URL="$(aws --profile "$PROFILE" --region "$BUCKET_REGION" s3 presign "s3://$BUCKET/$PREFIX/app.jar" --expires-in 3600)"
FRONTEND_URL="$(aws --profile "$PROFILE" --region "$BUCKET_REGION" s3 presign "s3://$BUCKET/$PREFIX/frontend.tar.gz" --expires-in 3600)"

python3 - "$JAR_URL" "$FRONTEND_URL" "$TMP/ssm.json" <<'PY'
import json, shlex, sys
jar, frontend, path = sys.argv[1:]
script = r"""
set -euo pipefail
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
curl --fail --silent --show-error --location --retry 3 --output "$work/app.jar" JAR_URL
curl --fail --silent --show-error --location --retry 3 --output "$work/frontend.tar.gz" FRONTEND_URL
mkdir -p "$work/frontend"
tar -xzf "$work/frontend.tar.gz" -C "$work/frontend"
test -s "$work/app.jar"
test -f "$work/frontend/index.html"
install -o dealseal -g dealseal -m 0644 "$work/app.jar" /opt/dealseal/app.jar.next
chown -R dealseal:dealseal "$work/frontend"
# Keep one previous release for rollback; never modify persistent data or secrets.
rm -rf /opt/dealseal/frontend.previous
if test -d /opt/dealseal/frontend; then mv /opt/dealseal/frontend /opt/dealseal/frontend.previous; fi
if test -f /opt/dealseal/app.jar; then cp -p /opt/dealseal/app.jar /opt/dealseal/app.jar.previous; fi
mv "$work/frontend" /opt/dealseal/frontend
mv /opt/dealseal/app.jar.next /opt/dealseal/app.jar
systemctl restart dealseal
sleep 12
if ! systemctl is-active --quiet dealseal || ! curl --silent --output /dev/null --max-time 5 http://127.0.0.1:8080/; then
  echo "Health check failed; rolling back application and frontend" >&2
  if test -f /opt/dealseal/app.jar.previous; then cp -p /opt/dealseal/app.jar.previous /opt/dealseal/app.jar; fi
  if test -d /opt/dealseal/frontend.previous; then
    rm -rf /opt/dealseal/frontend
    mv /opt/dealseal/frontend.previous /opt/dealseal/frontend
  fi
  systemctl restart dealseal || true
  exit 1
fi
# Refresh DNS after each successful deployment; the timer remains enabled independently.
if systemctl is-enabled --quiet duckdns-update.timer; then
  echo "DuckDNS timer enabled"
fi
if systemctl start duckdns-update.service; then
  echo "DuckDNS updated during deployment"
else
  echo "WARNING: DuckDNS update failed; check journalctl -u duckdns-update.service" >&2
fi
echo "DealSeal deployment healthy"
"""
script = script.replace("JAR_URL", shlex.quote(jar)).replace("FRONTEND_URL", shlex.quote(frontend))
with open(path, "w") as f:
    json.dump({"commands": [script], "executionTimeout": ["300"]}, f)
PY

echo "Deploying to EC2 via SSM ($INSTANCE_ID)..."
COMMAND_ID="$("${AWS[@]}" ssm send-command --instance-ids "$INSTANCE_ID" --document-name AWS-RunShellScript --comment "DealSeal deployment $DEPLOY_ID" --parameters "file://$TMP/ssm.json" --timeout-seconds 600 --query 'Command.CommandId' --output text)"
echo "SSM command: $COMMAND_ID"
# Command invocation may not be visible immediately due to eventual consistency.
for attempt in $(seq 1 90); do
  STATUS="$("${AWS[@]}" ssm get-command-invocation --command-id "$COMMAND_ID" --instance-id "$INSTANCE_ID" --query Status --output text 2>/dev/null || true)"
  case "$STATUS" in
    Success)
      "${AWS[@]}" ssm get-command-invocation --command-id "$COMMAND_ID" --instance-id "$INSTANCE_ID" --query StandardOutputContent --output text
      echo "Deployed: https://dealseal-demo.duckdns.org"
      exit 0 ;;
    Failed|Cancelled|TimedOut|Cancelling)
      echo "Deployment failed ($STATUS). Remote output:" >&2
      "${AWS[@]}" ssm get-command-invocation --command-id "$COMMAND_ID" --instance-id "$INSTANCE_ID" --query '[StandardOutputContent,StandardErrorContent]' --output text >&2 || true
      exit 1 ;;
  esac
  sleep 5
done
echo "Deployment still running; inspect SSM command $COMMAND_ID before retrying." >&2
exit 1
