// Fails if any artifact staged under */build/staging-deploy is already on Maven Central.
//
// Loaded by actions/github-script, which supplies `core` (annotations, step summary, failure) and `glob`:
//   script: await require('./.github/scripts/check-maven-central.js')({core, glob})
//
// Coordinates come from the staged POM paths (<group/path>/<artifactId>/<version>/<artifactId>-<version>.pom),
// so plugin marker artifacts are checked too. Each coordinate is looked up in two places:
//   - repo1.maven.org: the public repository (lags a Portal publication by up to ~30 min)
//   - Central Portal /api/v1/publisher/published: knows about publications not yet synced to repo1;
//     only queried when MAVEN_REPO_USERNAME / MAVEN_REPO_PASSWORD are set
// SNAPSHOT versions are skipped.
//
// MAVEN_CENTRAL_CHECK_MODE=warn (used by the PR check) reports an already-published version as a warning and passes;
// the default, fail (release), fails the step.

const path = require("node:path");

const STAGING_DIR = "build/staging-deploy";
const RETRIES = 3;
const RETRYABLE_STATUS = new Set([408, 429, 500, 502, 503, 504]);

// fetch with retries on network errors and transient HTTP statuses. Resolves to the last response, or throws.
async function fetchWithRetry(url, options = {}) {
  let lastError;
  for (let attempt = 0; attempt <= RETRIES; attempt++) {
    if (attempt > 0) await new Promise((resolve) => setTimeout(resolve, 1000 * attempt));
    try {
      const response = await fetch(url, options);
      if (!RETRYABLE_STATUS.has(response.status) || attempt === RETRIES) return response;
      lastError = new Error(`HTTP ${response.status}`);
    } catch (error) {
      lastError = error;
    }
  }
  throw lastError;
}

// "<root>/org/projectcontinuum/core/continuum-commons/0.0.22/continuum-commons-0.0.22.pom" -> coordinates
function parseCoordinate(pomPath) {
  const normalized = pomPath.split(path.sep).join("/");
  const relative = normalized.slice(normalized.indexOf(`${STAGING_DIR}/`) + STAGING_DIR.length + 1);
  const versionDir = path.posix.dirname(relative);
  const artifactDir = path.posix.dirname(versionDir);
  return {
    group: path.posix.dirname(artifactDir).split("/").join("."),
    artifact: path.posix.basename(artifactDir),
    version: path.posix.basename(versionDir),
    artifactDir,
  };
}

// Looks the coordinate up in the public repository. Returns { result, warning? }.
async function checkRepo({ repoUrl }, { artifactDir, artifact, version }) {
  const url = `${repoUrl}/${artifactDir}/${version}/${artifact}-${version}.pom`;
  try {
    const { status } = await fetchWithRetry(url);
    if (status === 200) return { result: "published" };
    if (status === 404) return { result: "not found" };
    return { result: `HTTP ${status}`, warning: `repo1 lookup returned HTTP ${status}` };
  } catch (error) {
    return { result: "HTTP 000", warning: `repo1 lookup failed: ${error.message}` };
  }
}

// Looks the coordinate up in the Central Portal. Returns { result, warning? }.
async function checkPortal({ portalUrl, portalAuth }, { group, artifact, version }) {
  if (!portalAuth) return { result: "not checked" };
  const query = new URLSearchParams({ namespace: group, name: artifact, version });
  try {
    const response = await fetchWithRetry(`${portalUrl}/published?${query}`, { headers: portalAuth });
    if (response.status !== 200) {
      return { result: `HTTP ${response.status}`, warning: `Portal lookup returned HTTP ${response.status}` };
    }
    const text = await response.text();
    let published;
    try {
      published = JSON.parse(text).published;
    } catch {
      // fall through to "unreadable"
    }
    if (published === true) return { result: "published" };
    if (published === false) return { result: "not found" };
    return { result: "unreadable response", warning: `unexpected Portal response: ${text.slice(0, 200)}` };
  } catch (error) {
    return { result: "HTTP 000", warning: `Portal lookup failed: ${error.message}` };
  }
}

