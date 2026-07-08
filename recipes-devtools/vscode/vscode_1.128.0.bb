SUMMARY = "Visual Studio Code"
DESCRIPTION = "Visual Studio Code, pre-built binaries from Microsoft's stable \
release CDN. The shipped binaries include the proprietary Microsoft branding, \
telemetry, and Marketplace access; the OSS-licensed portions of the source are \
the same code as github.com/microsoft/vscode but the assembled binary is \
covered by the Microsoft Software License Terms."
HOMEPAGE = "https://code.visualstudio.com/"

# Microsoft's distributed VSCode build is covered by the Microsoft Software
# License Terms; the embedded Chromium and Node.js components are BSD / MIT.
# We pin LIC_FILES_CHKSUM to LICENSES.chromium.html since it's the most
# stable artefact across versions and exists in every architecture's tarball.
LICENSE = "Proprietary"
LIC_FILES_CHKSUM = "file://LICENSES.chromium.html;md5=cfa5de8d0264d369c26a42ca85a40c86"

# Only the three Linux arches Microsoft actually ships pre-built binaries for.
# riscv64 / mips / etc users have to build from source (out of scope for this
# layer) or use a remote-development setup (code-server / openvscode-server).
COMPATIBLE_HOST = "(x86_64|aarch64|arm).*-linux"

def get_vscode_arch(d):
    arch = d.getVar('HOST_ARCH')
    mapping = {
        'aarch64': 'arm64',
        'arm':     'armhf',
        'x86_64':  'x64',
    }
    return mapping.get(arch, '')

VSCODE_ARCH ?= "${@get_vscode_arch(d)}"

# Per-arch upload timestamps Microsoft embeds in the tarball URL. Bumping
# the version means re-resolving these via
# `curl -sLI https://update.code.visualstudio.com/latest/linux-$arch/stable`
# and copying the timestamp out of the Location header.
GIT_SHA = "fc3def6774c76082adf699d366f31a557ce5573f"
TIMESTAMP-arm64 = "1783465268"
TIMESTAMP-armhf = "1783465276"
TIMESTAMP-x64   = "1783465271"

SRC_URI = "https://vscode.download.prss.microsoft.com/dbazure/download/stable/${GIT_SHA}/code-stable-${VSCODE_ARCH}-${TIMESTAMP-${VSCODE_ARCH}}.tar.gz;name=vscode-${VSCODE_ARCH}"

SRC_URI[vscode-x64.sha256sum]   = "a9b4ce974ecc10c7456da9879763bf0a7a432710bd26c95a89a8a168f2aff57b"
SRC_URI[vscode-arm64.sha256sum] = "6fd05e1fe7b6f6540fc7a5aee555b49b3f761ef099186b4e2fd0c22d297ef27f"
SRC_URI[vscode-armhf.sha256sum] = "b6244a29b26fbafbb53e0c3b64d54fa98b950e4f8cd641c1725427625f4b17bf"

# Styhead+ introduced UNPACKDIR as the directory do_unpack writes to,
# distinct from WORKDIR which is reserved for build artefacts. We have
# to source S from UNPACKDIR on those releases; on kirkstone/scarthgap
# UNPACKDIR isn't defined, so fall back to WORKDIR. A literal
# UNPACKDIR = ${WORKDIR} assignment is rejected by bitbake.conf's QA
# on styhead+, so compute the prefix via Python instead.
S = "${@(d.getVar('UNPACKDIR') or d.getVar('WORKDIR'))}/VSCode-linux-${VSCODE_ARCH}"

# These are arch-specific prebuilt ELFs, not noarch content; bin_package
# inherits allarch which is wrong here. Override back to per-machine
# packaging so an arm64 build and an x86_64 build produce distinct .ipks.
inherit bin_package
PACKAGE_ARCH = "${MACHINE_ARCH}"

do_install() {
    install -d ${D}${datadir}/vscode
    cp --preserve=mode,timestamps -R ${S}/. ${D}${datadir}/vscode/
}

FILES:${PN} = "${datadir}/vscode"

# The shipped binaries are stripped and link against the host's glibc / X /
# wayland stack; suppress Yocto QA checks that would otherwise complain
# about them.
INSANE_SKIP:${PN} += "already-stripped file-rdeps ldflags libdir arch staticdev"

# The Microsoft tarball includes its own libvulkan / chrome-sandbox under
# /usr/share/vscode/; Yocto's debug-info split picks those up and stashes a
# .debug/ next to them, which then trips the libdir QA check on ${PN}-dbg.
# Same root cause as the main package; suppress it there too.
INSANE_SKIP:${PN}-dbg += "libdir"
