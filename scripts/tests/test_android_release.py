import hashlib
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('release', Path(__file__).parents[1] / 'android_release.py')
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)
SHA = 'a' * 40

class ReleaseMetadataTests(unittest.TestCase):
    def source(self, code=1000041, name='4.0.1'):
        return f'versionCode = {code}\nversionName = "{name}"\n'

    def test_stable_orders_between_same_and_next_commit_development(self):
        dev = release.version(self.source(), 20, SHA, 'refs/heads/main')
        stable = release.version(self.source(), 20, SHA, 'refs/tags/v4.0.1')
        next_dev = release.version(self.source(), 21, SHA, 'refs/heads/main')
        self.assertLess(dev['version_code'], stable['version_code'])
        self.assertLess(stable['version_code'], next_dev['version_code'])
        self.assertEqual(dev['tag'], 'development')
        self.assertEqual(stable['version_name'], '4.0.1')

    def test_ref_and_stable_version_mismatches_fail(self):
        for ref in ['refs/heads/feature', 'refs/tags/v4.0.2', 'refs/tags/v4.0.1-beta.1']:
            with self.subTest(ref=ref), self.assertRaises(ValueError):
                release.version(self.source(), 20, SHA, ref)
        with self.assertRaises(ValueError):
            release.version(self.source(code=4), 20, SHA, 'refs/tags/v4.0.1')

    def test_invalid_history_and_limit_fail(self):
        for count in [0, -1, 2_100_000_000]:
            with self.subTest(count=count), self.assertRaises(ValueError):
                release.version(self.source(), count, SHA, 'refs/heads/main')
        with self.assertRaises(ValueError):
            release.version(self.source(), 20, 'short', 'refs/heads/main')

    def test_checksum_validates_bytes_inventory_and_paths(self):
        digest = hashlib.sha256(b'apk').hexdigest()
        valid = {'app.apk': b'apk', 'SHA256SUMS-build.txt': f'{digest}  app.apk\n'.encode()}
        release.validate_manifest(valid)
        for bad in [dict(valid, **{'app.apk': b'corrupted'}), dict(valid, **{'extra.aab': b'unlisted'}),
                    {'SHA256SUMS-build.txt': f'{digest}  ../app.apk\n'.encode(), 'app.apk': b'apk'}]:
            with self.assertRaises(ValueError):
                release.validate_manifest(bad)

    def test_framework_icon_does_not_hide_manifest_identity_checks(self):
        import os
        import tempfile
        from unittest.mock import patch
        xml = '''<manifest xmlns:android="http://schemas.android.com/apk/res/android"
            package="com.ankiride.bridge" android:versionCode="1000040"
            android:versionName="4.0.0-dev.test">
            <application android:icon="@android:drawable/ic_menu_manage" />
        </manifest>'''
        metadata = {'version_code': 1000040, 'version_name': '4.0.0-dev.test'}
        with tempfile.TemporaryDirectory() as folder:
            analyzer = Path(folder) / 'cmdline-tools/latest/bin/apkanalyzer'
            analyzer.parent.mkdir(parents=True)
            analyzer.touch()
            with patch.dict(os.environ, {'ANDROID_HOME': folder}), patch.object(release.subprocess, 'check_output', return_value=xml):
                release.verify_apk_metadata(Path('app.apk'), metadata)
                for invalid in [dict(metadata, version_code=1), dict(metadata, version_name='wrong')]:
                    with self.assertRaises(ValueError):
                        release.verify_apk_metadata(Path('app.apk'), invalid)
            with patch.dict(os.environ, {'ANDROID_HOME': folder}), patch.object(release.subprocess, 'check_output', return_value=xml.replace('com.ankiride.bridge', 'other.package')):
                with self.assertRaises(ValueError):
                    release.verify_apk_metadata(Path('app.apk'), metadata)

