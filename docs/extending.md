# Extending the layer

How to add a board, an application or a library, change what a library is built
with, change the kernel configuration, and get the result onto hardware. Each
section ends with how to check the result, because most of the mistakes below
were caught by looking at the built artifact rather than at the build log.

## A note on verifying

A build that prints `all succeeded` has not necessarily produced anything: if
the disk monitor stops it, or a second bitbake fails on the lock, the summary
still reports the tasks that did run and the previous artifact is still sitting
on disk. Check the thing you built, and check its timestamp.

`RRECOMMENDS` and `IMAGE_INSTALL` entries that name a package which does not
exist are dropped silently. Always confirm the file landed in the rootfs:

    debugfs -R "stat /usr/lib/libfoo.so.1" tmp/deploy/images/<machine>/<image>.ext4

## Adding a board

A board is a kas fragment: a BSP layer, a machine name, and whatever that BSP
needs in `local.conf`. `kas/whinlatter-raspberrypi5.yml` is the shortest
example -- it includes the qemu config, replaces `machine:`, and adds one repo.

Not every BSP fits that shape. A BSP that ships its own distro and its own
layer set -- meta-tegra through tegra-demo-distro is the case in point -- is
driven by that project's `setup-env` and an explicit `bblayers.conf` instead,
with this layer added to it by absolute path. Adding such a board is a matter
for the product layer's own setup instructions, not for a kas fragment here.

### In order

The order matters more than any individual step. Each one below fails in a way
that is hard to read if the one before it is not already working.

1. **Get `core-image-minimal` booting on the board**, from the BSP's own
   instructions and with none of this layer involved. Debugging a fresh BitBake
   setup and a Qt cross-build at the same time is how these ports stall.
2. **Rehearse on qemu.** `kas/whinlatter-qemux86-64.yml` and `run.sh` bring the
   same image up on a machine you already have. Everything except the BSP --
   the recipe, the plugin set, the image contents, the appliance init -- is the
   same code, so it is worth being sure that part works before a board is in
   the loop.
3. **Write the kas fragment**: BSP layer, machine name, whatever that BSP needs
   in `local.conf`.
4. **Work out how `init=` reaches that kernel**, per the next section. There is
   no common variable, and getting this wrong is silent.
5. **Get a display backend up**, per the section after it. `/dev/dri/card0`
   existing is not the same as a backend working -- Qt can pick an EGL
   integration that lands on a node with no KMS, and score exits with zero
   screens rather than telling you which one it chose.
6. **Choose systemd or appliance**, which decides the image recipe.
7. **Flash it**, per "Getting an image onto hardware" at the end.

Step 6 has a trap worth stating on its own. The appliance image sets
`IMAGE_FEATURES = ""` -- no ssh, no getty -- because it expects to boot straight
into score. That is baked into the recipe, but the `init=` argument that makes
it do so is not, since it depends on the BSP. Build that image without also
arranging for `init=`, and it boots the default init with no way at all to log
in: recoverable only by reflashing. Either verify the kernel command line before
flashing, or start from the non-appliance image, which keeps ssh.

Two things differ per BSP and neither is discoverable from the other configs:

**How `init=` reaches the kernel.** There is no common variable. meta-tegra
reads `KERNEL_ARGS`; meta-raspberrypi builds `cmdline.txt` from `CMDLINE`; qemu
ignores both and takes `bootparams=` on the runqemu command line. The appliance
image sets `APPEND` and `CMDLINE`, which covers the Pi and qemu and does nothing
at all on Tegra. A new board needs its own way of injecting the argument, set
where that BSP looks for it.

**Which Qt backend works.** `ossia-score-launch` picks `eglfs` when
`/dev/dri/card0` exists, and Qt then chooses an EGL device integration on its
own. That choice is not always right: on Tegra it picks `eglfs_kms_egldevice`,
lands on the host1x node that has no KMS, and score dies with zero screens.
`ossia-score-init` sources `/etc/profile.d/*.sh` before starting score, so the
fix is a drop-in there setting `QT_QPA_EGLFS_INTEGRATION`. `ossia-score-tegra-env`
in this layer is that drop-in for Tegra and the model for another board that
needs one. To select a different backend entirely rather than tune eglfs, set
`OSSIA_SCORE_MODE` in the same place.

Check: `bitbake -e <image> | grep '^MACHINE='`, then boot it and look at the
console -- the init script prints the address it obtained and, on failure, says
no interface appeared.

