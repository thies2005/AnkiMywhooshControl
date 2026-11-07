#!/usr/bin/env python3
"""Android release metadata and serialized, verified GitHub publication."""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import xml.etree.ElementTree as ET
from urllib.parse import quote

EPOCH = 1_000_000
MAX_CODE = 2_100_000_000


def git(*args: str) -> str:
    return subprocess.check_output(['git', *args], text=True).strip()


def version(source: str, count: int, sha: str, ref: str) -> dict:
    name = re.search(r'^\s*versionName = "([0-9]+\.[0-9]+\.[0-9]+)"', source, re.M)
    code = re.search(r'^\s*versionCode = ([0-9]+)', source, re.M)
    if not name or not code or not re.fullmatch(r'[0-9a-f]{40}', sha) or count < 1:
        raise ValueError('Invalid source version, full commit SHA, or history')
    stable = ref.startswith('refs/tags/')
    if ref != 'refs/heads/main' and not stable:
        raise ValueError('Only main and numeric stable version tags can be signed')
    allocated = EPOCH + count * 2 + int(stable)
    if allocated > MAX_CODE:
        raise ValueError('Android versionCode limit exceeded')
    base = name[1]
    if stable and (ref != f'refs/tags/v{base}' or int(code[1]) != allocated):
        raise ValueError('Stable tag/source version or allocated source versionCode mismatch')
    return dict(stable=stable, version_code=allocated,
                version_name=base if stable else f'{base}-dev.{sha[:12]}',
                tag=ref.removeprefix('refs/tags/') if stable else 'development', source_sha=sha)


def plan() -> None:
    sha = git('rev-parse', 'HEAD')
    if sha != os.environ['GITHUB_SHA']:
        raise ValueError('Checkout differs from triggering commit')
    ref = os.environ['GITHUB_REF']
    event = os.environ['GITHUB_EVENT_NAME']
    if event not in ('push', 'workflow_dispatch'):
        raise ValueError('Untrusted event')
    subprocess.run(['git', 'merge-base', '--is-ancestor', sha, 'origin/main'], check=True)
    result = version(Path('app/build.gradle.kts').read_text(), int(git('rev-list', '--count', sha)), sha, ref)
    if result['stable'] and not Path(f"docs/releases/{result['version_name']}.md").read_text().strip():
        raise ValueError('Empty stable release notes')
    publish = event == 'push' or os.environ.get('MANUAL_PUBLISH') == 'true'
    result['publish'] = publish
    Path(os.environ['RUNNER_TEMP'], 'release-plan.json').write_text(json.dumps(result))
    with open(os.environ['GITHUB_OUTPUT'], 'a') as out:
        for key, val in result.items():
            out.write(f'{key}={str(val).lower() if isinstance(val, bool) else val}\n')


def api(path: str, method: str = 'GET', data: dict | None = None, binary: bool = False) -> bytes:
    cmd = ['gh', 'api', path, '--method', method]
    if binary:
        cmd += ['-H', 'Accept: application/octet-stream']
    if data is not None:
        cmd += ['--input', '-']
    return subprocess.check_output(cmd, input=json.dumps(data).encode() if data is not None else None)


def validate_manifest(downloads: dict[str, bytes]) -> None:
    manifests = [name for name in downloads if name.startswith('SHA256SUMS-') and name.endswith('.txt')]
    if len(manifests) != 1:
        raise ValueError('Expected one checksum manifest')
    listed = set()
    for line in downloads[manifests[0]].decode().splitlines():
        match = re.fullmatch(r'([0-9a-f]{64})  ([^/\\]+)', line)
        if not match or match[2] in ('.', '..') or match[2] in listed:
            raise ValueError('Invalid checksum manifest entry')
        digest, name = match.groups()
        if name not in downloads or hashlib.sha256(downloads[name]).hexdigest() != digest:
            raise ValueError('Downloaded asset does not match published checksum')
        listed.add(name)
    if listed != set(downloads) - {manifests[0]}:
        raise ValueError('Checksum manifest does not cover exactly the asset set')


def verify_apk_metadata(path: Path, metadata: dict) -> None:
    tools = Path(os.environ['ANDROID_HOME']) / 'cmdline-tools'
    analyzer = tools / 'latest/bin/apkanalyzer'
    if not analyzer.is_file():
        candidates = sorted(tools.glob('*/bin/apkanalyzer'), key=lambda p: tuple(int(n) for n in re.findall(r'\d+', p.parent.parent.name)))
        if not candidates:
            raise ValueError('Android apkanalyzer is required for APK manifest verification')
        analyzer = candidates[-1]
    # Parse only manifest attributes. Legacy aapt badging can fail resolving a
    # framework icon even when package/version fields and the APK are valid.
    xml = subprocess.check_output([str(analyzer), 'manifest', 'print', str(path)], text=True)
    manifest = ET.fromstring(xml)
    android = '{http://schemas.android.com/apk/res/android}'
    expected = ('com.ankiride.bridge', str(metadata['version_code']), metadata['version_name'])
    actual = (manifest.get('package'), manifest.get(android + 'versionCode'), manifest.get(android + 'versionName'))
    if actual != expected:
        raise ValueError('APK package/version metadata mismatch')


