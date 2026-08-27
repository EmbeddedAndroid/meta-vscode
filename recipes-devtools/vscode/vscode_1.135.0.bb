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
LIC_FILES_CHKSUM = "file://LICENSES.chromium.html;md5=acdffd0ca8106d0b6e9c22eaf16acaef"

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
GIT_SHA = "08d4889f9ec4a1685d257b9b95de036c8e1ce1e5"
TIMESTAMP-arm64 = "1787669021"
TIMESTAMP-armhf = "1787669002"
TIMESTAMP-x64   = "1787668990"

SRC_URI = "https://vscode.download.prss.microsoft.com/dbazure/download/stable/${GIT_SHA}/code-stable-${VSCODE_ARCH}-${TIMESTAMP-${VSCODE_ARCH}}.tar.gz;name=vscode-${VSCODE_ARCH}"

SRC_URI[vscode-x64.sha256sum]   = "470f281e9d75f03b1b957f38e193580140de3b1d9c9ba6db13e7bf30aead4e00"
SRC_URI[vscode-arm64.sha256sum] = "9d67bedf244707de8510554d146273d1635018297c6f6ca792a5e37868856662"
SRC_URI[vscode-armhf.sha256sum] = "788df245c0972b430f4a302e06e68ad491ccc7fa4ca14228b60a85391d5b096b"

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
