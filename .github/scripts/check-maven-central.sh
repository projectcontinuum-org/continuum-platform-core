#!/usr/bin/env bash
# Fails if any artifact staged under */build/staging-deploy is already on Maven Central.
#
# Coordinates come from the staged POM paths (<group/path>/<artifactId>/<version>/<artifactId>-<version>.pom),
# so plugin marker artifacts are checked too. Each coordinate is looked up in two places:
#   - repo1.maven.org: the public repository (lags a Portal publication by up to ~30 min)
#   - Central Portal /api/v1/publisher/published: knows about publications not yet synced to repo1;
#     only queried when MAVEN_REPO_USERNAME / MAVEN_REPO_PASSWORD are set
# SNAPSHOT versions are skipped. Writes a table to $GITHUB_STEP_SUMMARY and ::error annotations when run in CI.
set -euo pipefail

REPO_URL="${MAVEN_CENTRAL_REPO_URL:-https://repo1.maven.org/maven2}"
PORTAL_URL="${MAVEN_CENTRAL_PORTAL_URL:-https://central.sonatype.com/api/v1/publisher}"
SUMMARY="${GITHUB_STEP_SUMMARY:-/dev/stdout}"

auth=""
if [ -n "${MAVEN_REPO_USERNAME:-}" ] && [ -n "${MAVEN_REPO_PASSWORD:-}" ]; then
  auth="Authorization: Bearer $(printf '%s:%s' "${MAVEN_REPO_USERNAME}" "${MAVEN_REPO_PASSWORD}" | base64 | tr -d '\n')"
fi

shopt -s nullglob globstar
poms=(*/build/staging-deploy/**/*.pom)
if [ ${#poms[@]} -eq 0 ]; then
  echo "::error title=Maven Central pre-check::No staged POMs found under */build/staging-deploy; run the publish task first."
  exit 1
fi

existing=0
warnings=0
rows=""

for pom in "${poms[@]}"; do
  rel="${pom#*/build/staging-deploy/}"         # org/projectcontinuum/core/continuum-commons/0.0.22/continuum-commons-0.0.22.pom
  version_dir="$(dirname "${rel}")"
  version="$(basename "${version_dir}")"
  artifact_dir="$(dirname "${version_dir}")"
  artifact="$(basename "${artifact_dir}")"
  group="$(dirname "${artifact_dir}" | tr '/' '.')"
  coordinate="${group}:${artifact}:${version}"

  if [[ "${version}" == *-SNAPSHOT ]]; then
    rows+="| \`${coordinate}\` | skipped (SNAPSHOT) | — | ⏭️ |"$'\n'
    continue
  fi

  repo_status="$(curl -sS -o /dev/null -w '%{http_code}' --retry 3 --retry-all-errors \
    "${REPO_URL}/${artifact_dir}/${version}/${artifact}-${version}.pom" || echo "000")"
  case "${repo_status}" in
    200) repo_result="published" ;;
    404) repo_result="not found" ;;
    *)   repo_result="HTTP ${repo_status}"; warnings=$((warnings + 1))
         echo "::warning title=Maven Central pre-check::${coordinate}: repo1 lookup returned HTTP ${repo_status}" ;;
  esac

  portal_result="not checked"
  if [ -n "${auth}" ]; then
    body="$(mktemp)"
    portal_status="$(curl -sS -o "${body}" -w '%{http_code}' --retry 3 --retry-all-errors -H "${auth}" \
      "${PORTAL_URL}/published?namespace=${group}&name=${artifact}&version=${version}" || echo "000")"
    if [ "${portal_status}" = "200" ]; then
      case "$(jq -r '.published // empty' "${body}" 2>/dev/null)" in
        true)  portal_result="published" ;;
        false) portal_result="not found" ;;
        *)     portal_result="unreadable response"; warnings=$((warnings + 1))
               echo "::warning title=Maven Central pre-check::${coordinate}: unexpected Portal response: $(head -c 200 "${body}")" ;;
      esac
    else
      portal_result="HTTP ${portal_status}"; warnings=$((warnings + 1))
      echo "::warning title=Maven Central pre-check::${coordinate}: Portal lookup returned HTTP ${portal_status}"
    fi
    rm -f "${body}"
  fi

  if [ "${repo_result}" = "published" ] || [ "${portal_result}" = "published" ]; then
    existing=$((existing + 1))
    rows+="| \`${coordinate}\` | ${repo_result} | ${portal_result} | ❌ already published |"$'\n'
    echo "::error title=Already on Maven Central::${coordinate} is already published. Maven Central versions are immutable; bump platformVersion in gradle.properties."
  else
    rows+="| \`${coordinate}\` | ${repo_result} | ${portal_result} | ✅ |"$'\n'
  fi
done

{
  echo "### Maven Central pre-check"
  echo
  if [ "${existing}" -gt 0 ]; then
    echo "> [!CAUTION]"
    echo "> ${existing} of ${#poms[@]} artifact(s) already exist on Maven Central, so the deploy was skipped."
    echo "> Maven Central versions cannot be overwritten. Bump \`platformVersion\` in \`gradle.properties\` and run again."
  else
    echo "All ${#poms[@]} staged artifact(s) are new to Maven Central."
  fi
  if [ "${warnings}" -gt 0 ]; then
    echo
    echo "> [!WARNING]"
    echo "> ${warnings} lookup(s) could not be completed; see the annotations. Those checks were not counted as published."
  fi
  if [ -z "${auth}" ]; then
    echo
    echo "> [!NOTE]"
    echo "> Central Portal not queried (no credentials), so a publication not yet synced to repo1 would go unnoticed."
  fi
  echo
  echo "| Artifact | repo1.maven.org | Central Portal | Result |"
  echo "|----------|-----------------|----------------|--------|"
  printf '%s' "${rows}"
} >> "${SUMMARY}"

[ "${existing}" -eq 0 ]
