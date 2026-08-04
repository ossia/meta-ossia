require ossia-score.inc

# Builds a local score checkout given SCORE_SRC_ROOT. A gitsm:// fetch of
# ossia/score would silently lack the ~20 addon repositories
# ci/common.deps.sh clones, so there is no fetching variant of this.
#
# The release recipe is preferred by default; select this one with
# PREFERRED_VERSION_ossia-score = "3.8.2+git".
PV = "3.8.2+git"

# Set in local.conf to point at a score checkout:
#   SCORE_SRC_ROOT = "/path/to/score"
SCORE_SRC_ROOT ?= ""

python () {
    if not d.getVar("SCORE_SRC_ROOT"):
        raise bb.parse.SkipRecipe("SCORE_SRC_ROOT is not set")
}

inherit externalsrc

# Otherwise do_configure drops oe-workdir/oe-logs symlinks into the checkout.
EXTERNALSRC_SYMLINKS = ""

EXTERNALSRC = "${SCORE_SRC_ROOT}"
EXTERNALSRC_BUILD = "${WORKDIR}/build"

# externalsrc has no do_fetch, so submodules and addons must already be there.
do_configure:prepend() {
    if [ ! -f "${S}/3rdparty/libossia/CMakeLists.txt" ]; then
        bbfatal "No libossia in ${S}/3rdparty. Run: git submodule update --init --recursive"
    fi
    if [ ! -d "${S}/src/addons/score-addon-avnd" ] && [ ! -e "${S}/src/addons/iscore-addon-network" ]; then
        bbwarn "No addons in ${S}/src/addons -- run ci/common.deps.sh LINUX to clone them."
    fi
}

# B is under TMPDIR and ends up in the binary. The release recipe is
# unaffected and stays subject to the check.
INSANE_SKIP:${PN} += "buildpaths"
INSANE_SKIP:${PN}-dbg += "buildpaths"
INSANE_SKIP:${PN}-src += "buildpaths"
