#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

FAKE_BIN="${TMP_DIR}/bin"
FAKE_ROOT="${TMP_DIR}/root"
LOG_FILE="${TMP_DIR}/calls.log"

mkdir -p \
  "${FAKE_BIN}" \
  "${FAKE_ROOT}/etc/nginx/sites-available" \
  "${FAKE_ROOT}/etc/nginx/sites-enabled" \
  "${FAKE_ROOT}/etc/facecheck/tls"

ENV_FILE="${TMP_DIR}/facecheck.env"
printf '%s\n' 'FACECHECK_BACKEND_HOST_PORT=19090' > "${ENV_FILE}"
printf '%s\n' 'FACECHECK_TLS_SUBJECT_ALT_NAME=IP:203.0.113.10' >> "${ENV_FILE}"

export LOG_FILE

cat > "${FAKE_BIN}/apt-get" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf 'apt-get %s\n' "$*" >> "${LOG_FILE:?}"
if [[ "${1:-}" == "install" && "${2:-}" == "-y" && "${3:-}" == "nginx" ]]; then
cat > "$(dirname "$0")/nginx" <<'INNER'
#!/usr/bin/env bash
set -euo pipefail
printf 'nginx %s\n' "$*" >> "${LOG_FILE:?}"
INNER
chmod +x "$(dirname "$0")/nginx"
fi
EOF

cat > "${FAKE_BIN}/systemctl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf 'systemctl %s\n' "$*" >> "${LOG_FILE:?}"
EOF

chmod +x "${FAKE_BIN}/apt-get" "${FAKE_BIN}/systemctl"

cat > "${FAKE_BIN}/openssl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf 'openssl %s\n' "$*" >> "${LOG_FILE:?}"
while (($#)); do
  case "$1" in
    -keyout)
      shift
      printf 'fake-key\n' > "$1"
      ;;
    -out)
      shift
      printf 'fake-cert\n' > "$1"
      ;;
  esac
  shift || true
done
EOF

chmod +x "${FAKE_BIN}/openssl"

PATH="${FAKE_BIN}:${PATH}" \
FACECHECK_ENV_FILE="${ENV_FILE}" \
FACECHECK_NGINX_AVAILABLE_DIR="${FAKE_ROOT}/etc/nginx/sites-available" \
FACECHECK_NGINX_ENABLED_DIR="${FAKE_ROOT}/etc/nginx/sites-enabled" \
FACECHECK_TLS_DIR="${FAKE_ROOT}/etc/facecheck/tls" \
bash "${REPO_ROOT}/deploy/facecheck/scripts/bootstrap_host.sh"

test -f "${FAKE_ROOT}/etc/nginx/sites-available/facecheck-campus.conf"
test -L "${FAKE_ROOT}/etc/nginx/sites-enabled/facecheck-campus.conf"
test -f "${FAKE_ROOT}/etc/facecheck/tls/facecheck-selfsigned.crt"
test -f "${FAKE_ROOT}/etc/facecheck/tls/facecheck-selfsigned.key"

if ! grep -q 'proxy_pass http://127.0.0.1:19090;' "${FAKE_ROOT}/etc/nginx/sites-available/facecheck-campus.conf"; then
  echo "expected nginx proxy_pass to honor FACECHECK_BACKEND_HOST_PORT from env file" >&2
  exit 1
fi

if ! grep -q 'listen 443 ssl;' "${FAKE_ROOT}/etc/nginx/sites-available/facecheck-campus.conf"; then
  echo "expected nginx to expose HTTPS with the self-signed certificate" >&2
  exit 1
fi

if ! grep -q '121.236.0.0/16 1;' "${FAKE_ROOT}/etc/nginx/sites-available/facecheck-campus.conf"; then
  echo "expected nginx allowlist to include 121.236.0.0/16" >&2
  exit 1
fi

if ! grep -q '58.192.0.0/16 1;' "${FAKE_ROOT}/etc/nginx/sites-available/facecheck-campus.conf"; then
  echo "expected nginx allowlist to include 58.192.0.0/16" >&2
  exit 1
fi

if ! grep -q '^openssl req -x509' "${LOG_FILE}"; then
  echo "expected bootstrap script to generate a self-signed certificate" >&2
  exit 1
fi

if ! grep -q 'subjectAltName=IP:203.0.113.10' "${LOG_FILE}"; then
  echo "expected self-signed certificate to include the configured SAN" >&2
  exit 1
fi

if ! grep -q '^apt-get update$' "${LOG_FILE}"; then
  echo "expected bootstrap script to update apt indexes before installing nginx" >&2
  exit 1
fi

if ! grep -q '^apt-get install -y nginx$' "${LOG_FILE}"; then
  echo "expected bootstrap script to install nginx when command is missing" >&2
  exit 1
fi

if ! grep -q '^nginx -t$' "${LOG_FILE}"; then
  echo "expected bootstrap script to run nginx -t" >&2
  exit 1
fi

if ! grep -q '^systemctl enable --now nginx$' "${LOG_FILE}"; then
  echo "expected bootstrap script to enable nginx" >&2
  exit 1
fi

if ! grep -q '^systemctl reload nginx$' "${LOG_FILE}"; then
  echo "expected bootstrap script to reload nginx" >&2
  exit 1
fi
