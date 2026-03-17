#!/bin/bash
set -euo pipefail

# -------------------------------------------------------------------
# Maven Central release script with curl-based upload fallback.
#
# JReleaser's Feign HTTP client has a hard read-timeout (~6 min) that
# cannot be reliably overridden via Gradle DSL for large bundles.
# This script first attempts jreleaserDeploy; if that fails, it falls
# back to uploading the bundle via curl with a generous timeout,
# then polls the Central Portal for deployment status.
# -------------------------------------------------------------------

UPLOAD_TIMEOUT=${1:-900}          # curl upload timeout in seconds (default 15 min)
MAX_STATUS_CHECKS=${2:-100}       # max status poll attempts
STATUS_CHECK_INTERVAL=${3:-20}    # seconds between status polls

CENTRAL_API="https://central.sonatype.com/api/v1/publisher"

# Credentials from environment (set by GitHub Actions)
USERNAME="${JRELEASER_MAVENCENTRAL_USERNAME:?JRELEASER_MAVENCENTRAL_USERNAME not set}"
PASSWORD="${JRELEASER_MAVENCENTRAL_PASSWORD:?JRELEASER_MAVENCENTRAL_PASSWORD not set}"
AUTH_TOKEN=$(echo -n "${USERNAME}:${PASSWORD}" | base64)

# --- Step 1: Try jreleaserDeploy (signs artifacts and creates bundle) ---
echo "==> Attempting jreleaserDeploy (signs + bundles + uploads)..."
if ./gradlew jreleaserDeploy --no-daemon --stacktrace; then
  echo "==> jreleaserDeploy succeeded"
  exit 0
fi

echo "==> jreleaserDeploy failed (likely upload timeout). Falling back to curl upload."

# --- Step 2: Locate the bundle created by JReleaser ---
BUNDLE_DIR="build/jreleaser/deploy/mavenCentral/sonatype"
BUNDLE=$(find "$BUNDLE_DIR" -name "*.zip" -type f 2>/dev/null | head -1)

if [ -z "$BUNDLE" ]; then
  echo "ERROR: No bundle zip found in $BUNDLE_DIR"
  echo "Contents of build/jreleaser/deploy/:"
  find build/jreleaser/deploy/ -type f 2>/dev/null || true
  exit 1
fi

echo "==> Found bundle: $BUNDLE"

# --- Step 3: Upload bundle via curl with retries ---
upload_bundle() {
  local attempt=$1
  echo "==> Upload attempt $attempt — timeout ${UPLOAD_TIMEOUT}s"

  HTTP_RESPONSE=$(curl -s -w "\n%{http_code}" \
    --max-time "$UPLOAD_TIMEOUT" \
    --connect-timeout 60 \
    -X POST "$CENTRAL_API/upload" \
    -H "Authorization: Bearer $AUTH_TOKEN" \
    -F "bundle=@$BUNDLE;type=application/octet-stream" \
    -F "publishingType=AUTOMATIC" \
    2>&1) || true

  HTTP_CODE=$(echo "$HTTP_RESPONSE" | tail -1)
  RESPONSE_BODY=$(echo "$HTTP_RESPONSE" | sed '$d')

  echo "==> Upload response: HTTP $HTTP_CODE"

  if [ "$HTTP_CODE" = "201" ]; then
    DEPLOYMENT_ID="$RESPONSE_BODY"
    echo "==> Upload successful. Deployment ID: $DEPLOYMENT_ID"
    return 0
  else
    echo "==> Upload failed: $RESPONSE_BODY"
    return 1
  fi
}

DEPLOYMENT_ID=""
for attempt in 1 2 3 4 5; do
  if upload_bundle "$attempt"; then
    break
  fi
  if [ "$attempt" -eq 5 ]; then
    echo "ERROR: All upload attempts failed"
    exit 1
  fi
  echo "==> Retrying upload in 30 seconds..."
  sleep 30
done

# --- Step 4: Poll for deployment status ---
if [ -z "$DEPLOYMENT_ID" ]; then
  echo "ERROR: No deployment ID obtained"
  exit 1
fi

echo "==> Polling deployment status (max $MAX_STATUS_CHECKS checks, every ${STATUS_CHECK_INTERVAL}s)..."

for (( i=1; i<=MAX_STATUS_CHECKS; i++ )); do
  sleep "$STATUS_CHECK_INTERVAL"

  STATUS_RESPONSE=$(curl -s \
    --max-time 60 \
    -X POST "$CENTRAL_API/status?id=$DEPLOYMENT_ID" \
    -H "Authorization: Bearer $AUTH_TOKEN" \
    -H "Accept: application/json" \
    2>&1) || true

  DEPLOYMENT_STATE=$(echo "$STATUS_RESPONSE" | grep -o '"deploymentState":"[^"]*"' | head -1 | cut -d'"' -f4)

  echo "  [$i/$MAX_STATUS_CHECKS] Deployment state: ${DEPLOYMENT_STATE:-UNKNOWN}"

  case "$DEPLOYMENT_STATE" in
    PUBLISHED)
      echo "==> Deployment published successfully!"
      exit 0
      ;;
    FAILED)
      echo "ERROR: Deployment failed on Maven Central"
      echo "Response: $STATUS_RESPONSE"
      exit 1
      ;;
    VALIDATED|PUBLISHING)
      echo "  Deployment is progressing..."
      ;;
    PENDING|VALIDATING)
      echo "  Still processing..."
      ;;
    *)
      echo "  Unexpected state, continuing to poll..."
      ;;
  esac
done

echo "ERROR: Timed out waiting for deployment status after $MAX_STATUS_CHECKS checks"
exit 1