class PublicationTests(unittest.TestCase):
    def test_rolling_upload_failure_retains_old_assets_and_tag(self):
        self.exercise(fail_upload=True)

    def test_rolling_verifies_downloads_before_pruning_and_moves_tag_last(self):
        self.exercise(fail_upload=False)

    def test_stable_draft_is_reused_and_verified_by_release_id(self):
        self.exercise(fail_upload=False, stable=True)

    def exercise(self, fail_upload, stable=False):
        import json
        import os
        import tempfile
        from unittest.mock import patch
        from urllib.parse import urlparse, parse_qs
        events = []
        assets = {'old.apk': {'id': 1, 'bytes': b'old'}}
        existing = {'id': 7, 'tag_name': 'v4.0.1' if stable else 'development', 'draft': stable, 'prerelease': not stable, 'body': ''}
        if stable:
            assets.clear()
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            staged = root / 'release-assets'
            staged.mkdir()
            (staged / 'new.apk').write_bytes(b'new')
            digest = hashlib.sha256(b'new').hexdigest()
            (staged / 'SHA256SUMS-new.txt').write_text(f'{digest}  new.apk\n')
            meta = dict(publish=True, source_sha=SHA, tag=existing['tag_name'], stable=stable, version_code=1000041 if stable else 1000040, version_name='4.0.1' if stable else '4.0.1-dev')
            if stable:
                (root / 'docs/releases').mkdir(parents=True)
                (root / 'docs/releases/4.0.1.md').write_text('# ClickControl 4.0.1\nCurated changes.')
            (root / 'release-plan.json').write_text(json.dumps(meta))
            def api(path, method='GET', data=None, binary=False):
                events.append((method, path))
                if path.endswith('/branches/main'):
                    return json.dumps({'commit': {'sha': SHA}}).encode()
                if '/commits/' in path:
                    return json.dumps({'sha': SHA}).encode()
                if path.endswith('/releases/latest'):
                    return json.dumps(existing).encode()
                if path.endswith('/releases/7'):
                    if method == 'PATCH':
                        existing.update(data)
                    return json.dumps(existing).encode()
                if '/assets?' in path:
                    return json.dumps([{'name': name, 'id': item['id']} for name, item in assets.items()]).encode()
                if '/releases/assets/' in path:
                    asset_id = int(path.rsplit('/', 1)[1])
                    name = next(name for name, item in assets.items() if item['id'] == asset_id)
                    if method == 'DELETE':
                        del assets[name]
                        return b''
                    return assets[name]['bytes']
                return b'{}'
            def run(cmd, **kwargs):
                self.assertIn('/releases/7/assets?', cmd[2], 'Uploads must address retained draft/release ID')
                if fail_upload:
                    raise RuntimeError('simulated interrupted upload')
                name = parse_qs(urlparse(cmd[2]).query)['name'][0]
                events.append(('UPLOAD', name))
                assets[name] = {'id': len(assets) + 1, 'bytes': Path(cmd[-1]).read_bytes()}
            previous = os.getcwd()
            os.chdir(root)
            try:
                with patch.dict(os.environ, {'RUNNER_TEMP': folder, 'GITHUB_REPOSITORY': 'owner/repo'}), patch.object(release, 'api', api), patch.object(release.subprocess, 'run', run), patch.object(release.subprocess, 'check_output', return_value=json.dumps([[existing]]).encode()):
                    if fail_upload:
                        with self.assertRaises(RuntimeError):
                            release.publish()
                        self.assertIn('old.apk', assets)
                        self.assertFalse(any(method in ('DELETE', 'PATCH') for method, _ in events))
                    else:
                        release.publish()
                        self.assertEqual(set(assets), {'new.apk', 'SHA256SUMS-new.txt'})
                        downloaded_new = [i for i, (method, path) in enumerate(events) if method == 'GET' and '/releases/assets/' in path]
                        self.assertTrue(downloaded_new)
                        mutations = [(method, path) for method, path in events if method in ('PATCH', 'POST', 'DELETE')]
                        if stable:
                            self.assertFalse(existing['draft'])
                            self.assertFalse(existing['prerelease'])
                            self.assertFalse(any(path.endswith('/releases') and method == 'POST' for method, path in events))
                            self.assertEqual(mutations[-1], ('PATCH', 'repos/owner/repo/releases/7'))
                        else:
                            deletion = next(i for i, (method, _) in enumerate(events) if method == 'DELETE')
                            self.assertLess(min(downloaded_new), deletion)
                            self.assertEqual(mutations[-1], ('PATCH', 'repos/owner/repo/git/refs/tags/development'))
                        self.assertEqual(events[-1][0], 'GET', 'Final verification must read back the published result')
            finally:
                os.chdir(previous)

