SUMMARY = "ossia score appliance image -- score as PID 1, no init system"
DESCRIPTION = "A fixed-function variant of ossia-score-image: no service \
manager running, no networking, no udev, no journal. Boot with \
init=/usr/bin/ossia-score-init."
LICENSE = "MIT"

inherit core-image

# No ssh, no package management: there is no way in over the network.
IMAGE_FEATURES = ""

IMAGE_INSTALL += " \
    ossia-score \
    alsa-plugins \
    kernel-modules \
"

# No udev, so no hotplug: everything must be attached at power-on.
# CONFIG_DEVTMPFS_MOUNT still provides /dev/dri. systemd is in the rootfs via
# packagegroup-core-boot but never runs.
# Needs >= 192 MB RAM; below that score is OOM-killed during startup.

# runqemu ignores APPEND; pass bootparams='init=...' instead.
APPEND:append = " init=/usr/bin/ossia-score-init"
CMDLINE:append = " init=/usr/bin/ossia-score-init"

IMAGE_ROOTFS_EXTRA_SPACE = "524288"

IMAGE_FSTYPES:append = " wic.xz wic.bmap"
# wic needs a .wks and this layer ships none, relying on the machine to provide
# one. The Tegra machines do not: they build a tegraflash bundle instead.
IMAGE_FSTYPES:remove:tegra = "wic.xz wic.bmap"

# kernel-modules pulls every module the kernel built, and the L4T kernel ships
# one named "error". That puts
#   ---> Package kernel-module-error-... will be installed
# in the do_rootfs log, where log_check greps for the word "error" and fails
# the image over a package name. Images that install only named modules never
# see this.
IMAGE_LOG_CHECK_EXCLUDES += "kernel-module-error"
