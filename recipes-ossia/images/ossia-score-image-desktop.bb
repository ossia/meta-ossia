SUMMARY = "ossia-score-image plus a desktop and development tools"
DESCRIPTION = "The image to develop on: the same score build as the other \
images, with an X11 session, a terminal, ssh and debuggers on top. Boots to a \
desktop; score is started by hand rather than by an init. \
\
Larger and slower to boot than either of the others, and not what an appliance \
should ship."

LICENSE = "MIT"

require recipes-ossia/images/ossia-score-image.bb

# XFCE is an X11 desktop, so this image only builds where x11 is in
# DISTRO_FEATURES -- the ossia distro leaves it out, since nothing else here
# needs it. Add it in the build configuration rather than trying to set it per
# image: it changes how score itself is configured, so the two cannot share a
# build directory. kas/whinlatter-qemux86-64-desktop.yml does this.
IMAGE_FEATURES += "x11-base tools-debug"

OSSIA_DESKTOP = " \
    packagegroup-xfce-base \
    xfce4-terminal \
    xfce4-taskmanager \
    thunar \
"

OSSIA_DEVTOOLS = " \
    gdb \
    strace \
    ltrace \
    htop \
    vim \
    git \
    rsync \
    tmux \
"

IMAGE_INSTALL:append = " ${OSSIA_DESKTOP} ${OSSIA_DEVTOOLS}"

IMAGE_ROOTFS_EXTRA_SPACE = "2097152"

# ossia-score.service is enabled by default, and at sysinit.target it would
# take DRM/KMS through eglfs before the display manager starts. On a desktop
# that is a fight over the same device, so leave the unit installed but not
# wanted -- `ossia-score-launch x11` from a terminal is the point of this image.
disable_ossia_score_service() {
    rm -f ${IMAGE_ROOTFS}${sysconfdir}/systemd/system/sysinit.target.wants/ossia-score.service
}
ROOTFS_POSTPROCESS_COMMAND += "disable_ossia_score_service"