def verify_staged_apk() -> None:
    paths = list(Path('release-assets').glob('*.apk'))
    if len(paths) != 1:
        raise ValueError('Expected exactly one staged APK')
    metadata = json.loads(Path(os.environ['RUNNER_TEMP'], 'release-plan.json').read_text())
    verify_apk_metadata(paths[0], metadata)


def verify_published_binaries(downloads: dict[str, bytes], metadata: dict) -> None:
    expected = os.environ['RELEASE_CERT_SHA256']
    tools = Path(os.environ['ANDROID_HOME']) / 'build-tools'
    signer = sorted(tools.glob('*/apksigner'), key=lambda p: tuple(int(n) for n in re.findall(r'\d+', p.parent.name)))[-1]
    with tempfile.TemporaryDirectory(prefix='verify-stable-') as folder:
        for name, content in downloads.items():
            if not name.endswith(('.apk', '.aab')):
                continue
            path = Path(folder) / name
            path.write_bytes(content)
            if name.endswith('.apk'):
                details = subprocess.check_output([str(signer), 'verify', '--print-certs', str(path)], text=True)
                certs = set(re.findall(r'certificate SHA-256 digest: ([0-9a-f]+)', details))
                if certs != {expected}:
                    raise ValueError('Published APK signing certificate mismatch')
                verify_apk_metadata(path, metadata)
            else:
                verified = subprocess.check_output(['jarsigner', '-verify', str(path)], text=True)
                if not re.search(r'^jar verified\.', verified, re.M):
                    raise ValueError('Published AAB signature did not verify')
                certificate = subprocess.check_output(['keytool', '-printcert', '-jarfile', str(path)], text=True)
                fingerprints = {value.replace(':', '').lower() for value in re.findall(r'SHA256: ([0-9A-Fa-f:]+)', certificate)}
                if fingerprints != {expected}:
                    raise ValueError('Published AAB signing certificate mismatch')
                bundletool = Path(os.environ['RUNNER_TEMP']) / 'bundletool.jar'
                manifest = subprocess.check_output(['java', '-jar', str(bundletool), 'dump', 'manifest', f'--bundle={path}', '--module=base'], text=True)
                root = ET.fromstring(manifest)
                android = '{http://schemas.android.com/apk/res/android}'
                if root.get('package') != 'com.ankiride.bridge' or root.get(android + 'versionCode') != str(metadata['version_code']) or root.get(android + 'versionName') != metadata['version_name']:
                    raise ValueError('Published AAB package metadata mismatch')


def checked_assets(release: dict, staged: Path, repo: str, exact: bool, rebuilt: bool = True) -> None:
    expected = {f.name: f for f in staged.iterdir() if f.is_file()}
    assets = {a['name']: a for a in json.loads(api(f"repos/{repo}/releases/{release['id']}/assets?per_page=100"))}
    if (set(assets) != set(expected) if exact else not set(expected) <= set(assets)):
        raise ValueError('Published asset inventory does not match staged artifacts')
    downloads = {}
    for name, path in expected.items():
        downloaded = api(f"repos/{repo}/releases/assets/{assets[name]['id']}", binary=True)
        if rebuilt and hashlib.sha256(downloaded).digest() != hashlib.sha256(path.read_bytes()).digest():
            raise ValueError(f'Published bytes differ: {name}')
        downloads[name] = downloaded
    validate_manifest(downloads)
    if not rebuilt:
        for name, content in downloads.items():
            if name.endswith('.json'):
                published, local = json.loads(content), json.loads(expected[name].read_text())
                for field in ('source_sha', 'version_name', 'version_code', 'stable', 'tag'):
                    if published.get(field) != local.get(field):
                        raise ValueError('Published stable metadata differs from selected source')
        verify_published_binaries(downloads, local)


def verify_release(release_id: int, staged: Path, repo: str, meta: dict, *, rebuilt: bool) -> None:
    current = json.loads(api(f'repos/{repo}/releases/{release_id}'))
    if current.get('id') != release_id or current.get('tag_name') != meta['tag']:
        raise ValueError('Published release identity changed')
    if current.get('draft') is not False or current.get('prerelease') is not (not meta['stable']):
        raise ValueError('Published release flags do not match the requested channel')
    if f"Commit: {meta['source_sha']}" not in (current.get('body') or ''):
        raise ValueError('Published release notes do not identify the source commit')
    if json.loads(api(f"repos/{repo}/commits/{meta['tag']}"))['sha'] != meta['source_sha']:
        raise ValueError('Published release tag moved from the selected source')
    if meta['stable']:
        latest = json.loads(api(f'repos/{repo}/releases/latest'))
        if latest.get('id') != release_id or latest.get('tag_name') != meta['tag'] or latest.get('draft') is not False or latest.get('prerelease') is not False:
            raise ValueError('Published stable release is not the verified Latest release')
    checked_assets(current, staged, repo, exact=True, rebuilt=rebuilt)


