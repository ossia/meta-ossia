SUMMARY = "ossia score - an interactive sequencer for audio-visual artists"
DESCRIPTION = "ossia score is a sequencer for audio-visual artists, designed to \
enable the creation of interactive shows, museum installations, intermedia \
digital artworks, interactive music and more."
HOMEPAGE = "https://ossia.io"
BUGTRACKER = "https://github.com/ossia/score/issues"
SECTION = "multimedia"

# Covers score's own sources. The vendored 3rdparty/ tree is mixed-licence;
# run `bitbake -c populate_lic ossia-score` before shipping commercially.
LICENSE = "GPL-3.0-only"
LIC_FILES_CHKSUM = "file://LICENSE.txt;md5=c8bf0f99367f566cced5973a2aa89d68"

# externalsrc keeps the file:// entries, so these reach UNPACKDIR either way.
SRC_URI = " \
    file://ossia-score-launch \
    file://ossia-score-init \
    file://ossia-score.service \
    file://95-ossia-score-rt.conf \
"

inherit qt6-cmake pkgconfig systemd features_check

# KFR does not build on non-x86 without clang. toolchain/clang.bbclass is in
# oe-core; meta-clang is not needed.
TOOLCHAIN = "clang"

# Qt Gui/Widgets/OpenGL are required at find_package() time even for --no-gui.
REQUIRED_DISTRO_FEATURES = "opengl"

DEPENDS = " \
    qtbase \
    qtdeclarative \
    qtscxml \
    qtshadertools \
    qtsvg \
    qtdeclarative-native \
    qtshadertools-native \
    boost \
    alsa-lib \
    ffmpeg \
    libsndfile1 \
    fftw \
    avahi \
    portaudio-v19 \
    re2 \
    snappy \
    spdlog \
    magic-enum \
    rapidfuzz-cpp \
    openssl \
    zlib \
    udev \
"

PACKAGECONFIG ??= "jack bluez websockets serialport quick3d gpsd hdf5 sdl coap \
                   lv2 faust ysfx \
                   ${@bb.utils.contains_any('TARGET_ARCH', 'aarch64 x86_64', 'jit', '', d)} \
                   ${@bb.utils.filter('DISTRO_FEATURES', 'wayland x11', d)}"

PACKAGECONFIG[pipewire]   = ",,pipewire"
PACKAGECONFIG[jack]       = ",,jack"
PACKAGECONFIG[bluez]      = ",,bluez5"
PACKAGECONFIG[websockets] = ",,qtwebsockets"
PACKAGECONFIG[serialport] = ",,qtserialport"
PACKAGECONFIG[quick3d]    = ",,qtquick3d"
PACKAGECONFIG[gpsd]       = ",,gpsd"
PACKAGECONFIG[hdf5]       = ",,hdf5"
PACKAGECONFIG[sdl]        = ",,libsdl2"
PACKAGECONFIG[coap]       = ",,libcoap"
PACKAGECONFIG[x11]        = ",,libx11 libxext libxcomposite libxrandr"
# The QPA plugin is dlopen'ed, hence the runtime dependency.
PACKAGECONFIG[wayland]    = ",,qtwayland wayland,qtwayland-plugins"

# Without an explicit LLVM_DIR, find_package(LLVM) resolves the native LLVM and
# the target link fails on -lclang. The JIT compiles at runtime, so it also
# needs clang's resource headers and a C++ header set.
PACKAGECONFIG[jit] = "-DLLVM_DIR=${STAGING_LIBDIR}/cmake/llvm,,clang,libclang clang libstdc++-dev"

# dev-deps: the JIT needs -dev packages at runtime.
INSANE_SKIP:${PN} += "${@bb.utils.contains('PACKAGECONFIG', 'jit', 'dev-deps', '', d)}"

# Without these the plugins self-disable silently.
PACKAGECONFIG[lv2]        = ",,lv2kit,lv2kit"
PACKAGECONFIG[faust]      = ",,faust,faust faustlibraries"
PACKAGECONFIG[ysfx]       = ",,ysfx"

