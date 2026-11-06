## Install

**macOS**: `brew install --cask adriandeleon/tap/exampleapp`, or download the DMG below (`macos-arm64`
for Apple Silicon, `macos-x64` for Intel), open it and drag ExampleApp to Applications. The app is
signed with a Developer ID and notarized by Apple.

**Windows**: `winget install AdrianDeLeon.ExampleApp`, or download and run the `windows-x64` MSI.
The installer is not Authenticode signed yet, so SmartScreen may warn on first run.

**Linux**: the DEB (`sudo apt install ./ExampleApp-*.deb`) or the RPM (`sudo dnf install
./ExampleApp-*.rpm`) for your architecture (`linux-x64` or `linux-arm64`). Without root, the
`AppImage` runs as it is (`chmod +x` it first), and the `tar.gz` unpacks to a folder whose
`bin/ExampleApp` starts the app.

**Any OS with Java 25**: the `.jar` for your platform runs with `java -jar`.

Check a download against `checksums_sha256.txt`, or its build provenance with
`gh attestation verify <file> --repo adriandeleon/ExampleApp`. Linux downloads are also signed:
`gpg --import exampleapp-signing-key.asc`, then `gpg --verify <file>.asc <file>`.

