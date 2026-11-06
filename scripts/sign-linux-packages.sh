#!/usr/bin/env bash
# Signs the Linux downloads in each directory with the release's OpenPGP key, in place:
#   .rpm   embedded (rpmsign), checked by `rpm -K`, dnf and zypper once the key is imported
#   .deb   embedded `_gpgorigin` member in debsigs' format, checked by debsig-verify
#   every  .deb .rpm .AppImage .tar.gz .jar gets a detached, armoured `<file>.asc`
#
# The key comes from the environment: EXAMPLEAPP_GPG_KEY (ASCII-armoured secret key) and
# EXAMPLEAPP_GPG_PASSPHRASE if it has one. Its public half is written to <public-key-out>. When
# EXAMPLEAPP_GPG_EXPECTED_KEY names a public key file (packaging/linux/signing-key.asc), the secret key
# must be that one, or the release would verify against nothing it publishes elsewhere.
#
# The deb is signed here rather than by debsigs, which only knows gzip members and silently signs
# debian-binary alone for dpkg-deb's .xz ones. Needs gpg, ar and rpmsign.
#
# Usage: sign-linux-packages.sh <public-key-out> <dir>...
set -euo pipefail

public_out="${1:?public key output required}"
shift
[ $# -gt 0 ] || { echo "usage: $0 <public-key-out> <dir>..." >&2; exit 2; }
[ -n "${EXAMPLEAPP_GPG_KEY:-}" ] || { echo "::error::EXAMPLEAPP_GPG_KEY is not set"; exit 1; }

GNUPGHOME="$(mktemp -d)"
export GNUPGHOME
trap 'gpgconf --kill gpg-agent 2>/dev/null || true; rm -rf "$GNUPGHOME"' EXIT

# Every gpg below, including the one rpmsign starts, reads this. Loopback takes the passphrase from
# the file rather than from a pinentry no runner can answer.
printf '%s\n' batch no-tty "pinentry-mode loopback" > "$GNUPGHOME/gpg.conf"
if [ -n "${EXAMPLEAPP_GPG_PASSPHRASE:-}" ]; then
    printf '%s' "$EXAMPLEAPP_GPG_PASSPHRASE" > "$GNUPGHOME/passphrase"
    echo "passphrase-file $GNUPGHOME/passphrase" >> "$GNUPGHOME/gpg.conf"
fi

printf '%s\n' "$EXAMPLEAPP_GPG_KEY" | gpg --quiet --import
fpr=$(gpg --with-colons --list-secret-keys | awk -F: '$1 == "fpr" { print $10; exit }')
[ -n "$fpr" ] || { echo "::error::EXAMPLEAPP_GPG_KEY holds no secret key"; exit 1; }
echo "Signing with $fpr"

expected="${EXAMPLEAPP_GPG_EXPECTED_KEY:-}"
if [ -n "$expected" ]; then
    [ -f "$expected" ] || { echo "::error::no public key at $expected"; exit 1; }
    want=$(gpg --with-colons --import-options show-only --import "$expected" |
        awk -F: '$1 == "fpr" { print $10; exit }')
    [ "$want" = "$fpr" ] || { echo "::error::EXAMPLEAPP_GPG_KEY is $fpr, but $expected is $want"; exit 1; }
fi
gpg --armor --export "$fpr" > "$public_out"

# debsigs' format: a detached signature over the three members dpkg reads, concatenated in archive
# order, appended as a member named _gpgorigin.
sign_deb() {
    local deb="$1" members=() member
    rm -rf "$GNUPGHOME/deb" && mkdir "$GNUPGHOME/deb"
    (cd "$GNUPGHOME/deb" && ar x "$deb")
    while read -r member; do
        case "$member" in
            debian-binary|control.tar*|data.tar*) members+=("$GNUPGHOME/deb/$member") ;;
            _gpgorigin) echo "::error::$(basename "$deb") is already signed"; exit 1 ;;
        esac
    done < <(ar t "$deb")
    [ "${#members[@]}" = 3 ] || { echo "::error::$(basename "$deb") does not have the three members of a deb"; exit 1; }
    cat "${members[@]}" | gpg --detach-sign --local-user "$fpr" --output "$GNUPGHOME/deb/_gpgorigin"
    ar q "$deb" "$GNUPGHOME/deb/_gpgorigin"
}

shopt -s nullglob
signed=0
for dir in "$@"; do
    dir="$(cd "$dir" && pwd)"   # absolute: sign_deb works from a directory of its own
    for rpm in "$dir"/*.rpm; do
        # rpm before 4.19 reads _gpg_name, from 4.19 _openpgp_sign_id; both are given.
        rpmsign --addsign --define "_gpg_path $GNUPGHOME" --define "_gpg_name $fpr" \
            --define "_openpgp_sign_id $fpr" "$rpm"
    done
    for deb in "$dir"/*.deb; do
        sign_deb "$deb"
    done
    # After the embedded signatures, which change the files they are added to.
    for file in "$dir"/*.deb "$dir"/*.rpm "$dir"/*.AppImage "$dir"/*.tar.gz "$dir"/*.jar; do
        gpg --armor --detach-sign --local-user "$fpr" --output "$file.asc" "$file"
        echo "signed $(basename "$file")"
        signed=$((signed + 1))
    done
done
[ "$signed" -gt 0 ] || { echo "::error::nothing to sign in $*"; exit 1; }
