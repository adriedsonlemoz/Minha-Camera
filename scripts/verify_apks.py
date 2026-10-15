#!/usr/bin/env python3
"""Validate a complete ABI release before naming/publishing any APK."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
from zipfile import ZipFile

ABIS = {'armeabi-v7a', 'arm64-v8a', 'x86_64', 'x86'}
LIBS = {'libvlc.so', 'libvlcjni.so', 'libc++_shared.so'}
ROOT = Path(__file__).resolve().parents[1]

def run(*args):
    return subprocess.check_output([str(a) for a in args], text=True, stderr=subprocess.STDOUT)

def inspect(apk, tools):
    with ZipFile(apk) as z:
        assert z.testzip() is None, f'CRC inválido: {apk}'
        names = z.namelist()
        assert len(names) == len(set(names)), f'Entradas ZIP duplicadas: {apk}'
        native = {n: hashlib.sha256(z.read(n)).hexdigest() for n in names
                  if n.startswith('lib/') and n.endswith('.so')}
        payload = {n: hashlib.sha256(z.read(n)).hexdigest() for n in names
                   if not n.startswith(('lib/', 'META-INF/')) and not n.endswith('/')}
    abis = {n.split('/')[1] for n in native}
    for abi in abis:
        assert LIBS <= {n.split('/')[-1] for n in native if n.startswith(f'lib/{abi}/')}, f'LibVLC incompleto: {apk}, {abi}'
    manifest = run(tools / 'aapt', 'dump', 'badging', apk)
    match = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", manifest)
    assert match, f'Manifesto ilegível: {apk}'
    sdk = re.search(r"sdkVersion:'(\d+)'", manifest)
    assert sdk and sdk.group(1) == '24', f'minSdk alterado: {apk}'
    signature = run(tools / 'apksigner', 'verify', '--verbose', '--print-certs', apk)
    certs = re.findall(r'^Signer #\d+ certificate SHA-256 digest: (\S+)', signature, re.M)
    assert len(certs) == 1, f'Assinatura inesperada: {apk}'
    run(tools / 'zipalign', '-c', '4', apk)
    return {'abis': abis, 'native': native, 'payload': payload,
            'identity': match.groups(), 'certificate': certs[0],
            'size_bytes': apk.stat().st_size,
            'sha256': hashlib.sha256(apk.read_bytes()).hexdigest()}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', type=Path, default=ROOT / 'app/build/outputs/apk/release')
    parser.add_argument('--dist', type=Path, default=ROOT / 'dist')
    parser.add_argument('--build-tools', type=Path)
    parser.add_argument('--previous-apk', type=Path, help='Compare assinatura legada sem prometer compatibilidade')
    args = parser.parse_args()
    sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    tools = args.build_tools or (Path(sdk) / 'build-tools/35.0.0' if sdk else None)
    assert tools and tools.is_dir(), 'Informe --build-tools ou ANDROID_HOME'
    metadata = json.loads((args.input / 'output-metadata.json').read_text())
    gradle = (ROOT / 'app/build.gradle.kts').read_text()
    version = re.search(r'versionName\s*=\s*"([^"]+)"', gradle).group(1)
    code = re.search(r'versionCode\s*=\s*(\d+)', gradle).group(1)
    expected_identity = ('com.minhacamera', code, version)
    assert metadata['applicationId'] == expected_identity[0]
    results = {}
    for element in metadata['elements']:
        filters = element['filters']
        assert len(filters) <= 1 and all(f['filterType'] == 'ABI' for f in filters)
        label = filters[0]['value'] if filters else 'universal'
        assert label not in results and label in ABIS | {'universal'}
        assert str(element['versionCode']) == code and element['versionName'] == version
        apk = args.input / element['outputFile']
        result = inspect(apk, tools)
        assert result['identity'] == expected_identity, f'Identidade divergente: {apk}'
        assert result['abis'] == (ABIS if label == 'universal' else {label}), f'ABIs inesperadas: {apk}, {result["abis"]}'
        result['source'] = apk
        results[label] = result
    assert set(results) == ABIS | {'universal'}, f'APKs faltantes: {set(results)}'
    universal = results['universal']
    # Impede publicar acidentalmente com outro certificado, mesmo que os cinco APKs coincidam.
    expected_cert = (ROOT / 'docs/SIGNING_CERT_SHA256.txt').read_text().strip().lower().replace(':', '')
    actual_cert = universal['certificate'].lower().replace(':', '')
    assert actual_cert == expected_cert, 'APK com certificado diferente do release permanente fixado'
    for label, result in results.items():
        assert result['certificate'] == universal['certificate'], f'Certificado divergente: {label}'
        assert result['payload'] == universal['payload'], f'Código/recursos divergentes: {label}'
        for name, digest in result['native'].items():
            assert universal['native'].get(name) == digest, f'Biblioteca alterada: {name}'
    previous = None
    if args.previous_apk:
        old = inspect(args.previous_apk, tools)
        previous = {'identity': old['identity'], 'certificate': old['certificate'],
                    'signature_matches': old['certificate'] == universal['certificate'],
                    'size_bytes': old['size_bytes']}
    args.dist.mkdir(parents=True, exist_ok=True)
    rows, sums, records = [], [], []
    for label in ['universal', 'arm64-v8a', 'armeabi-v7a', 'x86_64', 'x86']:
        result = results[label]
        filename = f'MinhaCamera-{version}-{label}.apk'
        shutil.copyfile(result['source'], args.dist / filename)
        reduction = 100 * (1 - result['size_bytes'] / universal['size_bytes'])
        rows.append(f'| {label} | {result["size_bytes"]} | {result["size_bytes"]/1_000_000:.2f} | {reduction:.2f}% |')
        sums.append(f'{result["sha256"]}  {filename}')
        records.append({'file': filename, 'abis': sorted(result['abis']),
                        'size_bytes': result['size_bytes'], 'reduction_percent': reduction,
                        'sha256': result['sha256'], 'certificate_sha256': result['certificate']})
    report = f'# Minha Câmera {version}+{code}: APKs verificados\n\n' + \
        '| APK | Bytes | MB (decimal) | Redução / Universal |\n|---|---:|---:|---:|\n' + '\n'.join(rows) + \
        f'\n\nApplicationId: com.minhacamera; versionName: {version}; versionCode: {code}.\n\n' + \
        f'Certificado SHA-256 comum: `{universal["certificate"]}`.\n\n' + \
        'CRC ZIP, alinhamento ZIP, assinaturas, metadados, ABIs exatas, bibliotecas LibVLC e igualdade do código/recursos: aprovados.\n\n' + \
        'Mesma assinatura neste conjunto não garante atualização sobre APKs de outro ambiente. Nenhum teste físico de câmera/decodificação/instalação foi realizado por este verificador.\n'
    if previous:
        report += f'\nAPK anterior: {previous["identity"]}; certificado `{previous["certificate"]}`; assinatura coincide: **{previous["signature_matches"]}**.\n'
    (args.dist / 'apk-report.md').write_text(report)
    (args.dist / 'apk-report.json').write_text(json.dumps({'identity': expected_identity, 'apks': records, 'previous': previous}, indent=2))
    (args.dist / 'SHA256SUMS').write_text('\n'.join(sums) + '\n')
    print(report)
    if 'GITHUB_STEP_SUMMARY' in os.environ:
        with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as out:
            out.write(report)
    if 'GITHUB_OUTPUT' in os.environ:
        with open(os.environ['GITHUB_OUTPUT'], 'a') as out:
            for label in results:
                out.write(f'{label}=dist/MinhaCamera-{version}-{label}.apk\n')

if __name__ == '__main__':
    main()
