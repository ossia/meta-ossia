SUMMARY = "Tegra display environment for ossia score"
DESCRIPTION = "\
Pins score's eglfs backend to eglfs_kms on Tegra, where Qt otherwise selects \
eglfs_kms_egldevice, lands on the host1x node that has no KMS, and score dies \
with zero screens. Ships a systemd drop-in for ossia-score.service and a \
profile.d snippet, so the systemd path and the appliance path agree -- \
ossia-score-init sources /etc/profile.d/*.sh before starting score."

LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://10-tegra-eglfs.conf \
    file://ossia-score-tegra-eglfs.sh \
"

# Config files only, and QT_QPA_EGLFS_INTEGRATION is read by the eglfs plugin
# alone -- it is inert under any other QPA -- so this is a no-op rather than a
# conflict on a board that does not need it.
inherit systemd allarch

do_install() {
    install -d ${D}${systemd_system_unitdir}/ossia-score.service.d
    install -m 0644 ${UNPACKDIR}/10-tegra-eglfs.conf \
        ${D}${systemd_system_unitdir}/ossia-score.service.d/10-tegra-eglfs.conf

    install -d ${D}${sysconfdir}/profile.d
    install -m 0644 ${UNPACKDIR}/ossia-score-tegra-eglfs.sh \
        ${D}${sysconfdir}/profile.d/ossia-score-tegra-eglfs.sh
}

FILES:${PN} = " \
    ${systemd_system_unitdir} \
    ${sysconfdir}/profile.d \
"

RDEPENDS:${PN} = "ossia-score"
