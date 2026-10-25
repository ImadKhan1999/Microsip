#!/usr/bin/env bash
# Dumps the public Java API of a Linphone SDK Android release with javap.
# Used to produce docs/reference/linphone-sdk-*-api.txt so agents without network
# access to download.linphone.org can still check exact method signatures.
# Usage: tools/dump-linphone-api.sh 5.5.35 > api.txt   (needs curl, unzip, javap)
set -euo pipefail
V="${1:?linphone sdk version}"
T=$(mktemp -d)
curl -sfL -o "$T/sdk.aar" "https://download.linphone.org/maven_repository/org/linphone/linphone-sdk-android/$V/linphone-sdk-android-$V.aar"
cd "$T" && unzip -q sdk.aar classes.jar && mkdir c && cd c && unzip -q ../classes.jar
for cls in $(ls org/linphone/core/*.class | sed 's/\.class$//; s#/#.#g' | grep -v 'Impl$\|Impl\$'); do
  echo "===== ${cls#org.linphone.core.}"
  javap -cp . "$cls" | grep -v '^Compiled' | sed 's/org\.linphone\.core\.//g; s/java\.lang\.//g'
done