class PublicationSafetyTests(unittest.TestCase):
    def test_moved_stable_tag_aborts_before_any_mutation_or_upload(self):
        import json
        import os
        import tempfile
        from unittest.mock import patch
        with tempfile.TemporaryDirectory() as folder:
            meta = dict(publish=True, source_sha=SHA, tag='v4.0.1', stable=True,
                        version_code=1000041, version_name='4.0.1')
            Path(folder, 'release-plan.json').write_text(json.dumps(meta))
            with patch.dict(os.environ, {'RUNNER_TEMP': folder, 'GITHUB_REPOSITORY': 'owner/repo'}), \
                 patch.object(release, 'api', return_value=json.dumps({'sha': 'b' * 40}).encode()) as request, \
                 patch.object(release.subprocess, 'check_output') as listing, \
                 patch.object(release.subprocess, 'run') as upload:
                with self.assertRaisesRegex(ValueError, 'Stable tag moved'):
                    release.publish()
            request.assert_called_once_with('repos/owner/repo/commits/v4.0.1')
            listing.assert_not_called()
            upload.assert_not_called()

    def test_existing_non_prerelease_development_is_not_mutated(self):
        import json
        import os
        import tempfile
        from unittest.mock import patch
        with tempfile.TemporaryDirectory() as folder:
            meta = dict(publish=True, source_sha=SHA, tag='development', stable=False,
                        version_code=1000040, version_name='4.0.1-dev')
            Path(folder, 'release-plan.json').write_text(json.dumps(meta))
            existing = dict(id=7, tag_name='development', draft=False, prerelease=False)
            with patch.dict(os.environ, {'RUNNER_TEMP': folder, 'GITHUB_REPOSITORY': 'owner/repo'}), \
                 patch.object(release, 'api', return_value=json.dumps({'commit': {'sha': SHA}}).encode()) as request, \
                 patch.object(release.subprocess, 'check_output', return_value=json.dumps([[existing]]).encode()), \
                 patch.object(release.subprocess, 'run') as upload:
                with self.assertRaisesRegex(ValueError, 'non-prerelease development'):
                    release.publish()
            request.assert_called_once_with('repos/owner/repo/branches/main')
            upload.assert_not_called()

    def test_final_state_mismatches_fail_and_original_stable_is_verified(self):
        import json
        from unittest.mock import patch
        meta = dict(source_sha=SHA, tag='v4.0.1', stable=True,
                    version_code=1000041, version_name='4.0.1')
        good = dict(id=7, tag_name='v4.0.1', draft=False, prerelease=False,
                    body=f'Curated notes\nCommit: {SHA}')
        cases = [('draft', dict(good, draft=True), good, SHA),
                 ('prerelease', dict(good, prerelease=True), good, SHA),
                 ('release identity', dict(good, id=8), good, SHA),
                 ('source notes', dict(good, body='missing source'), good, SHA),
                 ('source tag', good, good, 'b' * 40),
                 ('Latest id', good, dict(good, id=8), SHA),
                 ('Latest flag', good, dict(good, prerelease=True), SHA)]
        for label, current, latest, tag_sha in cases:
            def api(path, **kwargs):
                if path.endswith('/releases/7'):
                    return json.dumps(current).encode()
                if '/commits/' in path:
                    return json.dumps({'sha': tag_sha}).encode()
                if path.endswith('/releases/latest'):
                    return json.dumps(latest).encode()
                raise AssertionError(path)
            with self.subTest(case=label), patch.object(release, 'api', side_effect=api), \
                 patch.object(release, 'checked_assets') as assets:
                with self.assertRaises(ValueError):
                    release.verify_release(7, Path('unused'), 'owner/repo', meta, rebuilt=False)
                assets.assert_not_called()
        def api(path, **kwargs):
            if '/commits/' in path:
                return json.dumps({'sha': SHA}).encode()
            return json.dumps(good).encode()
        with patch.object(release, 'api', side_effect=api), \
             patch.object(release, 'checked_assets') as assets:
            release.verify_release(7, Path('unused'), 'owner/repo', meta, rebuilt=False)
        assets.assert_called_once_with(good, Path('unused'), 'owner/repo', exact=True, rebuilt=False)


if __name__ == '__main__':
    unittest.main()
