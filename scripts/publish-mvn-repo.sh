#!/usr/bin/env sh
# Publish the library modules to the GitHub hosted Maven repository sfe4j/mvn-repo.
#
# Usage:
#   sh scripts/publish-mvn-repo.sh          publish the version declared in pom.xml
#   sh scripts/publish-mvn-repo.sh -f       publish even if that version already exists
#
# Environment:
#   MVN_REPO_URL      repository to push to, default git@github.com:sfe4j/mvn-repo.git
#   MVN_REPO_WORK_DIR scratch directory, default a temporary directory
#
# The artifacts are deployed straight into a clone of the repository, so the
# existing maven-metadata.xml keeps every version that was published before.
# Only the three library modules are published, the sample application is not.
set -eu

ROOT=$(cd "$(dirname "$0")/.." && pwd)
MODULES="sfe4j-core,sfe4j-spring-boot,sfe4j-spring-boot-starter"
MVN_REPO_URL=${MVN_REPO_URL:-git@github.com:sfe4j/mvn-repo.git}
WORK_DIR=${MVN_REPO_WORK_DIR:-$(mktemp -d)}
FORCE=${1:-}

VERSION=$(sed -n 's/^    <version>\(.*\)<\/version>$/\1/p' "$ROOT/pom.xml" | head -1)
if [ -z "$VERSION" ]; then
    echo "cannot read the project version from $ROOT/pom.xml" >&2
    exit 1
fi

rm -rf "$WORK_DIR/mvn-repo"
mkdir -p "$WORK_DIR"
git clone --depth 1 "$MVN_REPO_URL" "$WORK_DIR/mvn-repo"

for module in $(echo "$MODULES" | tr ',' ' '); do
    if [ -d "$WORK_DIR/mvn-repo/com/amoylabs/$module/$VERSION" ] && [ "$FORCE" != "-f" ]; then
        echo "$module $VERSION is already published, pass -f to publish it again" >&2
        exit 1
    fi
done

echo "deploying $VERSION into $WORK_DIR/mvn-repo"
cd "$ROOT/sfe4j-core"
sh mvnw -f ../pom.xml -pl "$MODULES" -DskipTests \
    -Dsfe4j.mvn-repo.dir="$WORK_DIR/mvn-repo" \
    deploy

cd "$WORK_DIR/mvn-repo"
git add -A
if git diff --cached --quiet; then
    echo "nothing changed, nothing to publish"
    exit 0
fi

git commit -m "Release $VERSION"
git push origin HEAD
echo "published $VERSION to $MVN_REPO_URL"
