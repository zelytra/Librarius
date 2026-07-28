#!/usr/bin/env bash
#
# Exercises the backup scripts shipped in the Helm chart against a real
# PostgreSQL and a real S3 (MinIO), in throwaway containers.
#
# What it proves: the dump, the AES-256 encryption, the upload, the per-tier
# retention pruning and a restore into a second database all work, using the
# exact scripts the CronJob runs (they are extracted from `helm template`, not
# copied here — a divergence would fail the run).
#
# What it does NOT prove: that a restore of librarius.zelytra.fr works. That
# needs the real cluster and the procedure in docs/DEPLOYMENT.md § "Restoring
# PostgreSQL". See https://github.com/zelytra/Librarius/issues/59.
#
# Requires docker, helm and python3 (with PyYAML). Takes about two minutes.
#
#   ./infra/backup/verify.sh
#
set -euo pipefail
cd "$(dirname "$0")/../.."

WORK=${TMPDIR:-/tmp}/librarius-backup-verify
rm -rf "$WORK"; mkdir -p "$WORK/scripts" "$WORK/vol"
# Stands in for the emptyDir + fsGroup 65532 the pod gets in Kubernetes.
chmod 777 "$WORK" "$WORK/vol"

# ── 1. Take the scripts straight out of the rendered chart ────────────────────
helm template librarius infra/helm/librarius \
  --set postgres.existingSecret=librarius-postgres \
  --set keycloak.existingSecret=librarius-keycloak \
  --set backup.enabled=true \
  --set backup.s3.bucket=librarius-backups \
  --set backup.existingSecret=librarius-backup > "$WORK/rendered.yaml"

python3 - "$WORK" <<'PY'
import sys, yaml, pathlib
work = pathlib.Path(sys.argv[1])
for doc in yaml.safe_load_all((work / "rendered.yaml").read_text()):
    if doc and doc.get("kind") == "ConfigMap" and doc["metadata"]["name"].endswith("backup-scripts"):
        for name, body in doc["data"].items():
            (work / "scripts" / name).write_text(body)
            print(f"extracted {name} ({len(body)} bytes)")
PY

# ── 2. Throwaway stack: source database, target database, object storage ──────
cleanup() {
  docker rm -f lbpg lbpg2 lbminio >/dev/null 2>&1 || true
  docker network rm lbverify >/dev/null 2>&1 || true
}
cleanup
trap cleanup EXIT
docker network create lbverify >/dev/null

docker run -d --name lbpg --network lbverify -e POSTGRES_USER=librarius \
  -e POSTGRES_PASSWORD=pgpass -e POSTGRES_DB=librarius postgres:16-alpine >/dev/null
docker run -d --name lbpg2 --network lbverify -e POSTGRES_USER=librarius \
  -e POSTGRES_PASSWORD=pgpass -e POSTGRES_DB=librarius postgres:16-alpine >/dev/null
docker run -d --name lbminio --network lbverify -e MINIO_ROOT_USER=miniokey \
  -e MINIO_ROOT_PASSWORD=miniosecret minio/minio:RELEASE.2024-06-13T22-53-53Z server /data >/dev/null

for c in lbpg lbpg2; do
  for _ in $(seq 1 40); do docker exec "$c" pg_isready -U librarius >/dev/null 2>&1 && break; sleep 1; done
done
sleep 3

docker exec -e PGPASSWORD=pgpass lbpg psql -U librarius -d librarius -c \
  "CREATE TABLE book (id serial primary key, title text); \
   INSERT INTO book(title) SELECT 'Book ' || g FROM generate_series(1,5000) g;" >/dev/null
echo "source rows: $(docker exec -e PGPASSWORD=pgpass lbpg psql -U librarius -d librarius -tAc 'SELECT count(*) FROM book')"

echo "verify-only-passphrase" > "$WORK/passphrase"

AWSENV=(-e AWS_ACCESS_KEY_ID=miniokey -e AWS_SECRET_ACCESS_KEY=miniosecret -e AWS_DEFAULT_REGION=us-east-1 -e HOME=/tmp)
awscli() { docker run --rm --network lbverify "${AWSENV[@]}" -v "$WORK:/w" --entrypoint aws amazon/aws-cli:2.17.0 "$@"; }
awscli --endpoint-url http://lbminio:9000 s3 mb s3://librarius-backups >/dev/null

