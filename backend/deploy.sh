#!/usr/bin/env bash
# Deploy drp-bible-proxy to Cloud Run. Idempotent — safe to re-run.
#
#   export PROJECT_ID=your-project-id
#   export REGION=us-central1            # optional
#   export API_BIBLE_KEY=<key>           # only needed on first run / rotation
#   ./deploy.sh
set -euo pipefail

SERVICE="drp-bible-proxy"
REGION="${REGION:-us-central1}"
SECRET="api-bible-key"

: "${PROJECT_ID:?set PROJECT_ID}"

PROJECT_NUMBER="$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')"
RUNTIME_SA="${PROJECT_NUMBER}-compute@developer.gserviceaccount.com"

echo "==> project $PROJECT_ID ($PROJECT_NUMBER), region $REGION"

echo "==> enabling APIs"
gcloud services enable \
  run.googleapis.com \
  cloudbuild.googleapis.com \
  artifactregistry.googleapis.com \
  secretmanager.googleapis.com \
  firestore.googleapis.com \
  firebaseappcheck.googleapis.com \
  --project "$PROJECT_ID" --quiet

echo "==> secret"
if ! gcloud secrets describe "$SECRET" --project "$PROJECT_ID" >/dev/null 2>&1; then
  : "${API_BIBLE_KEY:?secret does not exist yet — set API_BIBLE_KEY for the first deploy}"
  gcloud secrets create "$SECRET" --replication-policy=automatic \
    --project "$PROJECT_ID" --quiet
fi
if [[ -n "${API_BIBLE_KEY:-}" ]]; then
  printf '%s' "$API_BIBLE_KEY" | gcloud secrets versions add "$SECRET" \
    --data-file=- --project "$PROJECT_ID" --quiet
  echo "    added a new secret version"
else
  echo "    reusing existing secret version"
fi

echo "==> granting the runtime service account access"
gcloud secrets add-iam-policy-binding "$SECRET" \
  --member="serviceAccount:${RUNTIME_SA}" \
  --role="roles/secretmanager.secretAccessor" \
  --project "$PROJECT_ID" --quiet >/dev/null
# Required for BUDGET_BACKEND=firestore.
gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member="serviceAccount:${RUNTIME_SA}" \
  --role="roles/datastore.user" --quiet >/dev/null

echo "==> deploying"
# --allow-unauthenticated is correct here: the gate is App Check inside the service,
# not Cloud Run IAM. The app has no Google identity to present at the IAM layer.
gcloud run deploy "$SERVICE" \
  --source . \
  --project "$PROJECT_ID" \
  --region "$REGION" \
  --platform managed \
  --allow-unauthenticated \
  --service-account "$RUNTIME_SA" \
  --set-secrets "API_BIBLE_KEY=${SECRET}:latest" \
  --set-env-vars "FIREBASE_PROJECT_NUMBER=${PROJECT_NUMBER},FIREBASE_PROJECT_ID=${PROJECT_ID},BUDGET_BACKEND=firestore,ALLOWED_BIBLES=NKJV,MONTHLY_CALL_BUDGET=140000,POLICY_ON_ATTESTATION_FAIL=${POLICY_ON_ATTESTATION_FAIL:-allow}" \
  --min-instances 0 \
  --max-instances 4 \
  --concurrency 40 \
  --cpu 1 --memory 512Mi \
  --timeout 30s \
  --quiet

URL="$(gcloud run services describe "$SERVICE" --region "$REGION" \
        --project "$PROJECT_ID" --format='value(status.url)')"

cat <<EOF

==> deployed
    $URL

    smoke test (both should behave as described):
      curl -i $URL/healthz                                 # 200
      curl -i "$URL/v1/passage?bible=NKJV&ref=GEN.1"        # 401, no attestation

    NOTE: deployed with POLICY_ON_ATTESTATION_FAIL=${POLICY_ON_ATTESTATION_FAIL:-allow}.
    That is log-only mode. Watch for 'attestation_failed' in the logs to size the
    lockout, then redeploy with POLICY_ON_ATTESTATION_FAIL=deny to enforce.
EOF
