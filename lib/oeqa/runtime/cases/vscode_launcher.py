# OEQA runtime tests for vscode + vscode-weston-launcher.
#
# These are picked up by the `testimage` bbclass when this layer is
# enabled and TEST_SUITES contains "vscode_launcher". They run against
# a booted qemu image via ssh.

import time

from oeqa.runtime.case import OERuntimeTestCase
from oeqa.core.decorator.depends import OETestDepends


class VSCodeLauncherInstallTest(OERuntimeTestCase):
    """Validate the vscode-weston-launcher postinst actually ran on the
    target and the VSCode binary itself is in place."""

    def test_weston_ini_has_launcher(self):
        # The postinst writes a sentinel-delimited [launcher] block
        # into weston.ini. If it isn't there the installer ran on a
        # target that didn't have /etc/xdg/weston/weston.ini yet (e.g.
        # weston-init wasn't installed before vscode-weston-launcher),
        # or the postinst failed silently.
        status, out = self.target.run(
            "grep meta-vscode-launcher-begin /etc/xdg/weston/weston.ini")
        self.assertEqual(
            status, 0,
            "vscode-weston-launcher postinst didn't insert its block "
            "into /etc/xdg/weston/weston.ini.\nOutput: %s" % out)

    def test_vscode_binary_is_executable(self):
        status, out = self.target.run(
            "test -x /usr/share/vscode/bin/code "
            "&& test -f /usr/share/vscode/resources/app/resources/linux/code.png")
        self.assertEqual(
            status, 0,
            "/usr/share/vscode/bin/code or the launcher icon are not "
            "present on the target.\nOutput: %s" % out)

    @OETestDepends([
        'vscode_launcher.VSCodeLauncherInstallTest.test_vscode_binary_is_executable',
    ])
    def test_vscode_cli_version(self):
        # CLI mode doesn't initialise Electron; it just dlopens enough
        # of node to print the version triplet:
        #
        #     1.120.0
        #     0958016b2af9f09bb4257e0df4a95e2f90590f9f
        #     x64
        #
        # This proves the binary's glibc / libstdc++ / libnss links
        # are all resolved on the target.
        status, out = self.target.run("/usr/share/vscode/bin/code --version")
        self.assertEqual(status, 0,
                         "code --version exit=%d: %s" % (status, out))
        lines = out.strip().splitlines()
        self.assertGreaterEqual(
            len(lines), 2,
            "Expected at least 2 lines from --version, got: %r" % out)


class WestonStartedTest(OERuntimeTestCase):
    """Validate weston-init started Weston on boot."""

    def test_weston_process_running(self):
        # weston-init is socket-activated via systemd and brings weston
        # up shortly after boot. Give it 30s to settle.
        deadline = time.time() + 30
        while time.time() < deadline:
            status, _ = self.target.run("pgrep -x weston")
            if status == 0:
                return
            time.sleep(1)
        self.fail("Weston did not start within 30s of boot")

    @OETestDepends([
        'vscode_launcher.WestonStartedTest.test_weston_process_running',
    ])
    def test_wayland_socket_present(self):
        # XDG_RUNTIME_DIR varies (root vs the weston user); look in
        # both well-known locations.
        status, out = self.target.run(
            "for d in /run/user/0 /run/user/1000; do "
            "  [ -S \"$d/wayland-0\" ] && echo \"$d\" && exit 0; "
            "done; exit 1")
        self.assertEqual(status, 0,
                         "No wayland-0 socket found on the target")


class VSCodeWaylandLaunchTest(OERuntimeTestCase):
    """Validate VSCode can talk to Weston: launch the Electron app
    against the live Wayland socket, give it a few seconds to settle,
    and verify the process is still alive and has the wayland socket
    open."""

    @OETestDepends([
        'vscode_launcher.VSCodeLauncherInstallTest.test_vscode_cli_version',
        'vscode_launcher.WestonStartedTest.test_wayland_socket_present',
    ])
    def test_vscode_starts_on_wayland(self):
        # Find weston's XDG_RUNTIME_DIR.
        status, runtime_dir = self.target.run(
            "for d in /run/user/0 /run/user/1000; do "
            "  [ -S \"$d/wayland-0\" ] && echo \"$d\" && exit 0; "
            "done")
        self.assertEqual(status, 0)
        runtime_dir = runtime_dir.strip()
        self.assertTrue(runtime_dir, "no wayland-0 socket found")

        # Launch VSCode pointed at the Wayland socket. --no-sandbox
        # because the chromium sandbox needs CAP_SYS_ADMIN which the
        # test rootfs doesn't grant. user-data-dir and extensions-dir
        # under /tmp so the launch doesn't try to write into
        # ~/.config / ~/.vscode and trip permission errors.
        launch_cmd = (
            "rm -rf /tmp/vscode-test* && "
            "env WAYLAND_DISPLAY=wayland-0 "
            "    XDG_RUNTIME_DIR=%s "
            "/usr/share/vscode/bin/code "
            "  --no-sandbox "
            "  --user-data-dir=/tmp/vscode-test "
            "  --extensions-dir=/tmp/vscode-test-ext "
            "  >/tmp/vscode.log 2>&1 &" % runtime_dir)
        self.target.run(launch_cmd)

        # Electron/VSCode takes 10-15s to fully spin up under qemu.
        time.sleep(20)

        # Process still running?
        status, _ = self.target.run("pgrep -f 'vscode-test'")
        self.assertEqual(
            status, 0,
            "VSCode died within 20s of launch. Last log lines:\n%s"
            % self.target.run("tail -40 /tmp/vscode.log")[1])

        # Has the wayland-0 socket open in its fd table?
        status, fd_list = self.target.run(
            "for pid in $(pgrep -f vscode-test); do "
            "  ls -l /proc/$pid/fd 2>/dev/null | grep -F wayland-0 && exit 0; "
            "done; exit 1")
        self.assertEqual(
            status, 0,
            "No VSCode process has wayland-0 open. fd dump:\n%s"
            % fd_list)

        # Tidy up so subsequent test runs don't trip over each other.
        self.target.run("pkill -9 -f vscode-test || true")
