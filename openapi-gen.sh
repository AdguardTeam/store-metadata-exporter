#!/usr/bin/env bash
set -euo pipefail

GENERATOR_VERSION="7.17.0"
GENERATOR_JAR="openapi-generator-cli-$GENERATOR_VERSION.jar"
PARENT_POM="pom.xml"

# Function to generate a single API
generate_api() {
  local API_SHORT="$1"

  API_NAME="${API_SHORT}-api"
  OPENAPI_YAML="${API_SHORT}.yaml"
  OPENAPI_JSON="${API_SHORT}.json"
  API_PACKAGE="com.adguard.stores.${API_SHORT}.api"
  MODEL_PACKAGE="com.adguard.stores.${API_SHORT}.model"
  GROUP_ID="com.adguard.stores"

  # path to the root POM
  if [[ ! -f "$PARENT_POM" ]]; then
    echo "Error: parent POM not found at $PARENT_POM" >&2
    exit 1
  fi

  # extract top-level groupId, artifactId and version from the root POM
  parentGroupId=$(awk -F '[<>]' '/<groupId>/{ if ($2=="groupId") { print $3; exit } }' "$PARENT_POM")
  parentArtifactId=$(awk -F '[<>]' '/<artifactId>/{ if ($2=="artifactId") { print $3; exit } }' "$PARENT_POM")
  parentVersion=$(awk -F '[<>]' '/<version>/{    if ($2=="version")    { print $3; exit } }' "$PARENT_POM")

  echo "Using parent coordinates: $parentGroupId:$parentArtifactId:$parentVersion"

  # Download the OpenAPI Generator JAR if not present
  if [[ ! -f "$GENERATOR_JAR" ]]; then
    echo "Downloading OpenAPI Generator CLI version $GENERATOR_VERSION ..."
    wget "https://repo1.maven.org/maven2/org/openapitools/openapi-generator-cli/$GENERATOR_VERSION/openapi-generator-cli-$GENERATOR_VERSION.jar" -O "$GENERATOR_JAR"
  fi

  # Convert YAML to JSON to avoid SnakeYAML stack overflow on large specs
  if [[ -f "$OPENAPI_YAML" ]]; then
    echo "Converting $OPENAPI_YAML to JSON (avoids parser stack overflow)..."
    if ! command -v yq &>/dev/null; then
      echo "Error: 'yq' is required for YAML to JSON conversion. Install with: brew install yq" >&2
      exit 1
    fi
    yq -o=json "$OPENAPI_YAML" > "$OPENAPI_JSON"
  elif [[ ! -f "$OPENAPI_JSON" ]]; then
    echo "Error: OpenAPI spec not found: $OPENAPI_YAML or $OPENAPI_JSON" >&2
    exit 1
  fi

  echo "Generating $API_NAME from $OPENAPI_JSON ..."

  # remove any existing output and recreate directory
  rm -rf "$API_NAME"
  mkdir "$API_NAME"

  # files and folders to ignore during generation
  ignore_content="pom.xml
build.sbt
build.gradle
settings.gradle
gradle/**
gradlew
gradlew.bat
.github/**
.travis.yml
.circleci/**
Jenkinsfile
.gitignore
gradle.properties
.openapi-generator/**
git_push.sh
src/main/AndroidManifest.xml"

  # Write ignore_content with real newlines using heredoc
  cat > "$API_NAME/.openapi-generator-ignore" <<EOF
$ignore_content
EOF

  # run OpenAPI Generator with injected parent coordinates and version
  # Large heap and stack for parsing big OpenAPI specs
  java -Xmx4g -Xss4m -jar "$GENERATOR_JAR" generate \
    -i "$OPENAPI_JSON" \
    -g java \
    -o "$API_NAME" \
    --group-id "$GROUP_ID" \
    --artifact-id "$API_NAME" \
    --api-package    "$API_PACKAGE" \
    --model-package  "$MODEL_PACKAGE" \
    --library native \
    --global-property apiDocs=false,modelDocs=false,apiTests=false,modelTests=false\
    --additional-properties=\
useJakartaEe=true,\
useRuntimeException=true,\
javaVersion=21,\
dateLibrary=java8,\
asyncNative=false,\
hideGenerationTimestamp=true,\
disallowAdditionalPropertiesIfNotPresent=false,\
parentGroupId="$parentGroupId",\
parentArtifactId="$parentArtifactId",\
parentVersion="$parentVersion",\
artifactVersion="$parentVersion" \
    --skip-validate-spec

  echo "Generation for $API_NAME complete. Output directory: $API_NAME"
}

# Main script
echo
echo "WARNING: Any changes made to generated code or project files will be overwritten."
echo

# Accept API name as command-line argument or show interactive menu
if [[ -n "${1:-}" ]]; then
  API_SHORT="$1"
else
  echo "Please select the API to generate:"

  PS3="Select API to generate: "
  options=("ALL" "appstoreconnect")
  select API_SHORT in "${options[@]}"; do
    if [[ -n "$API_SHORT" ]]; then
      break
    else
      echo "Invalid choice. Please try again."
    fi
  done
fi

# If "ALL" is selected, regenerate all APIs
if [[ "$API_SHORT" == "ALL" ]]; then
  ALL_APIS=("appstoreconnect")

  echo ""
  echo "=========================================="
  echo "Regenerating ALL API modules (${#ALL_APIS[@]} total)"
  echo "=========================================="
  echo ""

  for api in "${ALL_APIS[@]}"; do
    echo ""
    echo ">>> Generating: $api"
    echo ""
    generate_api "$api"

    if [[ $? -ne 0 ]]; then
      echo "ERROR: Failed to generate $api" >&2
      exit 1
    fi
  done

  echo ""
  echo "=========================================="
  echo "ALL API modules regenerated successfully!"
  echo "=========================================="
  exit 0
else
  generate_api "$API_SHORT"
fi