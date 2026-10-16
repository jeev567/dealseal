#!/usr/bin/env bash
set -euo pipefail
INSTANCE_ID="${1:?Usage: infra/deploy.sh INSTANCE_ID REGION}"
REGION="${2:-us-west-2}"
BUCKET="${DEALSEAL_DEPLOY_BUCKET:?Set DEALSEAL_DEPLOY_BUCKET to an existing private S3 bucket}"
./gradlew bootJar
(cd frontend && npm ci && npm run build)
JAR=$(find build/libs -maxdepth 1 -name '*.jar' ! -name '*plain.jar' | head -n 1)
test -n "$JAR"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
cp "$JAR" "$TMP/app.jar"
tar -C frontend/dist -czf "$TMP/frontend.tar.gz" .
KEY="dealseal-deploy/$(date +%s)-$$"
aws s3 cp "$TMP/app.jar" "s3://$BUCKET/$KEY/app.jar" --region "$REGION"
aws s3 cp "$TMP/frontend.tar.gz" "s3://$BUCKET/$KEY/frontend.tar.gz" --region "$REGION"
echo "Artifacts uploaded to s3://$BUCKET/$KEY"
echo "On the EC2 instance, run the following commands after installing/configuring AWS CLI:"
echo "  aws s3 cp s3://$BUCKET/$KEY/app.jar /opt/dealseal/app.jar"
echo "  aws s3 cp s3://$BUCKET/$KEY/frontend.tar.gz /tmp/frontend.tar.gz"
echo "  mkdir -p /opt/dealseal/frontend && tar -xzf /tmp/frontend.tar.gz -C /opt/dealseal/frontend"
echo "  chown -R dealseal:dealseal /opt/dealseal"
echo "  systemctl restart dealseal nginx"
echo "After deployment, remove artifacts: aws s3 rm s3://$BUCKET/$KEY/ --recursive"
echo "NOTE: This script stages files only; deployment commands are manual and require instance access."
