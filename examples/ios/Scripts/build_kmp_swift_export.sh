#!/bin/sh
# Builds the shared KMP module and exports it to Swift. Runs as a pre-build phase of the OidcSample target.
set -eu

if [ "${OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED:-}" = "YES" ]; then
    echo "Skipping Swift Export because Kotlin IDE integration owns the build."
    exit 0
fi

if [ "${XCODE_RUNNING_FOR_PREVIEWS:-}" = "1" ]; then
    echo "Skipping Swift Export for Xcode Previews; run a regular build after KMP changes."
    exit 0
fi

if [ -z "${JAVA_HOME:-}" ]; then
    JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || true)
    export JAVA_HOME
fi

if [ -z "${JAVA_HOME:-}" ]; then
    echo "error: JDK 21 is required to build the shared KMP module." >&2
    exit 1
fi

repo_root="$SRCROOT/../.."
export PATH="$JAVA_HOME/bin:$PATH"
unset SDKROOT TOOLCHAINS

# Swift Export output is tied to the Swift compiler version, so rebuild it from scratch when Xcode changes
swift_export_build_dir="$repo_root/examples/shared/build"
toolchain_stamp="$swift_export_build_dir/.swift-export/apple-swift-toolchain.stamp"
current_toolchain="$(xcrun --find swiftc)
$(xcrun swiftc --version)"
previous_toolchain=""

if [ -f "$toolchain_stamp" ]; then
    previous_toolchain=$(cat "$toolchain_stamp")
fi

if [ "$current_toolchain" != "$previous_toolchain" ]; then
    echo "Apple Swift toolchain changed; invalidating generated Swift Export modules."
    rm -rf \
        "$swift_export_build_dir/SPMBuild" \
        "$swift_export_build_dir/SPMDerivedData"
    mkdir -p "$(dirname "$toolchain_stamp")"
    printf '%s\n' "$current_toolchain" > "$toolchain_stamp"
fi

cd "$repo_root"
./gradlew --console=plain :examples:shared:embedSwiftExportForXcode