# Both fetch during configure, which do_configure cannot do, and neither
# cross-compiles. The list also filters src/addons.
SCORE_DISABLED_PLUGINS ?= "score-addon-onnx,score-addon-ultraleap"

SCORE_DISABLED_PLUGINS .= "${@bb.utils.contains('PACKAGECONFIG', 'jit', '', ',score-plugin-jit', d)}"

# Same configuration as ci/debian.trixie-system.build.sh and the Flatpak
# manifest. SCORE_USE_SYSTEM_LIBRARIES is a preference: libossia falls back to
# its vendored copies, which is also what suppresses the faustlibs fetch.
#
# CMAKE_BUILD_TYPE is unset by cmake.bbclass, but the bundled VST3 SDK does not
# compile without NDEBUG (base/source/fdebug.h).
EXTRA_OECMAKE = " \
    -DCMAKE_BUILD_TYPE=Release \
    -DSCORE_USE_SYSTEM_LIBRARIES=1 \
    -DOSSIA_USE_SYSTEM_LIBRARIES=1 \
    -DSCORE_DEPLOYMENT_BUILD=1 \
    -DSCORE_STATIC_PLUGINS=1 \
    -DSCORE_FHS_BUILD=1 \
    -DBUILD_SHARED_LIBS=0 \
    -DCMAKE_SKIP_RPATH=ON \
    -DCMAKE_UNITY_BUILD=1 \
    -DSCORE_PCH=0 \
    -DBOOST_ROOT=${STAGING_INCDIR} \
    -DSCORE_DISABLED_PLUGINS=${SCORE_DISABLED_PLUGINS} \
"

# -O3 rather than the -O2 default. ${DEBUG_LEVELFLAG} must stay or the -dbg
# package is empty.
FULL_OPTIMIZATION = "-O3 ${DEBUG_LEVELFLAG} -fno-semantic-interposition"

# Off by default: an LTO link of a ~70 MB static binary needs a lot of host RAM.
OSSIA_SCORE_LTO ?= "0"
EXTRA_OECMAKE += "${@bb.utils.contains('OSSIA_SCORE_LTO', '1', '-DCMAKE_INTERPROCEDURAL_OPTIMIZATION=ON', '', d)}"

# Unity build: very large translation units, so memory- rather than
# parallel-bound.
PARALLEL_MAKE ?= "-j 4"

SYSTEMD_SERVICE:${PN} = "ossia-score.service"
SYSTEMD_AUTO_ENABLE = "enable"

do_install:append() {
    # Vendored xtensor installs xeus/Jupyter files into ${prefix}/etc.
    rm -rf ${D}${datadir}/xeus-cpp ${D}${prefix}/etc/xeus-cpp
    rmdir --ignore-fail-on-non-empty ${D}${prefix}/etc 2>/dev/null || true

    install -d ${D}${bindir}
    install -m 0755 ${UNPACKDIR}/ossia-score-launch ${D}${bindir}/ossia-score-launch
    install -m 0755 ${UNPACKDIR}/ossia-score-init ${D}${bindir}/ossia-score-init

    install -d ${D}${sysconfdir}/security/limits.d
    install -m 0644 ${UNPACKDIR}/95-ossia-score-rt.conf \
        ${D}${sysconfdir}/security/limits.d/95-ossia-score-rt.conf

    if ${@bb.utils.contains('DISTRO_FEATURES', 'systemd', 'true', 'false', d)}; then
        install -d ${D}${systemd_system_unitdir}
        install -m 0644 ${UNPACKDIR}/ossia-score.service \
            ${D}${systemd_system_unitdir}/ossia-score.service
    fi
}

FILES:${PN} += " \
    ${datadir}/applications \
    ${datadir}/icons \
    ${datadir}/metainfo \
    ${datadir}/pixmaps \
    ${sysconfdir}/security/limits.d \
"

# dlopen'ed, so not picked up automatically. eglfs is the no-display-server path.
RDEPENDS:${PN} += " \
    qtbase-plugins \
    qtdeclarative-qmlplugins \
"

RRECOMMENDS:${PN} += "alsa-utils"
