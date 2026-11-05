SUMMARY = "Content-addressed OCI image store and composefs mount tool"
HOMEPAGE = "https://github.com/foundriesio/ocicfs"
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://src/${GO_IMPORT}/LICENSE;md5=3e50796147bee776fba45230e96f618b"
require ${BPN}-licenses.inc

GO_IMPORT = "github.com/foundriesio/ocicfs"
GO_INSTALL = "${GO_IMPORT}/cmd/ocicfs"

SRC_URI = "git://${GO_IMPORT}.git;protocol=https;branch=main;tag=v${PV};destsuffix=${GO_SRCURI_DESTSUFFIX}"
SRCREV = "404c8dca32452d1c4d111448145d839688ee398f"
require ${BPN}-go-mods.inc

inherit go-mod go-mod-update-modules

# Fully static, as upstream builds it: nothing in ocicfs needs cgo, and
# -linkshared (the aarch64 default) requires cgo.
export CGO_ENABLED = "0"
GO_LINKSHARED = ""
GO_EXTRA_LDFLAGS = "-X ${GO_IMPORT}/pkg/version.Version=v${PV} -X ${GO_IMPORT}/pkg/version.Revision=${SRCREV}"

# Only the binary: the inherited install also ships the source tree, whose
# packaging scripts would drag make and bash into the -dev package.
do_install() {
    install -Dm 0755 ${B}/${GO_BUILD_BINDIR}/ocicfs ${D}${bindir}/ocicfs
}

# pull builds the EROFS with mkcomposefs and fetches from registries over HTTPS.
RDEPENDS:${PN} = "composefs ca-certificates"
