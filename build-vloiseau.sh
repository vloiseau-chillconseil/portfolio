#!/usr/bin/env bash
set -euo pipefail

if [[ -n "$(git status --porcelain)" ]]; then
  echo "Working tree is not clean. Commit or stash changes before running this script."
  exit 1
fi

current_version="$(rg -m 1 "<version>.*</version>" portfolio-app/pom.xml | sed -E 's/.*<version>([^<]+)<\/version>.*/\1/')"

if [[ "${current_version}" != *"vloiseau-"* ]]; then
  echo "Current version does not include a vloiseau build suffix: ${current_version}"
  exit 1
fi

build_number="${current_version##*vloiseau-}"
if ! [[ "${build_number}" =~ ^[0-9]+$ ]]; then
  echo "Invalid vloiseau build suffix in version: ${current_version}"
  exit 1
fi

next_version="${current_version%vloiseau-*}vloiseau-$((build_number + 1))"

mvn -f portfolio-app/pom.xml clean verify

mvn -f portfolio-app/pom.xml tycho-versions:set-version -DnewVersion="${next_version}"

git add -A
git commit -m "Build ${current_version}"
