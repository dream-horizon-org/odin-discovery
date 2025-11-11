#!/usr/bin/env bash
set -e

release_version=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
mvn versions:set -DnextSnapshot=true
bump_version=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)

git add pom.xml
git commit -m "chore: release version ${release_version} and bump version to ${bump_version} [skip ci]"

# Get the default branch name passed from the workflow
# Fallback to 'main' if not set
default_branch="${DEFAULT_BRANCH:-main}"

echo "Pushing version bump to branch: ${default_branch}"

# Push to the default branch (we're in detached HEAD state during release)
git push origin HEAD:refs/heads/"${default_branch}"
