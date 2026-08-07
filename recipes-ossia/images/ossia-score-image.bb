SUMMARY = "Bare-bones image running ossia score"
DESCRIPTION = "A minimal bootable image whose only purpose is to run ossia \
score: kernel, init, audio and GPU stacks, networking, and score itself with \
every plugin and addon that cross-compiles. No desktop environment."
LICENSE = "MIT"

inherit core-image

# Add "debug-tweaks" locally for a passwordless root console during bring-up.
IMAGE_FEATURES += "ssh-server-openssh"

# No PipeWire: score talks to ALSA directly.
IMAGE_INSTALL += " \
    ossia-score \
    alsa-utils \
    alsa-plugins \
    kernel-modules \
    e2fsprogs-mke2fs \
    tzdata \
    ca-certificates \
    systemd-ossia-ordering \
"

# score's RSS is ~250 MB once the UI is up and it allocates well above that
# during startup. Budget 512 MB or more.
IMAGE_OVERHEAD_FACTOR = "1.3"
IMAGE_ROOTFS_EXTRA_SPACE = "524288"

# Appended, not assigned: machines with their own FSTYPES keep them.
IMAGE_FSTYPES:append = " wic.xz wic.bmap"
# wic needs a .wks and this layer ships none, relying on the machine to provide
# one. The Tegra machines do not: they build a tegraflash bundle instead.
IMAGE_FSTYPES:remove:tegra = "wic.xz wic.bmap"
