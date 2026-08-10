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

# Two benign messages that log_check, which greps do_rootfs's log for "error",
# would otherwise fail the image over.
#
#   kernel-module-error   kernel-modules pulls every module the kernel built,
#                         and the L4T kernel ships one named "error", so
#                         "Package kernel-module-error-... will be installed"
#                         lands in the log. Images installing named modules
#                         only never see this.
#   getty@tty1.service    systemd-ossia-ordering masks it on purpose, so score
#                         owns the VT; systemd's preset step then reports
#                         "Failed to preset unit: ... is masked".
#
# Entries are split on whitespace, so each must be a single token -- a phrase
# like "is masked" would become the patterns "is" and "masked" and silence
# almost everything.
IMAGE_LOG_CHECK_EXCLUDES += "kernel-module-error getty@tty1.service"