async function checkCoordinate(config, coordinate) {
  const name = `${coordinate.group}:${coordinate.artifact}:${coordinate.version}`;
  if (coordinate.version.endsWith("-SNAPSHOT")) {
    return { name, warnings: [], row: `| \`${name}\` | skipped (SNAPSHOT) | — | ⏭️ |` };
  }

  const [repo, portal] = await Promise.all([checkRepo(config, coordinate), checkPortal(config, coordinate)]);
  const warnings = [repo.warning, portal.warning].filter(Boolean).map((warning) => `${name}: ${warning}`);
  const published = repo.result === "published" || portal.result === "published";
  const verdict = published ? "❌ already published" : "✅";
  return { name, published, warnings, row: `| \`${name}\` | ${repo.result} | ${portal.result} | ${verdict} |` };
}

function buildSummary({ mode, portalAuth }, results, existing, warningCount) {
  const lines = ["### Maven Central pre-check", ""];
  if (existing > 0 && mode === "warn") {
    lines.push(
      "> [!WARNING]",
      `> ${existing} of ${results.length} artifact(s) already exist on Maven Central. This PR does not need to bump \`platformVersion\`,`,
      "> but the version must be bumped in `gradle.properties` before the next release, or the release will be rejected.",
    );
  } else if (existing > 0) {
    lines.push(
      "> [!CAUTION]",
      `> ${existing} of ${results.length} artifact(s) already exist on Maven Central, so publishing this version would be rejected.`,
      "> Maven Central versions cannot be overwritten. Bump `platformVersion` in `gradle.properties` and run again.",
    );
  } else {
    lines.push(`All ${results.length} staged artifact(s) are new to Maven Central.`);
  }
  if (warningCount > 0) {
    lines.push(
      "",
      "> [!WARNING]",
      `> ${warningCount} lookup(s) could not be completed; see the annotations. Those checks were not counted as published.`,
    );
  }
  if (!portalAuth) {
    lines.push(
      "",
      "> [!NOTE]",
      "> Central Portal not queried (no credentials), so a publication not yet synced to repo1 would go unnoticed.",
    );
  }
  lines.push(
    "",
    "| Artifact | repo1.maven.org | Central Portal | Result |",
    "|----------|-----------------|----------------|--------|",
    ...results.map((result) => result.row),
  );
  return lines.join("\n") + "\n";
}

module.exports = async function checkMavenCentral({ core, glob }) {
  const { MAVEN_REPO_USERNAME: username, MAVEN_REPO_PASSWORD: password } = process.env;
  const config = {
    mode: process.env.MAVEN_CENTRAL_CHECK_MODE || "fail",
    repoUrl: process.env.MAVEN_CENTRAL_REPO_URL || "https://repo1.maven.org/maven2",
    portalUrl: process.env.MAVEN_CENTRAL_PORTAL_URL || "https://central.sonatype.com/api/v1/publisher",
    portalAuth:
      username && password
        ? { Authorization: `Bearer ${Buffer.from(`${username}:${password}`).toString("base64")}` }
        : null,
  };

  if (config.mode !== "fail" && config.mode !== "warn") {
    core.setFailed(`MAVEN_CENTRAL_CHECK_MODE must be "fail" or "warn", got "${config.mode}"`);
    return;
  }

  const globber = await glob.create(`*/${STAGING_DIR}/**/*.pom`);
  const poms = (await globber.glob()).sort();
  if (poms.length === 0) {
    core.setFailed(`No staged POMs found under */${STAGING_DIR}; run the publish task first.`);
    return;
  }

  const results = await Promise.all(poms.map((pom) => checkCoordinate(config, parseCoordinate(pom))));

  let warningCount = 0;
  for (const result of results) {
    for (const warning of result.warnings) {
      warningCount++;
      core.warning(warning, { title: "Maven Central pre-check" });
    }
    if (!result.published) continue;
    if (config.mode === "warn") {
      core.warning(
        `${result.name} is already published. Bump platformVersion in gradle.properties before the next release.`,
        { title: "Already on Maven Central" },
      );
    } else {
      core.error(
        `${result.name} is already published. Maven Central versions are immutable; bump platformVersion in gradle.properties.`,
        { title: "Already on Maven Central" },
      );
    }
  }

  const existing = results.filter((result) => result.published).length;
  await core.summary.addRaw(buildSummary(config, results, existing, warningCount), true).write();

  if (existing > 0 && config.mode === "fail") {
    core.setFailed(`${existing} of ${results.length} staged artifact(s) already exist on Maven Central.`);
  }
};
