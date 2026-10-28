#!/usr/bin/env bash
#
# Compile le cœur RustDesk (librustdesk.so) pour Android arm64-v8a, hôte Windows.
#
# Chaque étape ici a coûté du temps une première fois : les pièges sont commentés à l'endroit
# où ils se produisent, pour que la deuxième fois ne les repaye pas.
#
# Prérequis (déjà présents sur cette machine) :
#   - Rust stable + cible aarch64-linux-android
#   - NDK 29.0.14206865 sous $DUALDESK_NDK
#   - vcpkg à la baseline épinglée par rustdesk-src/vcpkg.json, libsodium installé
#   - libclang (LLVM dans Program Files) pour bindgen
#
# Usage : ./scripts/build-rustdesk-android.sh [chemin vers rustdesk-src]

set -euo pipefail

RUSTDESK_SRC="${1:-D:/Codex/rustdesk-src}"
VCPKG_ROOT="${VCPKG_ROOT:-D:/Codex/vcpkg}"
DUALDESK_NDK="${DUALDESK_NDK:-D:/Codex/android-tools/sdk-root/ndk/29.0.14206865}"
OUT_DIR="${OUT_DIR:-D:/Codex/dualdesk-artifacts/rustdesk}"
LOG_DIR="${LOG_DIR:-D:/Codex/build-logs}"
VCPKG_BASELINE="9e593bb18ea69cc5095e012465dcd675a822ed0d"

export ANDROID_NDK_HOME="$DUALDESK_NDK"
export ANDROID_NDK_ROOT="$DUALDESK_NDK"
export VCPKG_ROOT
export VCPKG_DEFAULT_TRIPLET=arm64-android

TOOLCHAIN="$DUALDESK_NDK/toolchains/llvm/prebuilt/windows-x86_64"
SYSROOT="$TOOLCHAIN/sysroot"

# PIEGE 1 — OpenSSL vendored refuse le Perl de Strawberry : son Configure exige un Perl qui
# produit des chemins Unix. Celui que vcpkg provisionne (MSYS2) convient. Sans cela :
#   "This perl implementation doesn't produce Unix like paths"
MSYS_PERL="$(find "$VCPKG_ROOT/downloads/tools/msys2" -path '*/usr/bin' -maxdepth 4 -type d | head -1)"
if [ -n "$MSYS_PERL" ]; then
    export PATH="$MSYS_PERL:$PATH"
    echo "[rustdesk] Perl MSYS2 : $MSYS_PERL"
else
    echo "[rustdesk] ATTENTION : Perl MSYS2 introuvable ; OpenSSL échouera probablement." >&2
fi

# PIEGE 2 — libsodium n'est pas dans le manifeste vcpkg pour Android (il n'y est que pour
# Windows). Sans cette bibliothèque, libsodium-sys échoue sur "could not find native static
# library `sodium`". Le crate réclame `-llibsodium` alors que vcpkg livre `libsodium.a`.
#
# PIEGE 2 bis — et le plus vicieux : `SODIUM_LIB_DIR` est une variable GLOBALE, que cargo n'a
# aucun moyen de restreindre à une cible. Les **build scripts**, eux, sont compilés pour l'HÔTE
# (x64 Windows). En pointant ce dossier vers arm64-android, on faisait donc chercher à link.exe
# une libsodium aarch64 pour produire un exécutable x64 :
#   link.exe failed with exit code 1136 / LNK2019 : sodium_base642bin introuvable
#
# D'où `sodium-combined` : un dossier qui satisfait les deux architectures, sous des noms que
# les deux éditeurs de liens savent trouver (libsodium.a pour clang/Android,
# sodium.lib + libsodium.lib pour link.exe). Voir prepare-sodium-combined.sh.
SODIUM_COMBINED="${SODIUM_COMBINED:-D:/Codex/sodium-combined}"
if [ ! -f "$SODIUM_COMBINED/lib/libsodium.a" ] || [ ! -f "$SODIUM_COMBINED/lib/sodium.lib" ]; then
    echo "[rustdesk] $SODIUM_COMBINED incomplet." >&2
    echo "[rustdesk] Lancez ./scripts/prepare-sodium-combined.sh une fois." >&2
    exit 1
fi
export SODIUM_LIB_DIR="$SODIUM_COMBINED/lib"
export SODIUM_INCLUDE_DIR="$SODIUM_COMBINED/include"
# PIEGE 3 — SODIUM_STATIC est déprécié et fait PANIQUER le build script de libsodium-sys 0.2.7.
# Ne le définir sous aucun prétexte : l'absence de SODIUM_SHARED suffit à demander le statique.
unset SODIUM_STATIC || true

