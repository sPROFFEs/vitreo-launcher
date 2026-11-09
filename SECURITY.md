# Security

Vitreo has not undergone an independent security audit. Android remains
responsible for secure authentication and platform permission enforcement.

## Reporting

Do not disclose exploit details, tokens, signing keys, passwords, private
notifications or full personal backups in a public issue. If GitHub private
vulnerability reporting is enabled, use the repository's Security tab. Otherwise,
ask the maintainer for a private reporting channel without posting sensitive
details.

For ordinary bugs, include version, OS/build and reproduction steps, with reviewed
logs. See [Troubleshooting](docs/guide/TROUBLESHOOTING.md) and [Privacy](PRIVACY.md).

## Maintainer responsibilities

Protect the publishing branch and review changes to Gradle, scripts and workflows
that receive signing secrets. Pull requests do not receive the private key and
do not use pull_request_target. GitHub Secrets cannot protect a key from build
code deliberately allowed to access it.

Keep an encrypted offline keystore backup and separate credentials. Never publish
a keystore to make builds easier. If compromise is suspected, stop publication
and investigate before issuing further APKs. Signing continuity and release
asset rules are in [Signing](docs/development/SIGNING.md) and
[Releasing](docs/development/RELEASING.md).
