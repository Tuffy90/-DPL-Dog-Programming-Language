set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
SRC_DIR="$ROOT/src"
OUT_DIR="$ROOT/out"
DIST_DIR="$ROOT/dist"
JAR_NAME="dpl.jar"
MAIN_CLASS="Code"

echo ""
echo "=== DPL Build JAR (Unix) ==="
echo "Root: $ROOT"
echo ""

if ! command -v javac >/dev/null 2>&1; then
  echo "❌ javac not found. You likely have only JRE, not JDK."
  echo "Install JDK 8+ (recommended 17)."
  exit 1
fi

if ! command -v jar >/dev/null 2>&1; then
  echo "❌ jar tool not found (JDK required)."
  echo "Install JDK 8+."
  exit 1
fi

if ! command -v java >/dev/null 2>&1; then
  echo "❌ java not found."
  echo "Install Java 8+."
  exit 1
fi

if [ ! -d "$SRC_DIR" ]; then
  echo "❌ src folder not found: $SRC_DIR"
  echo "Put Java sources into DPL/src/"
  exit 1
fi

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR" "$DIST_DIR" "$ROOT/test_files_RDL" "$ROOT/assets/icons"

echo "✅ Compiling Java sources..."
javac -encoding UTF-8 -d "$OUT_DIR" "$SRC_DIR"/*.java

echo "✅ Writing manifest..."
MF="$OUT_DIR/manifest.mf"
{
  echo "Manifest-Version: 1.0"
  echo "Main-Class: $MAIN_CLASS"
  echo ""
} > "$MF"

echo "✅ Building JAR..."
jar cfm "$DIST_DIR/$JAR_NAME" "$MF" -C "$OUT_DIR" .

echo ""
echo "✅ Done: $DIST_DIR/$JAR_NAME"
echo ""