# PIEGE 4 — NE JAMAIS mettre `-isystem <lib/clang/.../include>` dans CFLAGS/CXXFLAGS.
# cc-rs place les drapeaux de l'utilisateur AVANT ses propres chemins, donc l'en-tête
# `stddef.h` de clang passait devant celui de libc++ et tout C++ échouait sur :
#   "<cstddef> tried including <stddef.h> but didn't find libc++'s <stddef.h> header"
# C'est l'inverse du problème qu'on croit corriger : clang trouve ses en-têtes intégrés
# tout seul, et dans le bon ordre. Le corriger à la main le casse.
unset CFLAGS CXXFLAGS CPPFLAGS LDFLAGS || true
unset CFLAGS_aarch64_linux_android CXXFLAGS_aarch64_linux_android || true

# bindgen, lui, ne reçoit rien de cc-rs : ni le sysroot, ni le dossier d'en-têtes intégrés de
# clang. Sans les deux il échoue sur
#   sysroot/usr/include\stdint.h:35:10: fatal error: 'stddef.h' file not found
# (hwcodec, par exemple, parse ses en-têtes par bindgen). Ici `-isystem` est correct : bindgen
# ne fait que PARSER du C, il ne compile pas de C++ — le piège 4 ne s'applique donc pas.
# Le suffixe est le triplet avec des soulignés, comme le lit bindgen.
CLANG_BUILTIN_INCLUDE="$TOOLCHAIN/lib/clang/21/include"
BINDGEN_ARGS="--sysroot=$SYSROOT -isystem $CLANG_BUILTIN_INCLUDE --target=aarch64-linux-android21"
export BINDGEN_EXTRA_CLANG_ARGS="$BINDGEN_ARGS"
export BINDGEN_EXTRA_CLANG_ARGS_aarch64_linux_android="$BINDGEN_ARGS"

# cc-rs : compilateurs explicites, pour ne pas dépendre de la résolution par PATH.
#
# PIEGE 7 — viser les vrais `clang.exe`, PAS les `aarch64-linux-android21-clang` du NDK :
# ces derniers sont des scripts shell sur Windows, et cc-rs les lance directement, ce qui donne
#   "%1 n'est pas une application Win32 valide" (Os error 193)
# et fait dériver tout openssl-sys (il ne détecte plus la famille du compilateur, puis Perl
# échoue sur un Configure mal amorcé). La cible et le niveau d'API sont donc fournis par des
# drapeaux que cc-rs ajoute APRÈS les siens : le dernier `--target` l'emporte.
ANDROID_TARGET_FLAG="--target=aarch64-linux-android21"
export CC_aarch64_linux_android="$TOOLCHAIN/bin/clang.exe"
export CXX_aarch64_linux_android="$TOOLCHAIN/bin/clang++.exe"
export AR_aarch64_linux_android="$TOOLCHAIN/bin/llvm-ar.exe"
export CFLAGS_aarch64_linux_android="$ANDROID_TARGET_FLAG"
export CXXFLAGS_aarch64_linux_android="$ANDROID_TARGET_FLAG"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$TOOLCHAIN/bin/clang.exe"
# PIEGE 7 bis — l'éditeur de liens reçoit les drapeaux GNU de rustc
# (`--version-script`, `-z`, `--eh-frame-hdr`). Si clang est lancé SANS `--target`, il retombe
# sur l'hôte Windows, passe ces drapeaux à lld en mode COFF, et l'édition de liens échoue sur
#   lld: error: unknown argument: --version-script=...
# Cargo n'accepte pas d'arguments dans la variable LINKER : on les ajoute par RUSTFLAGS, qui
# (avec un `--target` explicite) ne s'applique qu'à la cible, pas aux build scripts.
export RUSTFLAGS="${RUSTFLAGS:-} -Clink-arg=--target=aarch64-linux-android21"

echo "[rustdesk] source : $RUSTDESK_SRC"
echo "[rustdesk] HEAD   : $(git -C "$RUSTDESK_SRC" rev-parse HEAD)"

# PIEGE 5 — les bibliothèques du projet (libs/hbb_common) sont des sous-modules. Un clone
# superficiel les laisse absents, et cargo s'arrête sur "failed to load manifest for dependency".
git -C "$RUSTDESK_SRC" submodule update --init --recursive --depth 1

if [ ! -x "$VCPKG_ROOT/vcpkg.exe" ]; then
    echo "[rustdesk] vcpkg absent : voir docs/RUSTDESK_NATIVE.md §2 (bootstrap à la baseline)." >&2
    exit 1
fi
if [ "$(git -C "$VCPKG_ROOT" rev-parse --short HEAD)" != "${VCPKG_BASELINE:0:8}" ]; then
    echo "[rustdesk] ATTENTION : vcpkg n'est pas à la baseline $VCPKG_BASELINE" >&2
fi

# Dépendances du manifeste RustDesk (aom, ffmpeg, libvpx, opus, libyuv, libjpeg-turbo…) puis
# libsodium. Idempotent : vcpkg réutilise son cache binaire.
( cd "$RUSTDESK_SRC" && "$VCPKG_ROOT/vcpkg.exe" install --triplet arm64-android \
    --x-install-root="$VCPKG_ROOT/installed" )
