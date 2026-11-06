# {{jreleaserCreationStamp}}
# yaml-language-server: $schema=https://aka.ms/winget-manifest.installer.1.9.0.schema.json

# Replaces the stock template, which wrote empty Scope, InstallModes and UpgradeBehavior keys (empty
# is not absent to the schema). No ProductCode: winget derives it, and a wrong one breaks upgrades.

PackageIdentifier: {{wingetPackageIdentifier}}
PackageVersion: {{wingetPackageVersion}}
MinimumOSVersion: {{wingetMinimumOsVersion}}
InstallerType: msi
Scope: user
UpgradeBehavior: install
ReleaseDate: {{wingetReleaseDate}}
Installers:
  - Architecture: {{wingetInstallerArchitecture}}
    InstallerUrl: {{distributionUrl}}
    InstallerSha256: {{distributionChecksumSha256}}
ManifestType: installer
ManifestVersion: 1.9.0
