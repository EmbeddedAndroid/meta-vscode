DESCRIPTION = "Packaging for prebuilt versions of vscode"
HOMEPAGE = "https://code.visualstudio.com/download"
LICENSE = "CUSTOM"
LIC_FILES_CHKSUM = "file://LICENSE;md5=254392426853a1dff30e73de3f901347"

inherit bin_package allarch

GIT_SHA = "dc96b837cf6bb4af9cd736aa3af08cf8279f7685"
SRC_URI = "https://vscode.download.prss.microsoft.com/dbazure/download/stable/${GIT_SHA}/code-stable-arm64-1715058820.tar.gz"
SRC_URI[md5sum] = "c71bd32ab70f5615621e61c548be9e4c"
