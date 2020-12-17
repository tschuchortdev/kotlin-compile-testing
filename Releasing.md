# Making a release

The CI is configured to publish a release to Sonatype Nexus on every push to master. If the version name in gradle.properties has the SNAPSHOT suffix, then a snapshot release will be published on Sonatype and a pre-release with tag "latest" will be created/replaced on Github. If, on the other hand, the version name does not have the SNAPSHOT suffix, a proper release with that version name will be made on Sonatype and Github and subsequently, the version name in gradle.properties is updated to the SNAPSHOT of the next semantic patch version. In both cases, the Github release notes are created automatically from commit messages. Thus, to make a release it is sufficient (and recommended) to make a commit to master which only updates gradle.properties to the desired version. If a release is published manually and not through CI, it is important that the version be set to the next SNAPSHOT patch version, so that snapshot releases can continue to be published by the CI (otherwise CI will fail because the version already exist).


## Manual release

Releasing is done through the `nexus-staging` Gradle plugin in combination with the `signing` and `maven-publish` plugins.

See: https://proandroiddev.com/publishing-a-maven-artifact-3-3-step-by-step-instructions-to-mavencentral-publishing-bd661081645d

Steps:
- `./gradlew publish`
- `./gradlew closeAndReleaseRepository` (not necessary for SNAPSHOT releases)

The following environment variables have to be defined:
- `SONATYPE_NEXUS_USERNAME`
- `SONATYPE_NEXUS_PASSWORD`

In `/home/<user>/.gradle/gradle.properties` the following properties have to be defined:
- `signing.keyId` (last 8 digits of the RSA certify/signing-key's id/fingerprint)
- `signing.password` (password for the GPG signing key)
- `signing.secretKeyRingFile` (path to the armored GPG signing key)
