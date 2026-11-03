"""Test PID discovery against procfs fixtures, including app_process argv rewriting."""
import pathlib
import subprocess
import tempfile
import unittest
from shell_compat import environment, shells

ENGINE = pathlib.Path(__file__).resolve().parents[1]

class ProcessIdentityTest(unittest.TestCase):
    def discover(self, fixtures, listener=None, tcp6=False, extra_socket=False):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            proc = root / 'proc'; proc.mkdir(); (proc / 'net').mkdir()
            (proc / 'net/tcp').write_text('0: 0100007F:221D 00000000:0000 0A 0 0 0 0 0 9001\n' if listener and not tcp6 else '')
            if tcp6:
                (proc / 'net/tcp6').write_text('0: 0000000000000000FFFF00000100007F:221D 0:0000 0A 0 0 0 0 0 9001\n')
            if extra_socket:
                with (proc / 'net/tcp').open('a') as stream:
                    stream.write('1: 0100007F:221D 00000000:0000 0A 0 0 0 0 0 9002\n')
            for pid, comm, argv, uid in fixtures:
                directory = proc / str(pid); directory.mkdir(); (directory / 'fd').mkdir()
                (directory / 'comm').write_text(comm + '\n')
                (directory / 'cmdline').write_bytes(b'\0'.join(arg.encode() for arg in argv) + b'\0')
                (directory / 'status').write_text(f'Uid:\t{uid}\t{uid}\t{uid}\t{uid}\n')
                if pid == listener: (directory / 'fd/5').symlink_to('socket:[9001]')
            source = (ENGINE / 'vd.sh').read_text().split('\ncase "${1:-}"')[0]
            source = source.replace('/data/local/tmp/eta', str(root / 'eta')).replace('/proc/', str(proc) + '/')
            script = root / 'probe.sh'; script.write_text(source + '\nengine_pids\n')
            outcomes = []
            for name, shell in shells():
                with self.subTest(shell=name):
                    result = subprocess.run(shell + [str(script)], env=environment(root, shell),
                                            capture_output=True, text=True, timeout=5)
                    self.assertEqual(0, result.returncode, result.stderr)
                    outcomes.append(set(result.stdout.split()))
            self.assertTrue(all(value == outcomes[0] for value in outcomes))
            return outcomes[0]

    def test_new_name_legacy_full_argv_and_other_ports(self):
        self.assertEqual({'10', '11'}, self.discover([
            (10, 'eta-vdrive-8733', ['eta-vdrive-8733'], 0),
            (11, 'VDrive', ['app_process', '/system/bin', 'VDrive', '--tcp=8733'], 0),
            (12, 'VDrive', ['app_process', '/system/bin', 'VDrive', '--tcp=8736'], 0),
            (13, 'eta-vdrive-8733', ['eta-vdrive-8733'], 10001),
            (14, 'other', ['unrelated'], 0),
        ]))

    def test_rewritten_legacy_argv_requires_listening_socket_ownership(self):
        self.assertEqual({'20'}, self.discover([
            (20, 'VDrive', ['VDrive'], 0), (21, 'VDrive', ['VDrive'], 0),
        ], listener=20))

    def test_thread_name_changes_do_not_hide_engine_and_arguments_do_not_impersonate_it(self):
        self.assertEqual({'30', '31'}, self.discover([
            (30, 'vdrive-listen', ['eta-vdrive-8733'], 0),
            (31, 'main', ['/system/bin/app_process64', '/system/bin', 'VDrive', '--tcp=8733'], 0),
            (32, 'main', ['sh', 'eta-vdrive-8733', 'VDrive', '--tcp=8733'], 0),
            (33, 'eta-vdrive-8733', ['unrelated'], 0),
        ]))

    def test_ipv6_mapped_socket_and_multiple_inodes_are_matched_to_owner_only(self):
        self.assertEqual({'40'}, self.discover([
            (40, 'main', ['VDrive'], 0), (41, 'main', ['VDrive'], 0),
            (42, 'eta-vdrive-8733', ['unrelated'], 0),
        ], listener=40, tcp6=True, extra_socket=True))

    def test_socket_owner_without_engine_identity_is_never_selected(self):
        self.assertEqual(set(), self.discover([
            (50, 'eta-vdrive-8733', ['some_other_daemon'], 0),
            (51, 'main', ['VDrive'], 0),
        ], listener=50, tcp6=True))

    def test_cleanup_rechecks_process_start_and_identity_before_signalling(self):
        for scenario in ['own_engine', 'pid_reused', 'process_exec_changed']:
            for name, shell in shells():
                with self.subTest(scenario=scenario, shell=name), tempfile.TemporaryDirectory() as temp:
                    root = pathlib.Path(temp)
                    proc = root / 'proc'; proc.mkdir()
                    target = proc / '600'; target.mkdir()
                    (target / 'cmdline').write_bytes(b'eta-vdrive-8733\0')
                    (target / 'status').write_text('Uid:\t0\t0\t0\t0\n')
                    # starttime is field 22; comm is intentionally not the engine name.
                    stat = '600 (main thread) ' + ' '.join(['0'] * 19) + ' 100\n'
                    (target / 'stat').write_text(stat)
                    source = (ENGINE / 'vd.sh').read_text().split('\ncase "${1:-}"')[0]
                    source = source.replace('/data/local/tmp/eta', str(root / 'eta')).replace('/proc/', str(proc) + '/')
                    mutation = ':'
                    if scenario != 'own_engine':
                        mutation = f"printf 'unrelated\\0' > '{target}/cmdline'"
                    if scenario == 'pid_reused':
                        mutation += f"\nprintf '%s' '{stat.replace('100', '101')}' > '{target}/stat'"
                    source += f'''
rpc() {{ {mutation}
}}
sleep() {{ :; }}
kill() {{ printf '%s\\n' "$*" > '{root}/signalled'; rm -rf '{target}'; }}
wait_port_free() {{ printf 'PORT_FREE\\n'; }}
stop_engine
'''
                    script = root / 'stop.sh'; script.write_text(source)
                    result = subprocess.run(shell + [str(script)], env=environment(root, shell),
                                            capture_output=True, text=True, timeout=5)
                    if scenario == 'own_engine':
                        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
                        self.assertEqual('-9 600', (root / 'signalled').read_text().strip())
                    else:
                        self.assertFalse((root / 'signalled').exists(), result.stdout + result.stderr)
                        if scenario == 'pid_reused': self.assertEqual(0, result.returncode, result.stderr)
                        else: self.assertIn('ETA_ENGINE_ERROR=STOP_FAILED', result.stdout)

if __name__ == '__main__': unittest.main()
