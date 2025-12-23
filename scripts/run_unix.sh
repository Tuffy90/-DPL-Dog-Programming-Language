set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
DIST_DIR="$ROOT/dist"
JAR_NAME="dpl.jar"

echo ""
echo "=== DPL Run (Unix) ==="
echo "Root: $ROOT"
echo ""

if ! command -v java >/dev/null 2>&1; then
  echo "❌ Java not found."
  echo "Install Java 8+."
  exit 1
fi

if [ ! -f "$DIST_DIR/$JAR_NAME" ]; then
  echo "❌ '$DIST_DIR/$JAR_NAME' not found."
  echo "Run: ./scripts/build_jar_unix.sh"
  exit 1
fi

mkdir -p "$ROOT/out" "$ROOT/test_files_RDL"

echo "✅ Starting DPL..."
if [ "${1:-}" = "" ]; then
  java -jar "$DIST_DIR/$JAR_NAME"
else
  java -jar "$DIST_DIR/$JAR_NAME" "$1"
fi
