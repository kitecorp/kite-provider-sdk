#!/usr/bin/env bash
# Runs check.sh against throwaway keys and compares its whole output and its exit code with the
# expected text. Run from anywhere: bash .github/actions/check-publish-secrets/check.test.sh
# CI runs it in .github/workflows/check-publish-secrets.yml.
set -uo pipefail

here=$(cd "$(dirname "$0")" && pwd)
keys=$(mktemp -d)
# One download of the Bouncy Castle jars for every case
export BOUNCY_CASTLE_DIR="${BOUNCY_CASTLE_DIR:-$keys/bouncy-castle}"
trap 'GNUPGHOME="$keys" gpgconf --kill all >/dev/null 2>&1; rm -rf "$keys"' EXIT

# A throwaway ed25519 signing key, the kind the real release key is, with the passphrase given as
# $2 (none by default). Prints nothing; the key lands in the throwaway keyring.
new_key() {
  GNUPGHOME="$keys" gpg --batch --quiet --pinentry-mode loopback --passphrase "${2:-}" \
    --quick-gen-key "$1 <$1@example.invalid>" ed25519 sign never 2>/dev/null
}

key_id() {
  GNUPGHOME="$keys" gpg --batch --with-colons --list-keys "$1@example.invalid" 2>/dev/null \
    | awk -F: '$1 == "pub" { print $5; exit }'
}

public_key() {
  GNUPGHOME="$keys" gpg --batch --armor --export "$1@example.invalid"
}

private_key() {
  GNUPGHOME="$keys" gpg --batch --pinentry-mode loopback --passphrase "${2:-}" \
    --armor --export-secret-keys "$1@example.invalid"
}

new_key first
new_key second
new_key locked "a passphrase"
first_id=$(key_id first)
second_id=$(key_id second)
first_public=$(public_key first)
first_private=$(private_key first)
second_private=$(private_key second)
locked_public=$(public_key locked)
locked_private=$(private_key locked "a passphrase")

# The private block with its body cut short, as a secret pasted in part would be
first_private_cut=$(printf '%s\n' "$first_private" | awk 'NR <= 4 || /^-----END/')
# Every line indented, as YAML or a copied snippet can leave a key
first_private_indented=$(printf '%s\n' "$first_private" | sed 's/^/  /')
# Only the lines after the header indented: JReleaser reads this, so the check must too
first_private_body_indented=$(printf '%s\n' "$first_private" | awk 'NR == 1 { print; next } { print "  " $0 }')

failures=0

# check DESCRIPTION EXPECTED_EXIT EXPECTED_OUTPUT USERNAME PASSWORD PUBLIC_KEY PRIVATE_KEY
check() {
  local description=$1 expected_exit=$2 expected_output=$3
  local output exit_code
  output=$(env MAVEN_USERNAME="$4" MAVEN_PASSWORD="$5" GPG_PUBLIC_KEY="$6" GPG_PRIVATE_KEY="$7" \
    bash "$here/check.sh" 2>&1)
  exit_code=$?
  if [ "$exit_code" = "$expected_exit" ] && [ "$output" = "$expected_output" ]; then
    echo "ok   $description"
  else
    failures=$((failures + 1))
    echo "FAIL $description"
    echo "  expected exit $expected_exit and:"
    printf '    %s\n' "$expected_output"
    echo "  got exit $exit_code and:"
    printf '    %s\n' "$output"
  fi
}

check "two halves of one key pass" 0 \
  "The publishing secrets are set, and GPG_PUBLIC_KEY and GPG_PRIVATE_KEY are both key ${first_id}" \
  user password "$first_public" "$first_private"

check "empty secrets are all named" 1 \
  "::error::Secrets missing or empty for this repository: MAVEN_PASSWORD GPG_PRIVATE_KEY" \
  user "" "$first_public" ""

check "a public key that is not a key" 1 \
  "::error::GPG_PUBLIC_KEY is not an armored OpenPGP public key" \
  user password "not a key" "$first_private"

check "a private key cut short" 1 \
  "::error::GPG_PRIVATE_KEY is not an armored OpenPGP private key" \
  user password "$first_public" "$first_private_cut"

check "an indented private key" 1 \
  "::error::GPG_PRIVATE_KEY is not an armored OpenPGP private key" \
  user password "$first_public" "$first_private_indented"

check "a private key indented after its header still passes" 0 \
  "The publishing secrets are set, and GPG_PUBLIC_KEY and GPG_PRIVATE_KEY are both key ${first_id}" \
  user password "$first_public" "$first_private_body_indented"

check "a public key given as the private key" 1 \
  "::error::GPG_PRIVATE_KEY is not an armored OpenPGP private key" \
  user password "$first_public" "$first_public"

check "a private key given as the public key" 1 \
  "::error::GPG_PUBLIC_KEY is not an armored OpenPGP public key" \
  user password "$first_private" "$first_private"

check "both keys broken are both named" 1 \
  "::error::GPG_PUBLIC_KEY is not an armored OpenPGP public key
::error::GPG_PRIVATE_KEY is not an armored OpenPGP private key" \
  user password "not a key" "not a key either"

check "a private key locked with a passphrase" 1 \
  "::error::GPG_PRIVATE_KEY needs a passphrase, and the release signs without one" \
  user password "$locked_public" "$locked_private"

check "halves of two different keys" 1 \
  "::error::GPG_PUBLIC_KEY is key ${first_id} but GPG_PRIVATE_KEY is key ${second_id}; they must be the two halves of one key" \
  user password "$first_public" "$second_private"

if [ "$failures" -gt 0 ]; then
  echo "$failures failed"
  exit 1
fi
echo "all passed"
