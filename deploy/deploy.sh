#!/usr/bin/env bash
#
# Puts a release built on GitHub (.github/workflows/release.yml) live on this
# server, without building anything here. Run it from the site's folder:
#
#   cd /var/www/Vhost/servproware.appointware.com
#   ./deploy.sh v1.0.0
#
# Going back is the same command with the older version.
#
# What it changes:
#   - backend/servicepros-backend.jar, in place (the old one is kept as
#     .previous), and then the backend's PM2 process is restarted;
#   - app/admin, the web app's server, which ecosystem.config.js runs.
# It never touches the database or backend/.env - the backend reads its
# database, logins, mail and Twilio settings from there when it starts.
#
# Needs, once: the GitHub CLI (gh; a login only if the repository is private),
# backend/.env filled in (from backend/.env.example in the repository), and
# deploy.conf and ecosystem.config.js in this folder - copied from the
# .example files beside this script.
#
# The first run starts the two PM2 processes from ecosystem.config.js; after
# that it just restarts them.
set -euo pipefail

VERSION="${1:?Usage: ./deploy.sh <version>, for example ./deploy.sh v1.0.0}"
SITE_DIR="$(pwd)"

if [ ! -f "$SITE_DIR/deploy.conf" ]; then
  echo "ERROR: no deploy.conf in $SITE_DIR - copy deploy.conf.example to it and fill it in." >&2
  exit 1
fi
if [ ! -f "$SITE_DIR/ecosystem.config.js" ]; then
  echo "ERROR: no ecosystem.config.js in $SITE_DIR - copy ecosystem.config.js.example to it and fill it in." >&2
  exit 1
fi
# shellcheck disable=SC1091
source "$SITE_DIR/deploy.conf"   # REPO, BACKEND_NAME, BACKEND_JAR, ADMIN_NAME

if [ ! -f "$SITE_DIR/backend/.env" ]; then
  echo "ERROR: no backend/.env in $SITE_DIR - the backend needs its database and other settings there." >&2
  exit 1
fi
JAR="$SITE_DIR/$BACKEND_JAR"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "==> Downloading $VERSION from $REPO"
gh release download "$VERSION" --repo "$REPO" --dir "$TMP" \
  --pattern 'backend.jar' --pattern 'admin.tar.gz'

# Unpacked first, beside the running copy, so a broken download stops here
# with the site still on the old version.
mkdir -p "$SITE_DIR/app" "$(dirname "$JAR")"
rm -rf "$SITE_DIR/app/admin.new"
mkdir "$SITE_DIR/app/admin.new"
tar -xzf "$TMP/admin.tar.gz" -C "$SITE_DIR/app/admin.new"

echo "==> Backend"
# Copied beside the jar and renamed over it: a running Java keeps reading the
# old file until the restart, instead of having it change underneath it.
if [ -f "$JAR" ]; then cp -p "$JAR" "$JAR.previous"; fi
cp "$TMP/backend.jar" "$JAR.new"
mv -f "$JAR.new" "$JAR"

echo "==> Web app"
rm -rf "$SITE_DIR/app/admin.old"
if [ -d "$SITE_DIR/app/admin" ]; then mv "$SITE_DIR/app/admin" "$SITE_DIR/app/admin.old"; fi
mv "$SITE_DIR/app/admin.new" "$SITE_DIR/app/admin"

for name in "$BACKEND_NAME" "$ADMIN_NAME"; do
  if pm2 describe "$name" > /dev/null 2>&1; then
    pm2 restart "$name"
  else
    pm2 start "$SITE_DIR/ecosystem.config.js" --only "$name"
  fi
done
rm -rf "$SITE_DIR/app/admin.old"

pm2 save
echo "$VERSION" > "$SITE_DIR/app/VERSION"
echo "==> Done: $VERSION is live in $SITE_DIR (the backend takes about a minute to come up)"
