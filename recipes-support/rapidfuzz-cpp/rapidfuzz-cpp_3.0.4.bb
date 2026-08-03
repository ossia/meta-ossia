SUMMARY = "Rapid fuzzy string matching in C++"
DESCRIPTION = "Header-only C++ library for fast approximate string matching, \
using the Levenshtein distance and related metrics."
HOMEPAGE = "https://github.com/rapidfuzz/rapidfuzz-cpp"
SECTION = "libs"

LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE;md5=377d4340f3278257d178cafe2c22cfc8"

# Same version libossia vendors, so the system package and the fallback match.
# Needed because libossia's find_package(rapidfuzz CONFIG REQUIRED) aborts
# before its own vendored fallback, and no OE layer packages rapidfuzz-cpp.
# git, not the release tarball: OE QA rejects GitHub's generated archives.
SRC_URI = "git://github.com/rapidfuzz/rapidfuzz-cpp;protocol=https;branch=main"
SRCREV = "10426d24cd7479df0fe8c78b17877e756e1c3cd5"

inherit cmake

EXTRA_OECMAKE = "-DRAPIDFUZZ_BUILD_TESTING=OFF -DRAPIDFUZZ_ENABLE_LINTERS=OFF"

# Header-only: the main package is legitimately empty.
ALLOW_EMPTY:${PN} = "1"
RDEPENDS:${PN}-dev = ""
