#!/usr/bin/env bash
# Checks the four Maven Central publishing secrets before a release checks out or builds anything
# (kitecorp/kite-provider-sdk#11). JReleaser reports any key it cannot read, public or private, as
# "Did not find public key for signing", and only after the build: a broken GPG_PRIVATE_KEY then
# looks like a broken public key. This names the secret that is wrong, in seconds.
#
# The values come from the environment (MAVEN_USERNAME, MAVEN_PASSWORD, GPG_PUBLIC_KEY,
# GPG_PRIVATE_KEY), never from arguments, so they stay out of the process list; nothing here prints
# them. Needs java 11 or newer on the PATH and curl. BOUNCY_CASTLE_DIR, when set, is where the Bouncy
# Castle jars are kept between runs; otherwise they go to a temporary folder.
set -uo pipefail

here=$(cd "$(dirname "$0")" && pwd)

missing=""
for name in MAVEN_USERNAME MAVEN_PASSWORD GPG_PUBLIC_KEY GPG_PRIVATE_KEY; do
  # An organisation secret that this repository may not read arrives as an empty string
  if [ -z "${!name:-}" ]; then
    missing="${missing} ${name}"
  fi
done
if [ -n "$missing" ]; then
  echo "::error::Secrets missing or empty for this repository:${missing}"
  exit 1
fi

if [ -n "${BOUNCY_CASTLE_DIR:-}" ]; then
  jars=$BOUNCY_CASTLE_DIR
  mkdir -p "$jars"
else
  jars=$(mktemp -d)
  trap 'rm -rf "$jars"' EXIT
fi

sha256() {
  if command -v sha256sum >/dev/null; then sha256sum "$1"; else shasum -a 256 "$1"; fi | cut -c1-64
}

# The Bouncy Castle that JReleaser 1.23.0 ships, as Maven Central's .sha256 files list it
bc_version=1.83
classpath=""
while read -r artifact sum; do
  jar="$jars/$artifact-$bc_version.jar"
  if [ ! -f "$jar" ] || [ "$(sha256 "$jar")" != "$sum" ]; then
    if ! curl -fsSL --retry 3 -o "$jar" \
        "https://repo1.maven.org/maven2/org/bouncycastle/$artifact/$bc_version/$artifact-$bc_version.jar"; then
      echo "::error::Could not download $artifact $bc_version from Maven Central"
      exit 1
    fi
    if [ "$(sha256 "$jar")" != "$sum" ]; then
      rm -f "$jar"
      echo "::error::$artifact $bc_version from Maven Central does not have the expected SHA-256"
      exit 1
    fi
  fi
  classpath="$classpath${classpath:+:}$jar"
done <<'JARS'
bcprov-jdk18on 82cf3a2af766c3bc874f6d36b9f20a8b99a8f09762dc776e8a227a45d8daaafb
bcutil-jdk18on ee7d0eb4e74de70a735f7fb36b604dd5c6ad35720d50b914604db042114a0185
bcpg-jdk18on 4077fd4517761c98a81944c70a376ce73f4eb3e44c03db1eb5d699fc28ab48aa
JARS

java -cp "$classpath" "$here/CheckSigningKeys.java"