def publish() -> None:
    staged = Path('release-assets')
    meta = json.loads(Path(os.environ['RUNNER_TEMP'], 'release-plan.json').read_text())
    if not meta['publish']:
        raise ValueError('Publication not authorized by this workflow invocation')
    repo = os.environ['GITHUB_REPOSITORY']
    sha, tag = meta['source_sha'], meta['tag']
    # Recheck the live stable tag before any draft creation, upload or mutation.
    if meta['stable'] and json.loads(api(f'repos/{repo}/commits/{tag}'))['sha'] != sha:
        raise ValueError('Stable tag moved from the selected source; refusing publication')
    # The release queue is serialized; stale main builds remain available as Actions artifacts.
    if not meta['stable'] and json.loads(api(f'repos/{repo}/branches/main'))['commit']['sha'] != sha:
        print('A newer main commit exists; skipping development publication')
        return
    releases = json.loads(subprocess.check_output(['gh', 'api', '--paginate', '--slurp', f'repos/{repo}/releases?per_page=100']))
    releases = [release for page in releases for release in page]
    existing = next((r for r in releases if r['tag_name'] == tag), None)
    if existing and not meta['stable'] and existing.get('prerelease') is not True:
        raise ValueError('Refusing to mutate a non-prerelease development release')
    if meta['stable']:
        for previous in releases:
            previous_code = re.search(r'^Version code: ([0-9]+)$', previous.get('body') or '', re.M)
            if not previous['draft'] and not previous['prerelease'] and previous['tag_name'] != tag and previous_code:
                if int(previous_code[1]) >= meta['version_code']:
                    raise ValueError('Stable versionCode must exceed previously published stable codes')
    if existing and not existing['draft'] and meta['stable']:
        verify_release(existing['id'], staged, repo, meta, rebuilt=False)
        print('Published stable release verified; leaving it unchanged')
        return
    notes = Path(f"docs/releases/{meta['version_name']}.md").read_text() if meta['stable'] else '# ClickControl development build\n\nAutomatic signed Android build from main. This prerelease and its tag move; use the commit below for exact source.\n\nBack up app data and uninstall older debug-signed installations before switching to the release key.\n'
    notes += f"\nCommit: {sha}\nVersion code: {meta['version_code']}\n"
    title = f"ClickControl {meta['version_name']}" if meta['stable'] else 'Development build — automatic'
    if existing is None:
        existing = json.loads(api(f'repos/{repo}/releases', 'POST', dict(tag_name=tag, target_commitish=sha, name=title, body=notes, draft=True, prerelease=not meta['stable'])))
    release_id = existing['id']
    # Address surviving drafts by REST ID, not the published-only tag endpoint.
    assets = {a['name']: a for a in json.loads(api(f'repos/{repo}/releases/{release_id}/assets?per_page=100'))}
    for path in sorted(staged.iterdir()):
        if path.is_file():
            if path.name in assets:
                previous = api(f"repos/{repo}/releases/assets/{assets[path.name]['id']}", binary=True)
                if previous == path.read_bytes():
                    continue
                if not existing['draft']:
                    raise ValueError('Build-specific development asset already exists with different bytes')
                api(f"repos/{repo}/releases/assets/{assets[path.name]['id']}", 'DELETE')
            endpoint = f"https://uploads.github.com/repos/{repo}/releases/{release_id}/assets?name={quote(path.name)}"
            subprocess.run(['gh', 'api', endpoint, '--method', 'POST', '-H', 'Content-Type: application/octet-stream', '--input', str(path)], check=True, stdout=subprocess.DEVNULL)
    checked_assets(existing, staged, repo, exact=meta['stable'])
    if not meta['stable']:
        keep = {f.name for f in staged.iterdir() if f.is_file()}
        for asset in json.loads(api(f'repos/{repo}/releases/{release_id}/assets?per_page=100')):
            if asset['name'] not in keep:
                api(f"repos/{repo}/releases/assets/{asset['id']}", 'DELETE')
        checked_assets(existing, staged, repo, exact=True)
    api(f'repos/{repo}/releases/{release_id}', 'PATCH', dict(name=title, body=notes, draft=False, prerelease=not meta['stable'], make_latest='true' if meta['stable'] else 'false', target_commitish=sha))
    if not meta['stable']:
        # Move the rolling source tag only after every replacement download verified.
        api(f'repos/{repo}/git/refs/tags/development', 'PATCH', dict(sha=sha, force=True))
    # Read-only final checks follow the rolling tag's last mutation.
    verify_release(release_id, staged, repo, meta, rebuilt=True)
    print(f'https://github.com/{repo}/releases/tag/{tag}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('command', choices=['plan', 'publish', 'verify-apk'])
    args = parser.parse_args()
    {'plan': plan, 'publish': publish, 'verify-apk': verify_staged_apk}[args.command]()