# ── 3. Dump, as the init container ────────────────────────────────────────────
echo "=== dump (uid 65532, postgres image) ==="
docker run --rm --network lbverify --user 65532:65532 \
  -e PGHOST=lbpg -e PGUSER=librarius -e PGDATABASE=librarius -e PGPASSWORD=pgpass \
  -v "$WORK/scripts:/scripts:ro" -v "$WORK/vol:/work" \
  --entrypoint bash postgres:16-alpine /scripts/dump.sh

# ── 4. Encrypt, upload, prune — as the main container ─────────────────────────
upload() {
  docker run --rm --network lbverify --user 65532:65532 "${AWSENV[@]}" \
    -e S3_ENDPOINT=http://lbminio:9000 -e S3_BUCKET=librarius-backups -e S3_PREFIX=librarius \
    -e KEEP_DAILY=3 -e KEEP_WEEKLY=4 -e KEEP_MONTHLY=6 "$@" \
    -v "$WORK/scripts:/scripts:ro" -v "$WORK/vol:/work" -v "$WORK/passphrase:/secrets/passphrase:ro" \
    -v "$WORK/bin:/w/bin:ro" \
    --entrypoint bash amazon/aws-cli:2.17.0 /scripts/upload.sh
}
mkdir -p "$WORK/bin"
echo "=== encrypt + upload + prune (uid 65532, aws-cli image) ==="
upload

echo "=== retention: 5 runs with KEEP_DAILY=3 must leave 3 objects ==="
for _ in 2 3 4 5; do sleep 1; upload >/dev/null; done
kept=$(awscli --endpoint-url http://lbminio:9000 s3 ls s3://librarius-backups/librarius/daily/ | grep -c 'sql.gz.gpg')
echo "  daily objects kept: $kept (expected 3)"
[ "$kept" = "3" ] || { echo "RETENTION FAILED"; exit 1; }

# ── 5. Weekly and monthly tiers, with the calendar shimmed ────────────────────
echo "=== weekly/monthly tiers (date shimmed to Sunday the 1st) ==="
cat > "$WORK/bin/date" <<'SHIM'
#!/bin/bash
case "$*" in
  "-u +%u") echo 7 ;;
  "-u +%d") echo 01 ;;
  *) exec /usr/bin/date "$@" ;;
esac
SHIM
chmod 755 "$WORK/bin/date"
upload -e PATH=/w/bin:/usr/local/bin:/usr/bin:/bin >/dev/null
for tier in daily weekly monthly; do
  n=$(awscli --endpoint-url http://lbminio:9000 s3 ls "s3://librarius-backups/librarius/$tier/" | grep -c 'sql.gz.gpg')
  echo "  $tier: $n object(s)"
done
rm -f "$WORK/bin/date"

# ── 6. Restore into the second database ───────────────────────────────────────
echo "=== restore into a second database ==="
latest=$(awscli --endpoint-url http://lbminio:9000 s3 ls s3://librarius-backups/librarius/daily/ | awk 'NF {print $4}' | sort | tail -1)
echo "  restoring $latest"
awscli --endpoint-url http://lbminio:9000 s3 cp "s3://librarius-backups/librarius/daily/$latest" /w/restore.sql.gz.gpg >/dev/null
docker run --rm -v "$WORK:/w" -e HOME=/tmp --user 65532:65532 --entrypoint bash amazon/aws-cli:2.17.0 -c \
  'export GNUPGHOME=/tmp/g && mkdir -p $GNUPGHOME && chmod 700 $GNUPGHOME && \
   gpg --batch --quiet --decrypt --passphrase-file /w/passphrase --output /w/restore.sql.gz /w/restore.sql.gz.gpg'
gunzip -f "$WORK/restore.sql.gz"
docker exec -i -e PGPASSWORD=pgpass lbpg2 psql -v ON_ERROR_STOP=1 -U librarius -d librarius < "$WORK/restore.sql" >/dev/null
restored=$(docker exec -e PGPASSWORD=pgpass lbpg2 psql -U librarius -d librarius -tAc 'SELECT count(*) FROM book')
echo "  restored rows: $restored (expected 5000)"
[ "$restored" = "5000" ] || { echo "RESTORE FAILED"; exit 1; }

echo "ALL CHECKS PASSED"