## Adding an application

Put it in this layer if it is board-agnostic, and in your own product layer if
it is not. A recipe that will only ever build against one BSP does not belong
here: a `.bbappend` for a recipe that a build has no layer for is a hard parse
error, so it breaks every other board.

Install it by adding the package to `IMAGE_INSTALL` in an image recipe, or to
`IMAGE_INSTALL:append` in `local.conf` while you are still experimenting.

If the program opens libraries with `dlopen`, nothing links them and nothing
pulls them in. They have to be named explicitly -- see the next section.

## Adding a library

Three cases:

**It already exists in a layer you have.** Add the package name to
`RDEPENDS`/`RRECOMMENDS` of whatever needs it, or to the image. Find who
provides a file with `oe-pkgdata-util find-path /usr/lib/libfoo.so.1`.

**It exists in a layer you do not have.** meta-openembedded is already a
submodule of most BSP distros, and only some of its sub-layers are listed in
`bblayers.conf` -- `meta-xfce` and `meta-gnome` usually are not, even though the
files are on disk. Check before writing a recipe.

**It does not exist.** Write a recipe. `recipes-support/` in this layer has
short examples: `lv2kit`, `ysfx`, `rapidfuzz-cpp`.

For a `dlopen`ed library, use `RRECOMMENDS`, not `RDEPENDS`: it is installed by
default but an image can decline it with `BAD_RECOMMENDATIONS`. The set score
opens at runtime lives in `OSSIA_SCORE_OPTIONAL_LIBS` in `ossia-score.inc`,
where the BSP-specific entries are guarded by machine overrides.

Check: the library file is in the rootfs, per the note at the top.

## Updating a library

For a recipe in this layer, rename the file to the new version and update
`SRCREV` or `SRC_URI` and its checksums. Two traps:

- For a `git://` fetch, `SRCREV` must be the **commit**, not the tag object. An
  annotated tag has its own hash, and pointing `SRCREV` at that fails the fetch
  in a way that reads like a network error.
- `S` is not always `${WORKDIR}/git`. Recent releases set
  `BB_GIT_DEFAULT_DESTSUFFIX = "${BP}"`, so a recipe that hardcodes the old path
  breaks on upgrade.

`devtool upgrade <recipe> -V <version>` does the mechanical part and leaves you
a workspace to test in; `devtool finish` writes it back.

Check: `bitbake -e <recipe> | grep -E '^(PV|SRCREV|S)='` before building.

## Changing how a library is built

Use `PACKAGECONFIG` -- never patch a recipe to flip a build option. In a
`.bbappend`:

    PACKAGECONFIG:append = " gpl x264 x265"
    PACKAGECONFIG:remove = "ffmpeg"

Both of those are real: ffmpeg refuses to link the GPL encoders without its
`gpl` option, and x264 enables `PACKAGECONFIG[ffmpeg]` by default for its own
command-line tool, which makes `ffmpeg -> x264 -> ffmpeg` and stops the build
dead with a thousand unbuildable tasks. Removing it costs nothing if you only
want `libx264`.

Some recipes are gated on licence rather than configuration.
`LICENSE_FLAGS_ACCEPTED` is that gate, and OE's `commercial` flag is about
patent licensing, not copyleft -- accepting it is a legal decision, not a build
one. Note that changing it in a distro include invalidates task signatures well
beyond the recipes you meant to affect, and will rebuild much of the image; a
build directory's `local.conf` is the cheaper place to experiment.

Check: `bitbake -e <recipe> | grep '^PACKAGECONFIG='`, then look for the
feature in the built binary rather than trusting the flag, e.g.
`strings -a libavcodec.so.* | grep -x libx264`.

## Changing the kernel configuration

Add a `.cfg` fragment through a `.bbappend` on the kernel recipe. The recipe
name is per-BSP -- `linux-yocto`, `linux-raspberrypi`, `linux-jammy-nvidia-tegra`
-- which is exactly why such a bbappend belongs in a product or BSP layer rather
than here.

    # recipes-kernel/linux/<kernel-recipe>_%.bbappend
    FILESEXTRAPATHS:prepend := "${THISDIR}/files:"
    SRC_URI += "file://my-tuning.cfg"

    # files/my-tuning.cfg
    # CONFIG_FTRACE is not set
    CONFIG_MAGIC_SYSRQ_SERIAL_SEQUENCE="sysrq"

