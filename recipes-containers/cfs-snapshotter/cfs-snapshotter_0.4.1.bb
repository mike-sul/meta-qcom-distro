SUMMARY = "composefs-backed remote snapshotter for containerd"
HOMEPAGE = "https://github.com/foundriesio/cfs-snapshotter"
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://src/${GO_IMPORT}/LICENSE;md5=3e50796147bee776fba45230e96f618b"
require ${BPN}-licenses.inc

GO_IMPORT = "github.com/foundriesio/cfs-snapshotter"
GO_INSTALL = "${GO_IMPORT}/cmd/containerd-cfs-grpc"

SRC_URI = "git://${GO_IMPORT}.git;protocol=https;branch=main;tag=v${PV};destsuffix=${GO_SRCURI_DESTSUFFIX} \
           file://containerd-config.toml \
           file://daemon.json \
          "
SRCREV = "1f0a1d5006ed1a1857ecf4e601c46cfdb1528470"
require ${BPN}-go-mods.inc

inherit go-mod go-mod-update-modules systemd

# Fully static, as upstream builds it: nothing in the snapshotter needs cgo,
# and -linkshared (the aarch64 default) requires cgo.
export CGO_ENABLED = "0"
GO_LINKSHARED = ""
GO_EXTRA_LDFLAGS = "-X ${GO_IMPORT}/pkg/version.Version=v${PV} -X ${GO_IMPORT}/pkg/version.Revision=${SRCREV}"

PACKAGECONFIG ??= ""
# docker-default: make cfs docker's snapshotter by shipping
# /etc/docker/daemon.json (containerd-snapshotter feature, storage-driver
# cfs). This moves docker to containerd's image store, so images pulled
# before no longer show up. Without it docker keeps its own default and cfs
# serves only clients that ask for it (ctr --snapshotter cfs, or a
# daemon.json naming cfs).
PACKAGECONFIG[docker-default] = ""
# preloaded: serve only images placed in the store with ocicfs; decline the rest.
PACKAGECONFIG[preloaded] = ",,,ocicfs"

do_install() {
    install -Dm 0755 ${B}/${GO_BUILD_BINDIR}/containerd-cfs-grpc ${D}${bindir}/containerd-cfs-grpc

    install -Dm 0644 ${S}/src/${GO_IMPORT}/debian/cfs-snapshotter.service \
        ${D}${systemd_system_unitdir}/cfs-snapshotter.service
    if ${@bb.utils.contains('PACKAGECONFIG', 'preloaded', 'true', 'false', d)}; then
        # The daemon takes the last -admit on the command line, so this wins
        # over anything in CFS_OPTS.
        sed -i 's|^ExecStart=.*|& -admit=preloaded|' \
            ${D}${systemd_system_unitdir}/cfs-snapshotter.service
    fi
    install -Dm 0644 ${S}/src/${GO_IMPORT}/debian/cfs-snapshotter.default \
        ${D}${sysconfdir}/default/cfs-snapshotter

    # Neither QLI nor the containerd recipe ships a containerd config, so this
    # one is the whole file; should either start shipping one, the rootfs
    # build fails on the file conflict.
    install -Dm 0644 ${UNPACKDIR}/containerd-config.toml ${D}${sysconfdir}/containerd/config.toml
    if ${@bb.utils.contains('PACKAGECONFIG', 'docker-default', 'true', 'false', d)}; then
        install -Dm 0644 ${UNPACKDIR}/daemon.json ${D}${sysconfdir}/docker/daemon.json
    fi
}

SYSTEMD_SERVICE:${PN} = "cfs-snapshotter.service"
CONFFILES:${PN} = " \
    ${sysconfdir}/default/cfs-snapshotter \
    ${sysconfdir}/containerd/config.toml \
    ${@bb.utils.contains('PACKAGECONFIG', 'docker-default', '${sysconfdir}/docker/daemon.json', '', d)} \
"

# containerd loads the snapshotter; pulls are built into the store with
# mkcomposefs.
RDEPENDS:${PN} = "virtual-containerd composefs"
# vxn owns /etc/containerd/config.toml; docker-registry-config and
# vxn-docker-config own /etc/docker/daemon.json.
RCONFLICTS:${PN} = "vxn ${@bb.utils.contains('PACKAGECONFIG', 'docker-default', 'docker-registry-config vxn-docker-config', '', d)}"