( mkdir -p "$VCPKG_ROOT/../tmp-vcpkg" && cd "$VCPKG_ROOT/../tmp-vcpkg" && \
    "$VCPKG_ROOT/vcpkg.exe" install libsodium:arm64-android \
    --x-install-root="$VCPKG_ROOT/installed" )

# PIEGE 6 — le crate libsodium-sys demande `-llibsodium`, vcpkg livre `libsodium.a`.
if [ ! -f "$SODIUM_LIB_DIR/libsodium.a" ] && [ -f "$SODIUM_LIB_DIR/libsodium.a" ]; then
    :
fi
if [ ! -f "$SODIUM_LIB_DIR/libsodium.a" ]; then
    ALT="$(ls "$SODIUM_LIB_DIR"/libsodium*.a 2>/dev/null | head -1)"
    if [ -n "$ALT" ] && [ "$ALT" != "$SODIUM_LIB_DIR/libsodium.a" ]; then
        cp "$ALT" "$SODIUM_LIB_DIR/libsodium.a"
        echo "[rustdesk] alias libsodium.a créé depuis $(basename "$ALT")"
    fi
fi

# PIEGE 8 — `openssl-sys` vendore OpenSSL et le configure en lançant `perl ./Configure`.
# Sur cette machine ce Perl meurt à l'initialisation (aucun message, code 2), donc le build
# s'arrête sur « openssl-sys: failed to build OpenSSL from source ».
# On lui donne donc un OpenSSL déjà compilé, et on lui interdit le vendoring.
# Voir scripts/build-openssl-android.sh, qui produit ce dossier.
OPENSSL_ANDROID_DIR="${OPENSSL_ANDROID_DIR:-D:/Codex/openssl-android/install}"
if [ -f "$OPENSSL_ANDROID_DIR/lib/libssl.a" ] && [ -f "$OPENSSL_ANDROID_DIR/lib/libcrypto.a" ]; then
    export OPENSSL_NO_VENDOR=1
    export OPENSSL_STATIC=1
    export OPENSSL_DIR="$OPENSSL_ANDROID_DIR"
    echo "[rustdesk] OpenSSL précompilé : $OPENSSL_ANDROID_DIR"
else
    echo "[rustdesk] ATTENTION : $OPENSSL_ANDROID_DIR/lib/libssl.a absent." >&2
    echo "[rustdesk] Lancez d'abord ./scripts/build-openssl-android.sh, sinon openssl-sys échouera." >&2
    exit 1
fi

# PIEGE 9 — `hwcodec` ne se compile PAS pour Android depuis un hôte Windows.
# Son build.rs décide de la plateforme avec `#[cfg(windows)]`, qui décrit l'HÔTE et non la
# cible : il tente alors de compiler cpp/common/platform/win/win.cpp pour Android et meurt sur
#   fatal error: 'd3d11.h' file not found
# L'amont ne construit Android que sous Linux, d'où le trou. Conséquence assumée ici : pas de
# décodage H.264 matériel, la session retombe sur le décodeur logiciel. À reprendre plus tard
# en vendant hwcodec par un [patch] et en corrigeant son cfg — voir memory.md.
FEATURES="flutter"

mkdir -p "$LOG_DIR"
echo "[rustdesk] features : $FEATURES"
echo "[rustdesk] compilation (20 à 60 min la première fois)…"
# PIEGE 10 — `cargo build` construit aussi les binaires de bureau (`rustdesk`, `service`), qui
# ne peuvent pas se lier sur Android :
#   lld: error: undefined symbol: getrandom
# Seule la bibliothèque dynamique nous intéresse : on demande `--lib` et on s'arrête là.
( cd "$RUSTDESK_SRC" && cargo build --release --locked --lib \
    --target aarch64-linux-android \
    --features "$FEATURES" ) 2>&1 | tee "$LOG_DIR/librustdesk-arm64.log"
RUST_EXIT="${PIPESTATUS[0]}"
echo "RUST_EXIT=$RUST_EXIT"
[ "$RUST_EXIT" -eq 0 ] || exit "$RUST_EXIT"

# La crate déclare `name = "librustdesk"` : le fichier produit est donc `liblibrustdesk.so`.
# Android, lui, cherche `librustdesk.so` dans lib/<abi>/ (System.loadLibrary("rustdesk")).
SO="$RUSTDESK_SRC/target/aarch64-linux-android/release/liblibrustdesk.so"
if [ ! -f "$SO" ]; then
    echo "[rustdesk] $SO absent : la compilation n'a pas produit la bibliothèque." >&2
    exit 1
fi
mkdir -p "$OUT_DIR"
cp "$SO" "$OUT_DIR/librustdesk.so"
echo "[rustdesk] produit : $OUT_DIR/librustdesk.so ($(du -h "$OUT_DIR/librustdesk.so" | cut -f1))"
echo "[rustdesk] pour l'APK : ./gradlew :app:assembleDebug -Pdualdesk.rustdeskLib=$OUT_DIR/librustdesk.so"