Changing whether something is a module or built in is not a local change.
Making a driver built-in deletes its `kernel-module-*` package, and any image or
initramfs that installs that package by name then fails to build -- an initramfs
that lists `kernel-module-nvme` is a real example. Change the config and the
package lists together.

Check the generated config, not the fragment:

    grep -E "^CONFIG_FTRACE|^# CONFIG_FTRACE" \
      tmp/work/<machine>/<kernel-recipe>/*/linux-*-build/.config

## Adding a kernel driver

### If the driver is in the kernel already

Enable it with a `.cfg` fragment as above. Prefer `=m` over `=y`: a module can
be loaded when the hardware appears, and making something built-in has the
package-list consequences described in the previous section.

### If it is an out-of-tree driver

Write a recipe that inherits `module`. The class builds against the kernel of
the machine being built and packages the result as `kernel-module-<name>`:

    # recipes-kernel/my-camera/my-camera_1.0.bb
    SUMMARY = "Out-of-tree camera sensor driver"
    LICENSE = "GPL-2.0-only"
    LIC_FILES_CHKSUM = "file://COPYING;md5=..."

    inherit module

    SRC_URI = "git://git.example.org/my-camera.git;branch=main;protocol=https"
    SRCREV = "<commit, not a tag object>"
    S = "${UNPACKDIR}/${BP}"

    EXTRA_OEMAKE += "KERNEL_SRC=${STAGING_KERNEL_DIR}"

    # The image installs this name, not the recipe name.
    RPROVIDES:${PN} += "kernel-module-my-camera"

Then install `kernel-module-my-camera` into the image. The recipe belongs in a
BSP or product layer if it only makes sense for one board.

### Getting it loaded

A driver with a `MODULE_DEVICE_TABLE` gets a modalias, and udev loads it when
the device appears -- nothing else to do. Drivers that bind to a device tree
node or nothing at all have no alias and are never loaded automatically. For
those:

    KERNEL_MODULE_AUTOLOAD += "my-camera"

which writes `/etc/modules-load.d/my-camera.conf`. Under systemd that file is
read by `systemd-modules-load`; under the appliance init there is no systemd, so
`ossia-score-init` walks `/etc/modules-load.d/*.conf` itself and modprobes what
it finds. Either way the same file is the contract, so use it rather than
loading the module from a script.

Module parameters go through:

    KERNEL_MODULE_PROBECONF += "my-camera"
    module_conf_my-camera = "options my-camera exposure=auto"

which writes `/etc/modprobe.d/`. That is how, for example, the NVIDIA DRM module
gets `modeset=1`.

### Device tree

A CSI sensor is not usable because its module loaded: the device tree has to
describe it, or nothing probes. How that is expressed is BSP-specific -- a
`KERNEL_DEVICETREE` entry, an overlay applied at boot, or a patch against the
BSP's dts. Check what the BSP already does for its own sensors and follow it;
this is usually the part that takes the time, not the driver recipe.

### Firmware

If the sensor needs a firmware blob, install it under `${nonarch_base_libdir}/firmware`
from its own recipe and add that package to the image. Note that a kernel
command line may redirect the search path -- Tegra images ship
`firmware_class.path=/etc/firmware` -- so check where the running kernel is
actually looking before concluding the blob is missing.

### Checking it worked

    lsmod | grep my_camera
    dmesg | grep -i my-camera          # probe succeeded, or the error
    ls /dev/video*                     # a V4L2 driver should have created a node
    v4l2-ctl --list-devices
    modinfo my-camera                  # parameters and aliases the module declares

If the node is missing but the module is loaded, the driver has not bound --
that is a device tree problem, not a packaging one. If the module is absent
entirely, check `/lib/modules/$(uname -r)/modules.alias` for an alias matching
the hardware; no alias means it needs `KERNEL_MODULE_AUTOLOAD`.

## Getting an image onto hardware

**SD-card and eMMC boards** (Raspberry Pi, Rockchip) build a `.wic` image:

    bmaptool copy tmp/deploy/images/<machine>/<image>.wic.bz2 /dev/sdX

`bmaptool` uses the `.bmap` file to skip empty blocks and is much faster than
`dd`; with `dd`, decompress first and use `bs=4M conv=fsync`.

**Boards flashed over USB** (Jetson) unpack a bundle and run a flashing script
from it. Unpack into an *empty* directory: a previous flash leaves root-owned
files behind, and unpacking over them silently mixes old and new artifacts.
