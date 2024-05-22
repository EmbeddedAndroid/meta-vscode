DESCRIPTION = "Packaging for prebuilt versions of vscode"
HOMEPAGE = "https://code.visualstudio.com/download"
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://../VSCode-linux-arm64/LICENSES.chromium.html;md5=c590e5227ea914c9a4187d5b1cbefe93"

inherit bin_package

GIT_SHA = "dc96b837cf6bb4af9cd736aa3af08cf8279f7685"
SRC_URI = "https://vscode.download.prss.microsoft.com/dbazure/download/stable/${GIT_SHA}/code-stable-arm64-1715058820.tar.gz"
SRC_URI[md5sum] = "c71bd32ab70f5615621e61c548be9e4c"

do_install() {
    install -d ${D}/opt/vscode
    cp -r ${WORKDIR}/VSCode-linux-arm64/* ${D}/opt/vscode
}

FILES_${PN} = "/opt/*"
INSANE_SKIP:${PN} += "already-stripped file-rdeps ldflags"
