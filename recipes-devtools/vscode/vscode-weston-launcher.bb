SUMMARY = "Weston panel launcher entry for Visual Studio Code"
DESCRIPTION = "Optional companion package for meta-vscode: appends a [launcher] \
section to /etc/xdg/weston/weston.ini at install time so the VSCode icon \
appears on the Weston panel. Pull in via IMAGE_INSTALL when the target image \
ships a Weston desktop and should expose VSCode as a one-click launch."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

# Pure config; no compile + no arch-specific content.
inherit allarch

# We don't build-depend on either package, only need them on the
# target at install time.
RDEPENDS:${PN} = "vscode weston-init"

do_install() {
    install -d ${D}${datadir}/vscode-weston-launcher
    # The launcher fragment is appended verbatim to weston.ini at
    # postinst. Sentinel comments delimit the block so prerm can
    # cleanly strip it back out on package removal.
    cat > ${D}${datadir}/vscode-weston-launcher/launcher.ini <<'EOF'

# meta-vscode-launcher-begin
[launcher]
icon=${datadir}/vscode/resources/app/resources/linux/code.png
path=${datadir}/vscode/bin/code
displayname=Visual Studio Code
# meta-vscode-launcher-end
EOF
}

FILES:${PN} = "${datadir}/vscode-weston-launcher"

pkg_postinst:${PN} () {
    weston_ini="$D${sysconfdir}/xdg/weston/weston.ini"
    fragment="$D${datadir}/vscode-weston-launcher/launcher.ini"
    if [ -e "$weston_ini" ] && ! grep -q "meta-vscode-launcher-begin" "$weston_ini"; then
        cat "$fragment" >> "$weston_ini"
    fi
}

pkg_prerm:${PN} () {
    weston_ini="$D${sysconfdir}/xdg/weston/weston.ini"
    if [ -e "$weston_ini" ] && grep -q "meta-vscode-launcher-begin" "$weston_ini"; then
        sed -i '/# meta-vscode-launcher-begin/,/# meta-vscode-launcher-end/d' "$weston_ini"
    fi
}
