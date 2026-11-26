import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import sys
import tempfile
import zipfile
import shutil
from types import SimpleNamespace
import datetime
import argparse, json, re
import math
import xml.etree.ElementTree as ET
import zlib
import pathlib
_TOOLS_DIR = Path(__file__).resolve().parent
for _sub in ('generators', 'matrix', 'checks'):
    _p = str(_TOOLS_DIR / _sub)
    if _p not in sys.path:
        sys.path.insert(0, _p)

JAVA_ASSEMBLER_SOURCE = r'''import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.util.CheckClassAdapter;

public final class UniversalJarAssembler {
  private static final String PLATFORM = "dev/nightclient/platform";
  private static final String MIXIN = PLATFORM + "/mixin";
  private static final String[] SHARED = {"GeometryCanvas", "BlurPlan", "ItemOpacity", "opt/BitGridOcclusionEngine", "opt/ItemClusterGrid", "opt/NightNative"};

  private UniversalJarAssembler() {}

  private static boolean owned(String name, String prefix) {
    return name.equals(prefix) || name.startsWith(prefix + "/");
  }

  private static boolean shared(String name) {
    for (String simple : SHARED) {
      String base = PLATFORM + "/" + simple;
      if (name.equals(base) || name.startsWith(base + "$")) return true;
    }
    return false;
  }

  private static final class NamespaceRemapper extends Remapper {
    final String adapter, mixins;
    final boolean reverse;
    final Pattern textPattern;

    NamespaceRemapper(String adapter, boolean reverse) {
      super(Opcodes.ASM9);
      this.adapter = adapter;
      this.reverse = reverse;
      String id = adapter.substring("dev/nightclient/adapter/".length(), adapter.lastIndexOf('/'));
      mixins = "dev/nightclient/universal/mixin/" + id;
      String prefixes =
          reverse ? Pattern.quote(adapter) + "|" + Pattern.quote(mixins) : Pattern.quote(PLATFORM);
      String dotted =
          reverse
              ? Pattern.quote(adapter.replace('/', '.'))
                  + "|"
                  + Pattern.quote(mixins.replace('/', '.'))
              : Pattern.quote(PLATFORM.replace('/', '.'));
      textPattern =
          Pattern.compile(
              "(?:" + prefixes + ")(?:/[A-Za-z0-9_$]+)*|(?:" + dotted + ")(?:\\.[A-Za-z0-9_$]+)*");
    }

    @Override
    public String map(String name) {
      if (name == null) return null;
      if (reverse) {
        if (owned(name, mixins)) return MIXIN + name.substring(mixins.length());
        if (owned(name, adapter)) return PLATFORM + name.substring(adapter.length());
        return name;
      }
      if (shared(name)) return name;
      if (owned(name, MIXIN)) return mixins + name.substring(MIXIN.length());
      return owned(name, PLATFORM) ? adapter + name.substring(PLATFORM.length()) : name;
    }

    @Override
    public String mapPackageName(String name) {
      return map(name);
    }

    String text(String value) {
      Matcher matcher = textPattern.matcher(value);
      StringBuffer result = new StringBuffer();
      while (matcher.find()) {
        String token = matcher.group();
        boolean dotted = token.indexOf('/') < 0;
        String internal = dotted ? token.replace('.', '/') : token;

        String mapped = map(internal);
        if (!reverse)
          for (String simple : SHARED) {
            String base = PLATFORM + "/" + simple;
            if (internal.startsWith(base + "/")) mapped = internal;
          }
        matcher.appendReplacement(
            result, Matcher.quoteReplacement(dotted ? mapped.replace('/', '.') : mapped));
      }
      matcher.appendTail(result);
      return result.toString();
    }

    @Override
    public Object mapValue(Object value) {
      return value instanceof String ? text((String) value) : super.mapValue(value);
    }
  }

  private static byte[] transform(byte[] source, NamespaceRemapper remapper) {
    ClassReader reader = new ClassReader(source);
    ClassWriter writer = new ClassWriter(0);
    reader.accept(new ClassRemapper(writer, remapper), 0);
    return writer.toByteArray();
  }

  private static byte[] canonical(byte[] source, boolean debug) {
    ClassWriter writer = new ClassWriter(0);
    new ClassReader(source).accept(writer, debug ? 0 : ClassReader.SKIP_DEBUG);
    return writer.toByteArray();
  }

  private static void verify(byte[] source, byte[] mapped, String adapter) {
    new ClassReader(mapped).accept(new CheckClassAdapter(new ClassWriter(0), false), 0);
    byte[] roundtrip = transform(mapped, new NamespaceRemapper(adapter, true));
    if (!Arrays.equals(canonical(source, true), canonical(roundtrip, true)))
      throw new IllegalStateException(
          "Class relocation did not preserve round-trip semantics: "
              + new ClassReader(source).getClassName());
  }

  private static byte[] read(InputStream input) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[16384];
    int n;
    while ((n = input.read(buffer)) != -1) out.write(buffer, 0, n);
    return out.toByteArray();
  }

  private static byte[] entry(ZipFile file, String name) throws IOException {
    try (InputStream stream = file.getInputStream(file.getEntry(name))) {
      return read(stream);
    }
  }

  private static List<String> names(ZipFile source) {
    List<String> names = new ArrayList<String>();
    Enumeration<? extends ZipEntry> all = source.entries();
    while (all.hasMoreElements()) {
      ZipEntry e = all.nextElement();
      if (!e.isDirectory()) names.add(e.getName());
    }
    Collections.sort(names);
    return names;
  }

  private static void write(ZipOutputStream zip, String name, byte[] data, Set<String> entries)
      throws IOException {
    if (!entries.add(name)) throw new IllegalStateException("Duplicate output entry: " + name);
    ZipEntry entry = new ZipEntry(name);
    entry.setTime(315532800000L);
    zip.putNextEntry(entry);
    zip.write(data);
    zip.closeEntry();
  }

  public static void main(String[] args) throws Exception {
    if (args.length != 4 || !args[0].equals("platform"))
      throw new IllegalArgumentException(
          "Usage: UniversalJarAssembler platform INPUT.jar OUTPUT.jar"
              + " dotted.destination.platform");
    Path input = Paths.get(args[1]), output = Paths.get(args[2]);
    String destination = args[3].replace('.', '/');
    if (!destination.matches("dev/nightclient/adapter/mc[0-9_]+/platform"))
      throw new IllegalArgumentException("Invalid adapter namespace: " + args[3]);
    NamespaceRemapper remapper = new NamespaceRemapper(destination, false);
    Files.createDirectories(output.toAbsolutePath().getParent());
    int classes = 0, resources = 0, omitted = 0;
    Set<String> entries = new HashSet<String>();
    try (ZipFile source = new ZipFile(input.toFile());
        ZipOutputStream result = new ZipOutputStream(Files.newOutputStream(output))) {
      for (String name : names(source)) {
        if (!(name.startsWith(PLATFORM + "/") && name.endsWith(".class"))) continue;
        byte[] data = entry(source, name);
        if (name.endsWith(".class")) {
          String oldName = new ClassReader(data).getClassName();
          if (!name.equals(oldName + ".class"))
            throw new IllegalStateException("Class entry/name mismatch: " + name + " / " + oldName);
          if (shared(oldName)) {
            omitted++;
            continue;
          }
          byte[] mapped = transform(data, remapper);
          verify(data, mapped, destination);
          String newName = new ClassReader(mapped).getClassName();
          if (!(owned(newName, destination) || owned(newName, remapper.mixins)))
            throw new IllegalStateException("Product class escaped adapter namespace: " + newName);
          write(result, newName + ".class", mapped, entries);
          classes++;
        } else {
          if (name.endsWith(".json") || name.endsWith(".properties") || name.endsWith(".txt"))
            data =
                remapper
                    .text(new String(data, StandardCharsets.UTF_8))
                    .getBytes(StandardCharsets.UTF_8);
          write(result, name, data, entries);
          resources++;
        }
      }
    } catch (Exception error) {
      Files.deleteIfExists(output);
      throw error;
    }
    if (classes == 0) {
      Files.deleteIfExists(output);
      throw new IllegalStateException("No classes were processed");
    }
    System.out.println(
        "{\"mode\":\""
            + args[0]
            + "\",\"classes\":"
            + classes
            + ",\"resources\":"
            + resources
            + ",\"sharedClassesOmitted\":"
            + omitted
            + ",\"roundTripVerified\":true}");
  }
}
'''
ROOT = Path(__file__).resolve().parents[1]
PLATFORM = 'dev/nightclient/platform/'
SHARED_PLATFORM = ('GeometryCanvas', 'BlurPlan', 'ItemOpacity', 'opt/BitGridOcclusionEngine', 'opt/ItemClusterGrid', 'opt/NightNative')
COMMON_MODULES = ('api', 'core', 'renderer', 'ui', 'runtime')
INDEX_NAME = 'nightclient-universal-index.json'
PROPERTIES_NAME = 'nightclient-universal.properties'
BOOTSTRAP_CLIENT = 'dev.nightclient.bootstrap.UniversalClient'
BOOTSTRAP_PLUGIN = 'dev.nightclient.bootstrap.UniversalMixinPlugin'
MIXIN_PACKAGE = 'dev.nightclient.universal.mixin'

def sha(data):
    return hashlib.sha256(data).hexdigest()

def relative(path):
    try:
        return str(path.resolve().relative_to(ROOT))
    except ValueError:
        return str(path.resolve())

def fail(message):
    raise ValueError(message)

def jar_entries(path):
    with zipfile.ZipFile(path) as jar:
        names = [entry.filename for entry in jar.infolist() if not entry.is_dir()]
        if len(names) != len(set(names)):
            fail(f'Duplicate JAR entries: {path}')
        return {name: jar.read(name) for name in sorted(names)}

def shared_platform(name):
    return any((name == PLATFORM + base + '.class' or name.startswith(PLATFORM + base + '$') for base in SHARED_PLATFORM))

def utf8_constants(data):
    if data[:4] != b'\xca\xfe\xba\xbe':
        fail('Invalid class header')
    count = struct.unpack_from('>H', data, 8)[0]
    pos, index, strings = (10, 1, [])
    sizes = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4, 11: 4, 12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}
    while index < count:
        tag = data[pos]
        pos += 1
        if tag == 1:
            length = struct.unpack_from('>H', data, pos)[0]
            pos += 2
            strings.append(data[pos:pos + length].decode('utf-8', errors='replace'))
            pos += length
        else:
            if tag not in sizes:
                fail(f'Unknown constant-pool tag {tag}')
            pos += sizes[tag]
        index += 2 if tag in (5, 6) else 1
    return strings

def audit_shared(name, data):
    major = struct.unpack_from('>H', data, 6)[0]
    if major > 61:
        fail(f'Shared class requires newer than Java 17: {name} ({major})')
    for value in utf8_constants(data):
        if 'net/minecraft/' in value or 'net.minecraft.' in value:
            fail(f'Shared class references Minecraft: {name}: {value}')
        for match in re.finditer('dev[/\\.]nightclient[/\\.]platform[/\\.][A-Za-z0-9_$]+(?:[/\\.][A-Za-z0-9_$]+)*', value):
            referenced = match.group().replace('.', '/') + '.class'
            if not shared_platform(referenced):
                fail(f'Shared class references a version adapter: {name}: {value}')

def tools(java_home):
    java_home = Path(find_java_home(java_home))
    if not (java_home / 'bin/javac').is_file():
        fail('A JDK is required; pass --java-home (JDK 25 recommended).')
    gradle_home = Path(os.environ.get('GRADLE_USER_HOME') or (Path.home() / '.gradle'))
    jars = []
    for artifact in ('asm', 'asm-commons', 'asm-tree', 'asm-util', 'asm-analysis'):
        found = sorted((gradle_home / 'caches/modules-2/files-2.1/org.ow2.asm' / artifact / '9.10.1').glob('*/' + artifact + '-9.10.1.jar'))
        if len(found) != 1:
            fail(f'Expected cached ASM 9.10.1 {artifact}; build the Gradle project first.')
        jars.append(found[0])
    directory = ROOT / 'build/universal/tools'
    directory.mkdir(parents=True, exist_ok=True)
    source = directory / "UniversalJarAssembler.java"
    source.write_text(JAVA_ASSEMBLER_SOURCE)
    stamp = sha(source.read_bytes() + ''.join((sha(p.read_bytes()) for p in jars)).encode())
    stamp_file = directory / 'compiler-input.sha256'
    classpath = os.pathsep.join(map(str, jars))
    if not (directory / 'UniversalJarAssembler.class').is_file() or not stamp_file.is_file() or stamp_file.read_text() != stamp:
        subprocess.run([str(java_home / 'bin/javac'), '--release', '17', '-cp', classpath, '-d', str(directory), str(source)], check=True)
        stamp_file.write_text(stamp)
    return [str(java_home / 'bin/java'), '-cp', str(directory) + os.pathsep + classpath, 'UniversalJarAssembler']

def run_asm(command, *arguments):
    process = subprocess.run(command + list(map(str, arguments)), check=True, text=True, stdout=subprocess.PIPE)
    return json.loads(process.stdout)

def write_jar(path, entries):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + '.tmp')
    try:
        with zipfile.ZipFile(temporary, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as jar:
            for name, data in sorted(entries.items()):
                info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = 420 << 16
                jar.writestr(info, data, compresslevel=9)
        temporary.replace(path)
    finally:
        temporary.unlink(missing_ok=True)

def mod_version():
    properties = (ROOT / 'gradle.properties').read_text()
    return re.search('^modVersion=(.+)$', properties, re.MULTILINE).group(1).strip()

def assemble(args):
    official_capes = getattr(args, 'official_capes', True)
    include_native = getattr(args, 'include_native', True)
    fingerprint = source_fingerprint(official_capes=official_capes)
    targets = json.loads((ROOT / 'modules/platform/compatibility/versions.json').read_text())
    if len(targets) != 24 or len({t['minecraft'] for t in targets}) != 24:
        fail('Expected all 24 exact supported releases')
    version = mod_version()
    command = tools(args.java_home)
    rows = []
    for target in targets:
        mc = target['minecraft']
        result_file = ROOT / 'build/compatibility' / mc / 'result.json'
        if not result_file.is_file():
            fail(f'Missing matrix result for {mc}')
        row = json.loads(result_file.read_text())
        artifact = ROOT / row.get('artifact', '')
        if not row.get('compiled') or row.get('apiAudit') != 'passed' or row.get('sourceFingerprint') != fingerprint or row.get('sourceChangedWhileBuilding'):
            fail(f'Stale or unaudited adapter {mc}; run build.py first.')
        if not artifact.is_file() or row.get('artifactSha256') != sha(artifact.read_bytes()):
            fail(f'Adapter artifact/hash mismatch: {mc}')
        rows.append((target, row, artifact, jar_entries(artifact)))
    entries, categories = ({}, {})

    def add(name, data, category):
        if name in entries and entries[name] != data:
            fail(f'Conflicting shared entry: {name}')
        if name not in entries:
            entries[name], categories[name] = (data, category)
    common = {}
    for module in COMMON_MODULES:
        path = ROOT / 'modules' / module / 'build/libs' / f'{module}-{version}.jar'
        if not path.is_file():
            fail(f'Missing canonical shared JAR: {path}')
        for name, data in jar_entries(path).items():
            if name.endswith('.class'):
                if not name.startswith('dev/nightclient/') or name.startswith(PLATFORM):
                    fail(f'Unexpected canonical shared class: {name}')
                audit_shared(name, data)
                common[name] = data
                add(name, data, 'shared')
            elif name.startswith('assets/') or name.startswith('META-INF/licenses/'):
                add(name, data, 'shared')
    catalog_cls = common.get('dev/nightclient/cosmetics/OfficialCapesCatalog.class')
    if catalog_cls is None:
        fail('Missing OfficialCapesCatalog.class in canonical shared JARs')
    has_mojang_cape_url = any('textures.minecraft.net' in s for s in utf8_constants(catalog_cls))
    if official_capes and not has_mojang_cape_url:
        fail('Full profile assembly requires official capes enabled in :core:jar')
    if not official_capes and has_mojang_cape_url:
        fail('Store profile assembly refused: OfficialCapesCatalog.class contains Mojang cape URLs; rebuild with -PofficialCapes=false')
    if not args.bootstrap.is_file():
        fail(f'Missing Java 17 bootstrap JAR: {args.bootstrap}')
    bootstrap_classes = []
    for name, data in jar_entries(args.bootstrap).items():
        if name.endswith('.class'):
            if not (name.startswith('dev/nightclient/bootstrap/') or shared_platform(name)):
                fail(f'Unexpected bootstrap dependency/class: {name}')
            if shared_platform(name):
                audit_shared(name, data)
            elif struct.unpack_from('>H', data, 6)[0] > 61:
                fail(f'Bootstrap is newer than Java 17: {name}')
            add(name, data, 'shared')
            bootstrap_classes.append(name)
    for name in ('dev/nightclient/bootstrap/UniversalClient.class', 'dev/nightclient/bootstrap/UniversalMixinPlugin.class'):
        if name not in entries:
            fail(f'Missing bootstrap class: {name}')
    for name in SHARED_PLATFORM:
        if PLATFORM + name + '.class' not in entries:
            fail(f'Missing canonical platform class: {name}')
    index = {'schemaVersion': 1, 'modVersion': version, 'officialCapes': official_capes, 'sourceFingerprint': fingerprint, 'sharedPlatformClasses': list(SHARED_PLATFORM), 'versions': {}, 'adapters': {}}
    properties = ['schemaVersion=1', 'modVersion=' + version, 'versions=' + ','.join((t['minecraft'] for t in targets))]
    asset_baseline = None
    groups = {}
    with tempfile.TemporaryDirectory(prefix='assemble-', dir=ROOT / 'build/universal') as scratch:
        scratch = Path(scratch)
        for target, row, artifact, source in rows:
            mc = target['minecraft']
            metadata = json.loads(source['fabric.mod.json'])
            mixins = json.loads(source['nightclient.mixins.json'])
            if metadata['id'] != 'nightclient' or metadata['version'] != version + '+mc' + mc:
                fail(f'Unexpected adapter metadata: {mc}')
            if mixins['package'] != 'dev.nightclient.platform.mixin':
                fail(f'Unexpected source mixin package: {mc}')
            compatibility = 'JAVA_' + str(target['java'])
            if mixins['compatibilityLevel'] != compatibility:
                fail(f'Mixin compatibility mismatch: {mc}')
            assets = {name: data for name, data in source.items() if name.startswith('assets/') and (include_native or not name.startswith('assets/nightclient/natives/'))}
            if asset_baseline is None:
                asset_baseline = assets
            elif assets != asset_baseline:
                fail(f'Assets differ between adapter JARs: {mc}')
            for name, data in assets.items():
                add(name, data, 'shared')
            if target['java'] == 17:
                def _member_sig(blob):
                    jc = JavaClass(blob)
                    return (
                        {(m['name'], m['descriptor']) for m in jc.methods if not m['name'].startswith(('lambda$', 'access$'))},
                        {(f['name'], f['descriptor']) for f in jc.fields if not f['name'].startswith(('this$', 'val$'))},
                    )
                for name, data in source.items():
                    if shared_platform(name) and name in entries and _member_sig(entries[name]) != _member_sig(data):
                        fail(f'Stale bootstrap shared class {name}; rebuild :universal:jar before assembling ({mc})')
                    if name in common and _member_sig(common[name]) != _member_sig(data):
                        fail(f'Stale canonical shared class {name}; rebuild common module JARs before assembling ({mc})')
            platform = {name: data for name, data in source.items() if name.startswith(PLATFORM) and name.endswith('.class') and (not shared_platform(name))}
            digest = hashlib.sha256()
            for name, data in sorted(platform.items()):
                digest.update(name.encode() + b'\x00' + data)
            digest.update(json.dumps(mixins, sort_keys=True).encode())
            adapter_hash = digest.hexdigest()
            adapter = groups.get(adapter_hash)
            if adapter is not None:
                if index['adapters'][adapter]['classSetSha256'] != adapter_hash:
                    fail('Adapter class set hash mismatch')
                relocation = index['adapters'][adapter]['relocationAudit']
            else:
                adapter = 'mc' + mc.replace('.', '_')
                if adapter in index['adapters']:
                    fail(f'Adapter id already publishes a different class set: {adapter}')
                groups[adapter_hash] = adapter
                moved = scratch / (adapter + '.jar')
                relocation = run_asm(command, 'platform', artifact, moved, 'dev.nightclient.adapter.' + adapter + '.platform')
                relocated = jar_entries(moved)
                if len(relocated) != len(platform):
                    fail(f'Relocated class count mismatch: {mc}')
                for name, data in relocated.items():
                    add(name, data, 'adapter')
                index['adapters'][adapter] = {'classSetSha256': adapter_hash, 'namespace': 'dev.nightclient.adapter.' + adapter + '.platform', 'mixinNamespace': MIXIN_PACKAGE + '.' + adapter, 'classCount': len(relocated), 'relocationAudit': relocation}
            namespace = 'dev.nightclient.adapter.' + adapter + '.platform'
            mixin_namespace = MIXIN_PACKAGE + '.' + adapter
            simple_mixins = mixins.get('client', [])
            if not simple_mixins or any((not re.fullmatch('[A-Za-z_$][A-Za-z0-9_$]*', m) for m in simple_mixins)):
                fail(f'Invalid adapter mixin names: {mc}')
            for simple in simple_mixins:
                if mixin_namespace.replace('.', '/') + '/' + simple + '.class' not in entries:
                    fail(f'Missing relocated mixin: {mc}: {simple}')
            entrypoint = namespace + '.NightClientMod'
            if entrypoint.replace('.', '/') + '.class' not in entries:
                fail(f'Missing relocated entrypoint: {mc}')
            selected = {'adapter': adapter, 'namespace': namespace, 'mixinNamespace': mixin_namespace, 'entrypoint': entrypoint, 'mixins': simple_mixins, 'compatibilityLevel': compatibility, 'java': target['java'], 'mappingNamespace': 'intermediary' if target['obfuscated'] else 'official', 'sourceArtifact': row['artifact'], 'sourceArtifactSha256': row['artifactSha256']}
            index['versions'][mc] = selected
            prefix = 'version.' + mc + '.'
            properties.extend([prefix + 'adapter=' + adapter, prefix + 'entrypoint=' + entrypoint, prefix + 'mixinPackage=' + mixin_namespace, prefix + 'mixins=' + ','.join(simple_mixins), prefix + 'compatibilityLevel=' + compatibility])
    metadata = {'schemaVersion': 1, 'id': 'nightclient', 'version': version, 'name': 'NightClient', 'description': 'Glassmorphic client-side visuals, customizable HUD, atmosphere, world map & PvP utilities for Fabric.', 'authors': ['NemoCC'], 'contact': {'homepage': 'https://github.com/N3moCC/NightClient', 'sources': 'https://github.com/N3moCC/NightClient', 'issues': 'https://github.com/N3moCC/NightClient/issues', 'discord': 'https://discord.gg/GenCYdp7NX'}, 'license': 'MIT', 'icon': 'assets/nightclient/icon.png', 'environment': 'client', 'entrypoints': {'client': [BOOTSTRAP_CLIENT]}, 'mixins': ['nightclient.mixins.json'], 'depends': {'fabricloader': '>=0.19.5', 'minecraft': [t['minecraft'] for t in targets], 'java': '>=17'}}
    mixins = {'required': True, 'minVersion': '0.8', 'package': MIXIN_PACKAGE, 'plugin': BOOTSTRAP_PLUGIN, 'compatibilityLevel': 'JAVA_17', 'client': [], 'injectors': {'defaultRequire': 1}}
    for name, value in [('fabric.mod.json', metadata), ('nightclient.mixins.json', mixins), (INDEX_NAME, index)]:
        add(name, (json.dumps(value, indent=2, ensure_ascii=False) + '\n').encode(), 'metadata')
    add(PROPERTIES_NAME, ('\n'.join(properties) + '\n').encode('ascii'), 'metadata')
    add('META-INF/MANIFEST.MF', ('Manifest-Version: 1.0\r\nImplementation-Title: NightClient\r\nImplementation-Version: ' + version + '\r\nImplementation-Vendor: NemoCC\r\n\r\n').encode('ascii'), 'metadata')
    license_file = ROOT / 'LICENSE'
    if license_file.is_file():
        add('LICENSE', license_file.read_bytes(), 'metadata')
    if source_fingerprint(official_capes=official_capes) != fingerprint:
        fail('Sources changed during assembly; refresh the full matrix and retry.')
    write_jar(args.output, entries)
    artifact_hash = sha(args.output.read_bytes())
    with zipfile.ZipFile(args.output) as result:
        compressed = {'shared': 0, 'adapter': 0, 'metadata': 0}
        for info in result.infolist():
            compressed[categories[info.filename]] += info.compress_size
        if result.testzip() is not None:
            fail('Final universal JAR CRC audit failed')
    report = {'passed': True, 'artifact': relative(args.output), 'artifactSha256': artifact_hash, 'sourceFingerprint': fingerprint, 'modVersion': version, 'officialCapes': official_capes, 'adapterCount': len(index['adapters']), 'versions': list(index['versions']), 'sharedCompressedBytes': compressed['shared'], 'adapterCompressedBytes': compressed['adapter'], 'metadataCompressedBytes': compressed['metadata'], 'totalBytes': args.output.stat().st_size, 'entryCount': len(entries), 'sharedPlatformClasses': list(SHARED_PLATFORM), 'sharedClasses': len(common) + len(bootstrap_classes), 'assetCount': len(asset_baseline), 'roundTripVerified': True, 'runtimeTested': False, 'bootstrapArtifact': relative(args.bootstrap), 'bootstrapSha256': sha(args.bootstrap.read_bytes()), 'index': INDEX_NAME, 'properties': PROPERTIES_NAME}
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + '\n')
    args.output.with_suffix(args.output.suffix + '.sha256').write_text(artifact_hash + '  ' + args.output.name + '\n')
    print(json.dumps(report, indent=2))

def main_assemble_universal():
    version = mod_version()
    default_bootstrap = ROOT / 'modules/universal/build/libs' / f'universal-{version}.jar'
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--bootstrap', type=Path, default=default_bootstrap)
    parser.add_argument('--report', type=Path)
    parser.add_argument('--java-home')
    parser.add_argument('--profile', choices=('full', 'store', 'modrinth', 'curseforge', 'clean'), default='full')
    parser.add_argument('--no-official-capes', action='store_true')
    parser.add_argument('--no-native', action='store_true')
    args = parser.parse_args()
    official_capes = (not args.no_official_capes) and (args.profile == 'full')
    args.official_capes = official_capes
    args.include_native = not args.no_native
    if args.output is None:
        suffix = '' if official_capes else '-store'
        if not args.include_native:
            suffix += '-pure-java'
        args.output = ROOT / 'build/distributions' / f'NightClient-{version}{suffix}.jar'
    if args.report is None:
        suffix = '' if official_capes else '-store'
        if not args.include_native:
            suffix += '-pure-java'
        args.report = ROOT / 'build/universal' / f'assembly-report{suffix}.json'
    if args.bootstrap.resolve() == default_bootstrap.resolve():
        required_jars = [default_bootstrap] + [ROOT / 'modules' / m / 'build/libs' / f'{m}-{version}.jar' for m in COMMON_MODULES]
        latest_src = max(
            (p.stat().st_mtime for m in (*COMMON_MODULES, 'platform', 'universal') for p in (ROOT / 'modules' / m / 'src').rglob('*') if p.is_file()),
            default=0.0,
        )
        core_jar = ROOT / 'modules/core/build/libs' / f'core-{version}.jar'
        core_profile_ok = False
        if core_jar.is_file():
            cat_cls = jar_entries(core_jar).get('dev/nightclient/cosmetics/OfficialCapesCatalog.class')
            if cat_cls is not None:
                core_profile_ok = any('textures.minecraft.net' in s for s in utf8_constants(cat_cls)) == official_capes
        if not core_profile_ok or any((not j.is_file() or j.stat().st_mtime < latest_src) for j in required_jars):
            java_home = find_java_home(args.java_home)
            env = os.environ.copy()
            env['JAVA_HOME'] = java_home
            env['PATH'] = str(Path(java_home) / 'bin') + os.pathsep + env.get('PATH', '')
            subprocess.run(
                [str(ROOT / 'gradlew'), '--console=plain', '-PcommonOnly=true',
                 '-PofficialCapes=' + ('true' if official_capes else 'false'),
                 ':api:jar', ':core:jar', ':renderer:jar', ':ui:jar', ':runtime:jar', ':universal:jar', '--offline'],
                cwd=ROOT,
                env=env,
                check=True,
            )
    assemble(args)
GAME_PREFIXES = ('net/minecraft/', 'com/mojang/blaze3d/', 'com/mojang/renderpearl/', 'org/lwjgl/')

def argument_types(descriptor):
    result = []
    pos = 1
    while descriptor[pos] != ')':
        start = pos
        while descriptor[pos] == '[':
            pos += 1
        pos = descriptor.index(';', pos) + 1 if descriptor[pos] == 'L' else pos + 1
        result.append(descriptor[start:pos])
    return result

class Reader:

    def __init__(self, data):
        self.data = data
        self.pos = 0

    def take(self, count):
        value = self.data[self.pos:self.pos + count]
        if len(value) != count:
            raise ValueError('Truncated class file')
        self.pos += count
        return value

    def u1(self):
        return self.take(1)[0]

    def u2(self):
        return struct.unpack('>H', self.take(2))[0]

    def u4(self):
        return struct.unpack('>I', self.take(4))[0]

class JavaClass:

    def __init__(self, data):
        reader = Reader(data)
        if reader.u4() != 3405691582:
            raise ValueError('Invalid class file')
        reader.u2()
        self.major = reader.u2()
        count = reader.u2()
        self.cp = [None] * count
        index = 1
        while index < count:
            tag = reader.u1()
            if tag == 1:
                value = reader.take(reader.u2()).decode('utf-8', errors='replace')
            elif tag in (3, 4):
                value = reader.u4()
            elif tag in (5, 6):
                value = reader.take(8)
            elif tag in (7, 8, 16, 19, 20):
                value = reader.u2()
            elif tag in (9, 10, 11, 12, 17, 18):
                value = (reader.u2(), reader.u2())
            elif tag == 15:
                value = (reader.u1(), reader.u2())
            else:
                raise ValueError(f'Unknown constant pool tag {tag}')
            self.cp[index] = (tag, value)
            index += 2 if tag in (5, 6) else 1
        self.access = reader.u2()
        self.name = self.class_name(reader.u2())
        parent = reader.u2()
        self.parent = self.class_name(parent) if parent else None
        self.interfaces = [self.class_name(reader.u2()) for _ in range(reader.u2())]
        self.fields = self.members(reader)
        self.methods = self.members(reader)
        self.attributes = self.attributes_from(reader)

    def utf(self, index):
        return self.cp[index][1]

    def class_name(self, index):
        return self.utf(self.cp[index][1])

    def member_ref(self, index):
        tag, value = self.cp[index]
        if tag not in (9, 10, 11):
            return None
        owner, signature = value
        name, descriptor = self.cp[signature][1]
        return (tag, self.class_name(owner), self.utf(name), self.utf(descriptor))

    def attributes_from(self, reader):
        output = {}
        for _ in range(reader.u2()):
            name = self.utf(reader.u2())
            output[name] = reader.take(reader.u4())
        return output

    def members(self, reader):
        output = []
        for _ in range(reader.u2()):
            access, name, descriptor = (reader.u2(), self.utf(reader.u2()), self.utf(reader.u2()))
            output.append({'access': access, 'name': name, 'descriptor': descriptor, 'attributes': self.attributes_from(reader)})
        return output

    def annotation_value(self, reader):
        tag = chr(reader.u1())
        if tag == '@':
            return self.annotation(reader)
        if tag == '[':
            return [self.annotation_value(reader) for _ in range(reader.u2())]
        if tag == 'e':
            return {'enum': self.utf(reader.u2()), 'value': self.utf(reader.u2())}
        return self.utf(reader.u2())

    def annotation(self, reader):
        value = {'annotation': self.utf(reader.u2())}
        for _ in range(reader.u2()):
            key = self.utf(reader.u2())
            value[key] = self.annotation_value(reader)
        return value

    def annotations(self, attributes):
        output = []
        for key in ['RuntimeVisibleAnnotations', 'RuntimeInvisibleAnnotations']:
            if key in attributes:
                reader = Reader(attributes[key])
                output.extend((self.annotation(reader) for _ in range(reader.u2())))
        return output

    def code_refs(self, method):
        return [instruction['reference'] for instruction in self.member_instructions(method)]

    def member_instructions(self, method):
        attribute = method['attributes'].get('Code')
        if not attribute:
            return []
        reader = Reader(attribute)
        reader.u2()
        reader.u2()
        code = reader.take(reader.u4())
        output = []
        pos = 0
        while pos < len(code):
            op = code[pos]
            if 178 <= op <= 185:
                ref = self.member_ref(struct.unpack('>H', code[pos + 1:pos + 3])[0])
                if ref:
                    output.append({'opcode': op, 'offset': pos, 'reference': ref})
            size = 1
            if op in (16, 18, 188, 169) or 21 <= op <= 25 or 54 <= op <= 58:
                size = 2
            elif op in (17, 19, 20, 132, 187, 189, 192, 193, 198, 199) or 153 <= op <= 168 or 178 <= op <= 184:
                size = 3
            elif op == 197:
                size = 4
            elif op in (185, 186, 200, 201):
                size = 5
            elif op == 196:
                size = 6 if code[pos + 1] == 132 else 4
            elif op in (170, 171):
                start = pos + 1
                while start % 4:
                    start += 1
                if op == 170:
                    low, high = struct.unpack('>ii', code[start + 4:start + 12])
                    size = start - pos + 12 + 4 * (high - low + 1)
                else:
                    pairs = struct.unpack('>i', code[start + 4:start + 8])[0]
                    size = start - pos + 8 + 8 * pairs
            pos += size
        return output

class Classpath:

    def __init__(self, paths, own):
        self.paths = []
        self.cache = dict(own)
        java_home = os.environ.get('JAVA_HOME')
        self.javap = str(Path(java_home) / 'bin/javap') if java_home else shutil.which('javap')
        for path in paths:
            path = Path(path)
            if path.is_dir():
                self.paths.append(path)
            elif path.is_file() and path.suffix in ('.jar', '.jmod'):
                self.paths.append(zipfile.ZipFile(path))

    def get(self, name):
        if name in self.cache:
            return self.cache[name]
        data = None
        filename = name + '.class'
        for path in self.paths:
            if isinstance(path, Path):
                file = path / filename
                if file.is_file():
                    data = file.read_bytes()
                    break
            else:
                try:
                    data = path.read(filename)
                    break
                except KeyError:
                    if str(path.filename).endswith('.jmod'):
                        try:
                            data = path.read('classes/' + filename)
                            break
                        except KeyError:
                            pass
        self.cache[name] = JavaClass(data) if data else self.jdk_class(name)
        return self.cache[name]

    def jdk_class(self, name):
        if not name.startswith('java/') or not self.javap:
            return None
        result = subprocess.run([self.javap, '-p', '-s', name.replace('/', '.')], capture_output=True, text=True)
        if result.returncode:
            return None
        fields, methods = ([], [])
        parent = None if name == 'java/lang/Object' else 'java/lang/Object'
        interfaces = []
        previous = ''
        for line in result.stdout.splitlines():
            stripped = line.strip()
            if stripped.endswith('{') and (' class ' in stripped or ' interface ' in stripped):
                header = stripped
                while re.search('<[^<>]*>', header):
                    header = re.sub('<[^<>]*>', '', header)
                match = re.search('extends\\s+([\\w.$]+)', header)
                if match:
                    parent = match.group(1).replace('.', '/')
            elif stripped.startswith('descriptor:'):
                descriptor = stripped.split(':', 1)[1].strip()
                if '(' in previous:
                    member = previous.split('(', 1)[0].split()[-1]
                    if member == name.replace('/', '.'):
                        member = '<init>'
                    methods.append({'name': member, 'descriptor': descriptor, 'attributes': {}})
                elif previous.endswith(';') and previous != 'static {};':
                    fields.append({'name': previous[:-1].split()[-1], 'descriptor': descriptor})
            previous = stripped or previous
        return SimpleNamespace(name=name, parent=parent, interfaces=interfaces, fields=fields, methods=methods)

    def find_member(self, owner, name, descriptor, field=False):
        queue = [owner]
        visited = set()
        while queue:
            current = queue.pop()
            if current in visited:
                continue
            visited.add(current)
            cls = self.get(current)
            if not cls:
                continue
            for member in cls.fields if field else cls.methods:
                if member['name'] == name and (descriptor is None or member['descriptor'] == descriptor):
                    return (cls, member)
            if cls.parent:
                queue.append(cls.parent)
            queue.extend(cls.interfaces)
        return None

def mixin_audit(cls, resolver, errors):
    mixin = next((a for a in cls.annotations(cls.attributes) if a['annotation'] == 'Lorg/spongepowered/asm/mixin/Mixin;'), None)
    if not mixin:
        return 0
    targets = [x[1:-1] for x in mixin.get('value', [])]
    targets += [x.replace('.', '/') for x in mixin.get('targets', [])]
    count = 0
    for target in targets:
        target_class = resolver.get(target)
        if not target_class:
            errors.append(f'{cls.name}: missing Mixin target {target}')
            continue
        if getattr(cls, 'access', 0) & 512 and (not getattr(target_class, 'access', 0) & 512):
            for method in cls.methods:
                if method['access'] & 4096:
                    continue
                annotations = cls.annotations(method['attributes'])
                if not any((a['annotation'].endswith(('/Accessor;', '/Invoker;')) for a in annotations)):
                    errors.append(f"{cls.name}: interface mixin is not a pure accessor for class target {target}; unannotated method {method['name']}{method['descriptor']}")
        for field in cls.fields:
            for annotation in cls.annotations(field['attributes']):
                if not annotation['annotation'].endswith('/Shadow;'):
                    continue
                prefix = annotation.get('prefix', 'shadow$')
                name = field['name'][len(prefix):] if field['name'].startswith(prefix) else field['name']
                candidates = [name] + annotation.get('aliases', [])
                found = next((resolver.find_member(target, candidate, field['descriptor'], field=True) for candidate in candidates if resolver.find_member(target, candidate, field['descriptor'], field=True)), None)
                if not found:
                    errors.append(f"{cls.name}: shadow field/type mismatch {target}.{name}{field['descriptor']}")
                elif field['access'] & 8 != found[1]['access'] & 8:
                    errors.append(f'{cls.name}: shadow field static modifier mismatch {target}.{name}')
                else:
                    count += 1
        for method in cls.methods:
            for annotation in cls.annotations(method['attributes']):
                kind = annotation['annotation']
                if kind.endswith('/Shadow;'):
                    prefix = annotation.get('prefix', 'shadow$')
                    name = method['name'][len(prefix):] if method['name'].startswith(prefix) else method['name']
                    found = resolver.find_member(target, name, method['descriptor'])
                    if not found:
                        errors.append(f"{cls.name}: shadow method/type mismatch {target}.{name}{method['descriptor']}")
                    elif method['access'] & 8 != found[1]['access'] & 8:
                        errors.append(f'{cls.name}: shadow method static modifier mismatch {target}.{name}')
                    else:
                        count += 1
                elif kind.endswith('/Accessor;'):
                    field_name = annotation.get('value')
                    if not field_name:
                        errors.append(f'{cls.name}: accessor must name its exact target field')
                        continue
                    found = resolver.find_member(target, field_name, None, field=True)
                    arguments = argument_types(method['descriptor'])
                    returned = method['descriptor'].split(')', 1)[1]
                    descriptor = returned if not arguments and returned != 'V' else arguments[0] if len(arguments) == 1 and returned == 'V' else None
                    if not found or found[1]['descriptor'] != descriptor:
                        errors.append(f'{cls.name}: accessor field/type mismatch {target}.{field_name}')
                    elif method['access'] & 8 != found[1]['access'] & 8:
                        errors.append(f'{cls.name}: accessor static modifier mismatch {target}.{field_name}')
                    else:
                        count += 1
                elif kind.endswith('/Invoker;'):
                    name = annotation.get('value')
                    if not name:
                        errors.append(f'{cls.name}: invoker must name its exact target method')
                        continue
                    found = resolver.find_member(target, name, method['descriptor'])
                    if not found:
                        errors.append(f"{cls.name}: invoker method/type mismatch {target}.{name}{method['descriptor']}")
                    elif method['access'] & 8 != found[1]['access'] & 8:
                        errors.append(f'{cls.name}: invoker static modifier mismatch {target}.{name}')
                    else:
                        count += 1
                elif kind.endswith(('/Inject;', '/ModifyVariable;', '/Redirect;', '/ModifyArg;', '/ModifyArgs;')):
                    selectors = annotation.get('method', [])
                    if isinstance(selectors, str):
                        selectors = [selectors]
                    methods = []
                    for selector in selectors:
                        name = selector.split('(', 1)[0]
                        descriptor = selector[len(name):] or None
                        selected = [m for m in target_class.methods if m['name'] == name and (descriptor is None or m['descriptor'] == descriptor)]
                        if not selected:
                            errors.append(f'{cls.name}: injection method missing {target}.{selector}')
                        methods.extend(selected)
                    ats = annotation.get('at', [])
                    if isinstance(ats, dict):
                        ats = [ats]
                    for at in ats:
                        invoke = at.get('target', '')
                        if at.get('value') == 'INVOKE' and invoke:
                            match = re.fullmatch('L([^;]+);([^()]+)(\\(.*)', invoke)
                            if not match:
                                errors.append(f'{cls.name}: unsupported invoke selector {invoke}')
                                continue
                            owner, name, descriptor = match.groups()
                            hits = sum((1 for method_target in methods for ref in target_class.code_refs(method_target) if ref[1:] == (owner, name, descriptor)))
                            if hits == 0:
                                errors.append(f'{cls.name}: invoke absent inside injection method: {invoke}')
                            else:
                                count += 1
                            if kind.endswith('/ModifyArg;'):
                                args = argument_types(method['descriptor'])
                                returned = method['descriptor'].split(')', 1)[1]
                                invoked = argument_types(descriptor)
                                index = annotation.get('index', -1)
                                if len(args) != 1 or args[0] != returned or (index >= 0 and (index >= len(invoked) or invoked[index] != returned)) or (index < 0 and invoked.count(returned) != 1):
                                    errors.append(f'{cls.name}: ModifyArg handler type/index mismatch: {invoke}')
                            elif kind.endswith('/ModifyArgs;'):
                                args = argument_types(method['descriptor'])
                                returned = method['descriptor'].split(')', 1)[1]
                                if not args or args[0] != 'Lorg/spongepowered/asm/mixin/injection/invoke/arg/Args;' or returned != 'V':
                                    errors.append(f'{cls.name}: ModifyArgs handler descriptor mismatch: {invoke}')
                            if kind.endswith('/Redirect;'):
                                handler_args = argument_types(method['descriptor'])
                                handler_return = method['descriptor'].split(')', 1)[1]
                                for target_method in methods:
                                    for instruction in target_class.member_instructions(target_method):
                                        if instruction['reference'][1:] != (owner, name, descriptor):
                                            continue
                                        expected = argument_types(descriptor)
                                        if instruction['opcode'] != 184:
                                            expected = [f'L{owner};'] + expected
                                        result_type = descriptor.split(')', 1)[1]
                                        if handler_args[:len(expected)] != expected or handler_return != result_type:
                                            errors.append(f'{cls.name}: invoke redirect handler descriptor mismatch: {invoke}')
                        elif at.get('value') == 'FIELD' and invoke:
                            match = re.fullmatch('L([^;]+);([^:]+):(.+)', invoke)
                            if not match:
                                errors.append(f'{cls.name}: unsupported field selector {invoke}')
                                continue
                            owner, name, descriptor = match.groups()
                            opcode = at.get('opcode', -1)
                            if not resolver.find_member(owner, name, descriptor, field=True):
                                errors.append(f'{cls.name}: unavailable injection field: {invoke}')
                            for target_method in methods:
                                instructions = target_class.member_instructions(target_method)
                                hits = [instruction for instruction in instructions if instruction['reference'][1:] == (owner, name, descriptor) and (opcode == -1 or instruction['opcode'] == opcode)]
                                ordinal = at.get('ordinal', -1)
                                if not hits or (ordinal >= 0 and ordinal >= len(hits)):
                                    errors.append(f'{cls.name}: field access/opcode/ordinal absent in target: {invoke}')
                                else:
                                    count += 1
                                if kind.endswith('/Redirect;') and hits:
                                    handler_args = argument_types(method['descriptor'])
                                    handler_return = method['descriptor'].split(')', 1)[1]
                                    for instruction in hits:
                                        operation = instruction['opcode']
                                        expected_args = [f'L{owner};'] if operation in (180, 181) else []
                                        if operation in (179, 181):
                                            expected_args.append(descriptor)
                                        expected_return = descriptor if operation in (178, 180) else 'V'
                                        if handler_return != expected_return or handler_args[:len(expected_args)] != expected_args:
                                            errors.append(f'{cls.name}: field redirect handler descriptor mismatch: {invoke}')
                                    required = annotation.get('require', -1)
                                    if required > 0 and len(hits) < required:
                                        errors.append(f'{cls.name}: field redirect has fewer sites than require={required}: {invoke}')
                                if cls.name.endswith('/SaturationMixin') and name == 'hideGui':
                                    if len(hits) != 1 or opcode != 180:
                                        errors.append(f'{cls.name}: Saturation requires one exact GETFIELD hideGui before HUD branching')
                                        continue
                                    point = hits[0]['offset']
                                    before = [instruction for instruction in instructions if instruction['reference'][2] in ('renderLevel', 'renderConfusionOverlay') or instruction['reference'][1:3] == ('net/minecraft/client/renderer/PostChain', 'process')]
                                    after = [instruction for instruction in instructions if instruction['reference'][2] == 'renderItemActivationAnimation' or instruction['reference'][1:3] == ('net/minecraft/client/gui/Gui', 'render')]
                                    if not before or not after or any((i['offset'] >= point for i in before)) or any((i['offset'] <= point for i in after)):
                                        errors.append(f'{cls.name}: colour pass must follow world/post effects and precede item animation/HUD')
                    if methods:
                        count += len(methods)
                    if kind.endswith('/Inject;') and 'locals' not in annotation:
                        handler_args = argument_types(method['descriptor'])
                        callbacks = [i for i, argument in enumerate(handler_args) if argument in ('Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;', 'Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;')]
                        if len(callbacks) != 1 or callbacks[0] != len(handler_args) - 1:
                            errors.append(f'{cls.name}: injection handler must end with its callback parameter')
                        else:
                            captured = handler_args[:-1]
                            for target_method in methods:
                                if captured and captured != argument_types(target_method['descriptor']):
                                    errors.append(f"{cls.name}: captured injection arguments do not match {target}.{target_method['name']}{target_method['descriptor']}")
                    if kind.endswith('/ModifyVariable;') and annotation.get('argsOnly'):
                        arguments = argument_types(method['descriptor'])
                        variable_type = arguments[0] if arguments else None
                        return_type = method['descriptor'].split(')', 1)[1]
                        ordinal = annotation.get('ordinal', -1)
                        if variable_type != return_type:
                            errors.append(f'{cls.name}: ModifyVariable handler input/output type mismatch')
                        for target_method in methods:
                            matches = argument_types(target_method['descriptor']).count(variable_type)
                            if not matches or (ordinal >= 0 and ordinal >= matches):
                                errors.append(f"{cls.name}: ModifyVariable argument type/ordinal absent: {target}.{target_method['name']}{target_method['descriptor']}")
    return count

def version_audit(version):
    info_file = ROOT / 'build/compatibility' / version / 'classpath.json'
    if not info_file.is_file():
        raise ValueError('Build the version with :mcVERSION:writeAuditClasspath first.')
    info = json.loads(info_file.read_text())
    errors = []
    with zipfile.ZipFile(info['devJar']) as jar:
        own = {name[:-6]: JavaClass(jar.read(name)) for name in jar.namelist() if name.endswith('.class')}
    classpath = list(info['classpath'])
    java_home = os.environ.get('JAVA_HOME')
    if java_home:
        java_base = Path(java_home) / 'jmods/java.base.jmod'
        if java_base.is_file():
            classpath.append(str(java_base))
    resolver = Classpath(classpath, own)
    checked_refs = 0
    checked_hooks = 0
    seen = set()
    for cls in own.values():
        checked_hooks += mixin_audit(cls, resolver, errors)
        for index, entry in enumerate(cls.cp):
            if not entry or entry[0] not in (9, 10, 11):
                continue
            ref = cls.member_ref(index)
            tag, owner, name, descriptor = ref
            if not owner.startswith(GAME_PREFIXES) or ref in seen:
                continue
            seen.add(ref)
            checked_refs += 1
            if not resolver.find_member(owner, name, descriptor, field=tag == 9):
                errors.append(f'{cls.name}: unavailable API {owner}.{name}{descriptor}')
    artifact = Path(info['artifact'])
    with zipfile.ZipFile(artifact) as jar:
        metadata = json.loads(jar.read('fabric.mod.json'))
        if metadata.get('id') != 'nightclient' or metadata.get('environment') != 'client':
            errors.append('Artifact has incorrect mod identity/environment')
        if metadata.get('depends', {}).get('minecraft') != version:
            errors.append('Artifact does not declare the exact Minecraft release')
        for name in jar.namelist():
            if name.endswith('.class'):
                cls = JavaClass(jar.read(name))
                if cls.major > int(info['java']) + 44:
                    errors.append(f"{name}: bytecode exceeds target Java {info['java']}")
        for entry in metadata.get('entrypoints', {}).get('client', []):
            if entry.replace('.', '/') + '.class' not in jar.namelist():
                errors.append('Missing client entrypoint ' + entry)
        if 'assets/nightclient/textures/gui/font.png' not in jar.namelist():
            errors.append('Missing embedded font atlas')
        shared_function = jar.read('assets/nightclient/shaders/include/saturation.glsl').decode().strip()
        for dialect in ['scalar', 'ubo', 'vulkan', 'pearl']:
            vertex = f'assets/nightclient/shaders/core/fullscreen_{dialect}.vsh'
            fragment = f'assets/nightclient/shaders/core/saturation_{dialect}.fsh'
            if vertex not in jar.namelist() or fragment not in jar.namelist():
                errors.append('Missing generated Saturation shader dialect ' + dialect)
            elif shared_function not in jar.read(fragment).decode():
                errors.append('Shader dialect does not embed the canonical Saturation function: ' + dialect)
        mixin_config = json.loads(jar.read('nightclient.mixins.json'))
        mixin_package = mixin_config.get('package', '')
        if mixin_package != 'dev.nightclient.platform.mixin':
            errors.append('Mixin configuration must isolate the dedicated platform.mixin package')
        declared_mixins = set()
        for entry in mixin_config.get('client', []) + mixin_config.get('mixins', []):
            qualified = mixin_package.replace('.', '/') + '/' + entry.replace('.', '/')
            declared_mixins.add(qualified)
            filename = qualified + '.class'
            if filename not in jar.namelist():
                errors.append('Mixin configuration references a missing class ' + entry)
            else:
                packaged_class = JavaClass(jar.read(filename))
                if not any((annotation['annotation'] == 'Lorg/spongepowered/asm/mixin/Mixin;' for annotation in packaged_class.annotations(packaged_class.attributes))):
                    errors.append('Mixin configuration references a non-mixin class ' + entry)
        for name in jar.namelist():
            if name.startswith(mixin_package.replace('.', '/') + '/') and name.endswith('.class'):
                qualified = name[:-6]
                if qualified not in declared_mixins and '$' not in qualified:
                    errors.append('Ordinary class is inside the reserved Mixin package: ' + name)
        refmap = mixin_config.get('refmap')
        if refmap and refmap not in jar.namelist():
            errors.append('Mixin config references a missing refmap')
    result = {'minecraft': version, 'compiledArtifact': str(artifact), 'artifactSha256': hashlib.sha256(artifact.read_bytes()).hexdigest(), 'namedApiReferencesChecked': checked_refs, 'mixinChecks': checked_hooks, 'errors': errors, 'passed': not errors, 'runtime': 'not_run', 'scope': 'Static JVM API, injection/accessor targets, invoke sites, packaging and Java bytecode.'}
    (info_file.parent / 'audit.json').write_text(json.dumps(result, indent=2) + '\n')
    print(f'{version}: {checked_refs} API references; {checked_hooks} Mixin checks; ' + ('PASS (runtime not run)' if not errors else 'FAIL'))
    for error in errors:
        print(error, file=sys.stderr)
    return errors

def main_audit_compatibility():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--version')
    args = parser.parse_args()
    if not args.version:
        parser.error('Choose --version VER.')
    errors = version_audit(args.version)
    raise SystemExit(1 if errors else 0)

def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()

def source_fingerprint(official_capes=True):
    from build_matrix import source_fingerprint as matrix_fingerprint
    return matrix_fingerprint(official_capes=official_capes)

def write_summary(reports, versions):
    rows = []
    for version in versions:
        result = reports / version / 'result.json'
        rows.append(json.loads(result.read_text()) if result.exists() else {'minecraft': version, 'compiled': False, 'apiAudit': 'not_run', 'runtime': 'not_run', 'opengl': 'not_run', 'vulkan': 'not_run'})
    output = reports / 'matrix.json'
    temporary = output.with_suffix('.json.tmp')
    temporary.write_text(json.dumps(rows, indent=2) + '\n')
    temporary.replace(output)
    return output

def find_java_home(requested):
    candidates = [requested, os.environ.get('JAVA_HOME')]
    java = shutil.which('java')
    if java:
        candidates.append(str(Path(java).resolve().parents[1]))
    for candidate in candidates:
        if candidate and (Path(candidate) / 'bin/java').is_file():
            return str(Path(candidate).resolve())
    raise SystemExit('JDK 25 is required. Set JAVA_HOME or pass --java-home /path/to/jdk-25.')

def main_build_matrix():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--versions', help='Comma-separated exact versions; default: all 24 releases.')
    parser.add_argument('--java-home')
    parser.add_argument('--offline', action='store_true')
    parser.add_argument('--no-audit', action='store_true')
    parser.add_argument('--stop-on-error', action='store_true')
    parser.add_argument('--build-timeout', type=int, default=600, help='Maximum seconds per Gradle target; terminate a stalled download cleanly (default 600).')
    parser.add_argument('--stop-file', type=Path, default=ROOT / 'build/compatibility/stop-requested', help='If this file appears, finish the current target, save evidence, and stop (exit2).')
    parser.add_argument('--refresh-stale', action='store_true', help='Rebuild targets whose recorded source fingerprint differs from the current source.')
    parser.add_argument('--profile', choices=('full', 'store', 'modrinth', 'curseforge', 'clean'), default='full', help='Build profile: full (default) or store/modrinth/curseforge/clean (omits official Mojang capes).')
    parser.add_argument('--no-official-capes', action='store_true', help='Shorthand for --profile store (omits official Mojang capes).')
    parser.add_argument('--no-native', action='store_true', help='Exclude bundled nightclient-native binaries from built JARs.')
    args = parser.parse_args()
    official_capes = (not args.no_official_capes) and (args.profile == 'full')
    include_native = not args.no_native
    if args.build_timeout < 30:
        parser.error('--build-timeout must be at least 30 seconds')
    targets = json.loads((ROOT / 'modules/platform/compatibility/versions.json').read_text())
    versions = [target['minecraft'] for target in targets]
    selected = versions if not args.versions else args.versions.split(',')
    unknown = set(selected) - set(versions)
    if unknown:
        parser.error('Unknown release(s): ' + ', '.join(sorted(unknown)))
    selected = list(dict.fromkeys(selected))
    env = os.environ.copy()
    env['JAVA_HOME'] = find_java_home(args.java_home)
    env['PATH'] = str(Path(env['JAVA_HOME']) / 'bin') + os.pathsep + env.get('PATH', '')
    base = [str(ROOT / 'gradlew'), '--console=plain', '-PofficialCapes=' + ('true' if official_capes else 'false'), '-PincludeNative=' + ('true' if include_native else 'false')]
    if args.offline:
        base.append('--offline')
    reports = ROOT / 'build/compatibility'
    reports.mkdir(parents=True, exist_ok=True)
    if args.refresh_stale:
        fingerprint = source_fingerprint(official_capes=official_capes)
        fresh = []
        for version in selected:
            result_file = reports / version / 'result.json'
            row = json.loads(result_file.read_text()) if result_file.exists() else {}
            artifact = ROOT / row.get('artifact', '')
            artifact_matches = artifact.is_file() and row.get('artifactSha256') == hashlib.sha256(artifact.read_bytes()).hexdigest()
            if not row.get('compiled') or row.get('apiAudit') != 'passed' or row.get('sourceFingerprint') != fingerprint or row.get('sourceChangedWhileBuilding') or (not artifact_matches):
                fresh.append(version)
        selected = fresh
    failures = []
    paused = False
    for index, version in enumerate(selected, 1):
        if args.stop_file.exists():
            paused = True
            print(f'Stopped at a target boundary: {args.stop_file}', flush=True)
            break
        print(f'[{index}/{len(selected)}] Minecraft {version}', flush=True)
        project = ':mc' + version.replace('.', '_')
        report_dir = reports / version
        report_dir.mkdir(parents=True, exist_ok=True)
        log = report_dir / 'build.log'
        command = base + ['-PmcVersion=' + version, project + ':distribute', project + ':writeAuditClasspath']
        row = {'minecraft': version, 'startedAt': now(), 'officialCapes': official_capes, 'compiled': False, 'apiAudit': 'not_run', 'runtime': 'not_run', 'opengl': 'not_run', 'vulkan': 'not_run', 'buildLog': str(log.relative_to(ROOT))}
        source_before = source_fingerprint(official_capes=official_capes)
        with log.open('w') as output:
            process = subprocess.Popen(command, cwd=ROOT, env=env, stdout=output, stderr=subprocess.STDOUT)
            row['buildTimedOut'] = False
            try:
                returncode = process.wait(timeout=args.build_timeout)
            except subprocess.TimeoutExpired:
                row['buildTimedOut'] = True
                print(f'{version}: Gradle exceeded {args.build_timeout}s; terminating the stalled target.', flush=True)
                process.terminate()
                try:
                    returncode = process.wait(timeout=20)
                except subprocess.TimeoutExpired:
                    process.kill()
                    returncode = process.wait(timeout=5)
        row['compiled'] = returncode == 0 and (not row['buildTimedOut'])
        row['buildExitCode'] = returncode
        if not row['compiled']:
            print(log.read_text()[-12000:], file=sys.stderr)
            failures.append(version)
        else:
            info = json.loads((report_dir / 'classpath.json').read_text())
            row['artifact'] = 'build/adapters/' + Path(info['artifact']).name
            row['artifactSha256'] = hashlib.sha256((ROOT / row['artifact']).read_bytes()).hexdigest()
            if not args.no_audit:
                audit = subprocess.run([sys.executable, str(ROOT / "tools/build.py"), 'audit-compatibility', '--version', version], cwd=ROOT, env=env)
                row['apiAudit'] = 'passed' if audit.returncode == 0 else 'failed'
                if audit.returncode:
                    failures.append(version)
        row['completedAt'] = now()
        row['sourceFingerprint'] = source_before
        row['sourceChangedWhileBuilding'] = source_before != source_fingerprint(official_capes=official_capes)
        result_file = report_dir / 'result.json'
        result_temporary = result_file.with_suffix('.json.tmp')
        result_temporary.write_text(json.dumps(row, indent=2) + '\n')
        result_temporary.replace(result_file)
        write_summary(reports, versions)
        print(f"  compiled={row['compiled']} audit={row['apiAudit']} runtime=not_run", flush=True)
        if version in failures and args.stop_on_error:
            break
    summary = write_summary(reports, versions)
    print(f'Report: {summary}', flush=True)
    current_fingerprint = source_fingerprint(official_capes=official_capes)
    stale = []
    for version in selected:
        result_file = reports / version / 'result.json'
        row = json.loads(result_file.read_text()) if result_file.exists() else {}
        if row.get('sourceFingerprint') != current_fingerprint or row.get('sourceChangedWhileBuilding'):
            stale.append(version)
    if stale and (not paused):
        print('Source changes require --refresh-stale: ' + ', '.join(stale), flush=True)
    raise SystemExit(1 if failures else 2 if paused else 3 if stale else 0)

def main_build_universal():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', help='JDK 25 used for compilation.')
    parser.add_argument('--offline', action='store_true')
    parser.add_argument('--build-timeout', type=int, default=600)
    parser.add_argument('--profile', choices=('full', 'store', 'modrinth', 'curseforge', 'clean'), default='full', help='Build profile: full (default) or store/modrinth/curseforge/clean (omits official Mojang capes).')
    parser.add_argument('--no-official-capes', action='store_true', help='Shorthand for --profile store (omits official Mojang capes).')
    parser.add_argument('--no-native', action='store_true', help='Exclude bundled nightclient-native binaries from built JARs.')
    args = parser.parse_args()
    official_capes = (not args.no_official_capes) and (args.profile == 'full')
    include_native = not args.no_native
    java_home = find_java_home(args.java_home)
    env = os.environ.copy()
    env['JAVA_HOME'] = java_home
    env['PATH'] = str(Path(java_home) / 'bin') + os.pathsep + env.get('PATH', '')
    matrix = [sys.executable, str(ROOT / "tools/build.py"), 'build-matrix', '--refresh-stale', '--java-home', java_home, '--build-timeout', str(args.build_timeout)]
    if not official_capes:
        matrix.append('--no-official-capes')
    if not include_native:
        matrix.append('--no-native')
    if args.offline:
        matrix.append('--offline')
    subprocess.run(matrix, cwd=ROOT, env=env, check=True)
    bootstrap = [str(ROOT / 'gradlew'), '--console=plain', '-PcommonOnly=true', '-PofficialCapes=' + ('true' if official_capes else 'false'), '-PincludeNative=' + ('true' if include_native else 'false'), ':api:jar', ':core:jar', ':renderer:jar', ':ui:jar', ':runtime:jar', ':universal:jar']
    if args.offline:
        bootstrap.append('--offline')
    subprocess.run(bootstrap, cwd=ROOT, env=env, check=True)
    assemble_cmd = [sys.executable, str(ROOT / "tools/build.py"), "assemble-universal"]
    if not official_capes:
        assemble_cmd.append('--no-official-capes')
    if not include_native:
        assemble_cmd.append('--no-native')
    subprocess.run(assemble_cmd, cwd=ROOT, env=env, check=True)
BACKDROP_FUNCTION = 'vec4 ncKawaseSample(sampler2D image, vec2 uv, vec2 step, float up) {\n    if(up < 0.5) {\n        vec4 value=texture(image,uv)*4.0;\n        value+=texture(image,uv+vec2(-step.x,-step.y));\n        value+=texture(image,uv+vec2( step.x,-step.y));\n        value+=texture(image,uv+vec2(-step.x, step.y));\n        value+=texture(image,uv+vec2( step.x, step.y));\n        return value/8.0;\n    }\n    vec4 value=texture(image,uv+vec2(-2.0*step.x,0.0));\n    value+=texture(image,uv+vec2( 2.0*step.x,0.0));\n    value+=texture(image,uv+vec2(0.0,-2.0*step.y));\n    value+=texture(image,uv+vec2(0.0, 2.0*step.y));\n    value+=texture(image,uv+vec2(-step.x,-step.y))*2.0;\n    value+=texture(image,uv+vec2( step.x,-step.y))*2.0;\n    value+=texture(image,uv+vec2(-step.x, step.y))*2.0;\n    value+=texture(image,uv+vec2( step.x, step.y))*2.0;\n    return value/12.0;\n}\nvec4 ncKawase(sampler2D image, vec2 uv, vec2 step, float up, float samples) {\n    if(samples < 2.0) return ncKawaseSample(image,uv,step,up);\n    vec2 texel=1.0/vec2(textureSize(image,0));\n    vec4 value=vec4(0.0);\n    if(samples < 5.0) {\n        for(int y=0;y<2;y++) for(int x=0;x<2;x++)\n            value+=ncKawaseSample(image,uv+(vec2(x,y)-vec2(0.5))*0.5*texel,step,up);\n        return value/4.0;\n    }\n    for(int y=0;y<3;y++) for(int x=0;x<3;x++)\n        value+=ncKawaseSample(image,uv+(vec2(x,y)-vec2(1.0))/3.0*texel,step,up);\n    return value/9.0;\n}\n'

def main_generate_backdrop_assets():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=ROOT / 'modules/renderer/build/generated/resources')
    args = parser.parse_args()
    base = args.output / 'assets/nightclient/shaders'
    (base / 'core').mkdir(parents=True, exist_ok=True)
    (base / 'include').mkdir(parents=True, exist_ok=True)
    (base / 'include/backdrop.glsl').write_text(BACKDROP_FUNCTION)
    for dialect in ['ubo', 'vulkan', 'pearl']:
        version = '330' if dialect in ('vulkan', 'pearl') else '150'
        explicit = 'layout(location = 0) ' if dialect == 'pearl' else ''
        extension = '#extension GL_ARB_separate_shader_objects : enable\n' if dialect == 'pearl' else ''
        shader = f'#version {version}\n{extension}uniform sampler2D InSampler;\nlayout(std140) uniform NightBlurConfig {{ vec2 BlurStep; float Upsample; float KernelSamples; }};\n{explicit}in vec2 texCoord;\n{explicit}out vec4 fragColor;\n{BACKDROP_FUNCTION}\nvoid main() {{ fragColor=vec4(ncKawase(InSampler,texCoord,BlurStep,Upsample,KernelSamples).rgb,1.0); }}\n'
        (base / f'core/backdrop_{dialect}.fsh').write_text(shader)
    print('Generated bounded integrated Dual-Kawase kernel and three native blur dialects')

def source(version):
    from generate_backdrops import source as generated
    return generated(version)

TEMPLATES = ROOT / 'modules/platform/src/templates'

def write(output, name, source, bridge=False):
    p = Path(output) / 'dev/nightclient/platform' / ('' if bridge else 'mixin') / (name + '.java')
    p.parent.mkdir(parents=True, exist_ok=True)
    if not p.exists() or p.read_text() != source:
        p.write_text(source)

def mixin(name, owner, body, imports=''):
    return 'package dev.nightclient.platform.mixin;\nimport dev.nightclient.platform.CameraFeatureBridge;\nimport org.spongepowered.asm.mixin.Mixin;\nimport org.spongepowered.asm.mixin.injection.*;\nimport org.spongepowered.asm.mixin.injection.callback.*;\n' + imports + '\n@Mixin(' + owner + '.class) public abstract class ' + name + ' {\n' + body + '\n}\n'

def signature(args):
    return ','.join((t.replace('$', '.') + ' a' + str(i) for i, t in enumerate(args)))

def calls(args):
    return ','.join(('a' + str(i) for i in range(len(args))))

def generate_camera_features(version, output):
    profiles = json.loads((ROOT / 'modules/platform/compatibility/camera-features.json').read_text())
    p = profiles[version]
    v = tuple(map(int, version.split('.')))
    bridge = json.loads((ROOT / 'modules/platform/compatibility/bridges.json').read_text())[version]
    native = v >= (1, 21, 5)
    views = v >= (1, 21, 6)
    sdl = version == '26.3'
    camera_native = v >= (26, 1)
    deferred = (1, 21, 9) <= v <= (26, 1)
    tex = 'com.mojang.renderpearl.api.textures' if sdl else 'com.mojang.blaze3d.textures'
    buf = 'com.mojang.renderpearl.api.buffers' if sdl else 'com.mojang.blaze3d.buffers'
    category = 'KeyMapping.Category.register(GameBridge.identifier("nightclient:camera"))' if bridge['keyMappingCategory'] == 'Category' else '"key.categories.nightclient"'
    values = {'FREELOOK_KEY': 'new KeyMapping("key.nightclient.freelook",InputConstants.KEY_LALT,' + category + ')', 'SCREEN': 'mc.gui.screen()' if v >= (26, 2) else 'mc.screen', 'HIDDEN': 'mc.gui.hud.isHidden()' if v >= (26, 2) else 'mc.options.hideGui', 'MAIN_ARM_SIGN': '(Minecraft.getInstance().player.getMainArm()==HumanoidArm.RIGHT?1:-1)', 'OFF_ARM_SIGN': '(Minecraft.getInstance().player.getMainArm()==HumanoidArm.RIGHT?-1:1)', 'ROTATE': ''.join(('if(' + n + '!=0)pose.mulPose(com.mojang.math.Axis.' + axis + 'P.rotationDegrees(' + n + '));' for n, axis in [('x', 'X'), ('y', 'Y'), ('z', 'Z')])), 'MAIN_TARGET': 'mc.gameRenderer.mainRenderTarget()' if v >= (26, 2) else 'mc.getMainRenderTarget()', 'IMPORTS': '', 'DEPTH_PICK': '', 'PROJECTION_IMPORT': 'import dev.nightclient.platform.mixin.ViewmodelCachedProjectionAccessor;' if (1, 21, 6) <= v < (26, 1) else ''}
    projection_setup = ''
    if v >= (26, 1):
        projection = '((ViewmodelGameRendererAccessor)(Object)Minecraft.getInstance().gameRenderer).nightclient$hudProjection().getMatrix(new org.joml.Matrix4f())'
    elif v >= (1, 21, 6):
        projection_setup = 'ViewmodelCachedProjectionAccessor cachedProjection=(ViewmodelCachedProjectionAccessor)(Object)((ViewmodelGameRendererAccessor)(Object)Minecraft.getInstance().gameRenderer).nightclient$hudProjection();'
        projection = 'cachedProjection.nightclient$matrix(cachedProjection.nightclient$width(),cachedProjection.nightclient$height(),cachedProjection.nightclient$fov())'
    else:
        projection = 'RenderSystem.getProjectionMatrix()'
    modelview = 'RenderSystem.getModelViewStack()' if v >= (1, 20, 5) else 'RenderSystem.getModelViewMatrix()'
    values['PROJECT_HAND'] = projection_setup + 'new org.joml.Matrix4f(' + projection + ').mul(' + modelview + ').mul(pose.last().pose()).get(matrix);'
    cap = p['capture']
    args = signature(cap['args'])
    argnames = calls(cap['args'])
    accessor = 'package dev.nightclient.platform.mixin;\nimport net.minecraft.client.renderer.GameRenderer;\nimport org.spongepowered.asm.mixin.Mixin;\nimport org.spongepowered.asm.mixin.gen.Accessor;\nimport org.spongepowered.asm.mixin.gen.Invoker;\n@Mixin(GameRenderer.class) public interface ViewmodelGameRendererAccessor {\n @Invoker("renderItemInHand") void nightclient$replay(' + args + ');\n'
    if v <= (26, 1):
        accessor += ' @Accessor("renderBuffers") net.minecraft.client.renderer.RenderBuffers nightclient$buffers();\n'
    if deferred:
        accessor += ' @Accessor("featureRenderDispatcher") net.minecraft.client.renderer.feature.FeatureRenderDispatcher nightclient$features();\n'
    if v >= (26, 1):
        accessor += ' @Accessor("hudProjection") net.minecraft.client.renderer.Projection nightclient$hudProjection();\n'
    elif v >= (1, 21, 6):
        accessor += ' @Accessor("hud3dProjectionMatrixBuffer") net.minecraft.client.renderer.CachedPerspectiveProjectionMatrixBuffer nightclient$hudProjection();\n'
    accessor += '}\n'
    write(output, 'ViewmodelGameRendererAccessor', accessor)
    if (1, 21, 6) <= v < (26, 1):
        write(output, 'ViewmodelCachedProjectionAccessor', 'package dev.nightclient.platform.mixin;\nimport net.minecraft.client.renderer.CachedPerspectiveProjectionMatrixBuffer;\nimport org.spongepowered.asm.mixin.Mixin;\nimport org.spongepowered.asm.mixin.gen.Accessor;\nimport org.spongepowered.asm.mixin.gen.Invoker;\n@Mixin(CachedPerspectiveProjectionMatrixBuffer.class) public interface ViewmodelCachedProjectionAccessor {\n @Accessor("width") int nightclient$width();\n @Accessor("height") int nightclient$height();\n @Accessor("fov") float nightclient$fov();\n @Invoker("createProjectionMatrix") org.joml.Matrix4f nightclient$matrix(int width,int height,float fov);\n}\n')
    else:
        stale = Path(output) / 'dev/nightclient/platform/mixin/ViewmodelCachedProjectionAccessor.java'
        if stale.exists():
            stale.unlink()
    target = 'package dev.nightclient.platform.mixin;\nimport com.mojang.blaze3d.pipeline.RenderTarget;\nimport org.spongepowered.asm.mixin.Mixin;\nimport org.spongepowered.asm.mixin.gen.Accessor;\n@Mixin(RenderTarget.class) public interface ViewmodelTargetAccessor {\n'
    if native:
        for name, t in [('colorTexture', tex + '.GpuTexture'), ('depthTexture', tex + '.GpuTexture')] + ([('colorTextureView', tex + '.GpuTextureView'), ('depthTextureView', tex + '.GpuTextureView')] if views else []):
            target += ' @Accessor("' + name + '") void nightclient$' + name + '(' + t + ' value);\n'
    else:
        for name in ['colorTextureId', 'depthBufferId']:
            target += ' @Accessor("' + name + '") int nightclient$' + name + '();\n @Accessor("' + name + '") void nightclient$' + name + '(int value);\n'
    target += '}\n'
    write(output, 'ViewmodelTargetAccessor', target)
    if native:
        values['IMPORTS'] = 'import ' + tex + '.GpuTexture;\n' + ('import ' + tex + '.GpuTextureView;\n' if views else '') + 'import ' + buf + '.GpuBuffer;\n'
        values['TARGET_CREATE'] = 'new com.mojang.blaze3d.pipeline.TextureTarget("NightClient actual hand silhouette",main.width,main.height,' + ('main.getColorTexture().getFormat(),main.getDepthTexture()==null?null:main.getDepthTexture().getFormat()' if sdl else 'true' + (',main.getColorTexture().getFormat()' if v >= (26, 2) else '')) + ')'
        values['BEFORE_CAPTURE'] = ('((ViewmodelGameRendererAccessor)(Object)renderer).nightclient$features().renderAllFeatures();' if deferred else '') + ('((ViewmodelGameRendererAccessor)(Object)renderer).nightclient$buffers().bufferSource().endBatch();' if v <= (26, 1) else '') + '\n        final ViewmodelTargetAccessor attachments=(ViewmodelTargetAccessor)(Object)main;\n        final GpuTexture originalColor=main.getColorTexture(),originalDepth=main.getDepthTexture();\n' + ('final GpuTextureView originalColorView=main.getColorTextureView(),originalDepthView=main.getDepthTextureView();' if views else '') + ('final GpuTextureView previousColorOverride=RenderSystem.outputColorTextureOverride,previousDepthOverride=RenderSystem.outputDepthTextureOverride;' if views and (not sdl) else '')
        values['TARGET_BIND'] = 'attachments.nightclient$colorTexture(maskTarget.getColorTexture());attachments.nightclient$depthTexture(maskTarget.getDepthTexture());' + ('attachments.nightclient$colorTextureView(maskTarget.getColorTextureView());attachments.nightclient$depthTextureView(maskTarget.getDepthTextureView());' if views else '') + ('RenderSystem.outputColorTextureOverride=maskTarget.getColorTextureView();RenderSystem.outputDepthTextureOverride=maskTarget.getDepthTextureView();' if views and (not sdl) else '')
        values['RESTORE_TARGET'] = 'attachments.nightclient$colorTexture(originalColor);attachments.nightclient$depthTexture(originalDepth);' + ('attachments.nightclient$colorTextureView(originalColorView);attachments.nightclient$depthTextureView(originalDepthView);' if views else '') + ('RenderSystem.outputColorTextureOverride=previousColorOverride;RenderSystem.outputDepthTextureOverride=previousDepthOverride;' if views and (not sdl) else '')
        values['CLEAR_TARGET'] = 'if(hand==0)RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(maskTarget.getColorTexture(),' + ('new org.joml.Vector4f(0)' if v >= (26, 2) else '0') + ',maskTarget.getDepthTexture(),' + ('RenderSystem.DEFAULT_DEPTH_CLEAR_VALUE' if v >= (26, 2) else '1') + ');else RenderSystem.getDevice().createCommandEncoder().clearColorTexture(maskTarget.getColorTexture(),' + ('new org.joml.Vector4f(0)' if v >= (26, 2) else '0') + ');'
        values['FLUSH_HAND'] = ('((ViewmodelGameRendererAccessor)(Object)renderer).nightclient$features().renderAllFeatures();' if deferred else '') + ('((ViewmodelGameRendererAccessor)(Object)renderer).nightclient$buffers().bufferSource().endBatch();' if v <= (26, 1) else '')
        mapped = 'encoder.readBuffer(buffer)' if version == '1.21.5' else 'buffer.map(true,false)' if v >= (26, 2) else 'encoder.mapBuffer(buffer,true,false)'
        values['READ_MASK'] = 'private static void readMask(RenderTarget target,int logicalWidth,int logicalHeight,java.util.function.Consumer<PickMask> consumer){\n        final int width=target.width,height=target.height;\n        final GpuBuffer buffer=RenderSystem.getDevice().createBuffer(()->"NightClient hand-alpha readback",@@BUFFER_USAGE@@,width*height*4);\n        var encoder=RenderSystem.getDevice().createCommandEncoder();\n        try{\n            encoder.copyTextureToBuffer(target.getColorTexture(),buffer,0,()->{\n                PickMask mask=null;\n                try(var mapped=' + mapped + '){mask=PickMask.fromRgba(mapped.data(),width,height,logicalWidth,logicalHeight,true);}\n                finally{buffer.close();consumer.accept(mask);}\n            },0);\n        }catch(RuntimeException ex){buffer.close();throw ex;}\n    }'
        values['READ_MASK'] = values['READ_MASK'].replace('@@BUFFER_USAGE@@', 'com.mojang.blaze3d.buffers.BufferType.PIXEL_PACK,com.mojang.blaze3d.buffers.BufferUsage.STREAM_READ' if version == '1.21.5' else 'GpuBuffer.USAGE_MAP_READ|GpuBuffer.USAGE_COPY_DST')
        if sdl:
            values['ROTATE'] = ''.join(('if(' + n + '!=0)pose.rotateDegrees(com.mojang.math.Axis.' + axis + 'P,' + n + ');' for n, axis in [('x', 'X'), ('y', 'Y'), ('z', 'Z')]))
            values['DEPTH_PICK'] = 'public static GpuTextureView depthForPick(GpuTextureView original){return capturing&&maskTarget!=null?maskTarget.getDepthTextureView():original;}'
            argnames = 'a0,a1,CameraFeatureBridge.depthForPick(a2)'
    else:
        values['IMPORTS'] = 'import org.lwjgl.opengl.GL11;\nimport org.lwjgl.opengl.GL21;\nimport org.lwjgl.opengl.GL30;\nimport org.lwjgl.BufferUtils;\nimport java.nio.ByteBuffer;\nimport java.nio.IntBuffer;\nimport java.nio.FloatBuffer;\n'
        values['TARGET_CREATE'] = 'new com.mojang.blaze3d.pipeline.TextureTarget(main.width,main.height,true' + (',false' if v < (1, 21, 2) else '') + ')'
        values['BEFORE_CAPTURE'] = '((ViewmodelGameRendererAccessor)(Object)renderer).nightclient$buffers().bufferSource().endBatch();\n        final int drawFbo=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),readFbo=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);\n        legacyViewportBuf.clear();GL11.glGetIntegerv(GL11.GL_VIEWPORT,legacyViewportBuf);\n        legacyClearBuf.clear();GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE,legacyClearBuf);\n        final boolean scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),depthMask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);\n        legacyScissorBuf.clear();GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX,legacyScissorBuf);\n        legacyColorMaskBuf.clear();GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK,legacyColorMaskBuf);\n        final double depthClear=GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE);\n        final ViewmodelTargetAccessor attachments=(ViewmodelTargetAccessor)(Object)main;\n        final int originalFbo=main.frameBufferId,originalColor=attachments.nightclient$colorTextureId(),originalDepth=attachments.nightclient$depthBufferId();'
        values['CLEAR_TARGET'] = 'maskTarget.bindWrite(true);RenderSystem.disableScissor();RenderSystem.colorMask(true,true,true,true);RenderSystem.depthMask(true);RenderSystem.clearColor(0,0,0,0);GL11.glClearDepth(1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|(hand==0?GL11.GL_DEPTH_BUFFER_BIT:0));'
        values['TARGET_BIND'] = 'main.frameBufferId=maskTarget.frameBufferId;attachments.nightclient$colorTextureId(maskTarget.getColorTextureId());attachments.nightclient$depthBufferId(maskTarget.getDepthTextureId());'
        values['FLUSH_HAND'] = '((ViewmodelGameRendererAccessor)(Object)renderer).nightclient$buffers().bufferSource().endBatch();'
        values['RESTORE_TARGET'] = 'main.frameBufferId=originalFbo;attachments.nightclient$colorTextureId(originalColor);attachments.nightclient$depthBufferId(originalDepth);\n            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,drawFbo);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,readFbo);GL11.glViewport(legacyViewportBuf.get(0),legacyViewportBuf.get(1),legacyViewportBuf.get(2),legacyViewportBuf.get(3));\n            RenderSystem.colorMask(legacyColorMaskBuf.get(0)!=0,legacyColorMaskBuf.get(1)!=0,legacyColorMaskBuf.get(2)!=0,legacyColorMaskBuf.get(3)!=0);RenderSystem.depthMask(depthMask);GL11.glClearDepth(depthClear);if(scissor)RenderSystem.enableScissor(legacyScissorBuf.get(0),legacyScissorBuf.get(1),legacyScissorBuf.get(2),legacyScissorBuf.get(3));else RenderSystem.disableScissor();RenderSystem.clearColor(legacyClearBuf.get(0),legacyClearBuf.get(1),legacyClearBuf.get(2),legacyClearBuf.get(3));'
        values['READ_MASK'] = 'private static final IntBuffer legacyViewportBuf=BufferUtils.createIntBuffer(4),legacyScissorBuf=BufferUtils.createIntBuffer(4);\n    private static final FloatBuffer legacyClearBuf=BufferUtils.createFloatBuffer(4);\n    private static final ByteBuffer legacyColorMaskBuf=BufferUtils.createByteBuffer(4);\n    private static ByteBuffer legacyPixelsBuf;\n    private static void readMask(RenderTarget target,int logicalWidth,int logicalHeight,java.util.function.Consumer<PickMask> consumer){\n        int readFbo=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),alignment=GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT),pbo=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);\n        int needed=target.width*target.height*4;\n        if(legacyPixelsBuf==null||legacyPixelsBuf.capacity()<needed)legacyPixelsBuf=BufferUtils.createByteBuffer(needed);\n        legacyPixelsBuf.clear();legacyPixelsBuf.limit(needed);\n        try{GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,target.frameBufferId);GL30.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,0);GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,1);GL11.glReadPixels(0,0,target.width,target.height,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,legacyPixelsBuf);consumer.accept(PickMask.fromRgba(legacyPixelsBuf,target.width,target.height,logicalWidth,logicalHeight,true));}\n        finally{GL30.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,pbo);GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,alignment);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,readFbo);}\n    }'
    source = (TEMPLATES / 'CameraFeatureBridge.java.tpl').read_text()
    for k, value in values.items():
        source = source.replace('@@' + k + '@@', value)
    if re.search('@@[A-Z_]+@@', source):
        raise ValueError('Unresolved camera template ' + version)
    write(output, 'CameraFeatureBridge', source, True)
    write(output, 'ViewmodelOutlineBackend', outline_source(version), True)
    write(output, 'ViewmodelCaptureMixin', mixin('ViewmodelCaptureMixin', 'net.minecraft.client.renderer.GameRenderer', ' @Inject(method="' + cap['selector'] + '",at=@At("HEAD")) private void nightclient$capture(' + args + ',CallbackInfo ci){CameraFeatureBridge.capture((net.minecraft.client.renderer.GameRenderer)(Object)this,()->((ViewmodelGameRendererAccessor)(Object)this).nightclient$replay(' + argnames + '));}'))
    hand = p['hand']
    ha = hand['args']
    sig = signature(ha)
    pose = 'a' + str(ha.index('com.mojang.blaze3d.vertex.PoseStack'))
    interaction = 'a' + str(ha.index('net.minecraft.world.InteractionHand'))
    body = ' private boolean nightclient$transformed;\n @Inject(method="' + hand['selector'] + '",at=@At("HEAD"),cancellable=true) private void nightclient$transform(' + sig + ',CallbackInfo ci){nightclient$transformed=false;if(CameraFeatureBridge.skipHand(' + interaction + ')){ci.cancel();return;}nightclient$transformed=CameraFeatureBridge.beginHand(' + pose + ',' + interaction + ');}\n @Inject(method="' + hand['selector'] + '",at=@At("RETURN")) private void nightclient$restore(' + sig + ',CallbackInfo ci){CameraFeatureBridge.endHand(' + pose + ',nightclient$transformed);nightclient$transformed=false;}\n @ModifyVariable(method="' + hand['selector'] + '",at=@At("HEAD"),argsOnly=true,ordinal=3) private float nightclient$equip(float value){return CameraFeatureBridge.equip(value);}\n @Inject(method="' + p['attack']['selector'] + '",at=@At("HEAD"),cancellable=true) private void nightclient$swing(com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.world.entity.HumanoidArm arm,float progress,CallbackInfo ci){if(CameraFeatureBridge.customSwing(pose,arm,progress))ci.cancel();}'
    write(output, 'ViewmodelHandMixin', mixin('ViewmodelHandMixin', p['handOwner'], body))
    cam = p['camera']
    sel = cam['selector']
    body = ' @Inject(method="' + sel + '",at=@At("HEAD")) private void nightclient$camera(CallbackInfo ci){CameraFeatureBridge.cameraFrame();}\n'
    for name, outname in [('getViewYRot', 'Yaw'), ('getViewXRot', 'Pitch')]:
        body += ' @Redirect(method="' + sel + '",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;' + name + '(F)F")) private float nightclient$' + outname + '(net.minecraft.world.entity.Entity entity,float partial){return CameraFeatureBridge.camera' + outname + '(entity,entity.' + name + '(partial));}\n'
    write(output, 'CameraMixin', mixin('CameraMixin', 'net.minecraft.client.Camera', body))
    sel = p['mouse']['selector']
    write(output, 'FreelookMouseMixin', mixin('FreelookMouseMixin', 'net.minecraft.client.MouseHandler', ' @Redirect(method="' + sel + '",at=@At(value="INVOKE",target="Lnet/minecraft/client/player/LocalPlayer;turn(DD)V")) private void nightclient$turn(net.minecraft.client.player.LocalPlayer player,double yaw,double pitch){if(!CameraFeatureBridge.interceptTurn(player,yaw,pitch))player.turn(yaw,pitch);}'))
    if camera_native:
        body = ' @ModifyArgs(method="setupPerspective(FFFFF)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/Projection;setupPerspective(FFFFF)V")) private void nightclient$aspect(org.spongepowered.asm.mixin.injection.invoke.arg.Args args){float width=args.get(3),height=args.get(4);args.set(3,CameraFeatureBridge.aspect(width/Math.max(1f,height))*height);}'
        body += ' @ModifyArg(method="createProjectionMatrixForCulling",at=@At(value="INVOKE",target="Lorg/joml/Matrix4f;perspective(FFFFZ)Lorg/joml/Matrix4f;"),index=1) private float nightclient$cullingAspect(float original){return CameraFeatureBridge.aspect(original);}'
        owner = 'net.minecraft.client.Camera'
    else:
        owner = 'net.minecraft.client.renderer.GameRenderer'
        target = 'Lcom/mojang/math/Matrix4f;perspective(DFFF)Lcom/mojang/math/Matrix4f;' if v < (1, 19, 3) else 'Lorg/joml/Matrix4f;setPerspective(FFFF)Lorg/joml/Matrix4f;' if v < (1, 20, 5) else 'Lorg/joml/Matrix4f;perspective(FFFF)Lorg/joml/Matrix4f;'
        body = ' @ModifyArg(method="getProjectionMatrix",at=@At(value="INVOKE",target="' + target + '"),index=1) private float nightclient$aspect(float original){return CameraFeatureBridge.aspect(original);}'
    write(output, 'AspectRatioMixin', mixin('AspectRatioMixin', owner, body))
    duration = 'getModifiedSwingDuration(Lnet/minecraft/world/item/component/SwingAnimation;)I' if sdl else 'getCurrentSwingDuration()I'
    write(output, 'SwingDurationMixin', mixin('SwingDurationMixin', 'net.minecraft.world.entity.LivingEntity', ' @Inject(method="' + duration + '",at=@At("RETURN"),cancellable=true) private void nightclient$duration(CallbackInfoReturnable<Integer> cir){cir.setReturnValue(CameraFeatureBridge.swingDuration((net.minecraft.world.entity.Entity)(Object)this,cir.getReturnValue()));}'))
    screen = p['screenEffect']
    if screen:
        write(output, 'ViewmodelScreenEffectMixin', mixin('ViewmodelScreenEffectMixin', 'net.minecraft.client.renderer.ScreenEffectRenderer', ' @Inject(method="' + screen['selector'] + '",at=@At("HEAD"),cancellable=true) private ' + ('static ' if screen['static'] else '') + 'void nightclient$mask(CallbackInfo ci){if(CameraFeatureBridge.capturing())ci.cancel();}'))
    else:
        stale = Path(output) / 'dev/nightclient/platform/mixin/ViewmodelScreenEffectMixin.java'
        if stale.exists():
            stale.unlink()

def main_generate_camera_features():
    parser = argparse.ArgumentParser()
    parser.add_argument('--version', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    generate_camera_features(args.version, args.output)

def generate_features(version, output):
    output = Path(output)
    profiles = json.loads((ROOT / 'modules/platform/compatibility/features.json').read_text())
    bridges = json.loads((ROOT / 'modules/platform/compatibility/bridges.json').read_text())
    if version not in profiles or version not in bridges:
        raise ValueError('Minecraft release is outside the verified feature matrix: ' + version)
    p, bridge = (profiles[version], bridges[version])
    numeric = tuple(map(int, version.split('.')))
    native = numeric >= (26, 1)
    registry = 'net.minecraft.core.registries.BuiltInRegistries.ITEM'
    identifier = bridge['identifierType']
    category = 'KeyMapping.Category.register(GameBridge.identifier("nightclient:features"))' if bridge['keyMappingCategory'] == 'Category' else '"key.categories.nightclient"'
    screen = 'mc.gui.screen()' if numeric >= (26, 2) else 'mc.screen'
    values = {'FEATURE_IMPORTS': 'import com.mojang.blaze3d.platform.InputConstants;\n', 'ZOOM_KEY': 'new KeyMapping("key.nightclient.zoom",InputConstants.KEY_C,' + category + ')', 'SCREEN': screen, 'INVENTORY': 'mc.player.getInventory()', 'ITEM_ID': registry + '.getKey(stack.getItem()).toString()', 'TNT_FUSE': p['tntFuseGetter'], 'CROSSHAIR_OWNER': p['crosshairOwner'].replace('/', '.'), 'CROSSHAIR_SHORT': p['crosshairOwner'].rsplit('/', 1)[-1], 'CROSSHAIR_SELECTOR': p['crosshairHook'], 'ZOOM_OWNER': p['zoomOwner'].replace('/', '.'), 'ZOOM_SHORT': p['zoomOwner'].rsplit('/', 1)[-1], 'ZOOM_SELECTOR': p['zoomHook'], 'FOV_TYPE': 'double' if p['zoomHook'].endswith('D') else 'float', 'FOV_BOXED': 'Double' if p['zoomHook'].endswith('D') else 'Float', 'ZOOM_ARGUMENTS': 'float partialTick,' if native else 'net.minecraft.client.Camera camera,float partialTick,boolean worldFov,', 'ZOOM_GUARD': '' if native else 'if(!worldFov)return;', 'EDITBOX_RENDER': p['editBoxRender'], 'FONT_STRING_METHODS': ','.join((json.dumps(x) for x in p['fontStringMethods'])), 'FONT_SEQUENCE_METHODS': ','.join((json.dumps(x) for x in p['fontSequenceMethods']))}
    context = bridge['screenContext']
    context_import = 'com.mojang.blaze3d.vertex.PoseStack' if context == 'PoseStack' else 'net.minecraft.client.gui.' + context
    imports = 'import ' + context_import + ';\nimport net.minecraft.resources.' + identifier + ';'
    values['IDENTIFIER'] = identifier
    values['CONTEXT'] = context
    values['CROSSHAIR_RESOURCE'] = 'minecraft:hud/crosshair' if numeric >= (1, 20, 2) else 'minecraft:textures/gui/icons.png'
    if numeric >= (1, 21, 6):
        imports += '\nimport net.minecraft.client.renderer.RenderPipelines;'
        values['CROSSHAIR_PUSH'] = 'graphics.pose().pushMatrix();graphics.pose().translate(x,y);graphics.pose().scale(size/15,size/15);'
        values['CROSSHAIR_POP'] = 'graphics.pose().popMatrix();'
        values['CROSSHAIR_STATE_CAPTURE'] = ''
        values['CROSSHAIR_STATE_RESTORE'] = ''
        values['CROSSHAIR_DRAW'] = 'int channel=Math.max(0,Math.min(255,Math.round(opacity*255)));int tint=channel*0x01010101;graphics.blitSprite(RenderPipelines.CROSSHAIR,CROSSHAIR,0,0,15,15,tint);'
    else:
        imports += '\nimport com.mojang.blaze3d.systems.RenderSystem;'
        values['CROSSHAIR_PUSH'] = 'graphics.pose().pushPose();graphics.pose().translate(x,y,0);graphics.pose().scale(size/15,size/15,1);' if context == 'GuiGraphics' else 'graphics.pushPose();graphics.translate(x,y,0);graphics.scale(size/15,size/15,1);'
        values['CROSSHAIR_POP'] = 'graphics.pose().popPose();' if context == 'GuiGraphics' else 'graphics.popPose();'
        capture = 'LegacyUiRenderer.State before=new LegacyUiRenderer.State();'
        if numeric == (1, 16, 5):
            capture += 'float[] oldColor=new float[4];org.lwjgl.opengl.GL11.glGetFloatv(org.lwjgl.opengl.GL11.GL_CURRENT_COLOR,oldColor);'
            color = 'RenderSystem.color4f(opacity,opacity,opacity,opacity);'
            restore = 'RenderSystem.color4f(oldColor[0],oldColor[1],oldColor[2],oldColor[3]);RenderSystem.bindTexture(before.texture0);'
        else:
            capture += 'float[] oldColor=RenderSystem.getShaderColor().clone();'
            color = 'RenderSystem.setShaderColor(opacity,opacity,opacity,opacity);'
            restore = 'RenderSystem.setShaderColor(oldColor[0],oldColor[1],oldColor[2],oldColor[3]);'
            if numeric < (1, 21, 2):
                capture += 'final net.minecraft.client.renderer.ShaderInstance oldShader=RenderSystem.getShader();int oldTexture=RenderSystem.getShaderTexture(0);'
                restore += 'RenderSystem.setShader(()->oldShader);RenderSystem.setShaderTexture(0,oldTexture);'
        values['CROSSHAIR_STATE_CAPTURE'] = capture
        blend_sync = ''
        if (1, 21, 2) <= numeric <= (1, 21, 5):
            gl_pkg = 'com.mojang.blaze3d.opengl' if numeric == (1, 21, 5) else 'com.mojang.blaze3d.platform'
            blend_sync = f'boolean _bl=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);int _sr=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_RGB),_dr=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_RGB),_sa=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_ALPHA),_da=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_ALPHA);{gl_pkg}.GlStateManager._enableBlend();{gl_pkg}.GlStateManager._disableBlend();if(_bl){gl_pkg}.GlStateManager._enableBlend();{gl_pkg}.GlStateManager._blendFuncSeparate(org.lwjgl.opengl.GL11.GL_ONE,org.lwjgl.opengl.GL11.GL_ZERO,org.lwjgl.opengl.GL11.GL_ONE,org.lwjgl.opengl.GL11.GL_ZERO);{gl_pkg}.GlStateManager._blendFuncSeparate(_sr,_dr,_sa,_da);'
        values['CROSSHAIR_STATE_RESTORE'] = restore + 'before.restore();' + blend_sync
        if numeric >= (1, 21, 2):
            imports += '\nimport net.minecraft.client.renderer.RenderType;'
            draw = color + 'graphics.blitSprite(RenderType::crosshair,CROSSHAIR,0,0,15,15);graphics.flush();'
        else:
            blend = 'org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_BLEND);org.lwjgl.opengl.GL14.glBlendFuncSeparate(org.lwjgl.opengl.GL11.GL_ONE_MINUS_DST_COLOR,org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_COLOR,org.lwjgl.opengl.GL11.GL_ONE,org.lwjgl.opengl.GL11.GL_ZERO);'
            if numeric >= (1, 20, 2):
                draw = color + blend + 'graphics.blitSprite(CROSSHAIR,0,0,15,15);graphics.flush();'
            elif context == 'GuiGraphics':
                draw = color + blend + 'graphics.blit(CROSSHAIR,0,0,0f,0f,15,15,256,256);graphics.flush();'
            else:
                imports += '\nimport net.minecraft.client.gui.GuiComponent;'
                setup = 'mc.getTextureManager().bind(CROSSHAIR);RenderSystem.enableTexture();' if numeric == (1, 16, 5) else 'RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);RenderSystem.setShaderTexture(0,CROSSHAIR);'
                draw = setup + color + blend + 'GuiComponent.blit(graphics,0,0,0f,0f,15,15,256,256);'
        values['CROSSHAIR_DRAW'] = draw
    values['CROSSHAIR_IMPORTS'] = imports
    event_input = numeric >= (1, 21, 9)
    chat_render = bridge['screenMethod']
    context_desc = 'com/mojang/blaze3d/vertex/PoseStack' if context == 'PoseStack' else 'net/minecraft/client/gui/' + context
    chat_imports = 'import ' + context_import + ';'
    if event_input:
        chat_imports += '\nimport net.minecraft.client.input.KeyEvent;\nimport net.minecraft.client.input.MouseButtonEvent;\nimport net.minecraft.client.input.CharacterEvent;'
        chat_key_signature = 'KeyEvent event'
        chat_key_values = 'GameBridge.key(event.key(),' + ('event.shortcutKey()' if version == '26.3' else 'event.key()') + '),GameBridge.modifiers(event.modifiers())'
        chat_mouse_args = 'event.x(),event.y(),GameBridge.mouseButton(event.button())'
        chat_click_args = 'MouseButtonEvent event,boolean doubleClick,'
        chat_click_hook = 'mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z'
        chat_key_hook = 'keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z'
        chat_drag_signature = 'MouseButtonEvent event,double deltaX,double deltaY'
        chat_release_signature = 'MouseButtonEvent event'
        chat_super_drag = 'super.mouseDragged(event,deltaX,deltaY)'
        chat_super_release = 'super.mouseReleased(event)'
        chat_super_key_release = 'super.keyReleased(event)'
        chat_character = '@Override public boolean charTyped(CharacterEvent event){\n        ClientRuntime runtime=ClientRuntime.get();return runtime!=null&&runtime.chatHudCharTyped(this,event.codepoint())||super.charTyped(event);\n    }'
    else:
        chat_key_signature = 'int keyCode,int scanCode,int nativeModifiers'
        chat_key_values = 'GameBridge.key(keyCode,keyCode),GameBridge.modifiers(nativeModifiers)'
        chat_mouse_args = 'mouseX,mouseY,GameBridge.mouseButton(button)'
        chat_click_args = 'double mouseX,double mouseY,int button,'
        chat_click_hook = 'mouseClicked(DDI)Z'
        chat_key_hook = 'keyPressed(III)Z'
        chat_drag_signature = 'double mouseX,double mouseY,int button,double deltaX,double deltaY'
        chat_release_signature = 'double mouseX,double mouseY,int button'
        chat_super_drag = 'super.mouseDragged(mouseX,mouseY,button,deltaX,deltaY)'
        chat_super_release = 'super.mouseReleased(mouseX,mouseY,button)'
        chat_super_key_release = 'super.keyReleased(keyCode,scanCode,nativeModifiers)'
        chat_character = '@Override public boolean charTyped(char character,int modifiers){\n        ClientRuntime runtime=ClientRuntime.get();return runtime!=null&&runtime.chatHudCharTyped(this,character)||super.charTyped(character,modifiers);\n    }'
    chat_scroll_signature = 'double mouseX,double mouseY,double horizontal,double vertical' if bridge['mouseScrollAxes'] == 2 else 'double mouseX,double mouseY,double vertical'
    values.update({'CHAT_IMPORTS': chat_imports, 'CHAT_RENDER_HOOK': chat_render + '(L' + context_desc + ';IIF)V', 'CHAT_CLICK_HOOK': chat_click_hook, 'CHAT_CLICK_ARGS': chat_click_args, 'CHAT_MOUSE_ARGS': chat_mouse_args, 'CHAT_SCROLL_HOOK': 'mouseScrolled(' + ('DDDD' if bridge['mouseScrollAxes'] == 2 else 'DDD') + ')Z', 'CHAT_SCROLL_ARGS': chat_scroll_signature + ',', 'CHAT_KEY_HOOK': chat_key_hook, 'CHAT_KEY_ARGS': chat_key_signature + ',', 'CHAT_KEY_SIGNATURE': chat_key_signature, 'CHAT_KEY_VALUES': chat_key_values, 'CHAT_DRAG_SIGNATURE': chat_drag_signature, 'CHAT_RELEASE_SIGNATURE': chat_release_signature, 'CHAT_SUPER_DRAG': chat_super_drag, 'CHAT_SUPER_RELEASE': chat_super_release, 'CHAT_SUPER_KEY_RELEASE': chat_super_key_release, 'CHAT_CHARACTER_OVERRIDE': chat_character})
    turn_selector = 'turnPlayer()V' if numeric <= (1, 20, 4) else 'turnPlayer(D)V'
    if numeric < (1, 19):
        values['SENSITIVITY_HANDLER'] = '@Redirect(method="' + turn_selector + '",at=@At(value="FIELD",target="Lnet/minecraft/client/Options;sensitivity:D",opcode=180))\n    private double nightclient$sensitivity(net.minecraft.client.Options options){return FeatureBridge.zoomSensitivity(options.sensitivity);}'
    else:
        values['SENSITIVITY_HANDLER'] = '@Redirect(method="' + turn_selector + '",at=@At(value="INVOKE",target="Ljava/lang/Double;doubleValue()D"))\n    private double nightclient$sensitivity(Double original){return FeatureBridge.zoomSensitivity(original.doubleValue());}'
    values['LINE_HEIGHT_ACCESSOR'] = '@Invoker("getLineHeight") int nightclient$lineHeight();' if numeric >= (1, 19, 1) else ''
    files = ['FeatureBridge', 'CrosshairBridge', 'CrosshairMixin', 'ZoomFovMixin', 'ZoomScrollMixin', 'ZoomSensitivityMixin', 'ChatScreenAccessor', 'ChatLinesAccessor', 'ChatHudMixin', 'PasswordEditBoxMixin', 'ProtectFontMixin']
    if native:
        files.append('PasswordImeMixin')
    package_root = output / 'dev/nightclient/platform'
    for name in files:
        source = (TEMPLATES / (name + '.java.tpl')).read_text()
        for key, value in values.items():
            source = source.replace('@@' + key + '@@', value)
        if re.search('@@[A-Z_]+@@', source):
            raise ValueError('Unresolved feature template: ' + name + ': ' + version)
        target = package_root / (name + '.java' if name in ('FeatureBridge', 'CrosshairBridge') else 'mixin/' + name + '.java')
        target.parent.mkdir(parents=True, exist_ok=True)
        if not target.exists() or target.read_text() != source:
            target.write_text(source)
    for name in ('TntProjectionMixin', 'NativeGameRendererFeatureAccessor'):
        stale = package_root / 'mixin' / (name + '.java')
        if stale.exists():
            stale.unlink()
    if not native:
        stale = package_root / 'mixin' / 'PasswordImeMixin.java'
        if stale.exists():
            stale.unlink()

def main_generate_features():
    parser = argparse.ArgumentParser()
    parser.add_argument('--version', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    generate_features(args.version, args.output)

def points(path):
    tokens = re.findall('[MLHVQCZ]|-?(?:\\d*\\.\\d+|\\d+)(?:[eE][-+]?\\d+)?', path)
    at = 0
    x = y = 0.0
    shapes = []
    current = []
    while at < len(tokens):
        cmd = tokens[at]
        at += 1
        count = {'M': 2, 'L': 2, 'H': 1, 'V': 1, 'Q': 4, 'C': 6, 'Z': 0}.get(cmd)
        if count is None:
            raise ValueError('Only explicit absolute SVG commands are accepted: ' + cmd)
        data = list(map(float, tokens[at:at + count]))
        at += count
        if cmd == 'M':
            if current:
                shapes.append(current)
            x, y = data
            current = [(x, y)]
        elif cmd in ('L', 'H', 'V'):
            if cmd == 'L':
                x, y = data
            elif cmd == 'H':
                x = data[0]
            else:
                y = data[0]
            current.append((x, y))
        elif cmd in ('Q', 'C'):
            ax, ay = (x, y)
            for step in range(1, 13):
                t = step / 12
                s = 1 - t
                if cmd == 'Q':
                    x = s * s * ax + 2 * s * t * data[0] + t * t * data[2]
                    y = s * s * ay + 2 * s * t * data[1] + t * t * data[3]
                else:
                    x = s * s * s * ax + 3 * s * s * t * data[0] + 3 * s * t * t * data[2] + t * t * t * data[4]
                    y = s * s * s * ay + 3 * s * s * t * data[1] + 3 * s * t * t * data[3] + t * t * t * data[5]
                current.append((x, y))
        else:
            current.append(current[0])
            x, y = current[0]
    if current:
        shapes.append(current)
    return shapes

def pack_utf(value):
    encoded = value.encode('utf-8')
    return struct.pack('>H', len(encoded)) + encoded

def triangles(shape):
    polygon = shape[:-1] if shape[0] == shape[-1] else shape[:]
    clean = []
    for point in polygon:
        if not clean or abs(point[0] - clean[-1][0]) + abs(point[1] - clean[-1][1]) > 1e-07:
            clean.append(point)
    polygon = clean
    if len(polygon) < 3:
        raise ValueError('A filled SVG path needs at least three distinct points')

    def cross(a, b, c):
        return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])
    area = sum((polygon[i][0] * polygon[(i + 1) % len(polygon)][1] - polygon[(i + 1) % len(polygon)][0] * polygon[i][1] for i in range(len(polygon))))
    if abs(area) < 1e-07:
        raise ValueError('A filled SVG path has zero area')
    if area < 0:
        polygon.reverse()
    indices = list(range(len(polygon)))
    result = []
    while len(indices) > 3:
        found = False
        for i, b in enumerate(indices):
            a, c = (indices[i - 1], indices[(i + 1) % len(indices)])
            if cross(polygon[a], polygon[b], polygon[c]) <= 1e-07:
                continue
            if any((cross(polygon[a], polygon[b], polygon[p]) > 1e-07 and cross(polygon[b], polygon[c], polygon[p]) > 1e-07 and (cross(polygon[c], polygon[a], polygon[p]) > 1e-07) for p in indices if p not in (a, b, c))):
                continue
            result.extend((v for point in (polygon[a], polygon[b], polygon[c]) for v in point))
            del indices[i]
            found = True
            break
        if not found:
            for i, b in enumerate(indices):
                if abs(cross(polygon[indices[i - 1]], polygon[b], polygon[indices[(i + 1) % len(indices)]])) <= 1e-07:
                    del indices[i]
                    found = True
                    break
        if not found:
            raise ValueError('Filled SVG outline must be simple and have no holes')
    if len(indices) == 3:
        result.extend((v for i in indices for v in polygon[i]))
    return result

def chunk(name, data):
    return struct.pack('>I', len(data)) + name + data + struct.pack('>I', zlib.crc32(name + data) & 4294967295)

def main_generate_platform():
    parser = argparse.ArgumentParser()
    parser.add_argument('--version', required=True)
    parser.add_argument('--output', type=pathlib.Path, required=True)
    args = parser.parse_args()
    profiles = json.loads((ROOT / 'modules/platform/compatibility/bridges.json').read_text())
    if args.version not in profiles:
        parser.error('Minecraft release is outside the verified support matrix')
    v = args.version
    p = profiles[v]
    version = tuple((int(part) for part in v.split('.')))
    modern = version >= (1, 21, 6)
    native = version >= (26, 1)
    sdl = v == '26.3'
    context = p['screenContext']
    context_import = 'com.mojang.blaze3d.vertex.PoseStack' if context == 'PoseStack' else 'net.minecraft.client.gui.' + context
    identifier = p['identifierType']
    registry = 'BuiltInRegistries.ITEM' if version >= (1, 19, 3) else 'Registry.ITEM'
    registry_import = 'net.minecraft.core.registries.BuiltInRegistries'
    identifier_parse = identifier + '.parse(id)' if version >= (1, 21) else 'new ' + identifier + '(id)'
    parse_category = identifier + '.parse("nightclient:visuals")' if version >= (1, 21) else 'new ' + identifier + '("nightclient:visuals")'
    category = 'KeyMapping.Category.register(' + parse_category + ')' if p['keyMappingCategory'] == 'Category' else '"key.categories.nightclient"'
    default_key = 'InputConstants.KEY_RSHIFT'
    game_imports = 'import ' + registry_import + ';\nimport ' + context_import + ';'
    if not sdl:
        game_imports += '\nimport org.lwjgl.glfw.GLFW;'
    mods_source = 'InputConstants' if sdl else 'GLFW'
    mods_prefix = 'MOD_' if sdl else 'GLFW_MOD_'
    normalize_mods = 'int result=0;'
    for key in ['SHIFT', 'CONTROL', 'ALT', 'SUPER']:
        normalize_mods += 'if((nativeModifiers & ' + mods_source + '.' + mods_prefix + key + ')!=0)result|=Keys.MOD_' + key + ';'
    normalize_mods += 'return result;'
    normalize_key = 'return nativeKey;'
    if sdl:
        native_keys = [('ESCAPE', 'ESCAPE'), ('RETURN', 'ENTER'), ('NUMPADENTER', 'ENTER'), ('TAB', 'TAB'), ('BACKSPACE', 'BACKSPACE'), ('DELETE', 'DELETE'), ('LEFT', 'LEFT'), ('RIGHT', 'RIGHT'), ('UP', 'UP'), ('DOWN', 'DOWN'), ('HOME', 'HOME'), ('END', 'END'), ('SPACE', 'SPACE'), ('F5', 'F5')]
        normalize_key = ''.join(('if(nativeKey==InputConstants.KEY_' + a + ')return Keys.' + b + ';' for a, b in native_keys))
        extra_glfw = [('INSERT', 260), ('PAGEUP', 266), ('PAGEDOWN', 267), ('ADD', 334)] + [(f'F{i}', 289 + i) for i in range(1, 13) if i != 5] + [(f'NUMPAD{i}', 320 + i) for i in range(10)]
        normalize_key += ''.join((f'if(nativeKey==InputConstants.KEY_{name})return {code};' for name, code in extra_glfw))
        normalize_key += "if(shortcutKey>='a' && shortcutKey<='z')return shortcutKey-32;if(shortcutKey>=32 && shortcutKey<=126)return shortcutKey;"
        normalize_key += ''.join(('if(nativeKey==InputConstants.KEY_' + letter + ')return ' + str(ord(letter)) + ';' for letter in 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'))
        normalize_key += ''.join(('if(nativeKey==InputConstants.KEY_' + source + ')return Keys.' + target + ';' for source, target in [('LSHIFT', 'LEFT_SHIFT'), ('RSHIFT', 'RIGHT_SHIFT'), ('LCONTROL', 'LEFT_CONTROL'), ('RCONTROL', 'RIGHT_CONTROL'), ('LALT', 'LEFT_ALT'), ('RALT', 'RIGHT_ALT'), ('LGUI', 'LEFT_SUPER'), ('RGUI', 'RIGHT_SUPER')]))
        normalize_key += 'return -1;'
    normalize_button = 'return button;'
    if sdl:
        normalize_button = 'if(button==InputConstants.MOUSE_BUTTON_LEFT)return 0;if(button==InputConstants.MOUSE_BUTTON_RIGHT)return 1;if(button==InputConstants.MOUSE_BUTTON_MIDDLE)return 2;return Math.max(3,button-1);'
    scan_groups = ''
    if p['cooldownKeyType'] != 'Item':
        remember_item = 'Minecraft mc=Minecraft.getInstance();if(mc==null||mc.player==null)return;Object key=mc.player.getCooldowns().getCooldownGroup(stack);if(key!=null)rememberedItems.put(key,new ItemInfo(itemId(stack.getItem()),stack.getHoverName().getString()));'
        remember_inv = 'if(lastRememberTick!=state.nightclient$tickCount()){\n            lastRememberTick=state.nightclient$tickCount();\n            Inventory inv=mc.player.getInventory();\n            for(int slot=0;slot<inv.getContainerSize();slot++){\n                ItemStack st=inv.getItem(slot);if(st.isEmpty())continue;\n                Object grp=nativeCooldowns.getCooldownGroup(st);\n                if(grp!=null)rememberedItems.put(grp,new ItemInfo(itemId(st.getItem()),st.getHoverName().getString()));\n            }\n            ItemStack off=mc.player.getOffhandItem();\n            if(!off.isEmpty()){Object grp=nativeCooldowns.getCooldownGroup(off);if(grp!=null)rememberedItems.put(grp,new ItemInfo(itemId(off.getItem()),off.getHoverName().getString()));}\n        }'
        scan_groups = 'if(previousCooldownTick!=state.nightclient$tickCount()){\n            previousCooldownTick=state.nightclient$tickCount();\n            Inventory inventory=mc.player.getInventory();\n            for(int slot=0;slot<inventory.getContainerSize();slot++){\n                ItemStack stack=inventory.getItem(slot);if(stack.isEmpty())continue;\n                Object group=nativeCooldowns.getCooldownGroup(stack);if(!active.containsKey(group))continue;\n                String itemId=itemId(stack.getItem()),name=stack.getHoverName().getString();\n                ItemInfo cached=groupItems.get(group);\n                if(cached==null || !cached.id.equals(itemId) || !cached.name.equals(name))groupItems.put(group,new ItemInfo(itemId,name));\n            }\n            ItemStack off=mc.player.getOffhandItem();\n            if(!off.isEmpty()){Object group=nativeCooldowns.getCooldownGroup(off);if(active.containsKey(group))groupItems.put(group,new ItemInfo(itemId(off.getItem()),off.getHoverName().getString()));}\n            groupItems.keySet().retainAll(active.keySet());\n        }'
        cooldown_key = 'String id=entry.getKey().toString();ItemInfo info=groupItems.get(entry.getKey());\n            if(info==null)info=rememberedItems.get(entry.getKey());\n            if(info==null){Item item=itemForId(id);if(item!=null && item!=Items.AIR)info=new ItemInfo(itemId(item),itemName(item));}\n            String lowerKey=id.toLowerCase(java.util.Locale.ROOT);boolean fireFallback=lowerKey.contains("fire_charge")||lowerKey.contains("firecharge")||(lowerKey.contains("explosive")&&!lowerKey.contains("trap"))||lowerKey.contains("shtuch");\n            String name=info==null?(fireFallback?"Взрывная штучка":id):info.name,icon=info==null?(fireFallback?"minecraft:fire_charge":null):info.id;'
    else:
        remember_item = 'Item item=stack.getItem();rememberedItems.put(item,new ItemInfo(itemId(item),stack.getHoverName().getString()));'
        remember_inv = 'if(lastRememberTick!=state.nightclient$tickCount()){\n            lastRememberTick=state.nightclient$tickCount();\n            Inventory inv=mc.player.getInventory();\n            for(int slot=0;slot<inv.getContainerSize();slot++){\n                ItemStack st=inv.getItem(slot);if(st.isEmpty())continue;\n                Item it=st.getItem();rememberedItems.put(it,new ItemInfo(itemId(it),st.getHoverName().getString()));\n            }\n            ItemStack off=mc.player.getOffhandItem();\n            if(!off.isEmpty()){Item it=off.getItem();rememberedItems.put(it,new ItemInfo(itemId(it),off.getHoverName().getString()));}\n        }'
        cooldown_key = 'Item item=(Item)entry.getKey();ItemInfo info=rememberedItems.get(item);String id=itemId(item),name=info!=null?info.name:itemName(item),icon=id;'
    hud_next_stratum = 'graphics.nextStratum();' if modern else ''
    hud_legacy_delta_field = 'private float nightclient$lastDelta;' if version < (1, 20, 5) else ''
    hud_legacy_delta_save = 'this.nightclient$lastDelta=delta;' if version < (1, 20, 5) else ''
    flush_gui = 'graphics.flush();' if context == 'GuiGraphics' and (not modern) else ''
    values = {'VERSION': v, 'IDENTIFIER': identifier, 'IDENTIFIER_PARSE': identifier_parse, 'KEY_CATEGORY_DECLARATION': 'private static final ' + ('KeyMapping.Category' if p['keyMappingCategory'] == 'Category' else 'String') + ' keyCategory=' + category + ';', 'MODULE_KEY_CONSTRUCTION': 'new KeyMapping("key.nightclient.module."+id,InputConstants.UNKNOWN.getValue(),keyCategory)', 'WAYPOINT_KEY_CONSTRUCTION': 'new KeyMapping("key.nightclient.waypoint",' + ('InputConstants.KEY_B' if sdl else 'GLFW.GLFW_KEY_B') + ',keyCategory)', 'GAME_IMPORTS': game_imports, 'KEY_CONSTRUCTION': 'new KeyMapping("key.nightclient.menu",' + default_key + ',keyCategory)', 'SCREEN_ACCESS': 'mc.gui.screen()' if version >= (26, 2) else 'mc.screen', 'OVERLAY_ACCESS': 'mc.gui.overlay()' if version >= (26, 2) else 'mc.getOverlay()', 'SET_SCREEN_OPEN': 'mc.gui.setScreen(new NightScreen())' if version >= (26, 2) else 'mc.setScreen(new NightScreen())', 'SET_SCREEN_CLOSE': 'mc.gui.setScreen(null)' if version >= (26, 2) else 'mc.setScreen(null)', 'GAME_HUD_HIDDEN': 'mc.gui.hud.isHidden()' if version >= (26, 2) else 'mc.options.hideGui', 'NORMALIZE_BUTTON': normalize_button, 'NORMALIZE_MODIFIERS': normalize_mods, 'NORMALIZE_KEY': normalize_key, 'TEXT_FOCUS': 'Minecraft.getInstance().onTextInputFocusChange(owner,focused);' if native else '', 'REGISTRY': registry, 'REGISTRY_GET': 'getValue' if version >= (1, 21, 2) else 'get', 'REMEMBER_COOLDOWN_ITEM': remember_item, 'REMEMBER_INVENTORY_ITEMS': remember_inv, 'SCAN_GROUPS': scan_groups, 'COOLDOWN_KEY': cooldown_key, 'CONTEXT': context, 'CONTEXT_IMPORT': context_import, 'FLUSH_GUI': flush_gui, 'SCREEN_RENDER_METHOD': p['screenMethod'], 'SCREEN_TITLE': 'Component.literal(GameBridge.title())', 'SCREEN_IMPORTS': 'import ' + context_import + ';', 'CANVAS_IMPORTS': 'import ' + context_import + ';', 'HUD_OWNER': p['hudOwner'], 'HUD_SHORT': p['hudOwner'].split('.')[-1], 'HUD_METHOD': p['hudMethod'], 'HUD_EFFECTS_METHOD': 'extractEffects' if version >= (26, 1) else 'renderEffects', 'HUD_LEGACY_DELTA_FIELD': hud_legacy_delta_field, 'HUD_LEGACY_DELTA_SAVE': hud_legacy_delta_save, 'HUD_TAB_HOOK': ('@org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final private net.minecraft.client.gui.components.PlayerTabOverlay tabList;\n    @Inject(method="extractTabList(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",at=@At("HEAD"),cancellable=true)\n    private void nightclient$animateTabList(GuiGraphicsExtractor graphics,DeltaTracker delta,CallbackInfo callback){\n        this.nightclient$renderHudOnce(graphics,delta);\n        ' + hud_next_stratum + '\n        if(dev.nightclient.platform.WorldFeatureBridge.renderAnimatedTabList(graphics,this.tabList))callback.cancel();\n    }' if version >= (26, 1) else ('@org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final private net.minecraft.client.gui.components.PlayerTabOverlay tabList;\n    @Inject(method="renderTabList(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",at=@At("HEAD"),cancellable=true)\n    private void nightclient$animateTabList(GuiGraphics graphics,DeltaTracker delta,CallbackInfo callback){\n        this.nightclient$renderHudOnce(graphics,delta);\n        ' + hud_next_stratum + '\n        if(dev.nightclient.platform.WorldFeatureBridge.renderAnimatedTabList(graphics,this.tabList))callback.cancel();\n    }' if version >= (1, 21) else ('@org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final private net.minecraft.client.gui.components.PlayerTabOverlay tabList;\n    @Inject(method="renderTabList(Lnet/minecraft/client/gui/GuiGraphics;F)V",at=@At("HEAD"),cancellable=true)\n    private void nightclient$animateTabList(GuiGraphics graphics,float delta,CallbackInfo callback){\n        this.nightclient$renderHudOnce(graphics,delta);\n        ' + hud_next_stratum + '\n        if(dev.nightclient.platform.WorldFeatureBridge.renderAnimatedTabList(graphics,this.tabList))callback.cancel();\n    }' if version >= (1, 20, 5) else '@org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final private net.minecraft.client.gui.components.PlayerTabOverlay tabList;\n    @org.spongepowered.asm.mixin.injection.Redirect(method="render(Lnet/minecraft/client/gui/GuiGraphics;F)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/components/PlayerTabOverlay;render(Lnet/minecraft/client/gui/GuiGraphics;ILnet/minecraft/world/scores/Scoreboard;Lnet/minecraft/world/scores/Objective;)V"))\n    private void nightclient$skipVanillaTabList(net.minecraft.client.gui.components.PlayerTabOverlay overlay,GuiGraphics graphics,int width,net.minecraft.world.scores.Scoreboard scoreboard,net.minecraft.world.scores.Objective objective){\n        this.nightclient$renderHudOnce(graphics,this.nightclient$lastDelta);\n        if(!dev.nightclient.platform.WorldFeatureBridge.isTabAnimationEnabled())overlay.render(graphics,width,scoreboard,objective);\n    }'))), 'HUD_LEGACY_TAB_CALL': 'dev.nightclient.platform.WorldFeatureBridge.renderAnimatedTabList(graphics,this.tabList);' if version < (1, 20, 5) else '', 'HUD_IMPORTS': 'import ' + context_import + ';', 'HUD_DELTA_TYPE': p['hudDelta'], 'HUD_DELTA': 'delta.getGameTimeDeltaPartialTick(false)' if p['hudDelta'] == 'DeltaTracker' else 'delta', 'GUI_STATE_PACKAGE': 'net.minecraft.client.renderer.state.gui' if native else 'net.minecraft.client.gui.render.state', 'SUBMIT_GUI': 'addGuiElement' if native else 'submitGuiElement', 'PIPELINE_PACKAGE': 'com.mojang.renderpearl.api.pipeline' if sdl else 'com.mojang.blaze3d.pipeline', 'TEXTURE_PACKAGE': 'com.mojang.renderpearl.api.textures' if sdl else 'com.mojang.blaze3d.textures', 'BACKDROP_TEXTURE_SETUP': 'TextureSetup.singleTexture(view,RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))' if version >= (1, 21, 11) else 'TextureSetup.singleTexture(view)', 'MATERIAL_TEXTURE_SETUP': 'TextureSetup.singleTexture(texture.getTextureView(),RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR))' if version >= (1, 21, 11) else 'TextureSetup.singleTexture(texture.getTextureView())', 'MATERIAL_SAMPLER_SETUP': '' if version >= (1, 21, 11) else 'texture.setClamp(false);texture.setFilter(true,false);', 'HOTBAR_RESOURCE': 'minecraft:textures/gui/sprites/hud/hotbar.png' if version >= (1, 20, 2) else 'minecraft:textures/gui/widgets.png', 'HOTBAR_ATLAS_WIDTH': '182' if version >= (1, 20, 2) else '256', 'HOTBAR_ATLAS_HEIGHT': '22' if version >= (1, 20, 2) else '256', 'EFFECT_BG_RESOURCE': 'minecraft:textures/gui/sprites/hud/effect_background.png' if version >= (1, 20, 2) else 'minecraft:textures/gui/container/inventory.png', 'EFFECT_BG_U': '0' if version >= (1, 20, 2) else '141', 'EFFECT_BG_V': '0' if version >= (1, 20, 2) else '166', 'EFFECT_BG_ATLAS_WIDTH': '24' if version >= (1, 20, 2) else '256', 'EFFECT_BG_ATLAS_HEIGHT': '24' if version >= (1, 20, 2) else '256', 'SPRITE_TEXTURE_SETUP': 'TextureSetup.singleTexture(texture.getTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST))' if version >= (1, 21, 11) else 'TextureSetup.singleTexture(texture.getTextureView())', 'SPRITE_SAMPLER_SETUP': '' if version >= (1, 21, 11) else 'texture.setClamp(true);texture.setFilter(false,false);', 'NATIVE_TEXTURE_LOAD': '', 'NATIVE_TEXTURE_ID': '((GlTexture)Minecraft.getInstance().getTextureManager().getTexture(GameBridge.identifier(id)).getTexture()).glId()' if v == '1.21.5' else 'Minecraft.getInstance().getTextureManager().getTexture(GameBridge.identifier(id)).getId()', 'FONT_TEXTURE_SETUP': 'TextureSetup.singleTexture(texture.getTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))' if version >= (1, 21, 11) else 'TextureSetup.singleTexture(texture.getTextureView())', 'FONT_SAMPLER_SETUP': '' if version >= (1, 21, 11) else 'texture.setClamp(true);texture.setFilter(true,false);', 'HEAD_TEXTURE_SETUP': 'TextureSetup.singleTexture(texture.getTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST))' if version >= (1, 21, 11) else 'TextureSetup.singleTexture(texture.getTextureView())', 'HEAD_SAMPLER_SETUP': '' if version >= (1, 21, 11) else 'texture.setClamp(true);texture.setFilter(false,false);', 'BUILD_DEPTH_ARGUMENT': ',float depth' if (1, 21, 6) <= version <= (1, 21, 8) else '', 'BUILD_DEPTH': 'depth' if (1, 21, 6) <= version <= (1, 21, 8) else '0', 'ITEM_METHOD': 'item' if native else 'renderItem', 'BLIT_POSE_TYPE': 'Matrix3x2fc' if version >= (26, 2) else 'Matrix3x2f', 'F1_HUD_FALLBACK': '', 'LEGACY_BEGIN': 'LegacyUiRenderer.begin(width,height);', 'INVALIDATE_NATIVE_BUFFERS': '', 'SYNC_NATIVE_BLEND': '', 'NO_WORLD_MENU_CLEAR': '', 'NO_WORLD_MENU_CLEAR_METHOD': '', 'NATIVE_TEXTURE_COUNT': '12' if v == '1.21.5' else 'GlStateManager.TEXTURE_COUNT', 'INVERSE_PIPELINE': 'private static final RenderPipeline INVERSE=RenderPipelines.GUI_INVERT;', 'VANILLA_GUI_BLUR': '', 'SCREEN_BACKGROUND_OVERRIDE': '', 'VANILLA_GUI_BLUR_FALLBACK': '', 'FALLBACK_GUI_BLUR_NEEDED': '!runtime.isViewmodelEditing() && runtime.settings().menu.vanillaBlur && runtime.settings().menu.vanillaBlurRadius>0' if version < (1, 20, 5) else 'false'}
    if version < (1, 20, 2):
        preview_skin = 'mc.player.getSkinTextureLocation()'
        preview_cape = 'mc.player.getCloakTextureLocation()'
        preview_slim = '"slim".equals(mc.player.getModelName())'
    elif version < (1, 21, 9):
        preview_skin = 'mc.player.getSkin().texture()'
        preview_cape = 'mc.player.getSkin().capeTexture()'
        preview_slim = 'mc.player.getSkin().model()==net.minecraft.client.resources.PlayerSkin.Model.SLIM'
    else:
        preview_skin = 'mc.player.getSkin().body().texturePath()'
        preview_cape = 'mc.player.getSkin().cape()==null?null:mc.player.getSkin().cape().texturePath()'
        preview_slim = 'mc.player.getSkin().model()==net.minecraft.world.entity.player.PlayerModelType.SLIM'
    if version >= (1, 21, 2):
        potion_variant = 'if("strong_healing".equals(variant))stack.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,new net.minecraft.world.item.alchemy.PotionContents(net.minecraft.world.item.alchemy.Potions.STRONG_HEALING));else if("long_turtle_master".equals(variant))stack.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,new net.minecraft.world.item.alchemy.PotionContents(net.minecraft.world.item.alchemy.Potions.LONG_TURTLE_MASTER));else if(variant.startsWith("color:")){int rgb=Integer.parseInt(variant.substring(6));stack.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,new net.minecraft.world.item.alchemy.PotionContents(java.util.Optional.of(net.minecraft.world.item.alchemy.Potions.STRONG_HEALING),java.util.Optional.of(rgb),java.util.Collections.<net.minecraft.world.effect.MobEffectInstance>emptyList(),java.util.Optional.<String>empty()));}'
    elif version >= (1, 20, 5):
        potion_variant = 'if("strong_healing".equals(variant))stack.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,new net.minecraft.world.item.alchemy.PotionContents(net.minecraft.world.item.alchemy.Potions.STRONG_HEALING));else if("long_turtle_master".equals(variant))stack.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,new net.minecraft.world.item.alchemy.PotionContents(net.minecraft.world.item.alchemy.Potions.LONG_TURTLE_MASTER));else if(variant.startsWith("color:")){int rgb=Integer.parseInt(variant.substring(6));stack.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,new net.minecraft.world.item.alchemy.PotionContents(java.util.Optional.of(net.minecraft.world.item.alchemy.Potions.STRONG_HEALING),java.util.Optional.of(rgb),java.util.Collections.<net.minecraft.world.effect.MobEffectInstance>emptyList()));}'
    else:
        potion_variant = 'if("strong_healing".equals(variant))net.minecraft.world.item.alchemy.PotionUtils.setPotion(stack,net.minecraft.world.item.alchemy.Potions.STRONG_HEALING);else if("long_turtle_master".equals(variant))net.minecraft.world.item.alchemy.PotionUtils.setPotion(stack,net.minecraft.world.item.alchemy.Potions.LONG_TURTLE_MASTER);else if(variant.startsWith("color:"))stack.getOrCreateTag().putInt("CustomPotionColor",Integer.parseInt(variant.substring(6)));'
    values.update({
        'WORLD_MAP_KEY_CONSTRUCTION': 'new KeyMapping("key.nightclient.worldMap",' + ('InputConstants.KEY_M' if sdl else 'GLFW.GLFW_KEY_M') + ',keyCategory)',
        'MAP_SETTINGS_KEY_CONSTRUCTION': 'new KeyMapping("key.nightclient.mapSettings",' + ('InputConstants.KEY_Y' if sdl else 'GLFW.GLFW_KEY_Y') + ',keyCategory)',
        'MAP_ENLARGE_KEY_CONSTRUCTION': 'new KeyMapping("key.nightclient.mapEnlarge",' + ('InputConstants.KEY_Z' if sdl else 'GLFW.GLFW_KEY_Z') + ',keyCategory)',
        'WAYPOINTS_MENU_KEY_CONSTRUCTION': 'new KeyMapping("key.nightclient.waypointsMenu",' + ('InputConstants.KEY_U' if sdl else 'GLFW.GLFW_KEY_U') + ',keyCategory)',
        'SCREEN_ACCESS_ENLARGE': 'mcEnlarge.gui.screen()' if version >= (26, 2) else 'mcEnlarge.screen',
        'SET_MAP_SCREEN_OPEN': 'mc.gui.setScreen(new NightMapScreen())' if version >= (26, 2) else 'mc.setScreen(new NightMapScreen())',
        'SET_MAP_SETTINGS_SCREEN_OPEN': 'mc.gui.setScreen(new NightMapSettingsScreen())' if version >= (26, 2) else 'mc.setScreen(new NightMapSettingsScreen())',
        'SET_WAYPOINTS_SCREEN_OPEN': 'mc.gui.setScreen(new NightWaypointsScreen())' if version >= (26, 2) else 'mc.setScreen(new NightWaypointsScreen())',
        'SET_WAYPOINTS_CREATE_SCREEN_OPEN': 'mc.gui.setScreen(NightWaypointsScreen.forCreate(parent, px, py, pz))' if version >= (26, 2) else 'mc.setScreen(NightWaypointsScreen.forCreate(parent, px, py, pz))',
        'MAP_NEAREST_TEXTURE_SETUP': 'TextureSetup.singleTexture(texture.getTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST))' if version >= (1, 21, 11) else 'TextureSetup.singleTexture(texture.getTextureView())',
        'MAP_NEAREST_SAMPLER_SETUP': '' if version >= (1, 21, 11) else 'texture.setClamp(true);texture.setFilter(false,false);',
        'MAP_SCREEN_BACKGROUND_OVERRIDE': '',
        'APPLY_POTION_VARIANT': potion_variant,
        'PREVIEW_SKIN_ID': preview_skin,
        'PREVIEW_CAPE_ID': preview_cape,
        'PREVIEW_SLIM': preview_slim,
        'PREVIEW_TEXTURE_SETUP': 'TextureSetup.singleTexture(texture.getTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))' if version >= (1, 21, 11) else 'TextureSetup.singleTexture(texture.getTextureView())',
        'PREVIEW_SAMPLER_SETUP': '' if version >= (1, 21, 11) else 'texture.setClamp(true);texture.setFilter(true,false);',
    })
    rounded_shader = 'pearl' if sdl else 'vulkan' if version >= (26, 2) else 'ubo'
    rounded_builder = '.withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.PROJECTION).withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.DYNAMIC_TRANSFORMS)' if version >= (26, 2) else '.withUniform("DynamicTransforms",com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER).withUniform("Projection",com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)'
    if version >= (26, 1):
        pkg = values['PIPELINE_PACKAGE']
        rounded_builder += '.withColorTargetState(new ' + pkg + '.ColorTargetState(' + pkg + '.BlendFunction.TRANSLUCENT))'
    else:
        rounded_builder += '.withBlend(com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT)'
    if version >= (26, 2):
        topology_pkg = values['PIPELINE_PACKAGE'] if sdl else 'com.mojang.blaze3d'
        rounded_builder += '.withVertexBinding(0,com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR).withPrimitiveTopology(' + topology_pkg + '.PrimitiveTopology.QUADS)'
    else:
        rounded_builder += '.withVertexFormat(com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR,com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS)'
    values['ROUNDED_PIPELINE'] = 'private static final RenderPipeline ROUNDED=RenderPipeline.builder().withLocation(GameBridge.identifier("nightclient:pipeline/rounded_surface")).withVertexShader("core/position_tex_color").withFragmentShader(GameBridge.identifier("nightclient:core/rounded_surface_' + rounded_shader + '"))' + rounded_builder + '.withCull(false).build();'

    if version >= (26, 3):
        values['ROUNDED_PREWARM'] = 'com.mojang.blaze3d.systems.RenderSystem.getCompiledPipeline(ROUNDED);'
        values['INVERSE_PREWARM'] = 'com.mojang.blaze3d.systems.RenderSystem.getCompiledPipeline(INVERSE);'
    elif modern:
        values['ROUNDED_PREWARM'] = 'com.mojang.blaze3d.systems.RenderSystem.getDevice().precompilePipeline(ROUNDED);'
        values['INVERSE_PREWARM'] = 'com.mojang.blaze3d.systems.RenderSystem.getDevice().precompilePipeline(INVERSE);'
    else:
        values['ROUNDED_PREWARM'] = ''
        values['INVERSE_PREWARM'] = ''
    if not modern:
        gl_state_owner = 'com.mojang.blaze3d.opengl' if v == '1.21.5' else 'com.mojang.blaze3d.platform'
        values['CANVAS_IMPORTS'] += '\nimport ' + gl_state_owner + '.GlStateManager;\nimport org.lwjgl.opengl.GL11;\nimport org.lwjgl.opengl.GL13;'
    if version <= (1, 21, 4):
        values['CANVAS_IMPORTS'] += '\nimport com.mojang.blaze3d.pipeline.RenderTarget;\nimport java.nio.FloatBuffer;\nimport org.lwjgl.BufferUtils;\nimport org.lwjgl.opengl.GL30;'
        values['NO_WORLD_MENU_CLEAR'] = 'NativeCanvas.clearNoWorldMenu();'
        values['NO_WORLD_MENU_CLEAR_METHOD'] = '\n    private static final FloatBuffer menuClearColor=BufferUtils.createFloatBuffer(4);\n    public static void clearNoWorldMenu(){\n        Minecraft mc=Minecraft.getInstance();if(mc.level!=null)return;\n        RenderTarget target=mc.getMainRenderTarget();if(target.width<=0||target.height<=0)return;\n        menuClearColor.clear();GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE,menuClearColor);\n        LegacyUiRenderer.State state=new LegacyUiRenderer.State();\n        try{\n            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,target.frameBufferId);\n            GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glColorMask(true,true,true,true);\n            GL11.glClearColor(0,0,0,0);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);\n        }finally{\n            GL11.glClearColor(menuClearColor.get(0),menuClearColor.get(1),menuClearColor.get(2),menuClearColor.get(3));\n            state.restore();\n        }\n    }\n    '
    elif v == '1.21.5':
        values['NO_WORLD_MENU_CLEAR'] = 'NativeCanvas.clearNoWorldMenu();'
        values['NO_WORLD_MENU_CLEAR_METHOD'] = '\n    public static void clearNoWorldMenu(){\n        Minecraft mc=Minecraft.getInstance();if(mc.level!=null)return;\n        RenderTarget target=mc.getMainRenderTarget();if(target.width<=0||target.height<=0||target.getColorTexture()==null)return;\n        RenderSystem.getDevice().createCommandEncoder().clearColorTexture(target.getColorTexture(),0);\n    }\n    '
    if v == '1.21.5':
        values['CANVAS_IMPORTS'] += '\nimport org.lwjgl.opengl.GL14;'
        values['SYNC_NATIVE_BLEND'] = '\n        boolean blend=GL11.glIsEnabled(GL11.GL_BLEND);\n        int srcRgb=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dstRgb=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);\n        int srcAlpha=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),dstAlpha=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);\n        GlStateManager._enableBlend();GlStateManager._disableBlend();if(blend)GlStateManager._enableBlend();\n        GlStateManager._blendFuncSeparate(GL11.GL_ONE,GL11.GL_ZERO,GL11.GL_ONE,GL11.GL_ZERO);\n        GlStateManager._blendFuncSeparate(srcRgb,dstRgb,srcAlpha,dstAlpha);\n    '
    if version <= (1, 21, 4):
        values['INVALIDATE_NATIVE_BUFFERS'] = 'com.mojang.blaze3d.vertex.BufferUploader.invalidate();'
    if version < (1, 20, 5):
        values['VANILLA_GUI_BLUR'] = ''
        values['VANILLA_GUI_BLUR_FALLBACK'] = 'if(!ClientRuntime.get().isViewmodelEditing() && ClientRuntime.get().settings().menu.vanillaBlur && ClientRuntime.get().settings().menu.vanillaBlurRadius>0)\n            canvas.backdrop(0,0,width,height,0,ClientRuntime.get().settings().menu.vanillaBlurRadius,2,1);'
    elif version < (1, 21, 2):
        values['VANILLA_GUI_BLUR'] = 'if(!ClientRuntime.get().isViewmodelEditing() && ClientRuntime.get().settings().menu.vanillaBlur)renderBlurredBackground(delta);'
    elif version < (1, 21, 6):
        values['VANILLA_GUI_BLUR'] = 'if(!ClientRuntime.get().isViewmodelEditing() && ClientRuntime.get().settings().menu.vanillaBlur)renderBlurredBackground();'
    else:
        background_method = 'extractBackground' if native else 'renderBackground'
        blur_method = 'extractBlurredBackground' if native else 'renderBlurredBackground'
        subtitles = ''
        if version >= (26, 2):
            subtitles = 'net.minecraft.client.Minecraft.getInstance().gui.hud.extractDeferredSubtitles();'
        elif native:
            subtitles = 'net.minecraft.client.Minecraft.getInstance().gui.extractDeferredSubtitles();'
        elif version >= (1, 21, 9):
            subtitles = 'net.minecraft.client.Minecraft.getInstance().gui.renderDeferredSubtitles();'
        values['SCREEN_BACKGROUND_OVERRIDE'] = '@Override public void %s(%s graphics,int mouseX,int mouseY,float delta){\n        if(parent!=null && net.minecraft.client.Minecraft.getInstance().level==null){super.%s(graphics,mouseX,mouseY,delta);}\n        else if(!ClientRuntime.get().isViewmodelEditing() && ClientRuntime.get().settings().menu.vanillaBlur)%s(graphics);\n        %s\n    }' % (background_method, context, background_method, blur_method, subtitles)
        values['MAP_SCREEN_BACKGROUND_OVERRIDE'] = '@Override public void %s(%s graphics,int mouseX,int mouseY,float delta){%s}' % (background_method, context, subtitles)
    if v == '1.21.6':
        values['INVERSE_PIPELINE'] = 'private static final RenderPipeline INVERSE=RenderPipeline.builder()\n        .withLocation(GameBridge.identifier("nightclient:pipeline/gui_invert"))\n        .withVertexShader("core/gui").withFragmentShader("core/gui")\n        .withUniform("DynamicTransforms",com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)\n        .withUniform("Projection",com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)\n        .withVertexFormat(com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR,com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS)\n        .withBlend(new com.mojang.blaze3d.pipeline.BlendFunction(com.mojang.blaze3d.platform.SourceFactor.ONE_MINUS_DST_COLOR,com.mojang.blaze3d.platform.DestFactor.ONE_MINUS_SRC_COLOR,com.mojang.blaze3d.platform.SourceFactor.ONE,com.mojang.blaze3d.platform.DestFactor.ZERO))\n        .build();'
    if v == '1.21.5':
        values['CANVAS_IMPORTS'] += '\nimport com.mojang.blaze3d.pipeline.RenderTarget;\nimport com.mojang.blaze3d.opengl.GlDevice;\nimport com.mojang.blaze3d.opengl.GlTexture;\nimport com.mojang.blaze3d.systems.RenderSystem;'
        values['LEGACY_BEGIN'] = 'RenderTarget target=Minecraft.getInstance().getMainRenderTarget();\n        int framebuffer=((GlTexture)target.getColorTexture()).getFbo(((GlDevice)RenderSystem.getDevice()).directStateAccess(),target.getDepthTexture());\n        LegacyUiRenderer.begin(width,height,framebuffer,target.width,target.height);'
    if p['hudDelta'] == 'DeltaTracker':
        values['HUD_IMPORTS'] += '\nimport net.minecraft.client.DeltaTracker;'
    if version <= (1, 20, 4):
        hidden_context = 'new PoseStack()' if context == 'PoseStack' else 'new GuiGraphics(mc,mc.renderBuffers().bufferSource())'
        values['F1_HUD_FALLBACK'] = '\n    public static void renderHiddenHud(float partialTick){\n        ClientRuntime runtime=ClientRuntime.get();\n        if(runtime==null || runtime.settings().hud.hideWithGameHud || !runtime.shouldRenderHud())return;\n        Minecraft mc=Minecraft.getInstance();\n        if(!mc.options.hideGui || mc.screen!=null || mc.player==null)return;\n        renderHud(@@HIDDEN_CONTEXT@@,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),partialTick);\n    }'.replace('@@HIDDEN_CONTEXT@@', hidden_context)
    pipeline_path = values['PIPELINE_PACKAGE'].replace('.', '/') + '/RenderPipeline'
    values['BLIT_CTOR'] = '<init>(L' + pipeline_path + ';Lnet/minecraft/client/gui/render/TextureSetup;Lorg/joml/' + values['BLIT_POSE_TYPE'] + ';IIIIFFFFILnet/minecraft/client/gui/navigation/ScreenRectangle;Lnet/minecraft/client/gui/navigation/ScreenRectangle;)V'
    event_inputs = p['input'] == 'events'
    if event_inputs:
        values['SCREEN_IMPORTS'] += '\nimport net.minecraft.client.input.KeyEvent;\nimport net.minecraft.client.input.MouseButtonEvent;\nimport net.minecraft.client.input.CharacterEvent;'
        key_signature = 'KeyEvent event'
        matches = 'GameBridge.menuKey().matches(event)'
        mouse_matches = 'GameBridge.menuKey().matchesMouse(event)'
        native_key = 'event.key()'
        bind_key = 'InputConstants.getKey(event)' if version >= (1, 21, 9) else 'InputConstants.getKey(event.key(),event.scancode())'
        bind_mouse = 'InputConstants.Type.MOUSE.getOrCreate(event.button())'
        shortcut = 'event.shortcutKey()' if sdl else 'event.key()'
        native_mods = 'event.modifiers()'
        super_key = 'super.keyPressed(event)'
        mouse_click_sig = 'MouseButtonEvent event,boolean doubleClick'
        mouse_release_sig = 'MouseButtonEvent event'
        mouse_drag_sig = 'MouseButtonEvent event,double deltaX,double deltaY'
        mouse_args = 'event.x(),event.y(),GameBridge.mouseButton(event.button())'
        char_method = '@Override public boolean charTyped(CharacterEvent event){boolean handled=ClientRuntime.get().menu().charTyped(event.codepoint());syncTextFocus();return handled;}'
    else:
        key_signature = 'int keyCode,int scanCode,int nativeModifiers'
        matches = 'GameBridge.menuKey().matches(keyCode,scanCode)'
        mouse_matches = 'GameBridge.menuKey().matchesMouse(button)'
        native_key = shortcut = 'keyCode'
        bind_key = 'InputConstants.getKey(keyCode,scanCode)'
        bind_mouse = 'InputConstants.Type.MOUSE.getOrCreate(button)'
        native_mods = 'nativeModifiers'
        super_key = 'super.keyPressed(keyCode,scanCode,nativeModifiers)'
        mouse_click_sig = mouse_release_sig = 'double mouseX,double mouseY,int button'
        mouse_drag_sig = 'double mouseX,double mouseY,int button,double deltaX,double deltaY'
        mouse_args = 'mouseX,mouseY,GameBridge.mouseButton(button)'
        char_method = '@Override public boolean charTyped(char character,int modifiers){\n            if(Character.isHighSurrogate(character)){pendingHighSurrogate=character;return ClientRuntime.get().menu().isTextFocused();}\n            int codepoint=character;\n            if(Character.isLowSurrogate(character)){if(pendingHighSurrogate==0)return false;codepoint=Character.toCodePoint(pendingHighSurrogate,character);}\n            pendingHighSurrogate=0;boolean handled=ClientRuntime.get().menu().charTyped(codepoint);syncTextFocus();return handled;\n        }'
    scroll_signature = 'double mouseX,double mouseY,double horizontal,double vertical' if p['mouseScrollAxes'] == 2 else 'double mouseX,double mouseY,double vertical'
    values['SCROLL_MODIFIER_OWNER'] = 'Minecraft.getInstance()' if event_inputs else 'Screen'
    values['INPUT_METHODS'] = '@Override public boolean keyPressed(@@KEY_SIGNATURE@@){\n        if(ClientRuntime.get().menu().isBindingCapture()){boolean used=ClientRuntime.get().menu().captureBinding(GameBridge.key(@@NATIVE_KEY@@,@@SHORTCUT@@),@@BIND_KEY@@.getName());syncTextFocus();return used;}\n        if(@@MATCHES@@ && !ClientRuntime.get().menu().isTextFocused()){onClose();return true;}\n        boolean handled=ClientRuntime.get().menu().keyPressed(GameBridge.key(@@NATIVE_KEY@@,@@SHORTCUT@@),GameBridge.modifiers(@@NATIVE_MODS@@));\n        syncTextFocus();return handled || @@SUPER_KEY@@;\n    }\n    @Override public boolean keyReleased(@@KEY_SIGNATURE@@){return ClientRuntime.get().menu().keyReleased(GameBridge.key(@@NATIVE_KEY@@,@@SHORTCUT@@),GameBridge.modifiers(@@NATIVE_MODS@@));}\n    @Override public boolean mouseClicked(@@CLICK_SIG@@){if(ClientRuntime.get().menu().isBindingCapture()){boolean used=ClientRuntime.get().menu().captureBinding(0,@@BIND_MOUSE@@.getName());syncTextFocus();return used;}if(@@MOUSE_MATCHES@@ && !ClientRuntime.get().menu().isTextFocused()){onClose();return true;}boolean handled=ClientRuntime.get().menu().mouseClicked(@@MOUSE_ARGS@@);syncTextFocus();return handled;}\n    @Override public boolean mouseReleased(@@RELEASE_SIG@@){return ClientRuntime.get().menu().mouseReleased(@@MOUSE_ARGS@@);}\n    @Override public boolean mouseDragged(@@DRAG_SIG@@){return ClientRuntime.get().menu().mouseDragged(@@MOUSE_ARGS@@,deltaX,deltaY);}\n    @Override public boolean mouseScrolled(@@SCROLL_SIG@@){return ClientRuntime.get().menu().mouseScrolled(mouseX,mouseY,vertical,GameBridge.scrollModifiers());}\n    @@CHAR_METHOD@@'
    map_matches = 'GameBridge.worldMapKey().matches(event)' if event_inputs else 'GameBridge.worldMapKey().matches(keyCode,scanCode)'
    map_mouse_matches = 'GameBridge.worldMapKey().matchesMouse(event)' if event_inputs else 'GameBridge.worldMapKey().matchesMouse(button)'
    map_settings_matches = 'GameBridge.mapSettingsKey().matches(event)' if event_inputs else 'GameBridge.mapSettingsKey().matches(keyCode,scanCode)'
    map_settings_mouse_matches = 'GameBridge.mapSettingsKey().matchesMouse(event)' if event_inputs else 'GameBridge.mapSettingsKey().matchesMouse(button)'
    waypoints_matches = 'GameBridge.waypointsMenuKey().matches(event)' if event_inputs else 'GameBridge.waypointsMenuKey().matches(keyCode,scanCode)'
    waypoints_mouse_matches = 'GameBridge.waypointsMenuKey().matchesMouse(event)' if event_inputs else 'GameBridge.waypointsMenuKey().matchesMouse(button)'
    wp_char_method = '@Override public boolean charTyped(CharacterEvent event){ClientRuntime rt=ClientRuntime.get();if(rt==null)return false;boolean handled=rt.waypointsMenu().charTyped(event.codepoint());syncTextFocus();return handled;}' if event_inputs else '@Override public boolean charTyped(char character,int modifiers){\n            ClientRuntime rt=ClientRuntime.get();if(rt==null)return false;\n            if(Character.isHighSurrogate(character)){pendingHighSurrogate=character;return rt.waypointsMenu().isTextFocused();}\n            int codepoint=character;\n            if(Character.isLowSurrogate(character)){if(pendingHighSurrogate==0)return false;codepoint=Character.toCodePoint(pendingHighSurrogate,character);}\n            pendingHighSurrogate=0;boolean handled=rt.waypointsMenu().charTyped(codepoint);syncTextFocus();return handled;\n        }'
    values['MAP_INPUT_METHODS'] = '@Override public boolean keyPressed(@@KEY_SIGNATURE@@){\n        ClientRuntime rt=ClientRuntime.get();if(rt==null)return @@SUPER_KEY@@;\n        if(@@MAP_MATCHES@@){onClose();return true;}\n        if(@@MAP_SETTINGS_MATCHES@@){onClose();GameBridge.openMapSettingsScreen();return true;}\n        if(@@WAYPOINTS_MATCHES@@){onClose();GameBridge.openWaypointsMenuScreen();return true;}\n        boolean handled=rt.worldMap().keyPressed(GameBridge.key(@@NATIVE_KEY@@,@@SHORTCUT@@),GameBridge.modifiers(@@NATIVE_MODS@@));\n        return handled || @@SUPER_KEY@@;\n    }\n    @Override public boolean mouseClicked(@@CLICK_SIG@@){ClientRuntime rt=ClientRuntime.get();if(rt==null)return false;if(@@MAP_MOUSE_MATCHES@@){onClose();return true;}if(@@MAP_SETTINGS_MOUSE_MATCHES@@){onClose();GameBridge.openMapSettingsScreen();return true;}if(@@WAYPOINTS_MOUSE_MATCHES@@){onClose();GameBridge.openWaypointsMenuScreen();return true;}return rt.worldMap().mouseClicked(@@MOUSE_ARGS@@);}\n    @Override public boolean mouseReleased(@@RELEASE_SIG@@){ClientRuntime rt=ClientRuntime.get();return rt!=null&&rt.worldMap().mouseReleased(@@MOUSE_ARGS@@);}\n    @Override public boolean mouseDragged(@@DRAG_SIG@@){ClientRuntime rt=ClientRuntime.get();return rt!=null&&rt.worldMap().mouseDragged(@@MOUSE_ARGS@@,deltaX,deltaY);}\n    @Override public boolean mouseScrolled(@@SCROLL_SIG@@){ClientRuntime rt=ClientRuntime.get();return rt!=null&&rt.worldMap().mouseScrolled(mouseX,mouseY,vertical,GameBridge.scrollModifiers());}'
    scroll_call = 'super.mouseScrolled(mouseX, mouseY, horizontal, vertical)' if p['mouseScrollAxes'] == 2 else 'super.mouseScrolled(mouseX, mouseY, vertical)'
    super_click = 'super.mouseClicked(event, doubleClick)' if event_inputs else 'super.mouseClicked(mouseX, mouseY, button)'
    super_release = 'super.mouseReleased(event)' if event_inputs else 'super.mouseReleased(mouseX, mouseY, button)'
    super_drag = 'super.mouseDragged(event, deltaX, deltaY)' if event_inputs else 'super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)'
    click_xy = 'event.x(), event.y()' if event_inputs else 'mouseX, mouseY'
    values['MAP_SETTINGS_INPUT_METHODS'] = (
        '@Override public boolean keyPressed(@@KEY_SIGNATURE@@){\n'
        '        if(isBindingCapture()){return captureBinding(GameBridge.key(@@NATIVE_KEY@@,@@SHORTCUT@@),@@BIND_KEY@@.getName());}\n'
        '        if(@@MAP_SETTINGS_MATCHES@@){onClose();return true;}\n'
        '        if(handleCustomKey(GameBridge.key(@@NATIVE_KEY@@,@@SHORTCUT@@)))return true;\n'
        '        return @@SUPER_KEY@@;\n'
        '    }\n'
        '    @Override public boolean mouseClicked(@@CLICK_SIG@@){\n'
        '        if(isBindingCapture()){return captureBinding(0,@@BIND_MOUSE@@.getName());}\n'
        '        if(@@MAP_SETTINGS_MOUSE_MATCHES@@){onClose();return true;}\n'
        '        if(' + super_click + ')return true;\n'
        '        return handleListClick(@@MOUSE_ARGS@@);\n'
        '    }\n'
        '    @Override public boolean mouseReleased(@@RELEASE_SIG@@){\n'
        '        if(handleListRelease(@@MOUSE_ARGS@@))return true;\n'
        '        return ' + super_release + ';\n'
        '    }\n'
        '    @Override public boolean mouseDragged(@@DRAG_SIG@@){\n'
        '        if(handleListDrag(@@MOUSE_ARGS@@))return true;\n'
        '        return ' + super_drag + ';\n'
        '    }\n'
        '    @Override public boolean mouseScrolled(@@SCROLL_SIG@@){\n'
        '        if(handleListScroll(mouseX,mouseY,vertical))return true;\n'
        '        return ' + scroll_call + ';\n'
        '    }'
    )
    values['WAYPOINTS_INPUT_METHODS'] = (
        '@Override public boolean keyPressed(@@KEY_SIGNATURE@@){\n'
        '        if(@@WAYPOINTS_MATCHES@@ && !isTextFocused()){onClose();return true;}\n'
        '        if(handleCustomKey(GameBridge.key(@@NATIVE_KEY@@,@@SHORTCUT@@)))return true;\n'
        '        return @@SUPER_KEY@@;\n'
        '    }\n'
        '    @Override public boolean mouseClicked(@@CLICK_SIG@@){\n'
        '        syncFormBoxFocusOnClick(' + click_xy + ');\n'
        '        if(@@WAYPOINTS_MOUSE_MATCHES@@ && !isTextFocused()){onClose();return true;}\n'
        '        if(' + super_click + ')return true;\n'
        '        return handleListClick(@@MOUSE_ARGS@@);\n'
        '    }\n'
        '    @Override public boolean mouseScrolled(@@SCROLL_SIG@@){\n'
        '        if(handleListScroll(mouseX,mouseY,vertical))return true;\n'
        '        return ' + scroll_call + ';\n'
        '    }'
    )
    for key, value in {'KEY_SIGNATURE': key_signature, 'MATCHES': matches, 'MAP_MATCHES': map_matches, 'MAP_MOUSE_MATCHES': map_mouse_matches, 'MAP_SETTINGS_MATCHES': map_settings_matches, 'MAP_SETTINGS_MOUSE_MATCHES': map_settings_mouse_matches, 'WAYPOINTS_MATCHES': waypoints_matches, 'WAYPOINTS_MOUSE_MATCHES': waypoints_mouse_matches, 'MOUSE_MATCHES': mouse_matches, 'NATIVE_KEY': native_key, 'SHORTCUT': shortcut, 'NATIVE_MODS': native_mods, 'SUPER_KEY': super_key, 'CLICK_SIG': mouse_click_sig, 'RELEASE_SIG': mouse_release_sig, 'DRAG_SIG': mouse_drag_sig, 'MOUSE_ARGS': mouse_args, 'SCROLL_SIG': scroll_signature, 'CHAR_METHOD': char_method, 'WP_CHAR_METHOD': wp_char_method, 'BIND_KEY': bind_key, 'BIND_MOUSE': bind_mouse}.items():
        values['INPUT_METHODS'] = values['INPUT_METHODS'].replace('@@' + key + '@@', value)
        values['MAP_INPUT_METHODS'] = values['MAP_INPUT_METHODS'].replace('@@' + key + '@@', value)
        values['MAP_SETTINGS_INPUT_METHODS'] = values['MAP_SETTINGS_INPUT_METHODS'].replace('@@' + key + '@@', value)
        values['WAYPOINTS_INPUT_METHODS'] = values['WAYPOINTS_INPUT_METHODS'].replace('@@' + key + '@@', value)
    legacy_item = ''
    if not modern:
        legacy_item = 'graphics.pose().pushPose();try{graphics.pose().translate(x,y,0);graphics.pose().scale(size/16,size/16,1);graphics.renderItem(stack,0,0);graphics.flush();}finally{graphics.pose().popPose();}'
    values['LEGACY_ITEM'] = legacy_item
    if modern:
        values['GAME_TEXT'] = 'graphics.' + ('text' if native else 'drawString') + '(Minecraft.getInstance().font,value,0,0,argb,shadow);'
    else:
        values['GAME_TEXT'] = 'graphics.pose().pushPose();try{graphics.pose().translate(x,y,0);graphics.pose().scale(scale,scale,1);graphics.drawString(Minecraft.getInstance().font,value,0,0,argb,shadow);graphics.flush();}finally{graphics.pose().popPose();}'
    values['SET_SCREEN_PARENT_OPEN'] = 'mc.gui.setScreen(new NightScreen(parent))' if version >= (26, 2) else 'mc.setScreen(new NightScreen(parent))'
    values['SET_SCREEN_CLOSE_PARENT'] = 'mc.gui.setScreen(parent)' if version >= (26, 2) else 'mc.setScreen(parent)'
    values['PARENT_NO_WORLD_BACKGROUND'] = ''

    acc_imports = []
    acc_methods = []
    sb_imports = []
    sb_init_fields = []
    sb_capture = []
    sb_apply_offline = []
    sb_restore = []

    if version <= (1, 17, 1):
        acc_imports += ['import com.mojang.authlib.minecraft.SocialInteractionsService;',
                        'import com.mojang.authlib.properties.PropertyMap;']
        acc_methods += [
            '@Accessor("socialInteractionsService") SocialInteractionsService nightclient$social();',
            '@Mutable @Accessor("socialInteractionsService") void nightclient$setSocial(SocialInteractionsService value);',
            '@Accessor("profileProperties") PropertyMap nightclient$profileProperties();',
            '@Mutable @Accessor("profileProperties") void nightclient$setProfileProperties(PropertyMap value);',
        ]
        sb_imports += ['import com.mojang.authlib.minecraft.OfflineSocialInteractions;',
                       'import com.mojang.authlib.minecraft.SocialInteractionsService;',
                       'import com.mojang.authlib.properties.PropertyMap;']
        sb_init_fields += ['private static SocialInteractionsService initialSocial;',
                           'private static PropertyMap initialProfileProperties;']
        sb_capture += ['initialSocial = acc.nightclient$social();',
                       'initialProfileProperties = acc.nightclient$profileProperties();']
        sb_apply_offline += ['acc.nightclient$setSocial(new OfflineSocialInteractions());',
                             'acc.nightclient$setProfileProperties(new PropertyMap());']
        sb_restore += ['if (initialSocial != null) acc.nightclient$setSocial(initialSocial);',
                       'if (initialProfileProperties != null) acc.nightclient$setProfileProperties(initialProfileProperties);']
    else:
        acc_imports += ['import com.mojang.authlib.minecraft.UserApiService;']
        acc_methods += [
            '@Accessor("userApiService") UserApiService nightclient$userApiService();',
            '@Mutable @Accessor("userApiService") void nightclient$setUserApiService(UserApiService value);',
        ]
        sb_imports += ['import com.mojang.authlib.minecraft.UserApiService;']
        sb_init_fields += ['private static UserApiService initialUserApi;']
        sb_capture += ['initialUserApi = acc.nightclient$userApiService();']
        sb_apply_offline += ['acc.nightclient$setUserApiService(UserApiService.OFFLINE);']
        sb_restore += ['if (initialUserApi != null) acc.nightclient$setUserApiService(initialUserApi);']

        if version >= (1, 19):
            acc_imports += ['import net.minecraft.client.multiplayer.ProfileKeyPairManager;']
            acc_methods += [
                '@Accessor("profileKeyPairManager") ProfileKeyPairManager nightclient$profileKeyPairManager();',
                '@Mutable @Accessor("profileKeyPairManager") void nightclient$setProfileKeyPairManager(ProfileKeyPairManager value);',
            ]
            sb_imports += ['import net.minecraft.client.multiplayer.ProfileKeyPairManager;']
            sb_init_fields += ['private static ProfileKeyPairManager initialKeyPair;']
            sb_capture += ['initialKeyPair = acc.nightclient$profileKeyPairManager();']
            if version >= (1, 19, 3):
                sb_apply_offline += ['acc.nightclient$setProfileKeyPairManager(ProfileKeyPairManager.EMPTY_KEY_MANAGER);']
            else:
                sb_apply_offline += ['acc.nightclient$setProfileKeyPairManager(new ProfileKeyPairManager(UserApiService.OFFLINE, uuid, mc.gameDirectory.toPath()));']
            sb_restore += ['if (initialKeyPair != null) acc.nightclient$setProfileKeyPairManager(initialKeyPair);']

        if version >= (1, 19, 1):
            acc_imports += ['import net.minecraft.client.multiplayer.chat.report.ReportingContext;']
            acc_methods += [
                '@Accessor("reportingContext") ReportingContext nightclient$reportingContext();',
                '@Mutable @Accessor("reportingContext") void nightclient$setReportingContext(ReportingContext value);',
            ]
            sb_imports += ['import net.minecraft.client.multiplayer.chat.report.ReportEnvironment;',
                           'import net.minecraft.client.multiplayer.chat.report.ReportingContext;']
            sb_init_fields += ['private static ReportingContext initialReporting;']
            sb_capture += ['initialReporting = acc.nightclient$reportingContext();']
            sb_apply_offline += ['acc.nightclient$setReportingContext(ReportingContext.create(ReportEnvironment.local(), UserApiService.OFFLINE));']
            sb_restore += ['if (initialReporting != null) acc.nightclient$setReportingContext(initialReporting);']

        if version <= (1, 20, 1):
            acc_imports += ['import com.mojang.authlib.properties.PropertyMap;']
            acc_methods += [
                '@Accessor("profileProperties") PropertyMap nightclient$profileProperties();',
                '@Mutable @Accessor("profileProperties") void nightclient$setProfileProperties(PropertyMap value);',
            ]
            sb_imports += ['import com.mojang.authlib.properties.PropertyMap;']
            sb_init_fields += ['private static PropertyMap initialProfileProperties;']
            sb_capture += ['initialProfileProperties = acc.nightclient$profileProperties();']
            sb_apply_offline += ['acc.nightclient$setProfileProperties(new PropertyMap());']
            sb_restore += ['if (initialProfileProperties != null) acc.nightclient$setProfileProperties(initialProfileProperties);']
        else:
            pr_pkg = 'com.mojang.authlib.services' if version >= (26, 3) else 'com.mojang.authlib.yggdrasil'
            acc_imports += ['import java.util.concurrent.CompletableFuture;',
                            'import ' + pr_pkg + '.ProfileResult;']
            acc_methods += [
                '@Accessor("profileFuture") CompletableFuture<ProfileResult> nightclient$profileFuture();',
                '@Mutable @Accessor("profileFuture") void nightclient$setProfileFuture(CompletableFuture<ProfileResult> value);',
            ]
            sb_imports += ['import com.mojang.authlib.GameProfile;',
                           'import java.util.concurrent.CompletableFuture;',
                           'import ' + pr_pkg + '.ProfileResult;']
            sb_init_fields += ['private static CompletableFuture<ProfileResult> initialProfileFuture;']
            sb_capture += ['initialProfileFuture = acc.nightclient$profileFuture();']
            sb_apply_offline += ['acc.nightclient$setProfileFuture(CompletableFuture.completedFuture(new ProfileResult(new GameProfile(uuid, name))));']
            sb_restore += ['if (initialProfileFuture != null) acc.nightclient$setProfileFuture(initialProfileFuture);']

        if version >= (1, 20, 3):
            acc_methods += [
                '@Accessor("userPropertiesFuture") CompletableFuture<UserApiService.UserProperties> nightclient$userPropertiesFuture();',
                '@Mutable @Accessor("userPropertiesFuture") void nightclient$setUserPropertiesFuture(CompletableFuture<UserApiService.UserProperties> value);',
            ]
            sb_init_fields += ['private static CompletableFuture<UserApiService.UserProperties> initialUserPropertiesFuture;']
            sb_capture += ['initialUserPropertiesFuture = acc.nightclient$userPropertiesFuture();']
            sb_apply_offline += ['acc.nightclient$setUserPropertiesFuture(CompletableFuture.completedFuture(UserApiService.OFFLINE_PROPERTIES));']
            sb_restore += ['if (initialUserPropertiesFuture != null) acc.nightclient$setUserPropertiesFuture(initialUserPropertiesFuture);']

    if version <= (1, 17, 1):
        create_offline_user = 'new User(name, SavedAccount.undashedUuid(uuid), "0", "legacy")'
    elif version <= (1, 20, 1):
        create_offline_user = 'new User(name, SavedAccount.undashedUuid(uuid), "0", Optional.<String>empty(), Optional.<String>empty(), User.Type.LEGACY)'
    elif version <= (1, 21, 8):
        create_offline_user = 'new User(name, uuid, "0", Optional.<String>empty(), Optional.<String>empty(), User.Type.LEGACY)'
    else:
        create_offline_user = 'new User(name, uuid, "0", Optional.<String>empty(), Optional.<String>empty())'

    if version == (1, 16, 5):
        disconnect_world = 'mc.level.disconnect(); mc.clearLevel();'
        start_connecting = 'mc.setScreen(new ConnectScreen(parent, mc, server));'
    elif version <= (1, 19, 4):
        sb_imports += ['import net.minecraft.client.multiplayer.resolver.ServerAddress;']
        disconnect_world = 'mc.level.disconnect(); mc.clearLevel();'
        start_connecting = 'ConnectScreen.startConnecting(parent, mc, ServerAddress.parseString(server.ip), server);'
    elif version <= (1, 20, 1):
        sb_imports += ['import net.minecraft.client.multiplayer.resolver.ServerAddress;']
        disconnect_world = 'mc.level.disconnect(); mc.clearLevel();'
        start_connecting = 'ConnectScreen.startConnecting(parent, mc, ServerAddress.parseString(server.ip), server, false);'
    elif version <= (1, 20, 4):
        sb_imports += ['import net.minecraft.client.multiplayer.resolver.ServerAddress;']
        disconnect_world = 'mc.level.disconnect(); mc.disconnect();'
        start_connecting = 'ConnectScreen.startConnecting(parent, mc, ServerAddress.parseString(server.ip), server, false);'
    elif version <= (1, 21, 5):
        sb_imports += ['import net.minecraft.client.multiplayer.resolver.ServerAddress;']
        disconnect_world = 'mc.level.disconnect(); mc.disconnect();'
        start_connecting = 'ConnectScreen.startConnecting(parent, mc, ServerAddress.parseString(server.ip), server, false, null);'
    elif version <= (1, 21, 8):
        sb_imports += ['import net.minecraft.client.multiplayer.resolver.ServerAddress;',
                       'import net.minecraft.network.chat.Component;']
        disconnect_world = 'mc.level.disconnect(Component.translatable("multiplayer.status.quitting")); mc.disconnectWithProgressScreen();'
        start_connecting = 'ConnectScreen.startConnecting(parent, mc, ServerAddress.parseString(server.ip), server, false, null);'
    else:
        sb_imports += ['import net.minecraft.client.multiplayer.resolver.ServerAddress;',
                       'import net.minecraft.network.chat.Component;']
        disconnect_world = 'mc.disconnectFromWorld(Component.translatable("multiplayer.status.quitting"));'
        start_connecting = 'ConnectScreen.startConnecting(parent, mc, ServerAddress.parseString(server.ip), server, false, null);'

    values['SESSION_ACCESSOR_IMPORTS'] = '\n'.join(dict.fromkeys(acc_imports))
    values['SESSION_ACCESSOR_METHODS'] = '\n    '.join(acc_methods)
    values['SESSION_BRIDGE_IMPORTS'] = '\n'.join(dict.fromkeys(sb_imports))
    values['SESSION_INITIAL_FIELDS'] = '\n    '.join(sb_init_fields)
    values['SESSION_CAPTURE_INITIAL'] = '\n        '.join(sb_capture)
    values['SESSION_CREATE_OFFLINE_USER'] = create_offline_user
    values['SESSION_APPLY_OFFLINE_SERVICES'] = '\n                '.join(sb_apply_offline)
    values['SESSION_RESTORE_INITIAL_SERVICES'] = '\n                '.join(sb_restore)
    values['SESSION_DISCONNECT_WORLD'] = disconnect_world
    values['SESSION_START_CONNECTING'] = start_connecting
    values['SET_VANILLA_ACCOUNT_OPEN'] = 'mc.gui.setScreen(new NightVanillaAccountScreen(parent))' if version >= (26, 2) else 'mc.setScreen(new NightVanillaAccountScreen(parent))'
    values['VANILLA_SCREEN_IMPORTS'] = 'import net.minecraft.client.gui.GuiComponent;' if version <= (1, 19, 4) else ''
    values['VANILLA_COMPONENT'] = 'Component.literal(text == null ? "" : text)' if version >= (1, 19) else 'new net.minecraft.network.chat.TextComponent(text == null ? "" : text)'
    values['VANILLA_EMPTY_COMPONENT'] = 'Component.empty()' if version >= (1, 19) else 'new net.minecraft.network.chat.TextComponent("")'
    values['VANILLA_ADD_WIDGET'] = 'this.addButton(w)' if version == (1, 16, 5) else 'this.addRenderableWidget(w)'
    values['VANILLA_CLEAR_WIDGETS'] = 'this.buttons.clear(); this.children.clear();' if version == (1, 16, 5) else 'this.clearWidgets();'
    values['VANILLA_MAKE_BUTTON'] = 'new Button(x, y, w, h, label, onPress)' if version <= (1, 19, 2) else 'Button.builder(label, onPress).bounds(x, y, w, h).build()'
    values['VANILLA_EDITBOX_SET_FOCUS'] = 'box.setFocus(focused);' if version <= (1, 19, 3) else 'box.setFocused(focused);'
    values['VANILLA_EDITBOX_TICK'] = 'if (searchBox != null) searchBox.tick(); if (nameBox != null) nameBox.tick(); if (noteBox != null) noteBox.tick(); if (skinBox != null) skinBox.tick();' if version <= (1, 20, 1) else ''
    values['VANILLA_WP_EDITBOX_TICK'] = 'if (searchBox != null) searchBox.tick(); if (nameBox != null) nameBox.tick(); if (xBox != null) xBox.tick(); if (yBox != null) yBox.tick(); if (zBox != null) zBox.tick();' if version <= (1, 20, 1) else ''
    values['VANILLA_RENDER_BG'] = 'this.renderBackground(graphics);' if version <= (1, 20, 1) else ''
    values['VANILLA_FILL'] = 'GuiComponent.fill(graphics, x1, y1, x2, y2, color);' if version <= (1, 19, 4) else 'graphics.fill(x1, y1, x2, y2, color);'
    if version <= (1, 19, 4):
        values['VANILLA_TEXT_STATIC'] = 'GuiComponent.drawString(graphics, font, text, x, y, color);'
        values['VANILLA_CENTERED_TEXT_STATIC'] = 'GuiComponent.drawCenteredString(graphics, font, text, x, y, color);'
    elif version < (26, 1):
        values['VANILLA_TEXT_STATIC'] = 'graphics.drawString(font, text, x, y, color);'
        values['VANILLA_CENTERED_TEXT_STATIC'] = 'graphics.drawCenteredString(font, text, x, y, color);'
    else:
        values['VANILLA_TEXT_STATIC'] = 'graphics.text(font, text, x, y, color);'
        values['VANILLA_CENTERED_TEXT_STATIC'] = 'graphics.centeredText(font, text, x, y, color);'
    values['WIDGET_GET_X_BOX'] = 'box.x' if version <= (1, 19, 2) else 'box.getX()'
    values['WIDGET_GET_Y_BOX'] = 'box.y' if version <= (1, 19, 2) else 'box.getY()'
    values['WIDGET_GET_X_W'] = 'w.x' if version <= (1, 19, 2) else 'w.getX()'
    values['WIDGET_GET_Y_W'] = 'w.y' if version <= (1, 19, 2) else 'w.getY()'
    values['BTN_SET_POS'] = (
        'nightclient$accountBtn.x = nightclient$classicPlacement.x; nightclient$accountBtn.y = nightclient$classicPlacement.y;'
        if version <= (1, 19, 2)
        else 'nightclient$accountBtn.setX(nightclient$classicPlacement.x); nightclient$accountBtn.setY(nightclient$classicPlacement.y);'
    )
    values['MIXIN_ADD_WIDGET_INVOKER'] = (
        '@Invoker("addButton") protected abstract AbstractWidget nightclient$addWidget(AbstractWidget widget);'
        if version == (1, 16, 5)
        else '@Invoker("addRenderableWidget") protected abstract GuiEventListener nightclient$addWidget(GuiEventListener widget);'
    )
    if event_inputs:
        values['VANILLA_INPUT_OVERRIDES'] = (
            '@Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {\n'
            '        syncFormBoxFocusOnClick(event.x(), event.y());\n'
            '        if (super.mouseClicked(event, doubleClick)) return true;\n'
            '        return handleListClick(event.x(), event.y(), GameBridge.mouseButton(event.button()));\n'
            '    }\n'
            '    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {\n'
            '        if (handleListScroll(mouseX, mouseY, vertical)) return true;\n'
            '        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);\n'
            '    }\n'
            '    @Override public boolean keyPressed(KeyEvent event) {\n'
            '        int nk = GameBridge.key(event.key(), ' + shortcut + ');\n'
            '        if (handleCustomKey(nk)) return true;\n'
            '        return super.keyPressed(event);\n'
            '    }'
        )
    else:
        scroll_call = 'super.mouseScrolled(mouseX, mouseY, horizontal, vertical)' if p['mouseScrollAxes'] == 2 else 'super.mouseScrolled(mouseX, mouseY, vertical)'
        values['VANILLA_INPUT_OVERRIDES'] = (
            '@Override public boolean mouseClicked(double mouseX, double mouseY, int button) {\n'
            '        syncFormBoxFocusOnClick(mouseX, mouseY);\n'
            '        if (super.mouseClicked(mouseX, mouseY, button)) return true;\n'
            '        return handleListClick(mouseX, mouseY, button);\n'
            '    }\n'
            '    @Override public boolean mouseScrolled(' + scroll_signature + ') {\n'
            '        if (handleListScroll(mouseX, mouseY, vertical)) return true;\n'
            '        return ' + scroll_call + ';\n'
            '    }\n'
            '    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {\n'
            '        int nk = GameBridge.key(keyCode, keyCode);\n'
            '        if (handleCustomKey(nk)) return true;\n'
            '        return super.keyPressed(keyCode, scanCode, modifiers);\n'
            '    }'
        )

    press_const = 'com.mojang.blaze3d.platform.InputConstants.PRESS' if version >= (1, 17) else '1'
    mouse_click_body = (
        'if (action != ' + press_const + ' || GameBridge.mouseButton(btn) != 0) return;\n'
        '        Minecraft mc = Minecraft.getInstance();\n'
        '        if (mc == null || window != mc.getWindow().handle()) return;\n'
        '        Screen screen = ' + values['SCREEN_ACCESS'] + ';\n'
        '        if (!(screen instanceof TitleScreen) && !(screen instanceof JoinMultiplayerScreen)) return;\n'
        '        ClientRuntime runtime = ClientRuntime.get();\n'
        '        if (runtime == null) return;\n'
        '        if (runtime.settings() != null && runtime.settings().accountSwitcher != null && "classic".equals(runtime.settings().accountSwitcher.layout)) return;\n'
        '        int sw = mc.getWindow().getScreenWidth(), sh = mc.getWindow().getScreenHeight();\n'
        '        if (sw <= 0 || sh <= 0) return;\n'
        '        float mx = (float) (xpos * mc.getWindow().getGuiScaledWidth() / (double) sw);\n'
        '        float my = (float) (ypos * mc.getWindow().getGuiScaledHeight() / (double) sh);\n'
        '        if (runtime.hitScreenAccountOverlay(mx, my)) {\n'
        '            GameBridge.openAccountManager(screen);\n'
        '            callback.cancel();\n'
        '        }'
    )
    if version < (1, 21, 9):
        mouse_click_body = mouse_click_body.replace('mc.getWindow().handle()', 'mc.getWindow().getWindow()')
        values['MOUSE_ACCOUNT_IMPORTS'] = ''
        values['MOUSE_ACCOUNT_INJECT'] = (
            '@Inject(method = "onPress(JIII)V", at = @At("HEAD"), cancellable = true)\n'
            '    private void nightclient$accountOverlayClick(long window, int btn, int action, int mods, CallbackInfo callback) {\n'
            '        ' + mouse_click_body + '\n    }'
        )
    else:
        values['MOUSE_ACCOUNT_IMPORTS'] = 'import net.minecraft.client.input.MouseButtonInfo;'
        values['MOUSE_ACCOUNT_INJECT'] = (
            '@Inject(method = "onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V", at = @At("HEAD"), cancellable = true)\n'
            '    private void nightclient$accountOverlayClick(long window, MouseButtonInfo buttonInfo, int action, CallbackInfo callback) {\n'
            '        int btn = buttonInfo.input();\n'
            '        ' + mouse_click_body + '\n    }'
        )

    files = {'GameBridge.java': 'GameBridge.java.tpl', 'NightScreen.java': 'NightScreen.java.tpl', 'NightVanillaAccountScreen.java': 'NightVanillaAccountScreen.java.tpl', 'NightMapScreen.java': 'NightMapScreen.java.tpl', 'NightMapSettingsScreen.java': 'NightMapSettingsScreen.java.tpl', 'NightWaypointsScreen.java': 'NightWaypointsScreen.java.tpl', 'SessionBridge.java': 'SessionBridge.java.tpl', 'NativeCanvas.java': 'NativeCanvasModern.java.tpl' if modern else 'NativeCanvasLegacy.java.tpl', 'MapTextureBackend.java': 'MapTextureBackendModern.java.tpl' if modern else 'MapTextureBackendLegacy.java.tpl', 'PlayerPreviewSkinReader.java': 'PlayerPreviewSkinReader.java.tpl', 'PlayerPreviewBackend.java': 'PlayerPreviewBackendModern.java.tpl' if modern else 'PlayerPreviewBackendLegacy.java.tpl', 'mixin/HudMixin.java': 'HudMixin.java.tpl', 'mixin/SessionAccessor.java': 'SessionAccessor.java.tpl', 'mixin/ScreenAccountOverlayMixin.java': 'ScreenAccountOverlayMixin.java.tpl', 'mixin/MouseAccountOverlayMixin.java': 'MouseAccountOverlayMixin.java.tpl'}
    if (1, 17) <= version <= (1, 18, 2):
        files['mixin/BufferUploaderCacheAccessor.java'] = 'BufferUploaderCacheAccessor.java.tpl'
    else:
        stale = args.output / 'dev/nightclient/platform/mixin/BufferUploaderCacheAccessor.java'
        if stale.exists():
            stale.unlink()
    if modern:
        files.update({'GuiMeshState.java': 'GuiMeshState.java.tpl', 'GuiTextureMeshState.java': 'GuiTextureMeshState.java.tpl', 'mixin/GuiStateAccessor.java': 'GuiStateAccessor.java.tpl', 'mixin/ItemOpacityCaptureMixin.java': 'ItemOpacityCaptureMixin.java.tpl', 'mixin/ItemOpacityBlitMixin.java': 'ItemOpacityBlitMixin.java.tpl'})
    for name, template in files.items():
        source = (TEMPLATES / template).read_text()
        for key, value in values.items():
            source = source.replace('@@' + key + '@@', value)
        if re.search('@@[A-Z_]+@@', source):
            raise RuntimeError('Unresolved template token in ' + template)
        target = args.output / 'dev/nightclient/platform' / name
        target.parent.mkdir(parents=True, exist_ok=True)
        if not target.exists() or target.read_text() != source:
            target.write_text(source)
    generate_features(v, args.output)
    generate_world_features(v, args.output)
    generate_optimization_features(v, args.output)
    generate_camera_features(v, args.output)
    generate_vanilla_hud(v, args.output)
    generate_atmosphere_features(v, args.output)
    generate_custom_models(v, args.output)
    print('Generated typed bridges for Minecraft ' + v)

def version_key(value):
    return tuple(map(int, value.split('.')))

def native_source(version):
    key = version_key(version)
    scalar = version == '1.21.5'
    needs_vertex_buffer = key < (1, 21, 11)
    pearl = key >= (26, 3)
    layouts = key >= (26, 2)
    color_state = key >= (26, 1)
    sampler_object = key >= (1, 21, 11)
    pipeline_pkg = 'com.mojang.renderpearl.api.pipeline' if pearl else 'com.mojang.blaze3d.pipeline'
    tex_pkg = 'com.mojang.renderpearl.api.textures' if pearl else 'com.mojang.blaze3d.textures'
    buffer_pkg = 'com.mojang.renderpearl.api.buffers' if pearl else 'com.mojang.blaze3d.buffers'
    commands_pkg = 'com.mojang.renderpearl.api.commands' if pearl else 'com.mojang.blaze3d.systems'
    uniform_pkg = pipeline_pkg if pearl else 'com.mojang.blaze3d.shaders'
    shader_suffix = 'scalar' if scalar else 'pearl' if pearl else 'vulkan' if layouts else 'ubo'
    imports = f'import {pipeline_pkg}.RenderPipeline;\nimport {tex_pkg}.GpuTexture;\nimport {commands_pkg}.RenderPass;\nimport {commands_pkg}.CommandEncoder;\nimport {uniform_pkg}.UniformType;'
    if not scalar:
        imports += f'\nimport {tex_pkg}.GpuTextureView;\nimport {buffer_pkg}.GpuBuffer;'
    elif needs_vertex_buffer:
        imports += f'\nimport {buffer_pkg}.GpuBuffer;\nimport {buffer_pkg}.BufferType;\nimport {buffer_pkg}.BufferUsage;'
    if color_state:
        imports += f'\nimport {pipeline_pkg}.ColorTargetState;'
    if layouts:
        imports += f'\nimport {pipeline_pkg}.BindGroupLayout;'
    else:
        imports += '\nimport com.mojang.blaze3d.vertex.DefaultVertexFormat;\nimport com.mojang.blaze3d.vertex.VertexFormat;'
    if sampler_object:
        imports += f'\nimport {tex_pkg}.FilterMode;'
    else:
        imports += f'\nimport {tex_pkg}.AddressMode;\nimport {tex_pkg}.FilterMode;'
    if not color_state:
        imports += '\nimport com.mojang.blaze3d.platform.DepthTestFunction;'
    primitive_pkg = pipeline_pkg if pearl else 'com.mojang.blaze3d'
    if layouts:
        imports += f'\nimport {primitive_pkg}.PrimitiveTopology;'
    if layouts:
        binding = 'BindGroupLayout.builder().withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)' if pearl else 'BindGroupLayout.builder().withSampler("InSampler")'
        builder = f'.withBindGroupLayout({binding}.withUniform("NightGradeConfig", UniformType.UNIFORM_BUFFER).build())'
    else:
        builder = '.withSampler("InSampler").withUniform("Strength", UniformType.FLOAT)' if scalar else '.withSampler("InSampler").withUniform("NightGradeConfig", UniformType.UNIFORM_BUFFER)'
    if color_state:
        builder += '.withColorTargetState(ColorTargetState.DEFAULT).withDepthStencilState(Optional.empty())'
    else:
        builder += '.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).withoutBlend()'
    builder += '.withCull(false)'
    empty_vertex_format = 'VertexFormat.builder().build()' if scalar else 'DefaultVertexFormat.EMPTY'
    builder += '.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)' if layouts else f'.withVertexFormat({empty_vertex_format}, VertexFormat.Mode.TRIANGLES)'
    copy_init = 'copy = RenderSystem.getDevice().createTexture("NightClient scene copy", source.getFormat(), width, height, 1);' if scalar else 'copy = RenderSystem.getDevice().createTexture("NightClient scene copy", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, source.getFormat(), width, height, 1, 1);\n            copyView = RenderSystem.getDevice().createTextureView(copy);'
    if not sampler_object:
        copy_init += '\n            copy.setAddressMode(AddressMode.CLAMP_TO_EDGE); copy.setTextureFilter(FilterMode.NEAREST, false);'
    target = 'Minecraft.getInstance().gameRenderer.mainRenderTarget()' if key >= (26, 2) else 'Minecraft.getInstance().getMainRenderTarget()'
    render_pass = 'encoder.createRenderPass(target.getColorTexture(), OptionalInt.empty())' if scalar else 'encoder.createRenderPass(() -> "NightClient Saturation", target.getColorTextureView(), Optional.empty())' if layouts else 'encoder.createRenderPass(() -> "NightClient Saturation", target.getColorTextureView(), OptionalInt.empty())'
    set_pipeline = 'pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));' if pearl else 'pass.setPipeline(pipeline);'
    sampler = 'pass.setUniform("InSampler", copyView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));' if pearl else 'pass.bindTexture("InSampler", copyView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));' if sampler_object else 'pass.bindSampler("InSampler", copy);' if scalar else 'pass.bindSampler("InSampler", copyView);'
    buffer_decl = '' if scalar else 'private static GpuTextureView copyView;\n    private static GpuBuffer uniform;\n    private static float uploadedStrength = Float.NaN;\n    private static final ByteBuffer uniformData = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());'
    update = '' if scalar else 'if (uniform == null) {\n            uniform = RenderSystem.getDevice().createBuffer(() -> "NightClient colour grade", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 16);\n            uploadedStrength = Float.NaN;\n        }\n        if (amount != uploadedStrength) {\n            uniformData.clear(); uniformData.putFloat(amount).putFloat(0).putFloat(0).putFloat(0).flip();\n            encoder.writeToBuffer(uniform.slice(), uniformData); uploadedStrength = amount;\n        }'
    uniform_set = 'pass.setUniform("Strength", amount);' if scalar else 'pass.setUniform("NightGradeConfig", uniform);'
    draw = 'pass.draw(3, 1, 0, 0);' if layouts else 'pass.draw(0, 3);'
    destroy_view = '' if scalar else 'if (copyView != null) { copyView.close(); copyView = null; }'
    destroy_uniform = '' if scalar else 'if (uniform != null) { uniform.close(); uniform = null; } uploadedStrength = Float.NaN;'
    vertex_decl = 'private static GpuBuffer dummyVertex;' if needs_vertex_buffer else ''
    vertex_create = ('if (dummyVertex == null) dummyVertex = RenderSystem.getDevice().createBuffer(\n            () -> "NightClient fullscreen vertex binding", BufferType.VERTICES, BufferUsage.STATIC_WRITE, 64);' if scalar else 'if (dummyVertex == null) dummyVertex = RenderSystem.getDevice().createBuffer(\n            () -> "NightClient fullscreen vertex binding", GpuBuffer.USAGE_VERTEX, 64);') if needs_vertex_buffer else ''
    vertex_bind = 'pass.setVertexBuffer(0, dummyVertex);' if needs_vertex_buffer else ''
    vertex_close = 'if (dummyVertex != null) { dummyVertex.close(); dummyVertex = null; }' if needs_vertex_buffer else ''
    return f'package dev.nightclient.platform;\nimport dev.nightclient.runtime.ClientRuntime;\nimport net.minecraft.client.Minecraft;\nimport com.mojang.blaze3d.pipeline.RenderTarget;\nimport com.mojang.blaze3d.systems.RenderSystem;\nimport java.nio.ByteBuffer;\nimport java.nio.ByteOrder;\nimport java.util.Optional;\nimport java.util.OptionalInt;\n{imports}\n\npublic final class SaturationBackend {{\n    private static RenderPipeline pipeline;\n    private static GpuTexture copy;\n    private static int width, height;\n    private static volatile boolean invalidated;\n    {buffer_decl}\n    {vertex_decl}\n    private SaturationBackend() {{}}\n    public static void resourceReload() {{ invalidated = true; }}\n    public static void render() {{\n        ClientRuntime runtime = ClientRuntime.get();\n        if (runtime == null) return;\n        float amount = runtime.saturationAmount();\n        if (Math.abs(amount - 1) < 0.000001f) {{ if (copy != null) destroyCopy(); return; }}\n        if (invalidated) {{ close(); invalidated = false; }}\n        RenderTarget target = {target};\n        GpuTexture source = target.getColorTexture();\n        if (source == null || target.width <= 0 || target.height <= 0) return;\n        if (pipeline == null) pipeline = RenderPipeline.builder()\n                .withLocation(GameBridge.identifier("nightclient:pipeline/saturation"))\n                .withVertexShader(GameBridge.identifier("nightclient:core/fullscreen_{shader_suffix}"))\n                .withFragmentShader(GameBridge.identifier("nightclient:core/saturation_{shader_suffix}"))\n                {builder}.build();\n        if (copy == null || width != target.width || height != target.height || !copy.getFormat().equals(source.getFormat())) {{\n            destroyCopy(); width = target.width; height = target.height;\n            {copy_init}\n        }}\n        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();\n        {vertex_create}\n        {update}\n        encoder.copyTextureToTexture(source, copy, 0, 0, 0, 0, 0, width, height);\n        try (RenderPass pass = {render_pass}) {{\n            {set_pipeline}\n            {sampler}\n            {uniform_set}\n            {vertex_bind}\n            {draw}\n        }}\n    }}\n    private static void destroyCopy() {{ {destroy_view} if (copy != null) {{ copy.close(); copy = null; }} }}\n    public static void close() {{ destroyCopy(); {destroy_uniform} {vertex_close} pipeline = null; width = height = 0; }}\n}}\n'

def mixin_source(version):
    key = version_key(version)
    begin_method = 'extract' if key >= (26, 1) else 'render'
    if key < (1, 21, 6):
        at = 'value = "FIELD", target = "Lnet/minecraft/client/Options;hideGui:Z", opcode = 180'
    else:
        at = None
    if key >= (26, 2):
        target = 'Lnet/minecraft/client/gui/render/GuiRenderer;render()V'
    elif key >= (1, 21, 6):
        target = 'Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V'
    else:
        context = 'com/mojang/blaze3d/vertex/PoseStack' if key < (1, 20) else 'net/minecraft/client/gui/GuiGraphics'
        delta = 'F' if key < (1, 21) else 'Lnet/minecraft/client/DeltaTracker;'
        target = f'Lnet/minecraft/client/gui/Gui;render(L{context};{delta})V'
    if at is None:
        at = f'value = "INVOKE", target = "{target}", shift = At.Shift.BEFORE'
    handler_args = 'CallbackInfo ci'
    hidden_hud = ''
    if key <= (1, 20, 4):
        handler_args = 'float partialTick, long nanos, boolean renderWorld, CallbackInfo ci'
        hidden_hud = ' dev.nightclient.platform.GameBridge.renderHiddenHud(partialTick);'
    return f'package dev.nightclient.platform.mixin;\n\nimport dev.nightclient.platform.SaturationBackend;\nimport dev.nightclient.platform.BackdropBackend;\nimport net.minecraft.client.renderer.GameRenderer;\nimport org.spongepowered.asm.mixin.Mixin;\nimport org.spongepowered.asm.mixin.injection.At;\nimport org.spongepowered.asm.mixin.injection.Inject;\nimport org.spongepowered.asm.mixin.injection.callback.CallbackInfo;\n\n@Mixin(GameRenderer.class)\npublic abstract class SaturationMixin {{\n    @Inject(method = "{begin_method}", at = @At("HEAD"))\n    private void nightclient$beginInterfaceFrame(CallbackInfo ci) {{ BackdropBackend.beginFrame(); dev.nightclient.platform.MapTextureBackend.flushRetired(); dev.nightclient.platform.PlayerPreviewBackend.flushRetired(); }}\n    @Inject(method = "render", at = @At({at}))\n    private void nightclient$gradeBeforeInterface({handler_args}) {{ SaturationBackend.render(); BackdropBackend.capture();{hidden_hud} }}\n}}\n'

def main_generate_renderers():
    parser = argparse.ArgumentParser()
    parser.add_argument('--version', required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    dest = Path(args.output) / 'dev/nightclient/platform'
    dest.mkdir(parents=True, exist_ok=True)
    (dest / 'mixin').mkdir(exist_ok=True)
    if version_key(args.version) < (1, 21, 5):
        source = (ROOT / 'modules/platform/src/templates/backends/LegacySaturationBackend.java.tpl').read_text()
        shared_math = (ROOT / 'modules/renderer/src/main/resources/assets/nightclient/shaders/include/saturation.glsl').read_text()
        source = source.replace('@SHARED_SATURATION@', json.dumps(shared_math))
    else:
        source = native_source(args.version)
    (dest / 'SaturationBackend.java').write_text(source)
    (dest / 'BackdropBackend.java').write_text(backdrop_source(args.version))
    (dest / 'mixin/SaturationMixin.java').write_text(mixin_source(args.version))

from generate_resources import resource_assets, generate as _generate_resources_impl

def main_generate_resources():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--version', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    _generate_resources_impl(args.version, args.output)

def generate_vanilla_hud(version, output):
    profile = json.loads((ROOT / 'modules/platform/compatibility/bridges.json').read_text())[version]
    v = tuple(map(int, version.split('.')))
    context = profile['screenContext']
    context_import = 'com.mojang.blaze3d.vertex.PoseStack' if context == 'PoseStack' else 'net.minecraft.client.gui.' + context
    identifier = profile['identifierType']
    imports = 'import ' + context_import + ';\nimport net.minecraft.resources.' + identifier + ';'
    if v >= (1, 21, 6):
        push = 'graphics.pose().pushMatrix();graphics.pose().translate(x,y);graphics.pose().scale(size/9,size/9);'
        pop = 'graphics.pose().popMatrix();'
    elif context == 'GuiGraphics':
        push = 'graphics.pose().pushPose();graphics.pose().translate(x,y,0);graphics.pose().scale(size/9,size/9,1);'
        pop = 'graphics.pose().popPose();'
    else:
        push = 'graphics.pushPose();graphics.translate(x,y,0);graphics.scale(size/9,size/9,1);'
        pop = 'graphics.popPose();'
    if v >= (1, 20, 2):
        textures = 'private static final ' + identifier + '[] FOOD={GameBridge.identifier("minecraft:hud/food_empty"),GameBridge.identifier("minecraft:hud/food_half"),GameBridge.identifier("minecraft:hud/food_full")};\n    private static final ' + identifier + '[] HUNGER={GameBridge.identifier("minecraft:hud/food_empty_hunger"),GameBridge.identifier("minecraft:hud/food_half_hunger"),GameBridge.identifier("minecraft:hud/food_full_hunger")};'
        if v >= (1, 21, 6):
            imports += '\nimport net.minecraft.client.renderer.RenderPipelines;'
            call = 'graphics.blitSprite(RenderPipelines.GUI_TEXTURED,icons[INDEX],0,0,9,9);'
        elif v >= (1, 21, 2):
            imports += '\nimport net.minecraft.client.renderer.RenderType;'
            call = 'graphics.blitSprite(RenderType::guiTextured,icons[INDEX],0,0,9,9);'
        else:
            call = 'graphics.blitSprite(icons[INDEX],0,0,9,9);'
        draw = identifier + '[] icons=hunger?HUNGER:FOOD;' + call.replace('INDEX', '0')
        draw += 'if(fill>0){' + call.replace('INDEX', 'fill') + '}'
        if v < (1, 21, 6):
            draw += 'graphics.flush();'
    else:
        textures = 'private static final ' + identifier + ' ICONS=GameBridge.identifier("minecraft:textures/gui/icons.png");'
        imports += '\nimport com.mojang.blaze3d.systems.RenderSystem;'
        if context == 'GuiGraphics':
            call = 'graphics.blit(ICONS,0,0,(float)UV,27f,9,9,256,256);'
            setup = ''
        else:
            imports += '\nimport net.minecraft.client.gui.GuiComponent;'
            call = 'GuiComponent.blit(graphics,0,0,(float)UV,27f,9,9,256,256);'
            if v == (1, 16, 5):
                setup = 'mc.getTextureManager().bind(ICONS);RenderSystem.enableTexture();RenderSystem.color4f(1,1,1,1);'
            else:
                imports += '\nimport net.minecraft.client.renderer.GameRenderer;'
                setup = 'RenderSystem.setShader(GameRenderer::getPositionTexShader);RenderSystem.setShaderTexture(0,ICONS);RenderSystem.setShaderColor(1,1,1,1);'
        draw = setup + 'RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();'
        draw += call.replace('UV', '(hunger?133:16)')
        draw += 'if(fill>0){int u=(hunger?52:16)+(fill==2?36:45);' + call.replace('UV', 'u') + '}'
        if context == 'GuiGraphics':
            draw += 'graphics.flush();'
    values = {'CONTEXT': context, 'VANILLA_HUD_IMPORTS': imports, 'VANILLA_HUD_TEXTURES': textures, 'VANILLA_HUD_PUSH': push, 'VANILLA_HUD_POP': pop, 'VANILLA_HUD_DRAW': draw}
    source = (ROOT / 'modules/platform/src/templates/VanillaHudBridge.java.tpl').read_text()
    for key, value in values.items():
        source = source.replace('@@' + key + '@@', value)
    if re.search('@@[A-Z_]+@@', source):
        raise RuntimeError('Unresolved vanilla HUD template')
    target = output / 'dev/nightclient/platform/VanillaHudBridge.java'
    target.parent.mkdir(parents=True, exist_ok=True)
    if not target.exists() or target.read_text() != source:
        target.write_text(source)
VIEWMODEL_FUNCTION = 'float ncHandAlpha(vec2 uv, bool offHand) {\n    if(any(lessThan(uv,vec2(0.0))) || any(greaterThan(uv,vec2(1.0)))) return 0.0;\n    float offAlpha=texture(OffSampler,uv).a;\n    return offHand ? offAlpha : texture(MainSampler,uv).a*(1.0-step(0.0001,offAlpha));\n}\nfloat ncHandOutline(vec2 uv, vec2 pointer, float radius) {\n    if(any(lessThan(pointer,vec2(0.0))) || any(greaterThanEqual(pointer,vec2(1.0)))) return 0.0;\n    bool offHand=texture(OffSampler,pointer).a>0.0001;\n    if(!offHand && texture(MainSampler,pointer).a<=0.0001) return 0.0;\n\n    if(!offHand && texture(OffSampler,uv).a>0.0001) return 0.0;\n    float center=ncHandAlpha(uv,offHand);\n    if(center>0.0001) return 0.0;\n    vec2 texel=1.0/vec2(textureSize(MainSampler,0));\n    float core=0.0,halo=0.0;\n    for(int i=0;i<12;i++) {\n        float angle=float(i)*0.5235987756;\n        vec2 direction=vec2(cos(angle),sin(angle))*texel;\n        core=max(core,ncHandAlpha(uv+direction*radius,offHand));\n        halo+=ncHandAlpha(uv+direction*(radius*3.0),offHand);\n    }\n    return min(0.98,core*0.90+halo*(0.32/12.0));\n}\n'

def outline_source(version):
    v = tuple(map(int, version.split('.')))
    if v < (1, 21, 6):
        source = (ROOT / 'modules/platform/src/templates/LegacyViewmodelOutlineBackend.java.tpl').read_text()
        imports = ''
        fbo = 'target.frameBufferId'
        texture = 'target.getColorTextureId()'
        if v == (1, 21, 5):
            imports = 'import com.mojang.blaze3d.opengl.GlDevice;\nimport com.mojang.blaze3d.opengl.GlTexture;\n'
            fbo = '((GlTexture)target.getColorTexture()).getFbo(((GlDevice)RenderSystem.getDevice()).directStateAccess(),target.getDepthTexture())'
            texture = '((GlTexture)target.getColorTexture()).glId()'
        return source.replace('@@SOURCE_IMPORTS@@', imports).replace('@@SOURCE_FBO@@', fbo).replace('@@SOURCE_TEXTURE@@', texture).replace('@@KERNEL@@', json.dumps(VIEWMODEL_FUNCTION))
    pearl = v >= (26, 3)
    layouts = v >= (26, 2)
    color = v >= (26, 1)
    samplers = v >= (1, 21, 11)
    vertex = not samplers
    pipeline = 'com.mojang.renderpearl.api.pipeline' if pearl else 'com.mojang.blaze3d.pipeline'
    tex = 'com.mojang.renderpearl.api.textures' if pearl else 'com.mojang.blaze3d.textures'
    buf = 'com.mojang.renderpearl.api.buffers' if pearl else 'com.mojang.blaze3d.buffers'
    cmd = 'com.mojang.renderpearl.api.commands' if pearl else 'com.mojang.blaze3d.systems'
    uniform = pipeline if pearl else 'com.mojang.blaze3d.shaders'
    imports = '\n'.join(('import ' + p + ';' for p in [pipeline + '.RenderPipeline', tex + '.GpuTexture', tex + '.GpuTextureView', tex + '.FilterMode', buf + '.GpuBuffer', cmd + '.CommandEncoder', cmd + '.RenderPass', uniform + '.UniformType']))
    if not samplers:
        imports += '\nimport ' + tex + '.AddressMode;'
    if color:
        imports += '\nimport ' + pipeline + '.ColorTargetState;'
    else:
        imports += '\nimport com.mojang.blaze3d.platform.DepthTestFunction;'
    if layouts:
        imports += '\nimport ' + pipeline + '.BindGroupLayout;\nimport ' + (pipeline if pearl else 'com.mojang.blaze3d') + '.PrimitiveTopology;'
        binding = 'BindGroupLayout.builder()' + ('.withUniform("MainSampler",UniformType.COMBINED_IMAGE_SAMPLER).withUniform("OffSampler",UniformType.COMBINED_IMAGE_SAMPLER)' if pearl else '.withSampler("MainSampler").withSampler("OffSampler")')
        builder = '.withBindGroupLayout(' + binding + '.withUniform("NightOutlineConfig",UniformType.UNIFORM_BUFFER).build())'
    else:
        imports += '\nimport com.mojang.blaze3d.vertex.DefaultVertexFormat;\nimport com.mojang.blaze3d.vertex.VertexFormat;'
        builder = '.withSampler("MainSampler").withSampler("OffSampler").withUniform("NightOutlineConfig",UniformType.UNIFORM_BUFFER)'
    builder += ('.withColorTargetState(ColorTargetState.DEFAULT).withDepthStencilState(Optional.empty())' if color else '.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).withoutBlend()') + '.withCull(false)'
    builder += '.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)' if layouts else '.withVertexFormat(DefaultVertexFormat.EMPTY,VertexFormat.Mode.TRIANGLES)'
    sampler = lambda name, view: 'pass.setUniform("' + name + '",' + view + ',RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));' if pearl else 'pass.bindTexture("' + name + '",' + view + ',RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));' if samplers else 'pass.bindSampler("' + name + '",' + view + ');'
    suffix = 'pearl' if pearl else 'vulkan' if layouts else 'ubo'
    values = {'IMPORTS': imports, 'PIPELINE_BUILDER': builder, 'SUFFIX': suffix, 'PREWARM': 'RenderSystem.getCompiledPipeline(pipeline());' if pearl else 'RenderSystem.getDevice().precompilePipeline(pipeline());', 'PASS_CREATE': 'encoder.createRenderPass(()->"NightClient live hand outline",outline.view,' + ('Optional.empty()' if layouts else 'OptionalInt.empty()') + ')', 'SET_PIPELINE': 'pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));' if pearl else 'pass.setPipeline(pipeline);', 'SAMPLERS': sampler('MainSampler', 'main.view') + sampler('OffSampler', 'target.getColorTextureView()'), 'DRAW': 'pass.draw(3,1,0,0);' if layouts else 'pass.draw(0,3);', 'TEXTURE_SETTINGS': '' if samplers else 'texture.setAddressMode(AddressMode.CLAMP_TO_EDGE);texture.setTextureFilter(FilterMode.LINEAR,false);', 'MASK_SAMPLER_SETTINGS': '' if samplers else 'target.getColorTexture().setTextureFilter(FilterMode.LINEAR,false);', 'VERTEX_DECL': 'private static GpuBuffer vertex;' if vertex else '', 'VERTEX_CREATE': 'if(vertex==null)vertex=RenderSystem.getDevice().createBuffer(()->"NightClient outline vertex binding",GpuBuffer.USAGE_VERTEX,64);' if vertex else '', 'VERTEX_BIND': 'pass.setVertexBuffer(0,vertex);' if vertex else '', 'VERTEX_CLOSE': 'if(vertex!=null){vertex.close();vertex=null;}' if vertex else ''}
    source = (ROOT / 'modules/platform/src/templates/NativeViewmodelOutlineBackend.java.tpl').read_text()
    for k, value in values.items():
        source = source.replace('@@' + k + '@@', value)
    return source
from generate_world_features import BASE_MIXINS, world_feature_mixins, generate_world_features

OPTIMIZATION_MIXINS = (
    'OptimizationEntityCullMixin',
    'OptimizationEntitySubmitCullMixin',
    'OptimizationBlockEntityCullMixin',
    'OptimizationBlockEntitySubmitCullMixin',
    'OptimizationParticleCullMixin',
    'OptimizationNametagCullMixin',
    'OptFrameHookMixin',
    'OptEntityEarlyCullMixin',
    'OptBlockEntityEarlyCullMixin',
    'OptEntityShadowCullMixin',
    'OptSignTextCullMixin',
    'OptParticlePhysicsMixin',
    'OptParticleRenderCullMixin',
    'OptSoundThrottleMixin',
    'OptBiomeCacheMixin',
    'OptEntityPushSkipMixin',
    'OptFastMathMixin',
    'OptLightmapCacheMixin',
    'OptRenderTypeRetainedMixin',
    'OptStaticBeLevelMixin',
    'OptStaticBeFeatureMixin',
    'OptTerrainPrepareMixin',
    'OptUberGpuBufferGenMixin',
    'OptSectionOcclusionGenMixin',
    'OptCompiledSectionMeshMixin',
    'OptRenderSectionMeshMixin',
    'OptLeafCullMixin',
    'OptPoseNormalAccessor',
    'OptBufferBuilderFastMixin',
    'OptFastModelCubeMixin',
    'OptSpriteExpanderFastMixin',
    'OptTextFeatureCacheMixin',
    'OptTextFeatureAccessor',
    'OptRenderTypeGroupCaptureMixin',
    'OptStaticBeSubmitMixin',
    'OptStaticBeExecuteMixin',
    'OptScoreboardCacheMixin',
    'OptRetainedHudExtractMixin',
    'OptRetainedHudRendererMixin',
    'OptNativeCompressionDecoderMixin',
    'OptNativeCompressionEncoderMixin',
    'OptGlTexturePboMixin',
    'OptLegacyNativeImagePboMixin',
    'OptGlBufferAccessor',
    'OptRenderTypeStateAccessor',
    'OptRenderSetupAccessor',
    'OptNativeModelPartMixin',
    'OptNativeModelBatchMixin',
    'OptNativeNameTagMixin',
    'OptNativeModelPartFeatureMixin',
    'OptNameTagStorageAccessor',
    'OptModelPartStorageAccessor',
)

def generate_optimization_features(version, output):
    v = tuple(map(int, version.split('.')))
    submit = v >= (1, 21, 9)
    mixin_dir = Path(output) / 'dev/nightclient/platform/mixin'
    for name in OPTIMIZATION_MIXINS:
        stale = mixin_dir / (name + '.java')
        if stale.exists():
            stale.unlink()
    particle_group = 'ParticleGroup' if submit else 'java.util.Collection'
    particle_import = ('import net.minecraft.client.particle.ParticleGroup;\n' if submit else '')

    if v < (26, 1):
        gpu_imports = 'import org.lwjgl.opengl.GL15;\nimport org.lwjgl.opengl.GL33;'
        gpu_body = '''  private static final int RING = 4;
  private static int[] queries;
  private static boolean[] active;
  private static int cursor;
  private static boolean inQuery;
  private static long lastNanos;
  private static boolean disabled;

  private static void ensureInit() {
    if (queries != null || disabled) return;
    try {
      queries = new int[RING];
      active = new boolean[RING];
      for (int i = 0; i < RING; i++) queries[i] = GL15.glGenQueries();
    } catch (Throwable ignored) {
      disabled = true;
    }
  }

  public static void beginFrame() {
    if (disabled) return;
    ensureInit();
    if (disabled || queries == null) return;
    try {
      int readSlot = (cursor + 1) & (RING - 1);
      if (active[readSlot] && GL15.glGetQueryObjecti(queries[readSlot], GL15.GL_QUERY_RESULT_AVAILABLE) != 0) {
        lastNanos = GL33.glGetQueryObjecti64(queries[readSlot], GL15.GL_QUERY_RESULT);
        active[readSlot] = false;
      }
      GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, queries[cursor]);
      inQuery = true;
    } catch (Throwable ignored) {
      disabled = true;
      inQuery = false;
    }
  }

  public static void endFrame() {
    if (!inQuery) return;
    inQuery = false;
    try {
      GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
      active[cursor] = true;
      cursor = (cursor + 1) & (RING - 1);
    } catch (Throwable ignored) {
      disabled = true;
    }
  }

  public static long lastGpuNanos() {
    return lastNanos;
  }'''
    else:
        gpu_imports = ''
        gpu_body = '''  public static void beginFrame() {}
  public static void endFrame() {}
  public static long lastGpuNanos() { return 0L; }'''

    if v < (1, 21, 2):
        bench_imports = 'import net.minecraft.world.level.GameRules;'
        bench_level_settings = 'new LevelSettings(folder, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT)'
        fresh_tail = ', null' if v >= (1, 20, 3) else ''
        bench_create_fresh = (
            'mc.createWorldOpenFlows().createFreshLevel(folder, settings, worldOpts, '
            'reg -> reg.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(flat ? WorldPresets.FLAT : WorldPresets.NORMAL).value().createWorldDimensions()'
            + fresh_tail + ');'
        )
    elif v < (1, 21, 11):
        bench_imports = 'import net.minecraft.world.level.GameRules;'
        bench_level_settings = 'new LevelSettings(folder, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(WorldDataConfiguration.DEFAULT.enabledFeatures()), WorldDataConfiguration.DEFAULT)'
        bench_create_fresh = (
            'mc.createWorldOpenFlows().createFreshLevel(folder, settings, worldOpts, '
            'reg -> reg.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(flat ? WorldPresets.FLAT : WorldPresets.NORMAL).value().createWorldDimensions(), null);'
        )
    elif v < (26, 1):
        bench_imports = 'import net.minecraft.world.level.gamerules.GameRules;'
        bench_level_settings = 'new LevelSettings(folder, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(WorldDataConfiguration.DEFAULT.enabledFeatures()), WorldDataConfiguration.DEFAULT)'
        bench_create_fresh = (
            'mc.createWorldOpenFlows().createFreshLevel(folder, settings, worldOpts, '
            'reg -> reg.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(flat ? WorldPresets.FLAT : WorldPresets.NORMAL).value().createWorldDimensions(), null);'
        )
    else:
        bench_imports = ''
        bench_level_settings = 'new LevelSettings(folder, GameType.CREATIVE, new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT)'
        bench_create_fresh = (
            'mc.createWorldOpenFlows().createFreshLevel(folder, settings, worldOpts, '
            'reg -> reg.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(flat ? WorldPresets.FLAT : WorldPresets.NORMAL).value().createWorldDimensions(), null);'
        )

    if v < (1, 21, 2):
        sampler_pos_decl = 'BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();'
        state_solid_render = 'state.isSolidRender(level, mpos.set((secX << 4) | x, (secY << 4) | y, (secZ << 4) | z))'
    else:
        sampler_pos_decl = ''
        state_solid_render = 'state.isSolidRender()'

    if v >= (26, 3):
        ent_sr_method = 'shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDDF)Z'
        ent_sr_extra = 'float partialTick, '
    else:
        ent_sr_method = 'shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z'
        ent_sr_extra = ''

    if v >= (1, 21, 9):
        be_early_imports = (
            'import dev.nightclient.platform.StaticBeVboCache;\n'
            'import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;\n'
            'import net.minecraft.client.renderer.feature.ModelFeatureRenderer;'
        )
        be_early_cb = 'import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;'
        be_try_desc = (
            'tryExtractRenderState(Lnet/minecraft/world/level/block/entity/BlockEntity;FLnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;Z)Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;'
            if v >= (26, 2) else
            'tryExtractRenderState(Lnet/minecraft/world/level/block/entity/BlockEntity;FLnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;'
        )
        be_try_extra = 'boolean flag, ' if v >= (26, 2) else ''
        be_early_body = f'''  @SuppressWarnings("unchecked")
  @Inject(method = "{be_try_desc}", at = @At("HEAD"), cancellable = true)
  private <E extends BlockEntity, S extends BlockEntityRenderState> void nightclient$earlyCullAndCacheBe(
      E be, float partialTick, ModelFeatureRenderer.CrumblingOverlay crumble, {be_try_extra}CallbackInfoReturnable<S> cir) {{
    if (!OptimizationBridge.snapshotActive || be == null) return;
    if (be instanceof BeaconBlockEntity) return;
    if (OptimizationBridge.opt047EmptyBeSkip) {{
      if (be instanceof net.minecraft.world.level.block.entity.CampfireBlockEntity) {{
        net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> items =
            ((net.minecraft.world.level.block.entity.CampfireBlockEntity) be).getItems();
        boolean anyItem = false;
        for (int ci = 0; ci < items.size(); ci++) {{
          if (!items.get(ci).isEmpty()) {{ anyItem = true; break; }}
        }}
        if (!anyItem) {{
          cir.setReturnValue(null);
          return;
        }}
      }} else if (be instanceof net.minecraft.world.level.block.entity.LecternBlockEntity
          && !((net.minecraft.world.level.block.entity.LecternBlockEntity) be).hasBook()) {{
        cir.setReturnValue(null);
        return;
      }}
    }}
    if (StaticBeVboCache.isAlreadyBakedFastHit(be, partialTick, crumble)) {{
      cir.setReturnValue(null);
      return;
    }}
    BlockPos pos = be.getBlockPos();
    if (pos == null) return;
    double bx = pos.getX() + 0.5;
    double by = pos.getY() + 0.5;
    double bz = pos.getZ() + 0.5;
    double dx = bx - OptimizationBridge.camX;
    double dy = by - OptimizationBridge.camY;
    double dz = bz - OptimizationBridge.camZ;
    double distSq = dx * dx + dy * dy + dz * dz;

    if (OptimizationBridge.isOptActive(OptimizationFlags.OPT_041_EARLY_BE_CULL_OCCLUSION)
        && OptimizationBridge.cullBlockEntity(distSq, true)) {{
      if (BenchmarkDirector.isRecording()) BenchmarkDirector.recorder().addCulledBlockEntity();
      cir.setReturnValue(null);
      return;
    }}

    if (OptimizationBridge.opt024Occlusion) {{
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && be.getLevel() == mc.level) {{
        long key = 0x2000000000000000L ^ pos.asLong();
        if (OptimizationBridge.OCCLUSION.isOccluded(
            key,
            OptimizationBridge.camX, OptimizationBridge.camY, OptimizationBridge.camZ,
            pos.getX(), pos.getY(), pos.getZ(),
            pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0,
            WorldOcclusionSampler.INSTANCE)) {{
          if (BenchmarkDirector.isRecording()) BenchmarkDirector.recorder().addCulledBlockEntity();
          cir.setReturnValue(null);
          return;
        }}
      }}
    }}

    if (StaticBeVboCache.trySkipStaticBe(be, partialTick, crumble)) {{
      cir.setReturnValue(null);
    }}
  }}'''
    else:
        be_early_imports = (
            'import com.mojang.blaze3d.vertex.PoseStack;\n'
            'import net.minecraft.client.renderer.MultiBufferSource;'
        )
        be_early_cb = 'import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;'
        be_early_body = '''  @Inject(
      method = "render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V",
      at = @At("HEAD"),
      cancellable = true)
  private <E extends BlockEntity> void nightclient$legacyOcclusionBe(
      E be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, CallbackInfo ci) {
    if (!OptimizationBridge.snapshotActive || be == null) return;
    if (be instanceof BeaconBlockEntity) return;
    if (OptimizationBridge.opt047EmptyBeSkip) {
      if (be instanceof net.minecraft.world.level.block.entity.CampfireBlockEntity) {
        net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> items =
            ((net.minecraft.world.level.block.entity.CampfireBlockEntity) be).getItems();
        boolean anyItem = false;
        for (int idx = 0; idx < items.size(); idx++) {
          if (!items.get(idx).isEmpty()) { anyItem = true; break; }
        }
        if (!anyItem) {
          ci.cancel();
          return;
        }
      } else if (be instanceof net.minecraft.world.level.block.entity.LecternBlockEntity
          && !((net.minecraft.world.level.block.entity.LecternBlockEntity) be).hasBook()) {
        ci.cancel();
        return;
      }
    }
    if (!OptimizationBridge.isOptActive(OptimizationFlags.OPT_024_ASYNC_3D_DDA_OCCLUSION)) return;
    Minecraft mc = Minecraft.getInstance();
    if (mc.level == null || be.getLevel() != mc.level) return;
    BlockPos pos = be.getBlockPos();
    if (pos == null) return;
    long key = 0x2000000000000000L ^ pos.asLong();
    if (OptimizationBridge.OCCLUSION.isOccluded(
        key,
        OptimizationBridge.camX, OptimizationBridge.camY, OptimizationBridge.camZ,
        pos.getX(), pos.getY(), pos.getZ(),
        pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0,
        WorldOcclusionSampler.INSTANCE)) {
      if (BenchmarkDirector.isRecording()) BenchmarkDirector.recorder().addCulledBlockEntity();
      ci.cancel();
    }
  }'''

    if v >= (26, 1):
        sign_owner = 'AbstractSignRenderer'
        sign_imports = (
            'import net.minecraft.client.renderer.SubmitNodeCollector;\n'
            'import net.minecraft.client.renderer.blockentity.AbstractSignRenderer;\n'
            'import net.minecraft.client.renderer.blockentity.state.SignRenderState;\n'
            'import net.minecraft.core.BlockPos;\n'
            'import net.minecraft.world.level.block.entity.SignText;'
        )
        sign_body = '''  @Inject(
      method = "submitSignText(Lnet/minecraft/client/renderer/blockentity/state/SignRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/world/level/block/entity/SignText;)V",
      at = @At("HEAD"),
      cancellable = true)
  private void nightclient$cullSignText(SignRenderState state, PoseStack pose, SubmitNodeCollector collector, SignText text, CallbackInfo ci) {
    if (!OptimizationBridge.snapshotActive || state == null) return;
    if (!OptimizationBridge.isOptActive(OptimizationFlags.OPT_042_SIGN_TEXT_CULL_CACHE)) return;
    BlockPos pos = state.blockPos;
    if (pos == null) return;
    double dx = pos.getX() + 0.5 - OptimizationBridge.camX;
    double dy = pos.getY() + 0.5 - OptimizationBridge.camY;
    double dz = pos.getZ() + 0.5 - OptimizationBridge.camZ;
    if (OptimizationBridge.cullSignText(dx * dx + dy * dy + dz * dz, -1.0)) {
      ci.cancel();
    }
  }'''
    elif v >= (1, 21, 9):
        sign_owner = 'AbstractSignRenderer'
        sign_imports = (
            'import dev.nightclient.platform.StaticBeVboCache;\n'
            'import net.minecraft.client.model.Model;\n'
            'import net.minecraft.client.renderer.SubmitNodeCollector;\n'
            'import net.minecraft.client.renderer.blockentity.AbstractSignRenderer;\n'
            'import net.minecraft.client.renderer.blockentity.state.SignRenderState;\n'
            'import net.minecraft.client.renderer.feature.ModelFeatureRenderer;\n'
            'import net.minecraft.core.BlockPos;\n'
            'import net.minecraft.world.level.block.SignBlock;\n'
            'import net.minecraft.world.level.block.state.BlockState;\n'
            'import net.minecraft.world.level.block.state.properties.WoodType;'
        )
        sign_body = '''  @Inject(
      method = "submitSignWithText(Lnet/minecraft/client/renderer/blockentity/state/SignRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/SignBlock;Lnet/minecraft/world/level/block/state/properties/WoodType;Lnet/minecraft/client/model/Model$Simple;Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V",
      at = @At("HEAD"))
  private void nightclient$beforeSignWithText(
      SignRenderState state, PoseStack pose, BlockState bs, SignBlock sb, WoodType wt,
      Model.Simple model, ModelFeatureRenderer.CrumblingOverlay crumble, SubmitNodeCollector collector, CallbackInfo ci) {
    StaticBeVboCache.skipCurrentSignWood =
        !StaticBeVboCache.bakingSignWoodOnly
        && crumble == null
        && state != null && state.blockPos != null
        && StaticBeVboCache.isBakedSignWood(state.blockPos.asLong());
  }

  @Inject(
      method = "submitSignWithText(Lnet/minecraft/client/renderer/blockentity/state/SignRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/SignBlock;Lnet/minecraft/world/level/block/state/properties/WoodType;Lnet/minecraft/client/model/Model$Simple;Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V",
      at = @At("RETURN"))
  private void nightclient$afterSignWithText(
      SignRenderState state, PoseStack pose, BlockState bs, SignBlock sb, WoodType wt,
      Model.Simple model, ModelFeatureRenderer.CrumblingOverlay crumble, SubmitNodeCollector collector, CallbackInfo ci) {
    StaticBeVboCache.skipCurrentSignWood = false;
  }

  @Inject(
      method = "submitSign(Lcom/mojang/blaze3d/vertex/PoseStack;ILnet/minecraft/world/level/block/state/properties/WoodType;Lnet/minecraft/client/model/Model$Simple;Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V",
      at = @At("HEAD"),
      cancellable = true)
  private void nightclient$skipBakedSignWood(
      PoseStack pose, int light, WoodType wt, Model.Simple model,
      ModelFeatureRenderer.CrumblingOverlay crumble, SubmitNodeCollector collector, CallbackInfo ci) {
    if (StaticBeVboCache.skipCurrentSignWood) {
      ci.cancel();
    }
  }

  @Inject(
      method = "submitSignText(Lnet/minecraft/client/renderer/blockentity/state/SignRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Z)V",
      at = @At("HEAD"),
      cancellable = true)
  private void nightclient$cullSignText(SignRenderState state, PoseStack pose, SubmitNodeCollector collector, boolean front, CallbackInfo ci) {
    if (StaticBeVboCache.bakingSignWoodOnly) {
      ci.cancel();
      return;
    }
    if (!OptimizationBridge.snapshotActive || state == null) return;
    if (!OptimizationBridge.isOptActive(OptimizationFlags.OPT_042_SIGN_TEXT_CULL_CACHE)) return;
    BlockPos pos = state.blockPos;
    if (pos == null) return;
    double dx = pos.getX() + 0.5 - OptimizationBridge.camX;
    double dy = pos.getY() + 0.5 - OptimizationBridge.camY;
    double dz = pos.getZ() + 0.5 - OptimizationBridge.camZ;
    double distSq = dx * dx + dy * dy + dz * dz;
    double dot = -1.0;
    if (distSq > 2.25 && state.blockState != null && state.blockState.getBlock() instanceof SignBlock) {
      float deg = ((SignBlock) state.blockState.getBlock()).getYRotationDegrees(state.blockState);
      double rad = deg * (Math.PI / 180.0);
      double nx = -Math.sin(rad);
      double nz = Math.cos(rad);
      if (!front) {
        nx = -nx;
        nz = -nz;
      }
      // Positive dot(signToCam_negated, normal) means camera is behind this side of the sign board
      dot = (dx * nx + dz * nz) / Math.sqrt(distSq);
    }
    if (OptimizationBridge.cullSignText(distSq, dot)) {
      ci.cancel();
    }
  }'''
    else:
        sign_owner = 'AbstractSignRenderer' if v >= (1, 21, 4) else 'SignRenderer'
        sign_imports = (
            'import net.minecraft.client.renderer.MultiBufferSource;\n'
            'import net.minecraft.client.renderer.blockentity.' + sign_owner + ';\n'
            'import net.minecraft.core.BlockPos;\n'
            'import net.minecraft.world.level.block.entity.SignText;'
        )
        sign_body = '''  @Inject(
      method = "renderSignText(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/SignText;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IIIZ)V",
      at = @At("HEAD"),
      cancellable = true)
  private void nightclient$cullSignText(BlockPos pos, SignText text, PoseStack pose, MultiBufferSource buf, int packedLight, int lineHeight, int maxWidth, boolean front, CallbackInfo ci) {
    if (!OptimizationBridge.snapshotActive || pos == null) return;
    if (!OptimizationBridge.isOptActive(OptimizationFlags.OPT_042_SIGN_TEXT_CULL_CACHE)) return;
    double dx = pos.getX() + 0.5 - OptimizationBridge.camX;
    double dy = pos.getY() + 0.5 - OptimizationBridge.camY;
    double dz = pos.getZ() + 0.5 - OptimizationBridge.camZ;
    if (OptimizationBridge.cullSignText(dx * dx + dy * dy + dz * dz, -1.0)) {
      ci.cancel();
    }
  }'''

    has_retained_be = v == (1, 21, 11)
    has_retained_be_26_2 = v >= (26, 2)
    state_pkg = 'net.minecraft.client.renderer.state.level' if v >= (26, 1) else 'net.minecraft.client.renderer.state'
    state_desc_pkg = state_pkg.replace('.', '/')
    if has_retained_be:
        retained_rt_owner = 'RenderType'
        retained_rt_imports = (
            'import com.mojang.blaze3d.buffers.GpuBuffer;\n'
            'import com.mojang.blaze3d.buffers.GpuBufferSlice;\n'
            'import com.mojang.blaze3d.pipeline.RenderTarget;\n'
            'import com.mojang.blaze3d.systems.RenderPass;\n'
            'import com.mojang.blaze3d.systems.RenderSystem;\n'
            'import com.mojang.blaze3d.systems.ScissorState;\n'
            'import com.mojang.blaze3d.textures.GpuTextureView;\n'
            'import com.mojang.blaze3d.vertex.MeshData;\n'
            'import com.mojang.blaze3d.vertex.VertexFormat;\n'
            'import java.util.Map;\n'
            'import java.util.OptionalDouble;\n'
            'import java.util.OptionalInt;\n'
            'import net.minecraft.client.Minecraft;\n'
            'import net.minecraft.client.renderer.rendertype.RenderSetup;\n'
            'import net.minecraft.client.renderer.rendertype.RenderType;\n'
            'import org.joml.Matrix4f;\n'
            'import org.joml.Vector3f;\n'
            'import org.joml.Vector4f;'
        )
        retained_rt_extra = (
            'import org.spongepowered.asm.mixin.Final;\n'
            'import org.spongepowered.asm.mixin.Shadow;\n'
            'import org.spongepowered.asm.mixin.Unique;\n'
            'import org.spongepowered.asm.mixin.injection.At;\n'
            'import org.spongepowered.asm.mixin.injection.Inject;\n'
            'import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;'
        )
        retained_rt_body = '''  @Shadow @Final private RenderSetup state;
  @Unique private static final Matrix4f NC_MV = new Matrix4f();
  @Unique private static final Vector4f NC_COLOR = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);
  @Unique private static final Vector3f NC_OFFSET = new Vector3f();
  @Unique private static final Matrix4f NC_TEX = new Matrix4f();

  @Inject(method = "draw(Lcom/mojang/blaze3d/vertex/MeshData;)V", at = @At("HEAD"), cancellable = true)
  private void nightclient$captureStaticBeMesh(MeshData mesh, CallbackInfo ci) {
    if (StaticBeVboCache.isCapturing()) {
      StaticBeVboCache.onCapturedMesh((RenderType) (Object) this, mesh);
      mesh.close();
      ci.cancel();
    } else if (StaticEntityVboCache.isCapturing()) {
      StaticEntityVboCache.onCapturedMesh((RenderType) (Object) this, mesh);
      mesh.close();
      ci.cancel();
    }
  }

  @Override
  public void nightclient$drawRetained(Object gpuBuffer, int indexCount, float tx, float ty, float tz) {
    if (!(gpuBuffer instanceof GpuBuffer) || indexCount <= 0) return;
    GpuBuffer vbo = (GpuBuffer) gpuBuffer;
    if (vbo.isClosed()) return;
    RenderType self = (RenderType) (Object) this;
    NC_MV.set(RenderSystem.getModelViewMatrix()).translate(tx, ty, tz);
    GpuBufferSlice dyn = RenderSystem.getDynamicUniforms().writeTransform(NC_MV, NC_COLOR, NC_OFFSET, NC_TEX);
    Map<String, RenderSetup.TextureAndSampler> textures = this.state.getTextures();
    RenderSystem.AutoStorageIndexBuffer seq = RenderSystem.getSequentialBuffer(self.mode());
    GpuBuffer ibo = seq.getBuffer(indexCount);
    VertexFormat.IndexType itype = seq.type();
    RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
    GpuTextureView color = RenderSystem.outputColorTextureOverride != null
        ? RenderSystem.outputColorTextureOverride : target.getColorTextureView();
    GpuTextureView depth = target.useDepth
        ? (RenderSystem.outputDepthTextureOverride != null
            ? RenderSystem.outputDepthTextureOverride : target.getDepthTextureView())
        : null;
    try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
        () -> "NightClient StaticBE", color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
      pass.setPipeline(self.pipeline());
      ScissorState scissor = RenderSystem.getScissorStateForRenderTypeDraws();
      if (scissor.enabled()) {
        pass.enableScissor(scissor.x(), scissor.y(), scissor.width(), scissor.height());
      }
      RenderSystem.bindDefaultUniforms(pass);
      pass.setUniform("DynamicTransforms", dyn);
      pass.setVertexBuffer(0, vbo);
      for (Map.Entry<String, RenderSetup.TextureAndSampler> entry : textures.entrySet()) {
        RenderSetup.TextureAndSampler ts = entry.getValue();
        pass.bindTexture(entry.getKey(), ts.textureView(), ts.sampler());
      }
      pass.setIndexBuffer(ibo, itype);
      pass.drawIndexed(0, 0, indexCount, 1);
    }
  }'''

        static_be_imports = f'''import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.nightclient.config.OptimizationFlags;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.blockentity.state.ChestRenderState;
import net.minecraft.client.renderer.blockentity.state.DecoratedPotRenderState;
import net.minecraft.client.renderer.blockentity.state.SignRenderState;
import net.minecraft.client.renderer.blockentity.state.SkullBlockRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.feature.ModelPartFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import {state_pkg}.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;'''

        static_be_body = '''  private static final int CAP = 16384;
  private static final int MASK = CAP - 1;
  private static final int MIN_BAKE_COUNT = 16;

  private static final class Batch {
    final RenderType renderType;
    final GpuBuffer vbo;
    final int indexCount;

    Batch(RenderType renderType, GpuBuffer vbo, int indexCount) {
      this.renderType = renderType;
      this.vbo = vbo;
      this.indexCount = indexCount;
    }
  }

  private static final List<Batch> BATCHES = new ArrayList<Batch>();
  private static final long[] BAKED_KEYS = new long[CAP];
  private static final int[] BAKED_STATE_HASH = new int[CAP];
  private static final long[] BAKED_GEN = new long[CAP];
  private static final long[] BAKED_POS_LIST = new long[8192];
  private static final BlockPos.MutableBlockPos VERIFY_POS = new BlockPos.MutableBlockPos();

  private static long bakedGeneration = 1L;
  private static int bakedListSize;
  private static int verifyCursor;
  private static boolean bakedValid;
  private static boolean needsBake;
  private static boolean capturing;
  private static boolean armedForDraw;
  private static int unbakedFrameCount;
  private static long lastBakeFrame = -1000L;
  private static ClientLevel bakedLevel;
  private static int bakedSkyDarken = -1;
  private static boolean bakedChestsFlag;
  private static boolean bakedSignsFlag;
  private static long lastVerifyTick = Long.MIN_VALUE;
  private static boolean sameTickFastHit;
  public static boolean bakingSignWoodOnly;
  public static boolean skipCurrentSignWood;
  private static int anchorX, anchorY, anchorZ;
  private static int frameCandidateCount;

  private static final ModelFeatureRenderer TEMP_MODEL_RENDERER = new ModelFeatureRenderer();
  private static final ModelPartFeatureRenderer TEMP_PART_RENDERER = new ModelPartFeatureRenderer();
  private static final OutlineBufferSource TEMP_OUTLINE = new OutlineBufferSource();
  private static final PoseStack TEMP_POSE = new PoseStack();

  public static boolean isCapturing() {
    return capturing;
  }

  public static boolean isBakedSignWood(long posKey) {
    return bakedValid && bakedSignsFlag && findBakedSlot(posKey) >= 0;
  }

  public static void invalidate() {
    bakedValid = false;
    needsBake = false;
    armedForDraw = false;
    bakedListSize = 0;
    verifyCursor = 0;
    bakedGeneration++;
    for (int i = 0; i < BATCHES.size(); i++) {
      try {
        GpuBuffer vbo = BATCHES.get(i).vbo;
        if (vbo != null && !vbo.isClosed()) vbo.close();
      } catch (Throwable ignored) {
      }
    }
    BATCHES.clear();
  }

  public static void beginExtract(ClientLevel level) {
    frameCandidateCount = 0;
    unbakedFrameCount = 0;
    armedForDraw = false;
    boolean active = OptimizationBridge.snapshotActive
        && (OptimizationBridge.isOptActive(OptimizationFlags.OPT_040_STATIC_BE_CHUNK_BAKE)
            || OptimizationBridge.isOptActive(OptimizationFlags.OPT_051_DECORATED_POT_BELL_BAKE));
    if (!active || level == null) {
      if (bakedValid || !BATCHES.isEmpty()) invalidate();
      bakedLevel = null;
      return;
    }
    boolean chestsOn = OptimizationBridge.isOptActive(OptimizationFlags.OPT_040_STATIC_BE_CHUNK_BAKE);
    boolean signsOn = OptimizationBridge.isOptActive(OptimizationFlags.OPT_051_DECORATED_POT_BELL_BAKE);
    int sky = level.getSkyDarken();
    long tick = level.getGameTime();
    sameTickFastHit = (level == bakedLevel && tick == lastVerifyTick);
    lastVerifyTick = tick;
    if (level != bakedLevel || sky != bakedSkyDarken || chestsOn != bakedChestsFlag || signsOn != bakedSignsFlag) {
      invalidate();
      bakedLevel = level;
      bakedSkyDarken = sky;
      bakedChestsFlag = chestsOn;
      bakedSignsFlag = signsOn;
      sameTickFastHit = false;
    } else if (!sameTickFastHit && bakedValid && bakedListSize > 0) {
      // Amortized O(1) verification (checks 1/16th of baked BEs per frame for block removal/break)
      int checks = Math.max(8, bakedListSize >> 4);
      for (int c = 0; c < checks; c++) {
        if (verifyCursor >= bakedListSize) verifyCursor = 0;
        long pk = BAKED_POS_LIST[verifyCursor++];
        VERIFY_POS.set(BlockPos.getX(pk), BlockPos.getY(pk), BlockPos.getZ(pk));
        BlockEntity be = level.getBlockEntity(VERIFY_POS);
        if (be == null || !isEligibleBe(be, chestsOn, signsOn)) {
          invalidate();
          break;
        }
        int slot = findBakedSlot(pk);
        if (slot < 0 || BAKED_STATE_HASH[slot] != System.identityHashCode(be.getBlockState())) {
          invalidate();
          break;
        }
      }
    }
  }

  private static boolean isEligibleBe(BlockEntity be, boolean includeChests, boolean includeSigns) {
    return (includeChests && (be instanceof ChestBlockEntity
        || be instanceof EnderChestBlockEntity
        || be instanceof DecoratedPotBlockEntity
        || be instanceof SkullBlockEntity))
        || (includeSigns && be instanceof SignBlockEntity);
  }

  private static boolean isStaticClosed(BlockEntity be, float partialTick, ModelFeatureRenderer.CrumblingOverlay crumble) {
    if (crumble != null) return false;
    if (be instanceof LidBlockEntity) {
      return ((LidBlockEntity) be).getOpenNess(partialTick) <= 0.0001f;
    }
    return true;
  }

  private static boolean shouldSkipBakedSignText(SignBlockEntity sign, BlockPos pos) {
    if (!OptimizationBridge.opt042SignText) return false;
    double dx = pos.getX() + 0.5 - OptimizationBridge.camX;
    double dy = pos.getY() + 0.5 - OptimizationBridge.camY;
    double dz = pos.getZ() + 0.5 - OptimizationBridge.camZ;
    if (OptimizationBridge.signTextDistSq > 0.0 && dx * dx + dy * dy + dz * dz > OptimizationBridge.signTextDistSq) {
      return true;
    }
    net.minecraft.world.level.block.state.BlockState bs = sign.getBlockState();
    double nx = 0.0, nz = 0.0;
    boolean hasNormal = false;
    if (bs.getBlock() instanceof net.minecraft.world.level.block.StandingSignBlock) {
      int rot = bs.getValue(net.minecraft.world.level.block.StandingSignBlock.ROTATION);
      double ang = rot * (Math.PI / 8.0);
      nx = -Math.sin(ang);
      nz = Math.cos(ang);
      hasNormal = true;
    } else if (bs.getBlock() instanceof net.minecraft.world.level.block.WallSignBlock) {
      net.minecraft.core.Direction dir = bs.getValue(net.minecraft.world.level.block.WallSignBlock.FACING);
      nx = dir.getStepX();
      nz = dir.getStepZ();
      hasNormal = true;
    }
    if (!hasNormal) return false;
    double dot = nx * OptimizationBridge.lookX + nz * OptimizationBridge.lookZ;
    net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
    boolean frontVisible = dot <= 0.15 && ''' + (
        'sign.getText(net.minecraft.world.level.block.entity.SignTextSlot.FRONT).hasMessage(net.minecraft.client.Minecraft.getInstance().isTextFilteringEnabled());'
        if v >= (26, 3) else
        'sign.getFrontText().hasMessage(player);'
    ) + '''
    boolean backVisible = (-dot) <= 0.15 && ''' + (
        'sign.getText(net.minecraft.world.level.block.entity.SignTextSlot.BACK).hasMessage(net.minecraft.client.Minecraft.getInstance().isTextFilteringEnabled());'
        if v >= (26, 3) else
        'sign.getBackText().hasMessage(player);'
    ) + '''
    return !frontVisible && !backVisible;
  }

  /** Ultra-fast O(1) check for already-baked BEs BEFORE 3D-DDA occlusion rays run. */
  public static boolean isAlreadyBakedFastHit(
      BlockEntity be, float partialTick, ModelFeatureRenderer.CrumblingOverlay crumble) {
    if (!bakedValid || crumble != null) return false;
    BlockPos pos = be.getBlockPos();
    if (pos == null) return false;
    int slot = findBakedSlot(pos.asLong());
    if (slot < 0) return false;
    if (be instanceof SignBlockEntity) {
      if (!sameTickFastHit && BAKED_STATE_HASH[slot] != System.identityHashCode(be.getBlockState())) {
        invalidate();
        return false;
      }
      return shouldSkipBakedSignText((SignBlockEntity) be, pos);
    }
    if (sameTickFastHit) return true;
    if (BAKED_STATE_HASH[slot] != System.identityHashCode(be.getBlockState())
        || !isStaticClosed(be, partialTick, null)) {
      invalidate();
      return false;
    }
    return true;
  }

  public static boolean trySkipStaticBe(
      BlockEntity be, float partialTick, ModelFeatureRenderer.CrumblingOverlay crumble) {
    if (!OptimizationBridge.snapshotActive) return false;
    boolean chestsOn = OptimizationBridge.isOptActive(OptimizationFlags.OPT_040_STATIC_BE_CHUNK_BAKE);
    boolean signsOn = OptimizationBridge.isOptActive(OptimizationFlags.OPT_051_DECORATED_POT_BELL_BAKE);
    if (!chestsOn && !signsOn) return false;
    boolean eligible = (chestsOn && (be instanceof ChestBlockEntity || be instanceof EnderChestBlockEntity
            || be instanceof DecoratedPotBlockEntity || be instanceof SkullBlockEntity))
        || (signsOn && be instanceof SignBlockEntity);
    if (!eligible) return false;
    BlockPos pos = be.getBlockPos();
    if (pos == null) return false;
    long pk = pos.asLong();
    int stateHash = System.identityHashCode(be.getBlockState());

    if (!isStaticClosed(be, partialTick, crumble)) {
      if (bakedValid && findBakedSlot(pk) >= 0) {
        invalidate();
      }
      return false;
    }

    frameCandidateCount++;
    if (bakedValid) {
      int slot = findBakedSlot(pk);
      if (slot >= 0) {
        if (BAKED_STATE_HASH[slot] == stateHash) {
          if (be instanceof SignBlockEntity) {
            return shouldSkipBakedSignText((SignBlockEntity) be, pos);
          }
          return true;
        }
        invalidate();
        return false;
      }
      unbakedFrameCount++;
      if (unbakedFrameCount >= MIN_BAKE_COUNT && OptimizationBridge.frameIndex - lastBakeFrame >= 60L) {
        invalidate();
        needsBake = true;
      }
      return false;
    }
    needsBake = true;
    return false;
  }

  private static boolean isEligibleState(BlockEntityRenderState st, boolean chestsOn, boolean includeSigns) {
    if (st == null || st.breakProgress != null || st.blockPos == null) return false;
    if (st instanceof ChestRenderState) {
      return chestsOn && ((ChestRenderState) st).open <= 0.0001f;
    }
    if (st instanceof DecoratedPotRenderState || st instanceof SkullBlockRenderState) {
      return chestsOn;
    }
    return includeSigns && st instanceof SignRenderState;
  }

  public static void bakeAndFilter(BlockEntityRenderDispatcher dispatcher, LevelRenderState levelState) {
    boolean chestsOn = OptimizationBridge.snapshotActive
        && OptimizationBridge.isOptActive(OptimizationFlags.OPT_040_STATIC_BE_CHUNK_BAKE);
    boolean signsOn = OptimizationBridge.snapshotActive
        && OptimizationBridge.isOptActive(OptimizationFlags.OPT_051_DECORATED_POT_BELL_BAKE);
    if (!chestsOn && !signsOn) {
      armedForDraw = false;
      return;
    }
    if (!bakedValid && needsBake && frameCandidateCount >= MIN_BAKE_COUNT
        && levelState != null && levelState.blockEntityRenderStates != null
        && levelState.blockEntityRenderStates.size() >= MIN_BAKE_COUNT) {
      List<BlockEntityRenderState> all = levelState.blockEntityRenderStates;
      List<BlockEntityRenderState> toBake = new ArrayList<BlockEntityRenderState>();
      for (int i = 0; i < all.size(); i++) {
        BlockEntityRenderState st = all.get(i);
        if (isEligibleState(st, chestsOn, signsOn)) {
          toBake.add(st);
        }
      }
      if (toBake.size() >= MIN_BAKE_COUNT) {
        bakeStates(dispatcher, levelState, toBake);
        if (bakedValid && !BATCHES.isEmpty()) {
          // Remove newly baked non-sign states so they render ONCE from VRAM on this frame too
          int write = 0;
          for (int i = 0; i < all.size(); i++) {
            BlockEntityRenderState st = all.get(i);
            boolean baked = isEligibleState(st, chestsOn, signsOn)
                && findBakedSlot(st.blockPos.asLong()) >= 0;
            if (!baked || st instanceof SignRenderState) {
              all.set(write++, st);
            }
          }
          while (all.size() > write) all.remove(all.size() - 1);
        }
      }
    }
    armedForDraw = bakedValid && !BATCHES.isEmpty();
  }

  private static void bakeStates(
      BlockEntityRenderDispatcher dispatcher,
      LevelRenderState levelState,
      List<BlockEntityRenderState> states) {
    invalidate();
    anchorX = (int) Math.floor(OptimizationBridge.camX);
    anchorY = (int) Math.floor(OptimizationBridge.camY);
    anchorZ = (int) Math.floor(OptimizationBridge.camZ);

    SubmitNodeStorage storage = new SubmitNodeStorage();
    int added = 0;
    for (int i = 0; i < states.size(); i++) {
      BlockEntityRenderState st = states.get(i);
      BlockPos pos = st.blockPos;
      if (pos == null) continue;
      long pk = pos.asLong();
      int stateHash = System.identityHashCode(st.blockState);
      if (!insertBaked(pk, stateHash)) continue;
      if (bakedListSize < BAKED_POS_LIST.length) {
        BAKED_POS_LIST[bakedListSize++] = pk;
      }
      TEMP_POSE.pushPose();
      TEMP_POSE.translate(pos.getX() - anchorX, pos.getY() - anchorY, pos.getZ() - anchorZ);
      boolean isSign = st instanceof SignRenderState;
      if (isSign) bakingSignWoodOnly = true;
      try {
        dispatcher.submit(st, TEMP_POSE, storage, levelState.cameraRenderState);
      } finally {
        if (isSign) bakingSignWoodOnly = false;
      }
      TEMP_POSE.popPose();
      added++;
    }
    if (added < MIN_BAKE_COUNT) {
      invalidate();
      return;
    }

    // CRITICAL: capturing = true MUST wrap BOTH render() AND endBatch() because
    // MultiBufferSource.immediate() auto-flushes previous RenderTypes via endLastBatch()!
    capturing = true;
    try (ByteBufferBuilder shared = new ByteBufferBuilder(262144)) {
      MultiBufferSource.BufferSource buf = MultiBufferSource.immediate(shared);
      for (SubmitNodeCollection col : storage.getSubmitsPerOrder().values()) {
        TEMP_MODEL_RENDERER.render(col, buf, TEMP_OUTLINE, buf);
        TEMP_PART_RENDERER.render(col, buf, TEMP_OUTLINE, buf);
      }
      buf.endBatch();
    } catch (Throwable t) {
      invalidate();
      return;
    } finally {
      capturing = false;
    }
    storage.endFrame();
    bakedValid = !BATCHES.isEmpty();
    needsBake = false;
    lastBakeFrame = OptimizationBridge.frameIndex;
  }

  public static void onCapturedMesh(RenderType rt, MeshData mesh) {
    if (rt == null || mesh == null) return;
    int indexCount = mesh.drawState().indexCount();
    ByteBuffer vb = mesh.vertexBuffer();
    if (indexCount <= 0 || vb == null || !vb.hasRemaining()) return;
    GpuBuffer gpuBuf = RenderSystem.getDevice().createBuffer(
        () -> "NightClient StaticBE", GpuBuffer.USAGE_VERTEX, vb);
    BATCHES.add(new Batch(rt, gpuBuf, indexCount));
  }

  public static void drawIfArmed() {
    if (!armedForDraw) return;
    armedForDraw = false;
    if (!bakedValid || BATCHES.isEmpty()) return;
    float tx = (float) (anchorX - OptimizationBridge.camX);
    float ty = (float) (anchorY - OptimizationBridge.camY);
    float tz = (float) (anchorZ - OptimizationBridge.camZ);
    for (int i = 0; i < BATCHES.size(); i++) {
      Batch b = BATCHES.get(i);
      if (b.renderType instanceof RetainedRenderTypeDrawer) {
        ((RetainedRenderTypeDrawer) b.renderType).nightclient$drawRetained(b.vbo, b.indexCount, tx, ty, tz);
      }
    }
  }

  private static boolean insertBaked(long key, int stateHash) {
    int slot = mix(key) & MASK;
    for (int p = 0; p < 16; p++) {
      int idx = (slot + p) & MASK;
      if (BAKED_GEN[idx] != bakedGeneration) {
        BAKED_GEN[idx] = bakedGeneration;
        BAKED_KEYS[idx] = key;
        BAKED_STATE_HASH[idx] = stateHash;
        return true;
      }
      if (BAKED_KEYS[idx] == key) return false;
    }
    return false;
  }

  private static int findBakedSlot(long key) {
    int slot = mix(key) & MASK;
    for (int p = 0; p < 16; p++) {
      int idx = (slot + p) & MASK;
      if (BAKED_GEN[idx] != bakedGeneration) return -1;
      if (BAKED_KEYS[idx] == key) return idx;
    }
    return -1;
  }

  private static int mix(long z) {
    z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
    z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
    return (int) (z ^ (z >>> 31));
  }'''

        static_entity_imports = f'''import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.feature.BlockFeatureRenderer;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.feature.ModelPartFeatureRenderer;
import net.minecraft.client.renderer.feature.ShadowFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import {state_pkg}.LevelRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;'''

        static_entity_body = '''  private static final int CAP = 4096;
  private static final int MASK = CAP - 1;
  private static final int MIN_BAKE_COUNT = 8;
  private static final EquipmentSlot[] ARMOR_SLOTS = {
      EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
      EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
  };

  private static final class Batch {
    final RenderType renderType;
    final GpuBuffer vbo;
    final int indexCount;
    Batch(RenderType renderType, GpuBuffer vbo, int indexCount) {
      this.renderType = renderType;
      this.vbo = vbo;
      this.indexCount = indexCount;
    }
  }

  private static final class BakedNameTag {
    final double x, y, z;
    final Component tag;
    final Vec3 attachment;
    final int lightCoords;
    final boolean isDiscrete;
    BakedNameTag(double x, double y, double z, Component tag, Vec3 attachment, int lightCoords, boolean isDiscrete) {
      this.x = x; this.y = y; this.z = z;
      this.tag = tag; this.attachment = attachment;
      this.lightCoords = lightCoords; this.isDiscrete = isDiscrete;
    }
  }

  private static final List<Batch> BATCHES = new ArrayList<Batch>();
  private static final List<BakedNameTag> NAMETAGS = new ArrayList<BakedNameTag>();
  private static final int[] BAKED_IDS = new int[CAP];
  private static final int[] BAKED_HASH = new int[CAP];
  private static final long[] BAKED_GEN = new long[CAP];
  private static final int[] BAKED_ID_LIST = new int[2048];

  // Per-frame staging of eligible unbaked entities so bakeStates links EntityRenderState -> Entity id/hash
  private static final int[] CAND_IDS = new int[2048];
  private static final int[] CAND_HASH = new int[2048];
  private static final double[] CAND_X = new double[2048];
  private static final double[] CAND_Y = new double[2048];
  private static final double[] CAND_Z = new double[2048];
  private static int candCount;

  private static long bakedGeneration = 1L;
  private static int bakedListSize;
  private static int verifyCursor;
  private static boolean bakedValid;
  private static boolean needsBake;
  private static boolean capturing;
  private static boolean armedForDraw;
  private static boolean loggedError;
  private static int unbakedFrameCount;
  private static long lastBakeFrame = -1000L;
  private static ClientLevel bakedLevel;
  private static int bakedSkyDarken = -1;
  private static long lastVerifyTick = Long.MIN_VALUE;
  private static boolean sameTickFastHit;
  private static int anchorX, anchorY, anchorZ;

  private static final ModelFeatureRenderer TEMP_MODEL = new ModelFeatureRenderer();
  private static final ModelPartFeatureRenderer TEMP_PART = new ModelPartFeatureRenderer();
  private static final ItemFeatureRenderer TEMP_ITEM = new ItemFeatureRenderer();
  private static final BlockFeatureRenderer TEMP_BLOCK = new BlockFeatureRenderer();
  private static final CustomFeatureRenderer TEMP_CUSTOM = new CustomFeatureRenderer();
  private static final ShadowFeatureRenderer TEMP_SHADOW = new ShadowFeatureRenderer();
  private static final OutlineBufferSource TEMP_OUTLINE = new OutlineBufferSource();
  private static final PoseStack TEMP_POSE = new PoseStack();

  public static boolean isCapturing() {
    return capturing;
  }

  public static void invalidate() {
    bakedValid = false;
    needsBake = false;
    armedForDraw = false;
    bakedListSize = 0;
    verifyCursor = 0;
    bakedGeneration++;
    NAMETAGS.clear();
    for (int i = 0; i < BATCHES.size(); i++) {
      try {
        GpuBuffer vbo = BATCHES.get(i).vbo;
        if (vbo != null && !vbo.isClosed()) vbo.close();
      } catch (Throwable ignored) {
      }
    }
    BATCHES.clear();
  }

  public static void beginExtract(ClientLevel level) {
    candCount = 0;
    unbakedFrameCount = 0;
    armedForDraw = false;
    if (!OptimizationBridge.opt028StaticEntityBake || level == null) {
      if (bakedValid || !BATCHES.isEmpty()) invalidate();
      bakedLevel = null;
      return;
    }
    int sky = level.getSkyDarken();
    long tick = level.getGameTime();
    sameTickFastHit = (level == bakedLevel && tick == lastVerifyTick);
    lastVerifyTick = tick;
    if (level != bakedLevel || sky != bakedSkyDarken) {
      invalidate();
      bakedLevel = level;
      bakedSkyDarken = sky;
      sameTickFastHit = false;
    } else if (!sameTickFastHit && bakedValid && bakedListSize > 0) {
      int checks = Math.max(8, bakedListSize >> 3);
      for (int c = 0; c < checks; c++) {
        if (verifyCursor >= bakedListSize) verifyCursor = 0;
        int id = BAKED_ID_LIST[verifyCursor++];
        Entity e = level.getEntity(id);
        if (e == null || e.isRemoved() || !isStaticEligible(e)) {
          invalidate();
          break;
        }
        int slot = findBakedSlot(id);
        if (slot < 0 || BAKED_HASH[slot] != computeHash(e)) {
          invalidate();
          break;
        }
      }
    }
  }

  private static boolean isStaticEligible(Entity e) {
    if (e.isCurrentlyGlowing() || e.displayFireAnimation() || e.isPassenger() || e.isVehicle()) {
      return false;
    }
    if (e instanceof ArmorStand) {
      ArmorStand as = (ArmorStand) e;
      if (as.isInvisible() || as.hurtTime > 0 || as.deathTime > 0) return false;
      if (as.getX() != as.xo || as.getY() != as.yo || as.getZ() != as.zo) return false;
      if (as.getYRot() != as.yRotO) return false;
      Vec3 vel = as.getDeltaMovement();
      if (vel.x * vel.x + vel.z * vel.z > 1.0e-5) return false;
      for (int i = 0; i < ARMOR_SLOTS.length; i++) {
        ItemStack st = as.getItemBySlot(ARMOR_SLOTS[i]);
        if (!st.isEmpty() && st.hasFoil()) return false;
      }
      return true;
    }
    if (e instanceof ItemFrame) {
      ItemFrame f = (ItemFrame) e;
      if (f.hasFramedMap()) return false;
      ItemStack item = f.getItem();
      if (!item.isEmpty() && item.hasFoil()) return false;
      return true;
    }
    if (e instanceof Mob) {
      Mob mob = (Mob) e;
      if (!mob.isNoAi() || mob.isInvisible() || mob.hurtTime > 0 || mob.deathTime > 0 || !mob.isAlive()) {
        return false;
      }
      if (mob.getX() != mob.xo || mob.getY() != mob.yo || mob.getZ() != mob.zo) return false;
      if (mob.yBodyRot != mob.yBodyRotO || mob.yHeadRot != mob.yHeadRotO || mob.getXRot() != mob.xRotO) {
        return false;
      }
      if (mob.walkAnimation.speed() > 1.0e-4f) return false;
      Vec3 vel = mob.getDeltaMovement();
      if (vel.x * vel.x + vel.z * vel.z > 1.0e-5) return false;
      for (int i = 0; i < ARMOR_SLOTS.length; i++) {
        ItemStack st = mob.getItemBySlot(ARMOR_SLOTS[i]);
        if (!st.isEmpty() && st.hasFoil()) return false;
      }
      return true;
    }
    return false;
  }

  private static int computeHash(Entity e) {
    long h = Double.doubleToLongBits(e.getX()) * 31L
        ^ Double.doubleToLongBits(e.getY()) * 131L
        ^ Double.doubleToLongBits(e.getZ()) * 997L;
    int r = (int) (h ^ (h >>> 32));
    if (e instanceof ArmorStand) {
      ArmorStand as = (ArmorStand) e;
      r = r * 31 + Float.floatToRawIntBits(as.getYRot());
      r = r * 31 + (as.isSmall() ? 1 : 0) + (as.showArms() ? 2 : 0)
          + (as.showBasePlate() ? 4 : 0) + (as.isMarker() ? 8 : 0);
      r = r * 31 + as.getHeadPose().hashCode();
      r = r * 31 + as.getBodyPose().hashCode();
      r = r * 31 + as.getLeftArmPose().hashCode();
      r = r * 31 + as.getRightArmPose().hashCode();
      r = r * 31 + as.getLeftLegPose().hashCode();
      r = r * 31 + as.getRightLegPose().hashCode();
      for (int i = 0; i < ARMOR_SLOTS.length; i++) {
        ItemStack st = as.getItemBySlot(ARMOR_SLOTS[i]);
        r = r * 31 + (st.isEmpty() ? 0 : System.identityHashCode(st.getItem()) ^ st.getCount());
      }
    } else if (e instanceof ItemFrame) {
      ItemFrame f = (ItemFrame) e;
      r = r * 31 + f.getDirection().ordinal();
      r = r * 31 + f.getRotation();
      r = r * 31 + (f.isInvisible() ? 1 : 0);
      ItemStack item = f.getItem();
      r = r * 31 + (item.isEmpty() ? 0 : System.identityHashCode(item.getItem()) ^ item.getCount());
    } else if (e instanceof Mob) {
      Mob mob = (Mob) e;
      r = r * 31 + Float.floatToRawIntBits(mob.yBodyRot);
      r = r * 31 + Float.floatToRawIntBits(mob.yHeadRot);
      r = r * 31 + Float.floatToRawIntBits(mob.getXRot());
      r = r * 31 + (mob.isBaby() ? 1 : 0);
      for (int i = 0; i < ARMOR_SLOTS.length; i++) {
        ItemStack st = mob.getItemBySlot(ARMOR_SLOTS[i]);
        r = r * 31 + (st.isEmpty() ? 0 : System.identityHashCode(st.getItem()) ^ st.getCount());
      }
      if (mob instanceof Villager) {
        r = r * 31 + ((Villager) mob).getVillagerData().hashCode();
      }
    }
    return r;
  }

  public static boolean trySkipStaticEntity(Entity e) {
    if (!OptimizationBridge.opt028StaticEntityBake) return false;
    if (bakedValid) {
      int slot = findBakedSlot(e.getId());
      if (slot >= 0) {
        if (sameTickFastHit && e.getX() == e.xo && e.getY() == e.yo && e.getZ() == e.zo) {
          return true;
        }
        if (isStaticEligible(e) && BAKED_HASH[slot] == computeHash(e)) {
          return true;
        }
        invalidate();
        return false;
      }
    }
    if (!(e instanceof ArmorStand) && !(e instanceof ItemFrame) && !(e instanceof Mob && ((Mob) e).isNoAi())) {
      return false;
    }
    int id = e.getId();
    if (!isStaticEligible(e)) {
      return false;
    }
    int hash = computeHash(e);
    if (bakedValid) {
      unbakedFrameCount++;
      if (unbakedFrameCount >= MIN_BAKE_COUNT && OptimizationBridge.frameIndex - lastBakeFrame >= 60L) {
        invalidate();
        needsBake = true;
      } else {
        return false;
      }
    } else {
      needsBake = true;
    }
    if (candCount < CAND_IDS.length) {
      int idx = candCount++;
      CAND_IDS[idx] = id;
      CAND_HASH[idx] = hash;
      CAND_X[idx] = e.getX();
      CAND_Y[idx] = e.getY();
      CAND_Z[idx] = e.getZ();
    }
    return false;
  }

  public static void bakeAndFilter(
      EntityRenderDispatcher dispatcher,
      LevelRenderState levelState,
      PoseStack pose,
      SubmitNodeCollector collector) {
    if (!OptimizationBridge.opt028StaticEntityBake) {
      armedForDraw = false;
      return;
    }
    if (!bakedValid && needsBake && candCount >= MIN_BAKE_COUNT
        && levelState != null && levelState.entityRenderStates != null
        && levelState.entityRenderStates.size() >= MIN_BAKE_COUNT) {
      List<EntityRenderState> all = levelState.entityRenderStates;
      List<EntityRenderState> toBake = new ArrayList<EntityRenderState>();
      int[] matchIdx = new int[all.size()];
      int matchLen = 0;
      boolean[] used = new boolean[candCount];
      for (int i = 0; i < all.size(); i++) {
        EntityRenderState st = all.get(i);
        if (!isEligibleState(st)) continue;
        int found = -1;
        for (int j = 0; j < candCount; j++) {
          if (!used[j]
              && Math.abs(CAND_X[j] - st.x) < 0.05
              && Math.abs(CAND_Y[j] - st.y) < 0.05
              && Math.abs(CAND_Z[j] - st.z) < 0.05) {
            found = j;
            used[j] = true;
            break;
          }
        }
        if (found >= 0) {
          toBake.add(st);
          matchIdx[matchLen++] = found;
        }
      }
      if (toBake.size() >= MIN_BAKE_COUNT) {
        bakeStates(dispatcher, levelState, toBake, matchIdx);
        if (bakedValid && !BATCHES.isEmpty()) {
          int write = 0;
          int nextBaked = 0;
          for (int i = 0; i < all.size(); i++) {
            EntityRenderState st = all.get(i);
            if (nextBaked < toBake.size() && st == toBake.get(nextBaked)) {
              nextBaked++;
            } else {
              all.set(write++, st);
            }
          }
          while (all.size() > write) all.remove(all.size() - 1);
        }
      }
    }
    if (bakedValid && !BATCHES.isEmpty()) {
      armedForDraw = true;
      if (!NAMETAGS.isEmpty() && levelState != null && levelState.cameraRenderState != null) {
        Vec3 cam = levelState.cameraRenderState.pos;
        double cx = cam.x(), cy = cam.y(), cz = cam.z();
        for (int i = 0; i < NAMETAGS.size(); i++) {
          BakedNameTag nt = NAMETAGS.get(i);
          double dx = nt.x - cx, dy = nt.y - cy, dz = nt.z - cz;
          double distSq = dx * dx + dy * dy + dz * dz;
          if (OptimizationBridge.cullNametag(distSq, true, false, false)) continue;
          pose.pushPose();
          pose.translate(dx, dy, dz);
          collector.submitNameTag(
              pose, nt.attachment, 0, nt.tag, !nt.isDiscrete, nt.lightCoords, distSq,
              levelState.cameraRenderState);
          pose.popPose();
        }
      }
    } else {
      armedForDraw = false;
    }
  }

  private static boolean isEligibleState(EntityRenderState st) {
    if (st == null || st.outlineColor != 0 || st.displayFireAnimation) return false;
    if (st instanceof ArmorStandRenderState) {
      ArmorStandRenderState as = (ArmorStandRenderState) st;
      return !as.isInvisible && as.wiggle == 0.0f;
    }
    if (st instanceof ItemFrameRenderState) {
      ItemFrameRenderState f = (ItemFrameRenderState) st;
      return f.mapId == null;
    }
    if (st instanceof LivingEntityRenderState) {
      LivingEntityRenderState le = (LivingEntityRenderState) st;
      return !le.isInvisible && !le.hasRedOverlay && le.deathTime == 0.0f && le.walkAnimationSpeed < 1.0e-4f;
    }
    return false;
  }

  private static void bakeStates(
      EntityRenderDispatcher dispatcher,
      LevelRenderState levelState,
      List<EntityRenderState> states,
      int[] matchIdx) {
    invalidate();
    anchorX = (int) Math.floor(OptimizationBridge.camX);
    anchorY = (int) Math.floor(OptimizationBridge.camY);
    anchorZ = (int) Math.floor(OptimizationBridge.camZ);

    SubmitNodeStorage storage = new SubmitNodeStorage();
    int added = 0;
    for (int i = 0; i < states.size(); i++) {
      EntityRenderState st = states.get(i);
      int ci = matchIdx[i];
      int id = CAND_IDS[ci];
      int hash = CAND_HASH[ci];
      if (!insertBaked(id, hash)) continue;
      if (bakedListSize < BAKED_ID_LIST.length) {
        BAKED_ID_LIST[bakedListSize++] = id;
      }
      Component savedTag = st.nameTag;
      if (savedTag != null && st.nameTagAttachment != null) {
        NAMETAGS.add(new BakedNameTag(st.x, st.y, st.z, savedTag, st.nameTagAttachment, st.lightCoords, st.isDiscrete));
        st.nameTag = null;
      }
      try {
        dispatcher.submit(
            st, levelState.cameraRenderState,
            st.x - anchorX, st.y - anchorY, st.z - anchorZ,
            TEMP_POSE, storage);
      } finally {
        st.nameTag = savedTag;
      }
      added++;
    }
    if (added < MIN_BAKE_COUNT) {
      invalidate();
      return;
    }

    capturing = true;
    try (ByteBufferBuilder shared = new ByteBufferBuilder(262144)) {
      MultiBufferSource.BufferSource buf = MultiBufferSource.immediate(shared);
      var blockRenderer = Minecraft.getInstance().getBlockRenderer();
      for (SubmitNodeCollection col : storage.getSubmitsPerOrder().values()) {
        TEMP_MODEL.render(col, buf, TEMP_OUTLINE, buf);
        TEMP_PART.render(col, buf, TEMP_OUTLINE, buf);
        TEMP_ITEM.render(col, buf, TEMP_OUTLINE);
        TEMP_BLOCK.render(col, buf, blockRenderer, TEMP_OUTLINE);
        TEMP_CUSTOM.render(col, buf);
        TEMP_SHADOW.render(col, buf);
      }
      buf.endBatch();
    } catch (Throwable t) {
      if (!loggedError) {
        loggedError = true;
        System.err.println("[NightClient] StaticEntityVboCache bake failed: " + t);
        t.printStackTrace();
      }
      invalidate();
      return;
    } finally {
      capturing = false;
    }
    storage.endFrame();
    bakedValid = !BATCHES.isEmpty();
    needsBake = false;
    lastBakeFrame = OptimizationBridge.frameIndex;
  }

  public static void onCapturedMesh(RenderType rt, MeshData mesh) {
    if (rt == null || mesh == null) return;
    int indexCount = mesh.drawState().indexCount();
    ByteBuffer vb = mesh.vertexBuffer();
    if (indexCount <= 0 || vb == null || !vb.hasRemaining()) return;
    GpuBuffer gpuBuf = RenderSystem.getDevice().createBuffer(
        () -> "NightClient StaticEntity", GpuBuffer.USAGE_VERTEX, vb);
    BATCHES.add(new Batch(rt, gpuBuf, indexCount));
  }

  public static void drawIfArmed() {
    if (!armedForDraw) return;
    armedForDraw = false;
    if (!bakedValid || BATCHES.isEmpty()) return;
    float tx = (float) (anchorX - OptimizationBridge.camX);
    float ty = (float) (anchorY - OptimizationBridge.camY);
    float tz = (float) (anchorZ - OptimizationBridge.camZ);
    for (int i = 0; i < BATCHES.size(); i++) {
      Batch b = BATCHES.get(i);
      if (b.renderType instanceof RetainedRenderTypeDrawer) {
        ((RetainedRenderTypeDrawer) b.renderType).nightclient$drawRetained(b.vbo, b.indexCount, tx, ty, tz);
      }
    }
  }

  private static boolean insertBaked(int id, int hash) {
    int slot = mix(id) & MASK;
    for (int p = 0; p < 16; p++) {
      int idx = (slot + p) & MASK;
      if (BAKED_GEN[idx] != bakedGeneration) {
        BAKED_GEN[idx] = bakedGeneration;
        BAKED_IDS[idx] = id;
        BAKED_HASH[idx] = hash;
        return true;
      }
      if (BAKED_IDS[idx] == id) return false;
    }
    return false;
  }

  private static int findBakedSlot(int id) {
    int slot = mix(id) & MASK;
    for (int p = 0; p < 16; p++) {
      int idx = (slot + p) & MASK;
      if (BAKED_GEN[idx] != bakedGeneration) return -1;
      if (BAKED_IDS[idx] == id) return idx;
    }
    return -1;
  }

  private static int mix(int x) {
    x ^= x >>> 16;
    x *= 0x7feb352d;
    x ^= x >>> 15;
    x *= 0x846ca68b;
    return x ^ (x >>> 16);
  }'''

        static_be_level_owner = 'LevelRenderer'
        static_be_level_imports = (
            'import com.mojang.blaze3d.vertex.PoseStack;\n'
            'import net.minecraft.client.Camera;\n'
            'import net.minecraft.client.DeltaTracker;\n'
            'import net.minecraft.client.multiplayer.ClientLevel;\n'
            'import net.minecraft.client.renderer.LevelRenderer;\n'
            'import net.minecraft.client.renderer.SubmitNodeCollector;\n'
            'import net.minecraft.client.renderer.SubmitNodeStorage;\n'
            'import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;\n'
            'import net.minecraft.client.renderer.culling.Frustum;\n'
            'import net.minecraft.client.renderer.entity.EntityRenderDispatcher;\n'
            f'import {state_pkg}.LevelRenderState;'
        )
        static_be_level_extra = (
            'import org.spongepowered.asm.mixin.Final;\n'
            'import org.spongepowered.asm.mixin.Shadow;\n'
            'import org.spongepowered.asm.mixin.injection.At;\n'
            'import org.spongepowered.asm.mixin.injection.Inject;\n'
            'import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;'
        )
        static_be_level_body = f'''  @Shadow private ClientLevel level;
  @Shadow @Final private BlockEntityRenderDispatcher blockEntityRenderDispatcher;
  @Shadow @Final private EntityRenderDispatcher entityRenderDispatcher;

  @Inject(method = "extractVisibleBlockEntities(Lnet/minecraft/client/Camera;FL{state_desc_pkg}/LevelRenderState;)V", at = @At("HEAD"))
  private void nightclient$staticBeBeginExtract(Camera camera, float partialTick, LevelRenderState state, CallbackInfo ci) {{
    StaticBeVboCache.beginExtract(this.level);
  }}

  @Inject(method = "submitBlockEntities(Lcom/mojang/blaze3d/vertex/PoseStack;L{state_desc_pkg}/LevelRenderState;Lnet/minecraft/client/renderer/SubmitNodeStorage;)V", at = @At("HEAD"))
  private void nightclient$staticBeBakeAndFilter(PoseStack pose, LevelRenderState state, SubmitNodeStorage storage, CallbackInfo ci) {{
    StaticBeVboCache.bakeAndFilter(this.blockEntityRenderDispatcher, state);
  }}

  @Inject(method = "extractVisibleEntities(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;Lnet/minecraft/client/DeltaTracker;L{state_desc_pkg}/LevelRenderState;)V", at = @At("HEAD"))
  private void nightclient$staticEntityBeginExtract(Camera camera, Frustum frustum, DeltaTracker delta, LevelRenderState state, CallbackInfo ci) {{
    StaticEntityVboCache.beginExtract(this.level);
  }}

  @Inject(method = "submitEntities(Lcom/mojang/blaze3d/vertex/PoseStack;L{state_desc_pkg}/LevelRenderState;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V", at = @At("HEAD"))
  private void nightclient$staticEntityBakeAndFilter(PoseStack pose, LevelRenderState state, SubmitNodeCollector collector, CallbackInfo ci) {{
    StaticEntityVboCache.bakeAndFilter(this.entityRenderDispatcher, state, pose, collector);
  }}'''

        static_be_feature_owner = 'FeatureRenderDispatcher'
        static_be_feature_imports = 'import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;'
        static_be_feature_extra = (
            'import org.spongepowered.asm.mixin.injection.At;\n'
            'import org.spongepowered.asm.mixin.injection.Inject;\n'
            'import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;'
        )
        static_be_feature_body = '''  @Inject(method = "renderAllFeatures()V", at = @At("TAIL"))
  private void nightclient$drawRetainedStaticBes(CallbackInfo ci) {
    dev.nightclient.platform.NativeGpuBridge.flushFrame();
    StaticBeVboCache.drawIfArmed();
    StaticEntityVboCache.drawIfArmed();
  }'''
    elif has_retained_be_26_2:
        retained_rt_owner = ''
        retained_rt_imports = ''
        retained_rt_extra = ''
        retained_rt_body = ''

        static_be_imports = '''import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.nightclient.config.OptimizationFlags;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.blockentity.state.ChestRenderState;
import net.minecraft.client.renderer.blockentity.state.DecoratedPotRenderState;
import net.minecraft.client.renderer.blockentity.state.SignRenderState;
import net.minecraft.client.renderer.blockentity.state.SkullBlockRenderState;
import net.minecraft.client.renderer.feature.BlockModelFeatureRenderer;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.FeatureRendererType;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.feature.ShadowFeatureRenderer;
import net.minecraft.client.renderer.feature.phase.FeatureRenderPhase;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import org.joml.Matrix4fStack;'''

        static_be_body = '''  private static final int CAP = 16384;
  private static final int MASK = CAP - 1;
  private static final int MIN_BAKE_COUNT = 16;

  private static final class Batch {
    final RenderType renderType;
    final StagedVertexBuffer.Draw draw;
    final boolean translucent;
    Batch(RenderType renderType, StagedVertexBuffer.Draw draw) {
      this.renderType = renderType;
      this.draw = draw;
      this.translucent = renderType.hasBlending();
    }
  }

  private static final List<Batch> BATCHES = new ArrayList<Batch>();
  private static final long[] BAKED_KEYS = new long[CAP];
  private static final int[] BAKED_STATE_HASH = new int[CAP];
  private static final long[] BAKED_GEN = new long[CAP];
  private static final long[] BAKED_POS_LIST = new long[8192];
  private static final BlockPos.MutableBlockPos VERIFY_POS = new BlockPos.MutableBlockPos();

  private static StagedVertexBuffer bakedVbo;
  private static SubmitNodeStorage pendingStorage;
  private static long bakedGeneration = 1L;
  private static int bakedListSize;
  private static int verifyCursor;
  private static boolean bakedValid;
  private static boolean needsBake;
  private static boolean capturing;
  private static boolean armedSolid;
  private static boolean armedTranslucent;
  private static int unbakedFrameCount;
  private static long lastBakeFrame = -1000L;
  private static ClientLevel bakedLevel;
  private static int bakedSkyDarken = -1;
  private static boolean bakedChestsFlag;
  private static boolean bakedSignsFlag;
  private static long lastVerifyTick = Long.MIN_VALUE;
  private static boolean sameTickFastHit;
  public static boolean bakingSignWoodOnly;
  public static boolean skipCurrentSignWood;
  private static int anchorX, anchorY, anchorZ;
  private static int frameCandidateCount;

  private static final ModelFeatureRenderer TEMP_MODEL = new ModelFeatureRenderer();
  private static final ItemFeatureRenderer TEMP_ITEM = new ItemFeatureRenderer();
  private static final BlockModelFeatureRenderer TEMP_BLOCK = new BlockModelFeatureRenderer();
  private static final CustomFeatureRenderer TEMP_CUSTOM = new CustomFeatureRenderer();
  private static final ShadowFeatureRenderer TEMP_SHADOW = new ShadowFeatureRenderer();
  private static final PoseStack TEMP_POSE = new PoseStack();

  public static boolean isCapturing() {
    return capturing;
  }

  public static boolean isBakedSignWood(long posKey) {
    return bakedValid && bakedSignsFlag && findBakedSlot(posKey) >= 0;
  }

  public static void invalidate() {
    bakedValid = false;
    needsBake = false;
    armedSolid = false;
    armedTranslucent = false;
    pendingStorage = null;
    bakedListSize = 0;
    verifyCursor = 0;
    bakedGeneration++;
    BATCHES.clear();
    if (bakedVbo != null) {
      try {
        bakedVbo.endFrame();
        bakedVbo.close();
      } catch (Throwable ignored) {
      }
      bakedVbo = null;
    }
  }

  private static boolean isChestsActive() {
    return OptimizationBridge.snapshotActive
        && (OptimizationBridge.opt147StaticVramBatch
            || OptimizationBridge.isOptActive(OptimizationFlags.OPT_040_STATIC_BE_CHUNK_BAKE));
  }

  private static boolean isSignsActive() {
    // MC 26.2+ already renders sign wood via chunk block geometry; AbstractSignRenderer only submits text.
    return false;
  }

  public static void beginExtract(ClientLevel level) {
    frameCandidateCount = 0;
    unbakedFrameCount = 0;
    armedSolid = false;
    armedTranslucent = false;
    boolean chestsOn = isChestsActive();
    boolean signsOn = isSignsActive();
    if ((!chestsOn && !signsOn) || level == null) {
      if (bakedValid || bakedVbo != null || pendingStorage != null) invalidate();
      bakedLevel = null;
      return;
    }
    int sky = level.getSkyDarken();
    long tick = level.getGameTime();
    sameTickFastHit = (level == bakedLevel && tick == lastVerifyTick);
    lastVerifyTick = tick;
    if (level != bakedLevel || sky != bakedSkyDarken || chestsOn != bakedChestsFlag || signsOn != bakedSignsFlag) {
      invalidate();
      bakedLevel = level;
      bakedSkyDarken = sky;
      bakedChestsFlag = chestsOn;
      bakedSignsFlag = signsOn;
      sameTickFastHit = false;
    } else if (!sameTickFastHit && bakedValid && bakedListSize > 0) {
      int checks = Math.max(8, bakedListSize >> 4);
      for (int c = 0; c < checks; c++) {
        if (verifyCursor >= bakedListSize) verifyCursor = 0;
        long pk = BAKED_POS_LIST[verifyCursor++];
        VERIFY_POS.set(BlockPos.getX(pk), BlockPos.getY(pk), BlockPos.getZ(pk));
        BlockEntity be = level.getBlockEntity(VERIFY_POS);
        if (be == null || !isEligibleBe(be, chestsOn, signsOn)) {
          invalidate();
          break;
        }
        int slot = findBakedSlot(pk);
        if (slot < 0 || BAKED_STATE_HASH[slot] != System.identityHashCode(be.getBlockState())) {
          invalidate();
          break;
        }
      }
    }
  }

  private static boolean isEligibleBe(BlockEntity be, boolean includeChests, boolean includeSigns) {
    return (includeChests && (be instanceof ChestBlockEntity
        || be instanceof EnderChestBlockEntity
        || be instanceof DecoratedPotBlockEntity
        || be instanceof SkullBlockEntity))
        || (includeSigns && be instanceof SignBlockEntity);
  }

  private static boolean isStaticClosed(BlockEntity be, float partialTick, ModelFeatureRenderer.CrumblingOverlay crumble) {
    if (crumble != null) return false;
    if (be instanceof LidBlockEntity) {
      return ((LidBlockEntity) be).getOpenNess(partialTick) <= 0.0001f;
    }
    return true;
  }

  private static boolean shouldSkipBakedSignText(SignBlockEntity sign, BlockPos pos) {
    if (!OptimizationBridge.opt042SignText) return false;
    double dx = pos.getX() + 0.5 - OptimizationBridge.camX;
    double dy = pos.getY() + 0.5 - OptimizationBridge.camY;
    double dz = pos.getZ() + 0.5 - OptimizationBridge.camZ;
    if (OptimizationBridge.signTextDistSq > 0.0 && dx * dx + dy * dy + dz * dz > OptimizationBridge.signTextDistSq) {
      return true;
    }
    net.minecraft.world.level.block.state.BlockState bs = sign.getBlockState();
    double nx = 0.0, nz = 0.0;
    boolean hasNormal = false;
    if (bs.getBlock() instanceof net.minecraft.world.level.block.StandingSignBlock) {
      int rot = bs.getValue(net.minecraft.world.level.block.StandingSignBlock.ROTATION);
      double ang = rot * (Math.PI / 8.0);
      nx = -Math.sin(ang);
      nz = Math.cos(ang);
      hasNormal = true;
    } else if (bs.getBlock() instanceof net.minecraft.world.level.block.WallSignBlock) {
      net.minecraft.core.Direction dir = bs.getValue(net.minecraft.world.level.block.WallSignBlock.FACING);
      nx = dir.getStepX();
      nz = dir.getStepZ();
      hasNormal = true;
    }
    if (!hasNormal) return false;
    double dot = nx * OptimizationBridge.lookX + nz * OptimizationBridge.lookZ;
    net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
    boolean frontVisible = dot <= 0.15 && ''' + (
        'sign.getText(net.minecraft.world.level.block.entity.SignTextSlot.FRONT).hasMessage(net.minecraft.client.Minecraft.getInstance().isTextFilteringEnabled());'
        if v >= (26, 3) else
        'sign.getFrontText().hasMessage(player);'
    ) + '''
    boolean backVisible = (-dot) <= 0.15 && ''' + (
        'sign.getText(net.minecraft.world.level.block.entity.SignTextSlot.BACK).hasMessage(net.minecraft.client.Minecraft.getInstance().isTextFilteringEnabled());'
        if v >= (26, 3) else
        'sign.getBackText().hasMessage(player);'
    ) + '''
    return !frontVisible && !backVisible;
  }

  public static boolean isAlreadyBakedFastHit(
      BlockEntity be, float partialTick, ModelFeatureRenderer.CrumblingOverlay crumble) {
    if (!bakedValid || crumble != null) return false;
    BlockPos pos = be.getBlockPos();
    if (pos == null) return false;
    int slot = findBakedSlot(pos.asLong());
    if (slot < 0) return false;
    if (be instanceof SignBlockEntity) {
      if (!sameTickFastHit && BAKED_STATE_HASH[slot] != System.identityHashCode(be.getBlockState())) {
        invalidate();
        return false;
      }
      return shouldSkipBakedSignText((SignBlockEntity) be, pos);
    }
    if (sameTickFastHit) return true;
    if (BAKED_STATE_HASH[slot] != System.identityHashCode(be.getBlockState())
        || !isStaticClosed(be, partialTick, null)) {
      invalidate();
      return false;
    }
    return true;
  }

  public static boolean trySkipStaticBe(
      BlockEntity be, float partialTick, ModelFeatureRenderer.CrumblingOverlay crumble) {
    boolean chestsOn = isChestsActive();
    boolean signsOn = isSignsActive();
    if (!chestsOn && !signsOn) return false;
    if (!isEligibleBe(be, chestsOn, signsOn)) return false;
    BlockPos pos = be.getBlockPos();
    if (pos == null) return false;
    long pk = pos.asLong();
    int stateHash = System.identityHashCode(be.getBlockState());

    if (!isStaticClosed(be, partialTick, crumble)) {
      if (bakedValid && findBakedSlot(pk) >= 0) invalidate();
      return false;
    }

    frameCandidateCount++;
    if (bakedValid) {
      int slot = findBakedSlot(pk);
      if (slot >= 0) {
        if (BAKED_STATE_HASH[slot] == stateHash) {
          if (be instanceof SignBlockEntity) {
            return shouldSkipBakedSignText((SignBlockEntity) be, pos);
          }
          return true;
        }
        invalidate();
        return false;
      }
      unbakedFrameCount++;
      if (unbakedFrameCount >= MIN_BAKE_COUNT && OptimizationBridge.frameIndex - lastBakeFrame >= 60L) {
        invalidate();
        needsBake = true;
      }
      return false;
    }
    needsBake = true;
    return false;
  }

  private static boolean isEligibleState(BlockEntityRenderState st, boolean chestsOn, boolean includeSigns) {
    if (st == null || st.breakProgress != null || st.blockPos == null) return false;
    if (st instanceof ChestRenderState) {
      return chestsOn && ((ChestRenderState) st).open <= 0.0001f;
    }
    if (st instanceof DecoratedPotRenderState || st instanceof SkullBlockRenderState) {
      return chestsOn;
    }
    return includeSigns && st instanceof SignRenderState;
  }

  public static void bakeAndFilter(BlockEntityRenderDispatcher dispatcher, LevelRenderState levelState) {
    boolean chestsOn = isChestsActive();
    boolean signsOn = isSignsActive();
    if (!chestsOn && !signsOn) {
      armedSolid = false;
      armedTranslucent = false;
      return;
    }
    if (!bakedValid && needsBake && bakedLevel != null && frameCandidateCount >= MIN_BAKE_COUNT
        && levelState != null && levelState.blockEntityRenderStates != null
        && levelState.blockEntityRenderStates.size() >= MIN_BAKE_COUNT) {
      List<BlockEntityRenderState> all = levelState.blockEntityRenderStates;
      List<BlockEntityRenderState> toBake = new ArrayList<BlockEntityRenderState>();
      for (int i = 0; i < all.size(); i++) {
        BlockEntityRenderState st = all.get(i);
        if (isEligibleState(st, chestsOn, signsOn)) {
          toBake.add(st);
        }
      }
      if (toBake.size() >= MIN_BAKE_COUNT) {
        preparePendingBake(dispatcher, levelState, toBake);
        if (pendingStorage != null) {
          int write = 0;
          for (int i = 0; i < all.size(); i++) {
            BlockEntityRenderState st = all.get(i);
            boolean baked = isEligibleState(st, chestsOn, signsOn)
                && findBakedSlot(st.blockPos.asLong()) >= 0;
            if (!baked || st instanceof SignRenderState) {
              all.set(write++, st);
            }
          }
          while (all.size() > write) all.remove(all.size() - 1);
        }
      }
    }
    if (bakedValid && !BATCHES.isEmpty()) {
      armedSolid = true;
      armedTranslucent = true;
    }
  }

  private static void preparePendingBake(
      BlockEntityRenderDispatcher dispatcher,
      LevelRenderState levelState,
      List<BlockEntityRenderState> states) {
    invalidate();
    anchorX = (int) Math.floor(OptimizationBridge.camX);
    anchorY = (int) Math.floor(OptimizationBridge.camY);
    anchorZ = (int) Math.floor(OptimizationBridge.camZ);

    SubmitNodeStorage storage = new SubmitNodeStorage();
    int added = 0;
    for (int i = 0; i < states.size(); i++) {
      BlockEntityRenderState st = states.get(i);
      BlockPos pos = st.blockPos;
      if (pos == null) continue;
      long pk = pos.asLong();
      int stateHash = System.identityHashCode(bakedLevel.getBlockState(pos));
      if (!insertBaked(pk, stateHash)) continue;
      if (bakedListSize < BAKED_POS_LIST.length) {
        BAKED_POS_LIST[bakedListSize++] = pk;
      }
      TEMP_POSE.pushPose();
      TEMP_POSE.translate(pos.getX() - anchorX, pos.getY() - anchorY, pos.getZ() - anchorZ);
      boolean isSign = st instanceof SignRenderState;
      if (isSign) bakingSignWoodOnly = true;
      try {
        dispatcher.submit(st, TEMP_POSE, storage, levelState.cameraRenderState);
      } finally {
        if (isSign) bakingSignWoodOnly = false;
      }
      TEMP_POSE.popPose();
      added++;
    }
    if (added < MIN_BAKE_COUNT) {
      invalidate();
      return;
    }
    pendingStorage = storage;
    needsBake = false;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static void finishPendingBake(FeatureFrameContext ctx) {
    SubmitNodeStorage storage = pendingStorage;
    if (storage == null || ctx == null) return;
    pendingStorage = null;

    StagedVertexBuffer vbo = new StagedVertexBuffer(() -> "NightClient StaticBE", 262144);
    FeatureFrameContext bakeCtx = new FeatureFrameContext(
        ctx.options(), ctx.font(), ctx.blockStateModelSet(), ctx.blockColors(),
        ctx.textureManager(), ctx.atlasManager(), ctx.lightmap(), vbo);
    Map<FeatureRendererType<?>, List<SubmitNode>> grouped = new IdentityHashMap<FeatureRendererType<?>, List<SubmitNode>>();
    FeatureRenderPhase.Output collector = new FeatureRenderPhase.Output() {
      @Override
      public void accept(SubmitNode node, boolean translucent) {
        if (node == null) return;
        FeatureRendererType<?> t = node.featureType();
        grouped.computeIfAbsent(t, k -> new ArrayList<SubmitNode>()).add(node);
      }
      @Override
      public <S extends SubmitNode> void acceptFeatureGroup(
          FeatureRendererType<S> type, Collection<S> submits, boolean translucent) {
        if (type == null || submits == null || submits.isEmpty()) return;
        grouped.computeIfAbsent(type, k -> new ArrayList<SubmitNode>()).addAll(submits);
      }
    };
    storage.drainPhases(phase -> phase.sortInto(collector));

    BATCHES.clear();
    capturing = true;
    try {
      for (Map.Entry<FeatureRendererType<?>, List<SubmitNode>> e : grouped.entrySet()) {
        FeatureRendererType<?> t = e.getKey();
        List list = e.getValue();
        if (list.isEmpty()) continue;
        if (t == ModelFeatureRenderer.TYPE) {
          TEMP_MODEL.prepareGroup(bakeCtx, list, false);
          TEMP_MODEL.finishExecute(bakeCtx);
        } else if (t == ItemFeatureRenderer.TYPE) {
          TEMP_ITEM.prepareGroup(bakeCtx, list, false);
          TEMP_ITEM.finishExecute(bakeCtx);
        } else if (t == BlockModelFeatureRenderer.TYPE) {
          TEMP_BLOCK.prepareGroup(bakeCtx, list, false);
          TEMP_BLOCK.finishExecute(bakeCtx);
        } else if (t == CustomFeatureRenderer.TYPE) {
          TEMP_CUSTOM.prepareGroup(bakeCtx, list, false);
          TEMP_CUSTOM.finishExecute(bakeCtx);
        } else if (t == ShadowFeatureRenderer.TYPE) {
          TEMP_SHADOW.prepareGroup(bakeCtx, list, false);
          TEMP_SHADOW.finishExecute(bakeCtx);
        }
      }
      vbo.upload();
      bakedVbo = vbo;
      bakedValid = !BATCHES.isEmpty();
      armedSolid = bakedValid;
      armedTranslucent = bakedValid;
      lastBakeFrame = OptimizationBridge.frameIndex;
      if (!bakedValid) {
        invalidate();
      }
    } catch (Throwable t) {
      try {
        vbo.endFrame();
        vbo.close();
      } catch (Throwable ignored) {
      }
      invalidate();
    } finally {
      capturing = false;
    }
  }

  public static void onCapturedStagedDraw(RenderType rt, StagedVertexBuffer.Draw draw) {
    if (rt == null || draw == null) return;
    for (int i = 0; i < BATCHES.size(); i++) {
      if (BATCHES.get(i).draw == draw) return;
    }
    BATCHES.add(new Batch(rt, draw));
  }

  public static void drawIfArmed(boolean translucentPass''' + (', com.mojang.renderpearl.api.commands.RenderPass pass' if v >= (26, 3) else '') + ''') {
    if (translucentPass) {
      if (!armedTranslucent) return;
      armedTranslucent = false;
    } else {
      if (!armedSolid) return;
      armedSolid = false;
    }
    if (!bakedValid || bakedVbo == null || BATCHES.isEmpty()) return;
    boolean any = false;
    for (int i = 0; i < BATCHES.size(); i++) {
      if (BATCHES.get(i).translucent == translucentPass) { any = true; break; }
    }
    if (!any) return;
    float tx = (float) (anchorX - OptimizationBridge.camX);
    float ty = (float) (anchorY - OptimizationBridge.camY);
    float tz = (float) (anchorZ - OptimizationBridge.camZ);
    Matrix4fStack mv = RenderSystem.getModelViewStack();
    mv.pushMatrix();
    mv.translate(tx, ty, tz);
    try {
      for (int i = 0; i < BATCHES.size(); i++) {
        Batch b = BATCHES.get(i);
        if (b.translucent != translucentPass) continue;
        StagedVertexBuffer.ExecuteInfo info = bakedVbo.getExecuteInfo(b.draw);
        if (info == null) continue;
        PreparedRenderType prep = b.renderType.prepare();
        ''' + ('prep.drawFromBuffer(info, pass);' if v >= (26, 3) else 'prep.drawFromBuffer(info);') + '''
      }
    } finally {
      mv.popMatrix();
    }
  }

  private static boolean insertBaked(long key, int stateHash) {
    int slot = mix(key) & MASK;
    for (int p = 0; p < 16; p++) {
      int idx = (slot + p) & MASK;
      if (BAKED_GEN[idx] != bakedGeneration) {
        BAKED_GEN[idx] = bakedGeneration;
        BAKED_KEYS[idx] = key;
        BAKED_STATE_HASH[idx] = stateHash;
        return true;
      }
      if (BAKED_KEYS[idx] == key) return false;
    }
    return false;
  }

  private static int findBakedSlot(long key) {
    int slot = mix(key) & MASK;
    for (int p = 0; p < 16; p++) {
      int idx = (slot + p) & MASK;
      if (BAKED_GEN[idx] != bakedGeneration) return -1;
      if (BAKED_KEYS[idx] == key) return idx;
    }
    return -1;
  }

  private static int mix(long z) {
    z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
    z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
    return (int) (z ^ (z >>> 31));
  }'''

        static_entity_imports = '''import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.renderer.entity.state.BlockDisplayEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.ItemDisplayEntityRenderState;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.feature.BlockModelFeatureRenderer;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.FeatureRendererType;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.feature.ShadowFeatureRenderer;
import net.minecraft.client.renderer.feature.phase.FeatureRenderPhase;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fStack;'''

        static_entity_body = '''  private static final int CAP = 4096;
  private static final int MASK = CAP - 1;
  private static final int MIN_BAKE_COUNT = 8;
  private static final EquipmentSlot[] ARMOR_SLOTS = {
      EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
      EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
  };

  private static final class Batch {
    final RenderType renderType;
    final StagedVertexBuffer.Draw draw;
    final boolean translucent;
    Batch(RenderType renderType, StagedVertexBuffer.Draw draw) {
      this.renderType = renderType;
      this.draw = draw;
      this.translucent = renderType.hasBlending();
    }
  }

  private static final class BakedNameTag {
    final double x, y, z;
    final Component tag;
    final Vec3 attachment;
    final int lightCoords;
    final boolean isDiscrete;
    BakedNameTag(double x, double y, double z, Component tag, Vec3 attachment, int lightCoords, boolean isDiscrete) {
      this.x = x; this.y = y; this.z = z;
      this.tag = tag; this.attachment = attachment;
      this.lightCoords = lightCoords; this.isDiscrete = isDiscrete;
    }
  }

  private static final List<Batch> BATCHES = new ArrayList<Batch>();
  private static final List<BakedNameTag> NAMETAGS = new ArrayList<BakedNameTag>();
  private static final int[] BAKED_IDS = new int[CAP];
  private static final int[] BAKED_HASH = new int[CAP];
  private static final long[] BAKED_GEN = new long[CAP];
  private static final int[] BAKED_ID_LIST = new int[2048];

  private static final int[] CAND_IDS = new int[2048];
  private static final int[] CAND_HASH = new int[2048];
  private static final double[] CAND_X = new double[2048];
  private static final double[] CAND_Y = new double[2048];
  private static final double[] CAND_Z = new double[2048];
  private static int candCount;

  private static StagedVertexBuffer bakedVbo;
  private static SubmitNodeStorage pendingStorage;
  private static long bakedGeneration = 1L;
  private static int bakedListSize;
  private static int verifyCursor;
  private static boolean bakedValid;
  private static boolean needsBake;
  private static boolean capturing;
  private static boolean armedSolid;
  private static boolean armedTranslucent;
  private static int unbakedFrameCount;
  private static long lastBakeFrame = -1000L;
  private static ClientLevel bakedLevel;
  private static int bakedSkyDarken = -1;
  private static long lastVerifyTick = Long.MIN_VALUE;
  private static boolean sameTickFastHit;
  private static int anchorX, anchorY, anchorZ;

  private static final ModelFeatureRenderer TEMP_MODEL = new ModelFeatureRenderer();
  private static final ItemFeatureRenderer TEMP_ITEM = new ItemFeatureRenderer();
  private static final BlockModelFeatureRenderer TEMP_BLOCK = new BlockModelFeatureRenderer();
  private static final CustomFeatureRenderer TEMP_CUSTOM = new CustomFeatureRenderer();
  private static final ShadowFeatureRenderer TEMP_SHADOW = new ShadowFeatureRenderer();
  private static final PoseStack TEMP_POSE = new PoseStack();

  public static boolean isCapturing() {
    return capturing;
  }

  private static boolean isActive() {
    return OptimizationBridge.opt028StaticEntityBake || OptimizationBridge.opt147StaticVramBatch;
  }

  public static void invalidate() {
    bakedValid = false;
    needsBake = false;
    armedSolid = false;
    armedTranslucent = false;
    pendingStorage = null;
    bakedListSize = 0;
    verifyCursor = 0;
    bakedGeneration++;
    NAMETAGS.clear();
    BATCHES.clear();
    if (bakedVbo != null) {
      try {
        bakedVbo.endFrame();
        bakedVbo.close();
      } catch (Throwable ignored) {
      }
      bakedVbo = null;
    }
  }

  public static void beginExtract(ClientLevel level) {
    candCount = 0;
    unbakedFrameCount = 0;
    armedSolid = false;
    armedTranslucent = false;
    if (!isActive() || level == null) {
      if (bakedValid || bakedVbo != null || pendingStorage != null) invalidate();
      bakedLevel = null;
      return;
    }
    int sky = level.getSkyDarken();
    long tick = level.getGameTime();
    sameTickFastHit = (level == bakedLevel && tick == lastVerifyTick);
    lastVerifyTick = tick;
    if (level != bakedLevel || sky != bakedSkyDarken) {
      invalidate();
      bakedLevel = level;
      bakedSkyDarken = sky;
      sameTickFastHit = false;
    } else if (!sameTickFastHit && bakedValid && bakedListSize > 0) {
      int checks = Math.max(8, bakedListSize >> 3);
      for (int c = 0; c < checks; c++) {
        if (verifyCursor >= bakedListSize) verifyCursor = 0;
        int id = BAKED_ID_LIST[verifyCursor++];
        Entity e = level.getEntity(id);
        if (e == null || e.isRemoved() || !isStaticEligible(e)) {
          invalidate();
          break;
        }
        int slot = findBakedSlot(id);
        if (slot < 0 || BAKED_HASH[slot] != computeHash(e)) {
          invalidate();
          break;
        }
      }
    }
  }

  private static boolean isStaticEligible(Entity e) {
    if (e.isCurrentlyGlowing() || e.displayFireAnimation() || e.isPassenger() || e.isVehicle()) {
      return false;
    }
    if (e instanceof ArmorStand) {
      ArmorStand as = (ArmorStand) e;
      if (as.isInvisible() || as.hurtTime > 0 || as.deathTime > 0) return false;
      if (as.getX() != as.xo || as.getY() != as.yo || as.getZ() != as.zo) return false;
      if (as.getYRot() != as.yRotO) return false;
      Vec3 vel = as.getDeltaMovement();
      if (vel.x * vel.x + vel.z * vel.z > 1.0e-5) return false;
      for (int i = 0; i < ARMOR_SLOTS.length; i++) {
        ItemStack st = as.getItemBySlot(ARMOR_SLOTS[i]);
        if (!st.isEmpty() && st.hasFoil()) return false;
      }
      return true;
    }
    if (e instanceof ItemFrame) {
      ItemFrame f = (ItemFrame) e;
      if (f.hasFramedMap()) return false;
      ItemStack item = f.getItem();
      return item.isEmpty() || !item.hasFoil();
    }
    if (e instanceof Display.BlockDisplay || e instanceof Display.ItemDisplay) {
      Display d = (Display) e;
      if (d.getX() != d.xo || d.getY() != d.yo || d.getZ() != d.zo) return false;
      Display.RenderState rs = d.renderState();
      return rs != null && rs.billboardConstraints() == Display.BillboardConstraints.FIXED;
    }
    if (e instanceof Mob) {
      Mob mob = (Mob) e;
      if (!mob.isNoAi() || mob.isInvisible() || mob.hurtTime > 0 || mob.deathTime > 0 || !mob.isAlive()) {
        return false;
      }
      if (mob.getX() != mob.xo || mob.getY() != mob.yo || mob.getZ() != mob.zo) return false;
      if (mob.yBodyRot != mob.yBodyRotO || mob.yHeadRot != mob.yHeadRotO || mob.getXRot() != mob.xRotO) {
        return false;
      }
      if (mob.walkAnimation.speed() > 1.0e-4f) return false;
      Vec3 vel = mob.getDeltaMovement();
      if (vel.x * vel.x + vel.z * vel.z > 1.0e-5) return false;
      for (int i = 0; i < ARMOR_SLOTS.length; i++) {
        ItemStack st = mob.getItemBySlot(ARMOR_SLOTS[i]);
        if (!st.isEmpty() && st.hasFoil()) return false;
      }
      return true;
    }
    return false;
  }

  private static int computeHash(Entity e) {
    long h = Double.doubleToLongBits(e.getX()) * 31L
        ^ Double.doubleToLongBits(e.getY()) * 131L
        ^ Double.doubleToLongBits(e.getZ()) * 997L;
    int r = (int) (h ^ (h >>> 32));
    if (e instanceof ArmorStand) {
      ArmorStand as = (ArmorStand) e;
      r = r * 31 + Float.floatToRawIntBits(as.getYRot());
      r = r * 31 + (as.isSmall() ? 1 : 0) + (as.showArms() ? 2 : 0)
          + (as.showBasePlate() ? 4 : 0) + (as.isMarker() ? 8 : 0);
      r = r * 31 + as.getHeadPose().hashCode();
      r = r * 31 + as.getBodyPose().hashCode();
      r = r * 31 + as.getLeftArmPose().hashCode();
      r = r * 31 + as.getRightArmPose().hashCode();
      r = r * 31 + as.getLeftLegPose().hashCode();
      r = r * 31 + as.getRightLegPose().hashCode();
      for (int i = 0; i < ARMOR_SLOTS.length; i++) {
        ItemStack st = as.getItemBySlot(ARMOR_SLOTS[i]);
        r = r * 31 + (st.isEmpty() ? 0 : System.identityHashCode(st.getItem()) ^ st.getCount());
      }
    } else if (e instanceof ItemFrame) {
      ItemFrame f = (ItemFrame) e;
      r = r * 31 + f.getDirection().ordinal();
      r = r * 31 + f.getRotation();
      r = r * 31 + (f.isInvisible() ? 1 : 0);
      ItemStack item = f.getItem();
      r = r * 31 + (item.isEmpty() ? 0 : System.identityHashCode(item.getItem()) ^ item.getCount());
    } else if (e instanceof Display) {
      Display d = (Display) e;
      r = r * 31 + Float.floatToRawIntBits(d.getYRot());
      r = r * 31 + Float.floatToRawIntBits(d.getXRot());
      Display.RenderState rs = d.renderState();
      if (rs != null) r = r * 31 + System.identityHashCode(rs);
    } else if (e instanceof Mob) {
      Mob mob = (Mob) e;
      r = r * 31 + Float.floatToRawIntBits(mob.yBodyRot);
      r = r * 31 + Float.floatToRawIntBits(mob.yHeadRot);
      r = r * 31 + Float.floatToRawIntBits(mob.getXRot());
      r = r * 31 + (mob.isBaby() ? 1 : 0);
      for (int i = 0; i < ARMOR_SLOTS.length; i++) {
        ItemStack st = mob.getItemBySlot(ARMOR_SLOTS[i]);
        r = r * 31 + (st.isEmpty() ? 0 : System.identityHashCode(st.getItem()) ^ st.getCount());
      }
      if (mob instanceof Villager) {
        r = r * 31 + ((Villager) mob).getVillagerData().hashCode();
      }
    }
    return r;
  }

  public static boolean trySkipStaticEntity(Entity e) {
    if (!isActive()) return false;
    if (bakedValid) {
      int slot = findBakedSlot(e.getId());
      if (slot >= 0) {
        if (sameTickFastHit && e.getX() == e.xo && e.getY() == e.yo && e.getZ() == e.zo) {
          return true;
        }
        if (isStaticEligible(e) && BAKED_HASH[slot] == computeHash(e)) {
          return true;
        }
        invalidate();
        return false;
      }
    }
    if (!(e instanceof ArmorStand) && !(e instanceof ItemFrame)
        && !(e instanceof Display.BlockDisplay) && !(e instanceof Display.ItemDisplay)
        && !(e instanceof Mob && ((Mob) e).isNoAi())) {
      return false;
    }
    int id = e.getId();
    if (!isStaticEligible(e)) return false;
    int hash = computeHash(e);
    if (bakedValid) {
      unbakedFrameCount++;
      if (unbakedFrameCount >= MIN_BAKE_COUNT && OptimizationBridge.frameIndex - lastBakeFrame >= 60L) {
        invalidate();
        needsBake = true;
      } else {
        return false;
      }
    } else {
      needsBake = true;
    }
    if (candCount < CAND_IDS.length) {
      int idx = candCount++;
      CAND_IDS[idx] = id;
      CAND_HASH[idx] = hash;
      CAND_X[idx] = e.getX();
      CAND_Y[idx] = e.getY();
      CAND_Z[idx] = e.getZ();
    }
    return false;
  }

  public static void bakeAndFilter(
      EntityRenderDispatcher dispatcher,
      LevelRenderState levelState,
      PoseStack pose,
      SubmitNodeCollector collector) {
    if (!isActive()) {
      armedSolid = false;
      armedTranslucent = false;
      return;
    }
    if (!bakedValid && needsBake && candCount >= MIN_BAKE_COUNT
        && levelState != null && levelState.entityRenderStates != null
        && levelState.entityRenderStates.size() >= MIN_BAKE_COUNT) {
      List<EntityRenderState> all = levelState.entityRenderStates;
      List<EntityRenderState> toBake = new ArrayList<EntityRenderState>();
      int[] matchIdx = new int[all.size()];
      int matchLen = 0;
      boolean[] used = new boolean[candCount];
      for (int i = 0; i < all.size(); i++) {
        EntityRenderState st = all.get(i);
        if (!isEligibleState(st)) continue;
        int found = -1;
        for (int j = 0; j < candCount; j++) {
          if (!used[j]
              && Math.abs(CAND_X[j] - st.x) < 0.05
              && Math.abs(CAND_Y[j] - st.y) < 0.05
              && Math.abs(CAND_Z[j] - st.z) < 0.05) {
            found = j;
            used[j] = true;
            break;
          }
        }
        if (found >= 0) {
          toBake.add(st);
          matchIdx[matchLen++] = found;
        }
      }
      if (toBake.size() >= MIN_BAKE_COUNT) {
        preparePendingBake(dispatcher, levelState, toBake, matchIdx);
        if (pendingStorage != null) {
          int write = 0;
          int nextBaked = 0;
          for (int i = 0; i < all.size(); i++) {
            EntityRenderState st = all.get(i);
            if (nextBaked < toBake.size() && st == toBake.get(nextBaked)) {
              nextBaked++;
            } else {
              all.set(write++, st);
            }
          }
          while (all.size() > write) all.remove(all.size() - 1);
        }
      }
    }
    if ((bakedValid && !BATCHES.isEmpty()) || pendingStorage != null) {
      if (bakedValid && !BATCHES.isEmpty()) {
        armedSolid = true;
        armedTranslucent = true;
      }
      if (!NAMETAGS.isEmpty() && levelState != null && levelState.cameraRenderState != null) {
        Vec3 cam = levelState.cameraRenderState.pos;
        double cx = cam.x(), cy = cam.y(), cz = cam.z();
        for (int i = 0; i < NAMETAGS.size(); i++) {
          BakedNameTag nt = NAMETAGS.get(i);
          double dx = nt.x - cx, dy = nt.y - cy, dz = nt.z - cz;
          double distSq = dx * dx + dy * dy + dz * dz;
          if (OptimizationBridge.cullNametag(distSq, true, false, false)) continue;
          pose.pushPose();
          pose.translate(dx, dy, dz);
          collector.submitNameTag(
              pose, nt.attachment, 0, nt.tag, !nt.isDiscrete, nt.lightCoords,
              levelState.cameraRenderState);
          pose.popPose();
        }
      }
    } else {
      armedSolid = false;
      armedTranslucent = false;
    }
  }

  private static boolean isEligibleState(EntityRenderState st) {
    if (st == null || st.outlineColor != 0 || st.displayFireAnimation) return false;
    if (st instanceof ArmorStandRenderState) {
      ArmorStandRenderState as = (ArmorStandRenderState) st;
      return !as.isInvisible && as.wiggle == 0.0f;
    }
    if (st instanceof ItemFrameRenderState) {
      return ((ItemFrameRenderState) st).mapId == null;
    }
    if (st instanceof BlockDisplayEntityRenderState || st instanceof ItemDisplayEntityRenderState) {
      return true;
    }
    if (st instanceof LivingEntityRenderState) {
      LivingEntityRenderState le = (LivingEntityRenderState) st;
      return !le.isInvisible && !le.hasRedOverlay && le.deathTime == 0.0f && le.walkAnimationSpeed < 1.0e-4f;
    }
    return false;
  }

  private static void preparePendingBake(
      EntityRenderDispatcher dispatcher,
      LevelRenderState levelState,
      List<EntityRenderState> states,
      int[] matchIdx) {
    invalidate();
    anchorX = (int) Math.floor(OptimizationBridge.camX);
    anchorY = (int) Math.floor(OptimizationBridge.camY);
    anchorZ = (int) Math.floor(OptimizationBridge.camZ);

    SubmitNodeStorage storage = new SubmitNodeStorage();
    int added = 0;
    for (int i = 0; i < states.size(); i++) {
      EntityRenderState st = states.get(i);
      int ci = matchIdx[i];
      int id = CAND_IDS[ci];
      int hash = CAND_HASH[ci];
      if (!insertBaked(id, hash)) continue;
      if (bakedListSize < BAKED_ID_LIST.length) {
        BAKED_ID_LIST[bakedListSize++] = id;
      }
      Component savedTag = st.nameTag;
      if (savedTag != null && st.nameTagAttachment != null) {
        NAMETAGS.add(new BakedNameTag(st.x, st.y, st.z, savedTag, st.nameTagAttachment, st.lightCoords, st.isDiscrete));
        st.nameTag = null;
      }
      try {
        dispatcher.submit(
            st, levelState.cameraRenderState,
            st.x - anchorX, st.y - anchorY, st.z - anchorZ,
            TEMP_POSE, storage);
      } finally {
        st.nameTag = savedTag;
      }
      added++;
    }
    if (added < MIN_BAKE_COUNT) {
      invalidate();
      return;
    }
    pendingStorage = storage;
    needsBake = false;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static void finishPendingBake(FeatureFrameContext ctx) {
    SubmitNodeStorage storage = pendingStorage;
    if (storage == null || ctx == null) return;
    pendingStorage = null;

    StagedVertexBuffer vbo = new StagedVertexBuffer(() -> "NightClient StaticEntity", 262144);
    FeatureFrameContext bakeCtx = new FeatureFrameContext(
        ctx.options(), ctx.font(), ctx.blockStateModelSet(), ctx.blockColors(),
        ctx.textureManager(), ctx.atlasManager(), ctx.lightmap(), vbo);
    Map<FeatureRendererType<?>, List<SubmitNode>> grouped = new IdentityHashMap<FeatureRendererType<?>, List<SubmitNode>>();
    FeatureRenderPhase.Output collector = new FeatureRenderPhase.Output() {
      @Override
      public void accept(SubmitNode node, boolean translucent) {
        if (node == null) return;
        FeatureRendererType<?> t = node.featureType();
        grouped.computeIfAbsent(t, k -> new ArrayList<SubmitNode>()).add(node);
      }
      @Override
      public <S extends SubmitNode> void acceptFeatureGroup(
          FeatureRendererType<S> type, Collection<S> submits, boolean translucent) {
        if (type == null || submits == null || submits.isEmpty()) return;
        grouped.computeIfAbsent(type, k -> new ArrayList<SubmitNode>()).addAll(submits);
      }
    };
    storage.drainPhases(phase -> phase.sortInto(collector));

    BATCHES.clear();
    capturing = true;
    try {
      for (Map.Entry<FeatureRendererType<?>, List<SubmitNode>> e : grouped.entrySet()) {
        FeatureRendererType<?> t = e.getKey();
        List list = e.getValue();
        if (list.isEmpty()) continue;
        if (t == ModelFeatureRenderer.TYPE) {
          TEMP_MODEL.prepareGroup(bakeCtx, list, false);
          TEMP_MODEL.finishExecute(bakeCtx);
        } else if (t == ItemFeatureRenderer.TYPE) {
          TEMP_ITEM.prepareGroup(bakeCtx, list, false);
          TEMP_ITEM.finishExecute(bakeCtx);
        } else if (t == BlockModelFeatureRenderer.TYPE) {
          TEMP_BLOCK.prepareGroup(bakeCtx, list, false);
          TEMP_BLOCK.finishExecute(bakeCtx);
        } else if (t == CustomFeatureRenderer.TYPE) {
          TEMP_CUSTOM.prepareGroup(bakeCtx, list, false);
          TEMP_CUSTOM.finishExecute(bakeCtx);
        } else if (t == ShadowFeatureRenderer.TYPE) {
          TEMP_SHADOW.prepareGroup(bakeCtx, list, false);
          TEMP_SHADOW.finishExecute(bakeCtx);
        }
      }
      vbo.upload();
      bakedVbo = vbo;
      bakedValid = !BATCHES.isEmpty();
      armedSolid = bakedValid;
      armedTranslucent = bakedValid;
      lastBakeFrame = OptimizationBridge.frameIndex;
      if (!bakedValid) {
        invalidate();
      }
    } catch (Throwable t) {
      try {
        vbo.endFrame();
        vbo.close();
      } catch (Throwable ignored) {
      }
      invalidate();
    } finally {
      capturing = false;
    }
  }

  public static void onCapturedStagedDraw(RenderType rt, StagedVertexBuffer.Draw draw) {
    if (rt == null || draw == null) return;
    for (int i = 0; i < BATCHES.size(); i++) {
      if (BATCHES.get(i).draw == draw) return;
    }
    BATCHES.add(new Batch(rt, draw));
  }

  public static void drawIfArmed(boolean translucentPass''' + (', com.mojang.renderpearl.api.commands.RenderPass pass' if v >= (26, 3) else '') + ''') {
    if (translucentPass) {
      if (!armedTranslucent) return;
      armedTranslucent = false;
    } else {
      if (!armedSolid) return;
      armedSolid = false;
    }
    if (!bakedValid || bakedVbo == null || BATCHES.isEmpty()) return;
    boolean any = false;
    for (int i = 0; i < BATCHES.size(); i++) {
      if (BATCHES.get(i).translucent == translucentPass) { any = true; break; }
    }
    if (!any) return;
    float tx = (float) (anchorX - OptimizationBridge.camX);
    float ty = (float) (anchorY - OptimizationBridge.camY);
    float tz = (float) (anchorZ - OptimizationBridge.camZ);
    Matrix4fStack mv = RenderSystem.getModelViewStack();
    mv.pushMatrix();
    mv.translate(tx, ty, tz);
    try {
      for (int i = 0; i < BATCHES.size(); i++) {
        Batch b = BATCHES.get(i);
        if (b.translucent != translucentPass) continue;
        StagedVertexBuffer.ExecuteInfo info = bakedVbo.getExecuteInfo(b.draw);
        if (info == null) continue;
        PreparedRenderType prep = b.renderType.prepare();
        ''' + ('prep.drawFromBuffer(info, pass);' if v >= (26, 3) else 'prep.drawFromBuffer(info);') + '''
      }
    } finally {
      mv.popMatrix();
    }
  }

  private static boolean insertBaked(int id, int hash) {
    int slot = mix(id) & MASK;
    for (int p = 0; p < 16; p++) {
      int idx = (slot + p) & MASK;
      if (BAKED_GEN[idx] != bakedGeneration) {
        BAKED_GEN[idx] = bakedGeneration;
        BAKED_IDS[idx] = id;
        BAKED_HASH[idx] = hash;
        return true;
      }
      if (BAKED_IDS[idx] == id) return false;
    }
    return false;
  }

  private static int findBakedSlot(int id) {
    int slot = mix(id) & MASK;
    for (int p = 0; p < 16; p++) {
      int idx = (slot + p) & MASK;
      if (BAKED_GEN[idx] != bakedGeneration) return -1;
      if (BAKED_IDS[idx] == id) return idx;
    }
    return -1;
  }

  private static int mix(int x) {
    x ^= x >>> 16;
    x *= 0x7feb352d;
    x ^= x >>> 15;
    x *= 0x846ca68b;
    return x ^ (x >>> 16);
  }'''

        static_be_level_owner = 'LevelExtractor'
        static_be_level_imports = (
            'import net.minecraft.client.Camera;\n'
            'import net.minecraft.client.DeltaTracker;\n'
            'import net.minecraft.client.multiplayer.ClientLevel;\n'
            'import net.minecraft.client.renderer.culling.Frustum;\n'
            'import net.minecraft.client.renderer.extract.LevelExtractor;\n'
            'import net.minecraft.client.renderer.state.level.LevelRenderState;'
        )
        static_be_level_extra = (
            'import org.spongepowered.asm.mixin.Shadow;\n'
            'import org.spongepowered.asm.mixin.injection.At;\n'
            'import org.spongepowered.asm.mixin.injection.Inject;\n'
            'import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;'
        )
        static_be_level_body = '''  @Shadow private ClientLevel level;

  @Inject(method = "extractVisibleBlockEntities(Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/state/level/LevelRenderState;)V", at = @At("HEAD"))
  private void nightclient$staticBeBeginExtract(Camera camera, float partialTick, LevelRenderState state, CallbackInfo ci) {
    StaticBeVboCache.beginExtract(this.level);
  }

  @Inject(method = "extractVisibleEntities(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;Lnet/minecraft/client/DeltaTracker;Lnet/minecraft/client/renderer/state/level/LevelRenderState;)V", at = @At("HEAD"))
  private void nightclient$staticEntityBeginExtract(Camera camera, Frustum frustum, DeltaTracker delta, LevelRenderState state, CallbackInfo ci) {
    StaticEntityVboCache.beginExtract(this.level);
  }'''

        static_be_feature_owner = 'FeatureRenderDispatcher'
        static_be_feature_imports = (
            'import net.minecraft.client.renderer.SubmitNodeStorage;\n'
            'import net.minecraft.client.renderer.feature.FeatureFrameContext;\n'
            'import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;'
        )
        static_be_feature_extra = (
            'import org.spongepowered.asm.mixin.injection.At;\n'
            'import org.spongepowered.asm.mixin.injection.Inject;\n'
            'import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;'
        )
        static_be_feature_body = '''  @Inject(
      method = "prepareFrameWithContext(Lnet/minecraft/client/renderer/feature/FeatureFrameContext;Lnet/minecraft/client/renderer/SubmitNodeStorage;)Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;",
      at = @At("HEAD"))
  private void nightclient$finishPendingStaticBakes(
      FeatureFrameContext ctx, SubmitNodeStorage storage,
      CallbackInfoReturnable<FeatureRenderDispatcher.PreparedFrame> cir) {
    StaticBeVboCache.finishPendingBake(ctx);
    StaticEntityVboCache.finishPendingBake(ctx);
  }'''
    else:
        retained_rt_owner = ''
        retained_rt_imports = ''
        retained_rt_extra = ''
        retained_rt_body = ''
        static_be_imports = ''
        static_be_body = '''  public static boolean bakingSignWoodOnly;
  public static boolean skipCurrentSignWood;
  public static boolean isBakedSignWood(long posKey) { return false; }
  public static boolean isAlreadyBakedFastHit(Object be, float partialTick, Object crumble) { return false; }
  public static boolean trySkipStaticBe(Object be, float partialTick, Object crumble) { return false; }
  public static void invalidate() {}'''
        static_entity_imports = ''
        static_entity_body = '''  public static boolean isCapturing() { return false; }
  public static boolean trySkipStaticEntity(Object e) { return false; }
  public static void invalidate() {}'''
        static_be_level_owner = ''
        static_be_level_imports = ''
        static_be_level_extra = ''
        static_be_level_body = ''
        static_be_feature_owner = ''
        static_be_feature_imports = ''
        static_be_feature_extra = ''
        static_be_feature_body = ''

    if v >= (1, 21, 2):
        leaf_extra_imports = ''
        leaf_cull_body = '''  @Inject(
      method = "shouldRenderFace(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Z",
      at = @At("HEAD"),
      cancellable = true)
  private static void nightclient$cullInteriorLeaves(
      BlockState state, BlockState neighbor, Direction dir, CallbackInfoReturnable<Boolean> cir) {
    if (OptimizationBridge.opt011LeafCull
        && state.getBlock() instanceof LeavesBlock
        && neighbor.getBlock() instanceof LeavesBlock) {
      cir.setReturnValue(false);
    }
  }'''
    else:
        leaf_extra_imports = (
            'import net.minecraft.core.BlockPos;\n'
            'import net.minecraft.world.level.BlockGetter;'
        )
        leaf_cull_body = '''  @Inject(
      method = "shouldRenderFace(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lnet/minecraft/core/BlockPos;)Z",
      at = @At("HEAD"),
      cancellable = true)
  private static void nightclient$cullInteriorLeaves(
      BlockState state, BlockGetter level, BlockPos pos, Direction dir, BlockPos neighborPos,
      CallbackInfoReturnable<Boolean> cir) {
    if (OptimizationBridge.opt011LeafCull
        && state.getBlock() instanceof LeavesBlock
        && level.getBlockState(neighborPos).getBlock() instanceof LeavesBlock) {
      cir.setReturnValue(false);
    }
  }'''

    if v >= (26, 1):
        nametag_extra_imports = (
            'import com.mojang.blaze3d.vertex.PoseStack;\n'
            'import net.minecraft.client.renderer.SubmitNodeCollector;\n'
            'import net.minecraft.client.renderer.entity.state.EntityRenderState;\n'
            'import net.minecraft.client.renderer.state.level.CameraRenderState;'
        )
        nametag_submit_hook = '''  @Inject(
      method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
      at = @At("HEAD"),
      cancellable = true)
  private void nightclient$cullSubmittedNameTag(
      EntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState cam,
      CallbackInfo ci) {
    if (state == null || state.nameTag == null || !OptimizationBridge.nametagCullingActive()) return;
    if (OptimizationBridge.cullNametag(state.distanceToCameraSq, true, false, state.appearsGlowing())) {
      ci.cancel();
    }
  }'''
    elif v >= (1, 21, 9):
        nametag_extra_imports = (
            'import com.mojang.blaze3d.vertex.PoseStack;\n'
            'import net.minecraft.client.renderer.SubmitNodeCollector;\n'
            'import net.minecraft.client.renderer.entity.state.EntityRenderState;\n'
            'import net.minecraft.client.renderer.state.CameraRenderState;'
        )
        nametag_submit_hook = '''  @Inject(
      method = "submitNameTag(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
      at = @At("HEAD"),
      cancellable = true)
  private void nightclient$cullSubmittedNameTag(
      EntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState cam,
      CallbackInfo ci) {
    if (state == null || state.nameTag == null || !OptimizationBridge.nametagCullingActive()) return;
    if (OptimizationBridge.cullNametag(state.distanceToCameraSq, true, false, state.appearsGlowing())) {
      ci.cancel();
    }
  }'''
    elif v >= (1, 21, 2):
        nametag_extra_imports = (
            'import com.mojang.blaze3d.vertex.PoseStack;\n'
            'import net.minecraft.client.renderer.MultiBufferSource;\n'
            'import net.minecraft.client.renderer.entity.state.EntityRenderState;\n'
            'import net.minecraft.network.chat.Component;'
        )
        nametag_submit_hook = '''  @Inject(
      method = "renderNameTag(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
      at = @At("HEAD"),
      cancellable = true)
  private void nightclient$cullRenderedNameTag(
      EntityRenderState state, Component name, PoseStack pose, MultiBufferSource buf, int light,
      CallbackInfo ci) {
    if (state == null || !OptimizationBridge.nametagCullingActive()) return;
    if (OptimizationBridge.cullNametag(state.distanceToCameraSq, true, false, false)) {
      ci.cancel();
    }
  }'''
    elif v >= (1, 20, 5):
        nametag_extra_imports = (
            'import com.mojang.blaze3d.vertex.PoseStack;\n'
            'import net.minecraft.client.renderer.MultiBufferSource;\n'
            'import net.minecraft.network.chat.Component;'
        )
        nametag_submit_hook = '''  @Inject(
      method = "renderNameTag(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
      at = @At("HEAD"),
      cancellable = true)
  private void nightclient$cullLegacyRenderedNameTag(
      Entity entity, Component name, PoseStack pose, MultiBufferSource buf, int light, float partialTick,
      CallbackInfo ci) {
    if (entity == null || !OptimizationBridge.nametagCullingActive()) return;
    Minecraft mc = Minecraft.getInstance();
    if (mc.player == null || mc.level == null || entity.level() != mc.level) return;
    double distSq = mc.player.distanceToSqr(entity);
    if (OptimizationBridge.cullNametag(distSq, true, entity == mc.player, entity.isCurrentlyGlowing())) {
      ci.cancel();
    }
  }'''
    else:
        nametag_extra_imports = (
            'import com.mojang.blaze3d.vertex.PoseStack;\n'
            'import net.minecraft.client.renderer.MultiBufferSource;\n'
            'import net.minecraft.network.chat.Component;'
        )
        nametag_submit_hook = '''  @Inject(
      method = "renderNameTag(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
      at = @At("HEAD"),
      cancellable = true)
  private void nightclient$cullLegacyRenderedNameTag(
      Entity entity, Component name, PoseStack pose, MultiBufferSource buf, int light,
      CallbackInfo ci) {
    if (entity == null || !OptimizationBridge.nametagCullingActive()) return;
    Minecraft mc = Minecraft.getInstance();
    if (mc.player == null || mc.level == null || entity.level() != mc.level) return;
    double distSq = mc.player.distanceToSqr(entity);
    if (OptimizationBridge.cullNametag(distSq, true, entity == mc.player, entity.isCurrentlyGlowing())) {
      ci.cancel();
    }
  }'''

    has_fast_model_cube = v >= (1, 21, 2)
    has_fast_bulk_quad = v >= (1, 21, 11) and v < (26, 1)
    has_scoreboard_cache = v >= (1, 21, 2)
    has_retained_hud = v >= (1, 21, 6)
    has_text_quad_cache = v >= (1, 21, 11)
    fast_model_sodium_guard = '' if v >= (26, 2) else 'if (OptimizationBridge.sodiumLoaded) return;'

    if v >= (26, 2):
        text_cache_extra_imports = 'import net.minecraft.client.gui.font.TextRenderable;'
        text_cache_capture_body = '''  private static final class CaptureVisitor implements Font.GlyphVisitor {
    Font.DisplayMode mode;
    @Override
    public void acceptRenderable(TextRenderable renderable) {
      if (renderable == null) return;
      RenderType rt = renderable.renderType(mode);
      if (rt == null) return;
      renderable.render(IDENTITY, CAPTURER.select(rt), 0, false);
    }
  }
  private static final CaptureVisitor VISITOR = new CaptureVisitor();

  private static Bucket[] capture(
      Font font, FormattedCharSequence string, float x, float y,
      int color, boolean dropShadow, Font.DisplayMode displayMode, int bgColor) {
    CAPTURER.reset();
    VISITOR.mode = displayMode;
    try {
      Font.PreparedText prepared = font.prepareText(string, x, y, color, dropShadow, false, bgColor);
      prepared.visit(VISITOR);
      return finalizeCapture();
    } catch (Throwable ignored) {
      return null;
    }
  }'''
        text_mixin_imports = (
            'import com.mojang.blaze3d.vertex.VertexConsumer;\n'
            'import java.util.List;\n'
            'import net.minecraft.client.gui.Font;\n'
            'import net.minecraft.client.gui.font.TextRenderable;\n'
            'import net.minecraft.client.renderer.feature.FeatureFrameContext;\n'
            'import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;\n'
            'import net.minecraft.client.renderer.feature.TextFeatureRenderer;\n'
            'import net.minecraft.client.renderer.rendertype.RenderType;\n'
            'import org.joml.Matrix4f;'
        )
        text_mixin_body = ('''  @Inject(
      method = "buildGroup(Lnet/minecraft/client/renderer/feature/FeatureFrameContext;Ljava/util/List;)V",
      at = @At("HEAD"),
      cancellable = true)
  private void nightclient$cachedBuildGroup(
      FeatureFrameContext ctx, List<TextFeatureRenderer.Submit> submits, CallbackInfo ci) {
    if (!OptimizationBridge.opt144TextDisplayCache || submits == null || submits.isEmpty()) return;
    Font font = ctx.font();
    if (font == null) return;
    for (int i = 0; i < submits.size(); i++) {
      TextFeatureRenderer.Content c = submits.get(i).content();
      if (!(c instanceof TextFeatureRenderer.Content.Text t) || t.outlineColor() != 0) return;
    }
    TextFeatureRenderer self = (TextFeatureRenderer) (Object) this;
    TextQuadCache.ConsumerProvider provider = rt -> ((OptTextFeatureAccessor) (Object) self).nightclient$getBuilder(rt);
    for (int i = 0; i < submits.size(); i++) {
      TextFeatureRenderer.Submit s = submits.get(i);
      TextFeatureRenderer.Content.Text t = (TextFeatureRenderer.Content.Text) s.content();
      TextQuadCache.Entry entry = TextQuadCache.getOrBuild(
          font, t.string(), t.x(), t.y(), t.color(), t.dropShadow(), s.displayMode(), t.backgroundColor());
      if (entry != null) {
        TextQuadCache.emitEntry(entry, s.pose(), s.lightCoords(), provider);
      } else {
        Font.PreparedText prep = font.prepareText(
            t.string(), t.x(), t.y(), t.color(), t.dropShadow(), false, t.backgroundColor());
        final Matrix4f pose = new Matrix4f(s.pose());
        final int light = s.lightCoords();
        final Font.DisplayMode mode = s.displayMode();
        prep.visit(new Font.GlyphVisitor() {
          @Override
          public void acceptRenderable(TextRenderable r) {
            VertexConsumer vc = provider.get(r.renderType(mode));
            r.render(pose, vc, light, false);
          }
        });
      }
    }
    ci.cancel();
  }''' if v >= (26, 3) else '''  @Inject(
      method = "buildGroup(Lnet/minecraft/client/renderer/feature/FeatureFrameContext;Ljava/util/List;)V",
      at = @At("HEAD"),
      cancellable = true)
  private void nightclient$cachedBuildGroup(
      FeatureFrameContext ctx, List<TextFeatureRenderer.Submit> submits, CallbackInfo ci) {
    if (!OptimizationBridge.opt144TextDisplayCache || submits == null || submits.isEmpty()) return;
    Font font = ctx.font();
    if (font == null) return;
    for (int i = 0; i < submits.size(); i++) {
      if (submits.get(i).outlineColor() != 0) return;
    }
    TextFeatureRenderer self = (TextFeatureRenderer) (Object) this;
    TextQuadCache.ConsumerProvider provider = rt -> ((OptTextFeatureAccessor) (Object) self).nightclient$getBuilder(rt);
    for (int i = 0; i < submits.size(); i++) {
      TextFeatureRenderer.Submit s = submits.get(i);
      TextQuadCache.Entry entry = TextQuadCache.getOrBuild(
          font, s.string(), s.x(), s.y(), s.color(), s.dropShadow(), s.displayMode(), s.backgroundColor());
      if (entry != null) {
        TextQuadCache.emitEntry(entry, s.pose(), s.lightCoords(), provider);
      } else {
        Font.PreparedText prep = font.prepareText(
            s.string(), s.x(), s.y(), s.color(), s.dropShadow(), false, s.backgroundColor());
        final Matrix4f pose = new Matrix4f(s.pose());
        final int light = s.lightCoords();
        final Font.DisplayMode mode = s.displayMode();
        prep.visit(new Font.GlyphVisitor() {
          @Override
          public void acceptRenderable(TextRenderable r) {
            VertexConsumer vc = provider.get(r.renderType(mode));
            r.render(pose, vc, light, false);
          }
        });
      }
    }
    ci.cancel();
  }''')
    elif has_text_quad_cache:
        text_cache_extra_imports = 'import net.minecraft.client.renderer.MultiBufferSource;'
        text_cache_capture_body = '''  private static final MultiBufferSource CAPTURE_MBS = rt -> CAPTURER.select(rt);

  private static Bucket[] capture(
      Font font, FormattedCharSequence string, float x, float y,
      int color, boolean dropShadow, Font.DisplayMode displayMode, int bgColor) {
    CAPTURER.reset();
    try {
      font.drawInBatch(string, x, y, color, dropShadow, IDENTITY, CAPTURE_MBS, displayMode, bgColor, 0);
      return finalizeCapture();
    } catch (Throwable ignored) {
      return null;
    }
  }'''
        text_mixin_imports = (
            'import java.util.List;\n'
            'import net.minecraft.client.Minecraft;\n'
            'import net.minecraft.client.gui.Font;\n'
            'import net.minecraft.client.renderer.MultiBufferSource;\n'
            'import net.minecraft.client.renderer.SubmitNodeCollection;\n'
            'import net.minecraft.client.renderer.SubmitNodeStorage;\n'
            'import net.minecraft.client.renderer.feature.TextFeatureRenderer;'
        )
        text_mixin_body = '''  @Inject(
      method = "''' + ('renderTranslucent' if v >= (26, 1) else 'render') + '''(Lnet/minecraft/client/renderer/SubmitNodeCollection;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V",
      at = @At("HEAD"),
      cancellable = true)
  private void nightclient$cachedRenderText(
      SubmitNodeCollection collection, MultiBufferSource.BufferSource bufferSource, CallbackInfo ci) {
    if (!OptimizationBridge.opt144TextDisplayCache && !OptimizationBridge.opt165NativeGpuInstancing) return;
    List<SubmitNodeStorage.TextSubmit> submits = collection.getTextSubmits();
    if (submits == null || submits.isEmpty()) return;
    Font font = Minecraft.getInstance().font;
    if (font == null) return;
    TextQuadCache.ConsumerProvider provider = bufferSource::getBuffer;
    for (int i = 0; i < submits.size(); i++) {
      SubmitNodeStorage.TextSubmit s = submits.get(i);
      if (s.outlineColor() != 0) {
        font.drawInBatch8xOutline(
            s.string(), s.x(), s.y(), s.color(), s.outlineColor(),
            s.pose(), bufferSource, s.lightCoords());
        continue;
      }
      TextQuadCache.Entry entry = TextQuadCache.getOrBuild(
          font, s.string(), s.x(), s.y(), s.color(), s.dropShadow(), s.displayMode(), s.backgroundColor());
      if (entry != null) {
        TextQuadCache.emitEntry(entry, s.pose(), s.lightCoords(), provider);
      } else {
        font.drawInBatch(
            s.string(), s.x(), s.y(), s.color(), s.dropShadow(),
            s.pose(), bufferSource, s.displayMode(), s.backgroundColor(), s.lightCoords());
      }
    }
    ci.cancel();
  }'''
    else:
        text_cache_extra_imports = ''
        text_cache_capture_body = ''
        text_mixin_imports = ''
        text_mixin_body = ''

    if v >= (26, 2):
        sb_owner = 'Hud'
        sb_owner_import = 'net.minecraft.client.gui.Hud'
    else:
        sb_owner = 'Gui'
        sb_owner_import = 'net.minecraft.client.gui.Gui'
    if v >= (26, 1):
        sb_gfx = 'GuiGraphicsExtractor'
        sb_gfx_import = 'net.minecraft.client.gui.GuiGraphicsExtractor'
        sb_gfx_desc = 'Lnet/minecraft/client/gui/GuiGraphicsExtractor;'
        sb_text_method = 'text'
    else:
        sb_gfx = 'GuiGraphics'
        sb_gfx_import = 'net.minecraft.client.gui.GuiGraphics'
        sb_gfx_desc = 'Lnet/minecraft/client/gui/GuiGraphics;'
        sb_text_method = 'drawString'

    if v >= (26, 2):
        rhud_extract_imports = (
            'import net.minecraft.client.gui.Hud;\n'
            'import net.minecraft.client.gui.screens.Overlay;\n'
            'import net.minecraft.client.gui.screens.Screen;'
        )
        rhud_extract_body = '''  @Shadow @Final public Hud hud;
  @Shadow private Screen screen;
  @Shadow private Overlay overlay;

  @Inject(
      method = "extractRenderState(Lnet/minecraft/client/DeltaTracker;ZZ)V",
      at = @At("HEAD"),
      cancellable = true)
  private void nightclient$retainedHudExtract(
      DeltaTracker deltaTracker, boolean renderHud, boolean renderScreen, CallbackInfo ci) {
    boolean hasOverlayOrScreen = this.screen != null || this.overlay != null;
    boolean debugShown = this.hud != null && this.hud.getDebugOverlay() != null
        && this.hud.getDebugOverlay().showDebugScreen();
    boolean hudHidden = this.hud == null || this.hud.isHidden();
    int guiTicks = this.hud != null ? this.hud.getGuiTicks() : 0;
    if (RetainedHudState.evaluateAndCheckHit(
        this.minecraft, renderHud, hasOverlayOrScreen, debugShown, hudHidden, guiTicks)) {
      ci.cancel();
    }
  }'''
        rhud_renderer_imports = (
            'import net.minecraft.client.renderer.StagedVertexBuffer;\n'
            'import net.minecraft.client.renderer.state.gui.GuiRenderState;'
        )
        rhud_renderer_body = '''  @Shadow @Final private GuiRenderState renderState;
  @Shadow @Final private List<?> draws;
  @Shadow @Final private StagedVertexBuffer vertexBuffer;
  @Shadow private int firstDrawIndexAfterBlur;
  @Shadow protected abstract void prepare();
  @Shadow protected abstract void draw();
  @Shadow protected abstract void clearUnusedOversizedItemRenderers();

  @Unique private boolean ncRetainedActive;

  @Unique
  private void ncFlushRetained(boolean resetState) {
    if (!this.ncRetainedActive) return;
    this.ncRetainedActive = false;
    this.vertexBuffer.endDraw();
    this.vertexBuffer.endFrame();
    this.draws.clear();
    if (resetState) {
      this.renderState.reset();
    }
    this.firstDrawIndexAfterBlur = Integer.MAX_VALUE;
    this.clearUnusedOversizedItemRenderers();
  }

  @Inject(method = "render()V", at = @At("HEAD"), cancellable = true)
  private void nightclient$retainedHudRender(CallbackInfo ci) {
    if (RetainedHudState.reusedThisFrame() && this.ncRetainedActive && !this.draws.isEmpty()) {
      this.draw();
      ci.cancel();
      return;
    }
    if (!RetainedHudState.wantRetainThisFrame() || this.renderState.panoramaRenderState != null) {
      if (this.ncRetainedActive) {
        this.ncFlushRetained(false);
        RetainedHudState.invalidate();
      }
      return;
    }
    if (this.ncRetainedActive) {
      this.ncFlushRetained(false);
    }
    this.prepare();
    this.vertexBuffer.upload();
    this.draw();
    if (this.firstDrawIndexAfterBlur >= this.draws.size() && !this.draws.isEmpty()) {
      this.ncRetainedActive = true;
      RetainedHudState.markBuilt();
    } else {
      this.vertexBuffer.endDraw();
      this.vertexBuffer.endFrame();
      this.draws.clear();
      this.renderState.reset();
      this.firstDrawIndexAfterBlur = Integer.MAX_VALUE;
      this.clearUnusedOversizedItemRenderers();
      RetainedHudState.invalidate();
    }
    ci.cancel();
  }

  @Inject(method = "endFrame()V", at = @At("HEAD"), cancellable = true)
  private void nightclient$retainedHudEndFrame(CallbackInfo ci) {
    if (this.ncRetainedActive && RetainedHudState.reusedThisFrame()) {
      ci.cancel();
    }
  }

  @Inject(method = "close()V", at = @At("HEAD"))
  private void nightclient$retainedHudClose(CallbackInfo ci) {
    if (this.ncRetainedActive) {
      this.ncFlushRetained(true);
      RetainedHudState.invalidate();
    }
  }'''
    elif has_retained_hud:
        rhud_gfx_cls = 'GuiGraphicsExtractor' if v >= (26, 1) else 'GuiGraphics'
        rhud_extract_method = 'extractRenderState' if v >= (26, 1) else 'render'
        rhud_extract_imports = (
            f'import net.minecraft.client.gui.{rhud_gfx_cls};\n'
            'import net.minecraft.client.gui.components.DebugScreenOverlay;'
        )
        rhud_extract_body = f'''  @Shadow public abstract int getGuiTicks();
  @Shadow public abstract DebugScreenOverlay getDebugOverlay();

  @Inject(
      method = "{rhud_extract_method}(Lnet/minecraft/client/gui/{rhud_gfx_cls};Lnet/minecraft/client/DeltaTracker;)V",
      at = @At("HEAD"),
      cancellable = true)
  private void nightclient$retainedHudExtract(
      {rhud_gfx_cls} gfx, DeltaTracker deltaTracker, CallbackInfo ci) {{
    boolean hasOverlayOrScreen = this.minecraft.screen != null || this.minecraft.getOverlay() != null;
    DebugScreenOverlay dbg = this.getDebugOverlay();
    boolean debugShown = dbg != null && dbg.showDebugScreen();
    boolean hudHidden = this.minecraft.options != null && this.minecraft.options.hideGui;
    if (RetainedHudState.evaluateAndCheckHit(
        this.minecraft, true, hasOverlayOrScreen, debugShown, hudHidden, this.getGuiTicks())) {{
      ci.cancel();
    }}
  }}'''
        rhud_state_import = (
            'net.minecraft.client.renderer.state.gui.GuiRenderState'
            if v >= (26, 1) else
            'net.minecraft.client.gui.render.state.GuiRenderState'
        )
        rhud_renderer_imports = (
            'import com.mojang.blaze3d.buffers.GpuBufferSlice;\n'
            'import com.mojang.blaze3d.vertex.VertexFormat;\n'
            'import java.util.Map;\n'
            f'import {rhud_state_import};\n'
            'import net.minecraft.client.renderer.MappableRingBuffer;'
        )
        rhud_renderer_body = '''  @Shadow @Final private GuiRenderState renderState;
  @Shadow @Final private List<?> draws;
  @Shadow @Final private List<?> meshesToDraw;
  @Shadow @Final private Map<VertexFormat, MappableRingBuffer> vertexBuffers;
  @Shadow private int firstDrawIndexAfterBlur;
  @Shadow protected abstract void prepare();
  @Shadow protected abstract void draw(GpuBufferSlice fogBuffer);
  @Shadow protected abstract void clearUnusedOversizedItemRenderers();

  @Unique private boolean ncRetainedActive;

  @Unique
  private void ncFlushRetained(boolean resetState) {
    if (!this.ncRetainedActive) return;
    this.ncRetainedActive = false;
    for (MappableRingBuffer buf : this.vertexBuffers.values()) {
      buf.rotate();
    }
    this.draws.clear();
    this.meshesToDraw.clear();
    if (resetState) {
      this.renderState.reset();
    }
    this.firstDrawIndexAfterBlur = Integer.MAX_VALUE;
    this.clearUnusedOversizedItemRenderers();
  }

  @Inject(
      method = "render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V",
      at = @At("HEAD"),
      cancellable = true)
  private void nightclient$retainedHudRender(GpuBufferSlice fogBuffer, CallbackInfo ci) {
    if (RetainedHudState.reusedThisFrame() && this.ncRetainedActive && !this.draws.isEmpty()) {
      this.draw(fogBuffer);
      ci.cancel();
      return;
    }
    if (!RetainedHudState.wantRetainThisFrame()) {
      if (this.ncRetainedActive) {
        this.ncFlushRetained(false);
        RetainedHudState.invalidate();
      }
      return;
    }
    if (this.ncRetainedActive) {
      this.ncFlushRetained(false);
    }
    this.prepare();
    this.draw(fogBuffer);
    if (this.firstDrawIndexAfterBlur >= this.draws.size() && !this.draws.isEmpty()) {
      this.ncRetainedActive = true;
      RetainedHudState.markBuilt();
    } else {
      for (MappableRingBuffer buf : this.vertexBuffers.values()) {
        buf.rotate();
      }
      this.draws.clear();
      this.meshesToDraw.clear();
      this.renderState.reset();
      this.firstDrawIndexAfterBlur = Integer.MAX_VALUE;
      this.clearUnusedOversizedItemRenderers();
      RetainedHudState.invalidate();
    }
    ci.cancel();
  }

  @Inject(method = "close()V", at = @At("HEAD"))
  private void nightclient$retainedHudClose(CallbackInfo ci) {
    if (this.ncRetainedActive) {
      this.ncFlushRetained(true);
      RetainedHudState.invalidate();
    }
  }'''
    else:
        rhud_extract_imports = ''
        rhud_extract_body = ''
        rhud_renderer_imports = ''
        rhud_renderer_body = ''

    if v >= (1, 21, 9):
        model_vx, model_vy, model_vz = 'mv.worldX()', 'mv.worldY()', 'mv.worldZ()'
    else:
        model_vx, model_vy, model_vz = 'mv.pos().x() * 0.0625f', 'mv.pos().y() * 0.0625f', 'mv.pos().z() * 0.0625f'

    if v >= (26, 2):
        fast_bb_format_shadows = '  @Shadow @Final private boolean entityFormat;'
        fast_bb_cube_guard = '''if (!NC_LITTLE_ENDIAN || !this.entityFormat || !this.building || this.vertexSize != 36) return false;
    final boolean full = true;
    final int stride = 36;'''
    else:
        fast_bb_format_shadows = (
            '  @Shadow @Final private boolean fastFormat;\n'
            '  @Shadow @Final private boolean fullFormat;'
        )
        fast_bb_cube_guard = '''if (!NC_LITTLE_ENDIAN || !this.fastFormat || !this.building) return false;
    final boolean full = this.fullFormat;
    final int stride = this.vertexSize;
    if (full) {
      if (stride != 36) return false;
    } else {
      if (stride != 32) return false;
    }'''

    if has_fast_bulk_quad:
        fast_bb_interfaces = 'VertexConsumer, '
        fast_bb_extra_imports = (
            'import com.mojang.blaze3d.vertex.PoseStack;\n'
            'import com.mojang.blaze3d.vertex.VertexConsumer;\n'
            'import dev.nightclient.platform.OptimizationBridge;\n'
            'import net.minecraft.client.model.geom.builders.UVPair;\n'
            'import net.minecraft.client.renderer.LightTexture;\n'
            'import net.minecraft.client.renderer.block.model.BakedQuad;\n'
            'import org.joml.Vector3fc;'
        )
        fast_bb_bulk_quad_body = '''  @Unique
  private static int ncTransformAndPackNormal(PoseStack.Pose pose, Vector3fc unit) {
    Matrix3f n = pose.normal();
    float fnx = unit.x(), fny = unit.y(), fnz = unit.z();
    float nx = n.m00() * fnx + n.m10() * fny + n.m20() * fnz;
    float ny = n.m01() * fnx + n.m11() * fny + n.m21() * fnz;
    float nz = n.m02() * fnx + n.m12() * fny + n.m22() * fnz;
    Object poseObj = pose;
    boolean trusted = poseObj instanceof OptPoseNormalAccessor
        && ((OptPoseNormalAccessor) poseObj).nightclient$hasTrustedNormals();
    if (!trusted) {
      float inv = org.joml.Math.invsqrt(nx * nx + ny * ny + nz * nz);
      nx *= inv;
      ny *= inv;
      nz *= inv;
    }
    return ncPackNormal(nx, ny, nz);
  }

  @Unique
  private void ncWriteQuadVert(
      long ptr, boolean full,
      float m00, float m10, float m20, float m30,
      float m01, float m11, float m21, float m31,
      float m02, float m12, float m22, float m32,
      Vector3fc p, long packedUv, int abgr, int overlay, int light, int packedNormal) {
    float x = p.x(), y = p.y(), z = p.z();
    MemoryUtil.memPutFloat(ptr,      m00 * x + m10 * y + m20 * z + m30);
    MemoryUtil.memPutFloat(ptr + 4L, m01 * x + m11 * y + m21 * z + m31);
    MemoryUtil.memPutFloat(ptr + 8L, m02 * x + m12 * y + m22 * z + m32);
    MemoryUtil.memPutInt(ptr + 12L, abgr);
    MemoryUtil.memPutFloat(ptr + 16L, UVPair.unpackU(packedUv));
    MemoryUtil.memPutFloat(ptr + 20L, UVPair.unpackV(packedUv));
    if (full) {
      MemoryUtil.memPutInt(ptr + 24L, overlay);
      MemoryUtil.memPutInt(ptr + 28L, light);
      MemoryUtil.memPutInt(ptr + 32L, packedNormal);
    } else {
      MemoryUtil.memPutInt(ptr + 24L, light);
      MemoryUtil.memPutInt(ptr + 28L, packedNormal);
    }
  }

  @Override
  public void putBulkData(
      PoseStack.Pose pose, BakedQuad quad,
      float r, float g, float b, float a, int light, int overlay) {
    if (!OptimizationBridge.opt117FastBulkQuad
        || !NC_LITTLE_ENDIAN || !this.fastFormat || !this.building
        || (this.fullFormat ? this.vertexSize != 36 : this.vertexSize != 32)
        || this.vertices + 4 >= 16777215) {
      VertexConsumer.super.putBulkData(pose, quad, r, g, b, a, light, overlay);
      return;
    }
    final boolean full = this.fullFormat;
    final int stride = this.vertexSize;
    this.ensureBuilding();
    this.endLastVertex();
    long ptr = this.buffer.reserve(stride << 2);
    this.vertices += 4;
    this.vertexPointer = ptr + (long) stride * 3L;
    this.elementsToFill = 0;

    final int abgr = ARGB.toABGR(ARGB.colorFromFloat(a, r, g, b));
    final int emitLight = LightTexture.lightCoordsWithEmission(light, quad.lightEmission());
    final int packedNormal = ncTransformAndPackNormal(pose, quad.direction().getUnitVec3f());
    final Matrix4f m = pose.pose();
    final float m00 = m.m00(), m10 = m.m10(), m20 = m.m20(), m30 = m.m30();
    final float m01 = m.m01(), m11 = m.m11(), m21 = m.m21(), m31 = m.m31();
    final float m02 = m.m02(), m12 = m.m12(), m22 = m.m22(), m32 = m.m32();

    ncWriteQuadVert(ptr, full, m00, m10, m20, m30, m01, m11, m21, m31, m02, m12, m22, m32,
        quad.position0(), quad.packedUV0(), abgr, overlay, emitLight, packedNormal);
    ncWriteQuadVert(ptr + stride, full, m00, m10, m20, m30, m01, m11, m21, m31, m02, m12, m22, m32,
        quad.position1(), quad.packedUV1(), abgr, overlay, emitLight, packedNormal);
    ncWriteQuadVert(ptr + (stride << 1), full, m00, m10, m20, m30, m01, m11, m21, m31, m02, m12, m22, m32,
        quad.position2(), quad.packedUV2(), abgr, overlay, emitLight, packedNormal);
    ncWriteQuadVert(ptr + (stride * 3L), full, m00, m10, m20, m30, m01, m11, m21, m31, m02, m12, m22, m32,
        quad.position3(), quad.packedUV3(), abgr, overlay, emitLight, packedNormal);
  }

  @Override
  public void putBulkData(
      PoseStack.Pose pose, BakedQuad quad,
      float[] brightness, float r, float g, float b, float a, int[] lights, int overlay) {
    if (!OptimizationBridge.opt117FastBulkQuad
        || !NC_LITTLE_ENDIAN || !this.fastFormat || !this.building
        || (this.fullFormat ? this.vertexSize != 36 : this.vertexSize != 32)
        || this.vertices + 4 >= 16777215) {
      VertexConsumer.super.putBulkData(pose, quad, brightness, r, g, b, a, lights, overlay);
      return;
    }
    final boolean full = this.fullFormat;
    final int stride = this.vertexSize;
    this.ensureBuilding();
    this.endLastVertex();
    long ptr = this.buffer.reserve(stride << 2);
    this.vertices += 4;
    this.vertexPointer = ptr + (long) stride * 3L;
    this.elementsToFill = 0;

    final int emission = quad.lightEmission();
    final int packedNormal = ncTransformAndPackNormal(pose, quad.direction().getUnitVec3f());
    final Matrix4f m = pose.pose();
    final float m00 = m.m00(), m10 = m.m10(), m20 = m.m20(), m30 = m.m30();
    final float m01 = m.m01(), m11 = m.m11(), m21 = m.m21(), m31 = m.m31();
    final float m02 = m.m02(), m12 = m.m12(), m22 = m.m22(), m32 = m.m32();

    float br0 = brightness[0];
    ncWriteQuadVert(ptr, full, m00, m10, m20, m30, m01, m11, m21, m31, m02, m12, m22, m32,
        quad.position0(), quad.packedUV0(),
        ARGB.toABGR(ARGB.colorFromFloat(a, br0 * r, br0 * g, br0 * b)),
        overlay, LightTexture.lightCoordsWithEmission(lights[0], emission), packedNormal);
    float br1 = brightness[1];
    ncWriteQuadVert(ptr + stride, full, m00, m10, m20, m30, m01, m11, m21, m31, m02, m12, m22, m32,
        quad.position1(), quad.packedUV1(),
        ARGB.toABGR(ARGB.colorFromFloat(a, br1 * r, br1 * g, br1 * b)),
        overlay, LightTexture.lightCoordsWithEmission(lights[1], emission), packedNormal);
    float br2 = brightness[2];
    ncWriteQuadVert(ptr + (stride << 1), full, m00, m10, m20, m30, m01, m11, m21, m31, m02, m12, m22, m32,
        quad.position2(), quad.packedUV2(),
        ARGB.toABGR(ARGB.colorFromFloat(a, br2 * r, br2 * g, br2 * b)),
        overlay, LightTexture.lightCoordsWithEmission(lights[2], emission), packedNormal);
    float br3 = brightness[3];
    ncWriteQuadVert(ptr + (stride * 3L), full, m00, m10, m20, m30, m01, m11, m21, m31, m02, m12, m22, m32,
        quad.position3(), quad.packedUV3(),
        ARGB.toABGR(ARGB.colorFromFloat(a, br3 * r, br3 * g, br3 * b)),
        overlay, LightTexture.lightCoordsWithEmission(lights[3], emission), packedNormal);
  }'''
    else:
        fast_bb_interfaces = ''
        fast_bb_extra_imports = ''
        fast_bb_bulk_quad_body = ''

    abstract_arrow_import = (
        'net.minecraft.world.entity.projectile.arrow.AbstractArrow'
        if v >= (1, 21, 11) else
        'net.minecraft.world.entity.projectile.AbstractArrow'
    )

    has_terrain_engine = (v == (1, 21, 11) or v == (26, 2))
    if v == (26, 2):
        compiled_mesh_extra_imports = ''
        compiled_mesh_extra_body = ''
        terrain_prepare_imports = (
            'import com.mojang.blaze3d.IndexType;\n'
            'import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;\n'
            'import net.minecraft.client.renderer.texture.TextureManager;'
        )
        terrain_prepare_body = '''  @Shadow @Final private TextureManager textureManager;
  @Shadow @Final private ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections;
  @Shadow private SectionRenderDispatcher sectionRenderDispatcher;

  @Unique private static final EnumMap<ChunkSectionLayer, Int2ObjectOpenHashMap<List<RenderPass.Draw<GpuBufferSlice[]>>>>
      NC_DRAW_GROUPS = ncCreateDrawGroups();
  @Unique private static final ArrayList<ArrayList<RenderPass.Draw<GpuBufferSlice[]>>> NC_LIST_POOL =
      new ArrayList<ArrayList<RenderPass.Draw<GpuBufferSlice[]>>>(32);
  @Unique private static int ncPoolCursor;
  @Unique private static int[] ncLayerVertexSizes;

  @Unique
  private static EnumMap<ChunkSectionLayer, Int2ObjectOpenHashMap<List<RenderPass.Draw<GpuBufferSlice[]>>>>
      ncCreateDrawGroups() {
    EnumMap<ChunkSectionLayer, Int2ObjectOpenHashMap<List<RenderPass.Draw<GpuBufferSlice[]>>>> map =
        new EnumMap<ChunkSectionLayer, Int2ObjectOpenHashMap<List<RenderPass.Draw<GpuBufferSlice[]>>>>(ChunkSectionLayer.class);
    for (int i = 0; i < NC_LAYERS.length; i++) {
      map.put(NC_LAYERS[i], new Int2ObjectOpenHashMap<List<RenderPass.Draw<GpuBufferSlice[]>>>(8));
    }
    return map;
  }

  @Unique
  private static ArrayList<RenderPass.Draw<GpuBufferSlice[]>> ncBorrowList() {
    int idx = ncPoolCursor++;
    if (idx < NC_LIST_POOL.size()) {
      ArrayList<RenderPass.Draw<GpuBufferSlice[]>> list = NC_LIST_POOL.get(idx);
      list.clear();
      return list;
    }
    ArrayList<RenderPass.Draw<GpuBufferSlice[]>> list = new ArrayList<RenderPass.Draw<GpuBufferSlice[]>>(128);
    NC_LIST_POOL.add(list);
    return list;
  }

  @Inject(
      method = "prepareChunkRenders(Lorg/joml/Matrix4fc;)Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;",
      at = @At("HEAD"),
      cancellable = true)
  private void nightclient$fastPrepareChunkRenders26(
      Matrix4fc modelView,
      CallbackInfoReturnable<ChunkSectionsToRender> cir) {
    if (OptimizationBridge.sodiumLoaded) return;
    final boolean useHorizon = OptimizationBridge.opt132HorizonOcclusion;
    final boolean useUnderground = OptimizationBridge.opt005UndergroundCull;
    if (!useHorizon && !useUnderground) return;

    final ClientLevel lvl = Minecraft.getInstance().level;
    final GpuTextureView atlasView =
        this.textureManager.getTexture(TextureAtlas.LOCATION_BLOCKS).getTextureView();
    final int count = this.visibleSections.size();
    final long visGen = TerrainFaceBuckets.visibleGen;
    final long bufGen = TerrainFaceBuckets.bufferGen;
    final int modeBits = (useHorizon ? 1 : 0) | (useUnderground ? 2 : 0);
    final double camX = OptimizationBridge.camX;
    final double camY = OptimizationBridge.camY;
    final double camZ = OptimizationBridge.camZ;
    final int camCellX = ((int) Math.floor(camX)) >> 3;
    final int camCellY = ((int) Math.floor(camY)) >> 3;
    final int camCellZ = ((int) Math.floor(camZ)) >> 3;

    // Fast retained draw-list cache hit: zero HashMap lookups, zero RenderPass.Draw or ChunkSectionInfo allocs!
    if (useHorizon
        && !ncCachedAnyFading
        && ncCachedInfos != null
        && visGen == ncCachedVisGen
        && bufGen == ncCachedBufGen
        && count == ncCachedVisCount
        && modeBits == ncCachedModeBits
        && lvl == ncCachedWorld
        && atlasView == ncCachedAtlasView
        && camCellX == ncCachedCamCellX
        && camCellY == ncCachedCamCellY
        && camCellZ == ncCachedCamCellZ) {
      NC_SHARED_MV.set(modelView);
      GpuBufferSlice[] slices = RenderSystem.getDynamicUniforms().writeChunkSections(ncCachedInfos);
      cir.setReturnValue(new ChunkSectionsToRender(atlasView, NC_DRAW_GROUPS, ncCachedMaxIndices, slices));
      return;
    }

    int[] vSizes = ncLayerVertexSizes;
    if (vSizes == null) {
      vSizes = new int[NC_LAYERS.length];
      for (int i = 0; i < NC_LAYERS.length; i++) {
        vSizes[i] = NC_LAYERS[i].pipeline().getVertexFormatBinding(0).getVertexSize();
      }
      ncLayerVertexSizes = vSizes;
    }

    ncPoolCursor = 0;
    for (int i = 0; i < NC_LAYERS.length; i++) {
      NC_DRAW_GROUPS.get(NC_LAYERS[i]).clear();
    }
    NC_SECTION_INFOS.clear();

    int atlasW = atlasView.getWidth(0);
    int atlasH = atlasView.getHeight(0);
    int maxIndices = 0;
    boolean anyFading = false;
    NC_SHARED_MV.set(modelView);

    SectionRenderDispatcher dispatcher = this.sectionRenderDispatcher;
    if (dispatcher != null) {
      final long nowMs = Util.getMillis();

      LayerLightEventListener skyListener = null;
      boolean cullUnderground = false;
      double minUndergroundDistSq = 1024.0;
      double underYMargin = 16.0;
      if (lvl != null && lvl.dimensionType().hasSkyLight()) {
        LayerLightEventListener sl = lvl.getLightEngine().getLayerListener(LightLayer.SKY);
        if (useHorizon) {
          NC_CAM_BLOCK_POS.set(
              (int) Math.floor(camX), (int) Math.floor(camY), (int) Math.floor(camZ));
          if (sl.getLightValue(NC_CAM_BLOCK_POS) >= 12) {
            cullUnderground = true;
            skyListener = sl;
          }
        } else if (camY > 70.0) {
          cullUnderground = true;
          skyListener = sl;
          minUndergroundDistSq = 2304.0;
          underYMargin = 24.0;
        }
        if (cullUnderground) {
          NC_SKY_CACHE.beginFrame(lvl, OptimizationBridge.frameIndex);
        }
      }

      dispatcher.lock();
      try {
        final long lockedBufGen = TerrainFaceBuckets.bufferGen;
        for (int si = 0; si < count; si++) {
          SectionRenderDispatcher.RenderSection section = this.visibleSections.get(si);
          BlockPos origin = section.getRenderOrigin();
          int ox = origin.getX();
          int oy = origin.getY();
          int oz = origin.getZ();

          if (cullUnderground && oy + 16 < camY - underYMargin) {
            double dx = ox + 8.0 - camX;
            double dz = oz + 8.0 - camZ;
            if (dx * dx + dz * dz > minUndergroundDistSq
                && NC_SKY_CACHE.isSealedUnderground(lvl, section.getSectionNode(), ox, oy, oz, skyListener)) {
              continue;
            }
          }

          SectionMesh mesh = section.getSectionMesh();
          if (!mesh.hasRenderableLayers()) continue;

          // Per-mesh O(1) UberGpuBuffer slice cache (eliminates 22.8% CPU HashMap.getNode even during 360 spin!)
          Object[] cachedSlices = null;
          if (mesh instanceof TerrainFaceBuckets.SliceCacheHolder holder) {
            if (holder.nightclient$getCachedSliceGen() == lockedBufGen) {
              cachedSlices = holder.nightclient$getCachedSlices();
            } else {
              cachedSlices = new Object[NC_LAYERS.length];
              for (int li = 0; li < NC_LAYERS.length; li++) {
                ChunkSectionLayer layer = NC_LAYERS[li];
                if (mesh.getSectionDraw(layer) != null) {
                  cachedSlices[li] = dispatcher.getRenderSectionSlice(mesh, layer);
                }
              }
              holder.nightclient$setCachedSlices(cachedSlices, lockedBufGen);
            }
          }

          int infoIndex = -1;
          BiConsumer<GpuBufferSlice[], RenderPass.UniformUploader> uploader = null;

          for (int li = 0; li < NC_LAYERS.length; li++) {
            ChunkSectionLayer layer = NC_LAYERS[li];
            SectionMesh.SectionDraw draw = mesh.getSectionDraw(layer);
            if (draw == null) continue;
            SectionRenderDispatcher.RenderSectionBufferSlice slice = cachedSlices != null
                ? (SectionRenderDispatcher.RenderSectionBufferSlice) cachedSlices[li]
                : dispatcher.getRenderSectionSlice(mesh, layer);
            if (slice == null) continue;
            boolean custom = draw.hasCustomIndexBuffer();
            if (custom && slice.indexBuffer() == null) continue;

            if (infoIndex == -1) {
              float vis = section.getVisibility(nowMs);
              if (vis < 0.9999f) anyFading = true;
              infoIndex = NC_SECTION_INFOS.size();
              NC_SECTION_INFOS.add(new DynamicUniforms.ChunkSectionInfo(
                  NC_SHARED_MV, ox, oy, oz, vis, atlasW, atlasH));
              final int idx = infoIndex;
              uploader = idx < NC_UPLOADERS.length
                  ? NC_UPLOADERS[idx]
                  : (slices, u) -> u.upload("ChunkSection", slices[idx]);
            }

            GpuBuffer vb = slice.vertexBuffer();
            int groupKey = 173;
            boolean notTranslucent = layer != ChunkSectionLayer.TRANSLUCENT;
            if (notTranslucent) {
              groupKey = 31 * groupKey + vb.hashCode();
            }

            GpuBuffer ibo;
            IndexType itype;
            int firstIdx;
            int idxCount = draw.indexCount();
            if (!custom) {
              if (idxCount > maxIndices) maxIndices = idxCount;
              ibo = null;
              itype = null;
              firstIdx = 0;
            } else {
              ibo = slice.indexBuffer();
              itype = draw.indexType();
              if (notTranslucent) {
                groupKey = 31 * (31 * groupKey + ibo.hashCode()) + itype.hashCode();
              }
              firstIdx = (int) (slice.indexBufferOffset() / (long) itype.bytes);
            }

            int baseVertex = (int) (slice.vertexBufferOffset() / (long) vSizes[li]);
            Int2ObjectOpenHashMap<List<RenderPass.Draw<GpuBufferSlice[]>>> layerMap =
                NC_DRAW_GROUPS.get(layer);
            List<RenderPass.Draw<GpuBufferSlice[]>> drawList = layerMap.get(groupKey);
            if (drawList == null) {
              drawList = ncBorrowList();
              layerMap.put(groupKey, drawList);
            }
            drawList.add(new RenderPass.Draw<GpuBufferSlice[]>(
                0, vb, ibo, itype, firstIdx, idxCount, baseVertex, uploader));
          }
        }
      } finally {
        dispatcher.unlock();
      }
    }

    DynamicUniforms.ChunkSectionInfo[] infosArr =
        NC_SECTION_INFOS.toArray(new DynamicUniforms.ChunkSectionInfo[0]);
    ncCachedInfos = infosArr;
    ncCachedVisGen = visGen;
    ncCachedBufGen = TerrainFaceBuckets.bufferGen;
    ncCachedVisCount = count;
    ncCachedModeBits = modeBits;
    ncCachedWorld = lvl;
    ncCachedAtlasView = atlasView;
    ncCachedMaxIndices = maxIndices;
    ncCachedAnyFading = anyFading;
    ncCachedCamCellX = camCellX;
    ncCachedCamCellY = camCellY;
    ncCachedCamCellZ = camCellZ;

    GpuBufferSlice[] slices = RenderSystem.getDynamicUniforms().writeChunkSections(infosArr);
    cir.setReturnValue(new ChunkSectionsToRender(atlasView, NC_DRAW_GROUPS, maxIndices, slices));
  }'''
    elif v == (1, 21, 11):
        compiled_mesh_extra_imports = (
            'import org.spongepowered.asm.mixin.injection.At;\n'
            'import org.spongepowered.asm.mixin.injection.Inject;\n'
            'import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;'
        )
        compiled_mesh_extra_body = '''  @Inject(method = "uploadMeshLayer", at = @At("RETURN"))
  private void nightclient$onUploadMeshLayer(CallbackInfo ci) {
    TerrainFaceBuckets.bufferGen++;
  }

  @Inject(method = "uploadLayerIndexBuffer", at = @At("RETURN"))
  private void nightclient$onUploadLayerIndex(CallbackInfo ci) {
    TerrainFaceBuckets.bufferGen++;
  }

  @Inject(method = "close", at = @At("HEAD"))
  private void nightclient$onMeshClose(CallbackInfo ci) {
    TerrainFaceBuckets.bufferGen++;
  }'''
        terrain_prepare_imports = (
            'import com.mojang.blaze3d.vertex.VertexFormat;\n'
            'import net.minecraft.client.renderer.chunk.SectionBuffers;'
        )
        terrain_prepare_body = '''  @Shadow @Final private Minecraft minecraft;
  @Shadow private ClientLevel level;
  @Shadow @Final private ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections;

  @Unique private static final EnumMap<ChunkSectionLayer, List<RenderPass.Draw<GpuBufferSlice[]>>>
      NC_DRAWS_PER_LAYER = ncCreateDrawsMap();

  @Unique
  private static EnumMap<ChunkSectionLayer, List<RenderPass.Draw<GpuBufferSlice[]>>> ncCreateDrawsMap() {
    EnumMap<ChunkSectionLayer, List<RenderPass.Draw<GpuBufferSlice[]>>> map =
        new EnumMap<ChunkSectionLayer, List<RenderPass.Draw<GpuBufferSlice[]>>>(ChunkSectionLayer.class);
    for (int i = 0; i < NC_LAYERS.length; i++) {
      map.put(NC_LAYERS[i], new ArrayList<RenderPass.Draw<GpuBufferSlice[]>>(256));
    }
    return map;
  }

  @Inject(
      method = "prepareChunkRenders(Lorg/joml/Matrix4fc;DDD)Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;",
      at = @At("HEAD"),
      cancellable = true)
  private void nightclient$fastPrepareChunkRenders121(
      Matrix4fc modelView,
      double camX, double camY, double camZ,
      CallbackInfoReturnable<ChunkSectionsToRender> cir) {
    if (OptimizationBridge.sodiumLoaded) return;
    final boolean useHorizon = OptimizationBridge.opt132HorizonOcclusion;
    final boolean useUnderground = OptimizationBridge.opt005UndergroundCull;
    if (!useHorizon && !useUnderground) return;
    if (this.level == null) return;

    final GpuTextureView atlasView =
        this.minecraft.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getTextureView();
    final int count = this.visibleSections.size();
    final long visGen = TerrainFaceBuckets.visibleGen;
    final long bufGen = TerrainFaceBuckets.bufferGen;
    final int modeBits = (useHorizon ? 1 : 0) | (useUnderground ? 2 : 0);
    final int camCellX = ((int) Math.floor(camX)) >> 3;
    final int camCellY = ((int) Math.floor(camY)) >> 3;
    final int camCellZ = ((int) Math.floor(camZ)) >> 3;

    if (useHorizon
        && !ncCachedAnyFading
        && ncCachedInfos != null
        && visGen == ncCachedVisGen
        && bufGen == ncCachedBufGen
        && count == ncCachedVisCount
        && modeBits == ncCachedModeBits
        && this.level == ncCachedWorld
        && atlasView == ncCachedAtlasView
        && camCellX == ncCachedCamCellX
        && camCellY == ncCachedCamCellY
        && camCellZ == ncCachedCamCellZ) {
      NC_SHARED_MV.set(modelView);
      GpuBufferSlice[] slices = RenderSystem.getDynamicUniforms().writeChunkSections(ncCachedInfos);
      cir.setReturnValue(new ChunkSectionsToRender(atlasView, NC_DRAWS_PER_LAYER, ncCachedMaxIndices, slices));
      return;
    }

    for (int i = 0; i < NC_LAYERS.length; i++) {
      NC_DRAWS_PER_LAYER.get(NC_LAYERS[i]).clear();
    }
    NC_SECTION_INFOS.clear();

    int maxIndices = 0;
    boolean anyFading = false;
    int atlasW = atlasView.getWidth(0);
    int atlasH = atlasView.getHeight(0);
    long nowMs = Util.getMillis();
    NC_SHARED_MV.set(modelView);

    LayerLightEventListener skyListener = null;
    boolean cullUnderground = false;
    double minUndergroundDistSq = 1024.0;
    double underYMargin = 16.0;
    if (this.level.dimensionType().hasSkyLight()) {
      LayerLightEventListener sl = this.level.getLightEngine().getLayerListener(LightLayer.SKY);
      if (useHorizon) {
        NC_CAM_BLOCK_POS.set(
            (int) Math.floor(camX), (int) Math.floor(camY), (int) Math.floor(camZ));
        if (sl.getLightValue(NC_CAM_BLOCK_POS) >= 12) {
          cullUnderground = true;
          skyListener = sl;
        }
      } else if (camY > 70.0) {
        cullUnderground = true;
        skyListener = sl;
        minUndergroundDistSq = 2304.0;
        underYMargin = 24.0;
      }
      if (cullUnderground) {
        NC_SKY_CACHE.beginFrame(this.level, OptimizationBridge.frameIndex);
      }
    }

    for (int i = 0; i < count; i++) {
      SectionRenderDispatcher.RenderSection section = this.visibleSections.get(i);
      BlockPos origin = section.getRenderOrigin();
      int ox = origin.getX();
      int oy = origin.getY();
      int oz = origin.getZ();

      if (cullUnderground && oy + 16 < camY - underYMargin) {
        double dx = ox + 8.0 - camX;
        double dz = oz + 8.0 - camZ;
        if (dx * dx + dz * dz > minUndergroundDistSq
            && NC_SKY_CACHE.isSealedUnderground(this.level, section.getSectionNode(), ox, oy, oz, skyListener)) {
          continue;
        }
      }

      SectionMesh mesh = section.getSectionMesh();
      if (!mesh.hasRenderableLayers()) continue;
      int infoIndex = -1;
      BiConsumer<GpuBufferSlice[], RenderPass.UniformUploader> uploader = null;

      for (int li = 0; li < NC_LAYERS.length; li++) {
        ChunkSectionLayer layer = NC_LAYERS[li];
        SectionBuffers buffers = mesh.getBuffers(layer);
        if (buffers == null) continue;

        if (infoIndex == -1) {
          float vis = section.getVisibility(nowMs);
          if (vis < 0.9999f) anyFading = true;
          infoIndex = NC_SECTION_INFOS.size();
          NC_SECTION_INFOS.add(new DynamicUniforms.ChunkSectionInfo(
              NC_SHARED_MV, ox, oy, oz, vis, atlasW, atlasH));
          final int idx = infoIndex;
          uploader = idx < NC_UPLOADERS.length
              ? NC_UPLOADERS[idx]
              : (slices, u) -> u.upload("ChunkSection", slices[idx]);
        }

        GpuBuffer customIbo = buffers.getIndexBuffer();
        VertexFormat.IndexType itype = customIbo != null ? buffers.getIndexType() : null;
        int totalIndices = buffers.getIndexCount();
        if (customIbo == null && totalIndices > maxIndices) {
          maxIndices = totalIndices;
        }
        NC_DRAWS_PER_LAYER.get(layer).add(new RenderPass.Draw<GpuBufferSlice[]>(
            0, buffers.getVertexBuffer(), customIbo, itype, 0, totalIndices, uploader));
      }
    }

    DynamicUniforms.ChunkSectionInfo[] infosArr =
        NC_SECTION_INFOS.toArray(new DynamicUniforms.ChunkSectionInfo[0]);
    ncCachedInfos = infosArr;
    ncCachedVisGen = visGen;
    ncCachedBufGen = TerrainFaceBuckets.bufferGen;
    ncCachedVisCount = count;
    ncCachedModeBits = modeBits;
    ncCachedWorld = this.level;
    ncCachedAtlasView = atlasView;
    ncCachedMaxIndices = maxIndices;
    ncCachedAnyFading = anyFading;
    ncCachedCamCellX = camCellX;
    ncCachedCamCellY = camCellY;
    ncCachedCamCellZ = camCellZ;

    GpuBufferSlice[] slices = RenderSystem.getDynamicUniforms().writeChunkSections(infosArr);
    cir.setReturnValue(new ChunkSectionsToRender(atlasView, NC_DRAWS_PER_LAYER, maxIndices, slices));
  }'''
    else:
        compiled_mesh_extra_imports = ''
        compiled_mesh_extra_body = ''
        terrain_prepare_imports = ''
        terrain_prepare_body = ''

    values = {
        'PARTICLE_GROUP': particle_group,
        'PARTICLE_GROUP_IMPORT': particle_import,
        'LEVEL_ACCESS': '.level()',
        'GLOW': 'isCurrentlyGlowing',
        'ABSTRACT_ARROW_IMPORT': abstract_arrow_import,
        'CAM_POS': 'position()' if v >= (1, 21, 11) else 'getPosition()',
        'CAM_LOOK': 'forwardVector()' if v >= (1, 21, 11) else 'getLookVector()',
        'MAIN_CAMERA': 'mainCamera()' if v >= (26, 2) else 'getMainCamera()',
        'GPU_TIMER_IMPORTS': gpu_imports,
        'GPU_TIMER_BODY': gpu_body,
        'BENCH_EXTRA_IMPORTS': bench_imports,
        'BENCH_SCREEN': 'mc.gui.screen()' if v >= (26, 2) else 'mc.screen',
        'BENCH_MAIN_TARGET': 'mc.gameRenderer.mainRenderTarget()' if v >= (26, 2) else 'mc.getMainRenderTarget()',
        'BENCH_KEEP_AWAKE': 'mc.getFramerateLimitTracker().onInputReceived();' if v >= (1, 21, 2) else '',
        'BENCH_ALL_CHANGED': 'mc.levelExtractor.allChanged();' if v >= (26, 2) else 'mc.levelRenderer.allChanged();',
        'BENCH_CHAT': 'mc.player.sendSystemMessage(Component.literal(msg));' if v >= (26, 1) else 'mc.player.displayClientMessage(Component.literal(msg), false);',
        'ITEM_COUNT_NBT': 'count:1' if v >= (1, 20, 5) else 'Count:1b',
        'BENCH_OPEN_WORLD': (
            'mc.createWorldOpenFlows().openWorld(folder, () -> {});' if v >= (1, 20, 5)
            else 'mc.createWorldOpenFlows().checkForBackupAndLoad(folder, () -> {});' if v >= (1, 20, 3)
            else 'mc.createWorldOpenFlows().loadLevel(null, folder);'
        ),
        'BENCH_LEVEL_SETTINGS': bench_level_settings,
        'BENCH_CREATE_FRESH': bench_create_fresh,
        'SAMPLER_POS_DECL': sampler_pos_decl,
        'STATE_SOLID_RENDER': state_solid_render,
        'ENTITY_SHOULD_RENDER_METHOD': ent_sr_method,
        'ENTITY_SHOULD_RENDER_EXTRA_ARG': ent_sr_extra,
        'BE_EARLY_IMPORTS': be_early_imports,
        'BE_EARLY_CALLBACK_IMPORT': be_early_cb,
        'BE_EARLY_BODY': be_early_body,
        'SIGN_IMPORTS': sign_imports,
        'SIGN_OWNER': sign_owner,
        'SIGN_BODY': sign_body,
        'RETAINED_RT_OWNER': retained_rt_owner,
        'RETAINED_RT_IMPORTS': retained_rt_imports,
        'RETAINED_RT_EXTRA_MIXIN_IMPORTS': retained_rt_extra,
        'RETAINED_RT_BODY': retained_rt_body,
        'STATIC_BE_IMPORTS': static_be_imports,
        'STATIC_BE_BODY': static_be_body,
        'STATIC_ENTITY_IMPORTS': static_entity_imports,
        'STATIC_ENTITY_BODY': static_entity_body,
        'STATIC_BE_LEVEL_OWNER': static_be_level_owner,
        'STATIC_BE_LEVEL_IMPORTS': static_be_level_imports,
        'STATIC_BE_LEVEL_EXTRA_IMPORTS': static_be_level_extra,
        'STATIC_BE_LEVEL_BODY': static_be_level_body,
        'STATIC_BE_FEATURE_OWNER': static_be_feature_owner,
        'STATIC_BE_FEATURE_IMPORTS': static_be_feature_imports,
        'STATIC_BE_FEATURE_EXTRA_IMPORTS': static_be_feature_extra,
        'STATIC_BE_FEATURE_BODY': static_be_feature_body,
        'LEAF_EXTRA_IMPORTS': leaf_extra_imports,
        'LEAF_CULL_BODY': leaf_cull_body,
        'NAMETAG_EXTRA_IMPORTS': nametag_extra_imports,
        'NAMETAG_SUBMIT_HOOK': nametag_submit_hook,
        'MODEL_VERTEX_X': model_vx,
        'MODEL_VERTEX_Y': model_vy,
        'MODEL_VERTEX_Z': model_vz,
        'FAST_MODEL_SODIUM_GUARD': fast_model_sodium_guard,
        'TEXT_CACHE_EXTRA_IMPORTS': text_cache_extra_imports,
        'TEXT_CACHE_CAPTURE_BODY': text_cache_capture_body,
        'TEXT_CACHE_NATIVE_SUBMIT': (
            'if (NativeGpuBridge.trySubmitTextBucket(b, pose, lightCoords)) continue;'
            if v == (1, 21, 11) else ''
        ),
        'TEXT_MIXIN_IMPORTS': text_mixin_imports,
        'TEXT_MIXIN_BODY': text_mixin_body,
        'FAST_BB_INTERFACES': fast_bb_interfaces,
        'FAST_BB_EXTRA_IMPORTS': fast_bb_extra_imports,
        'FAST_BB_FORMAT_SHADOWS': fast_bb_format_shadows,
        'FAST_BB_CUBE_GUARD': fast_bb_cube_guard,
        'FAST_BB_BULK_QUAD_BODY': fast_bb_bulk_quad_body,
        'VERTEX_FORMAT_IMPORT': (
            'com.mojang.renderpearl.api.vertex.VertexFormat'
            if v >= (26, 3) else
            'com.mojang.blaze3d.vertex.VertexFormat'
        ),
        'STATIC_EXEC_EXTRA_IMPORTS': (
            'import com.mojang.renderpearl.api.commands.RenderPass;'
            if v >= (26, 3) else ''
        ),
        'STATIC_EXEC_BODY': ('''  @Inject(method = "executeSolid(Lcom/mojang/renderpearl/api/commands/RenderPass;)V", at = @At("TAIL"))
  private void nightclient$drawRetainedStaticSolid(RenderPass pass, CallbackInfo ci) {
    StaticBeVboCache.drawIfArmed(false, pass);
    StaticEntityVboCache.drawIfArmed(false, pass);
  }

  @Inject(method = "executeTranslucent(Lcom/mojang/renderpearl/api/commands/RenderPass;)V", at = @At("TAIL"))
  private void nightclient$drawRetainedStaticTranslucent(RenderPass pass, CallbackInfo ci) {
    StaticBeVboCache.drawIfArmed(true, pass);
    StaticEntityVboCache.drawIfArmed(true, pass);
  }''' if v >= (26, 3) else '''  @Inject(method = "executeSolid()V", at = @At("TAIL"))
  private void nightclient$drawRetainedStaticSolid(CallbackInfo ci) {
    StaticBeVboCache.drawIfArmed(false);
    StaticEntityVboCache.drawIfArmed(false);
  }

  @Inject(method = "executeTranslucent()V", at = @At("TAIL"))
  private void nightclient$drawRetainedStaticTranslucent(CallbackInfo ci) {
    StaticBeVboCache.drawIfArmed(true);
    StaticEntityVboCache.drawIfArmed(true);
  }'''),
        'SB_OWNER': sb_owner,
        'SB_OWNER_IMPORT': sb_owner_import,
        'SB_GFX': sb_gfx,
        'SB_GFX_IMPORT': sb_gfx_import,
        'SB_GFX_DESC': sb_gfx_desc,
        'SB_TEXT_METHOD': sb_text_method,
        'RHUD_EXTRACT_IMPORTS': rhud_extract_imports,
        'RHUD_EXTRACT_BODY': rhud_extract_body,
        'RHUD_RENDERER_IMPORTS': rhud_renderer_imports,
        'RHUD_RENDERER_BODY': rhud_renderer_body,
        'COMPILED_MESH_EXTRA_IMPORTS': compiled_mesh_extra_imports,
        'COMPILED_MESH_EXTRA_BODY': compiled_mesh_extra_body,
        'RENDER_SECTION_MESH_CB_IMPORT': (
            'import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;'
            if v >= (26, 2) else
            'import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;'
        ),
        'RENDER_SECTION_MESH_CB_PARAM': (
            'CallbackInfoReturnable<SectionMesh> cir' if v >= (26, 2) else 'CallbackInfo ci'
        ),
        'TERRAIN_PREPARE_IMPORTS': terrain_prepare_imports,
        'TERRAIN_PREPARE_BODY': terrain_prepare_body,
        'SPRITE_EXPANDER_EXTRA_IMPORT': (
            'import net.minecraft.client.renderer.texture.UvMapping;' if v >= (26, 3) else ''
        ),
        'SPRITE_EXPANDER_FIELD': (
            '@Shadow @Final private UvMapping mapping;'
            if v >= (26, 3) else
            '@Shadow @Final private TextureAtlasSprite sprite;'
        ),
        'SPRITE_EXPANDER_RESOLVE': (
            'if (!(this.mapping instanceof TextureAtlasSprite s)) return false;'
            if v >= (26, 3) else
            'TextureAtlasSprite s = this.sprite; if (s == null) return false;'
        ),
        'PBO_LEGACY_SET_FILTER_SHADOW': (
            '' if v >= (1, 21, 4) else '@Shadow private static void setFilter(boolean blur, boolean mipmap) {}'
        ),
        'PBO_LEGACY_UPLOAD_DESC': (
            '_upload(IIIIIIIZ)V' if v >= (1, 21, 4) else '_upload(IIIIIIIZZZZ)V'
        ),
        'PBO_LEGACY_EXTRA_PARAMS': (
            '' if v >= (1, 21, 4) else 'boolean blur, boolean clamp, boolean mipmap, '
        ),
        'PBO_LEGACY_PRE_UPLOAD': (
            '' if v >= (1, 21, 4) else 'setFilter(blur, mipmap);'
        ),
        'PBO_LEGACY_POST_UPLOAD': (
            '' if v >= (1, 21, 4) else
            'if (clamp) { GlStateManager._texParameter(3553, 10242, 33071); GlStateManager._texParameter(3553, 10243, 33071); }'
        ),
        'PBO_GL_ENCODER_IMPORT': (
            '' if v >= (26, 1) else 'import com.mojang.blaze3d.opengl.GlCommandEncoder;'
        ),
        'PBO_GL_ENCODER_MIXIN': (
            '@Mixin(targets = "com.mojang.blaze3d.opengl.GlCommandEncoder")'
            if v >= (26, 1) else
            '@Mixin(GlCommandEncoder.class)'
        ),
    }
    if submit:
        camera_import = ('net.minecraft.client.renderer.state.level.CameraRenderState'
                         if v >= (26, 1) else 'net.minecraft.client.renderer.state.CameraRenderState')
        player_owner = 'EntityTypes' if v >= (26, 2) else 'EntityType'
        values.update({
            'CAMERA_IMPORT': camera_import,
            'CAMERA_DESCRIPTOR': camera_import.replace('.', '/'),
            'PLAYER_TYPE_OWNER': player_owner,
            'DISTANCE_DESCRIPTOR': 'D',
            'DISTANCE_ARG': 'double observedDistanceSq, ',
            'DISTANCE_EXPRESSION': 'observedDistanceSq',
        })
        files = (
            ('OptimizationEntitySubmitCullMixin', 'OptimizationEntitySubmitCullMixin.java.tpl', False),
            ('OptimizationBlockEntitySubmitCullMixin', 'OptimizationBlockEntitySubmitCullMixin.java.tpl', False),
        )
    else:
        values.update({
            'SECOND_FLOAT': 'F' if v < (1, 21, 2) else '',
            'SECOND_FLOAT_ARG': 'float unusedArg, ' if v < (1, 21, 2) else '',
            'DISTANCE_DESCRIPTOR': '' if v < (1, 21, 2) else 'D',
            'DISTANCE_ARG': '' if v < (1, 21, 2) else 'double observedDistanceSq, ',
            'DISTANCE_EXPRESSION': 'minecraft.player.distanceToSqr(entity)' if v < (1, 21, 2) else 'observedDistanceSq',
        })
        files = (
            ('OptimizationEntityCullMixin', 'OptimizationEntityCullMixin.java.tpl', False),
            ('OptimizationBlockEntityCullMixin', 'OptimizationBlockEntityCullMixin.java.tpl', False),
        )
    files += (
        ('OptimizationParticleCullMixin', 'OptimizationParticleCullMixin.java.tpl', False),
        ('OptimizationNametagCullMixin', 'OptimizationNametagCullMixin.java.tpl', False),
        ('OptFrameHookMixin', 'opt/OptFrameHookMixin.java.tpl', False),
        ('OptEntityEarlyCullMixin', 'opt/OptEntityEarlyCullMixin.java.tpl', False),
        ('OptBlockEntityEarlyCullMixin', 'opt/OptBlockEntityEarlyCullMixin.java.tpl', False),
        ('OptSignTextCullMixin', 'opt/OptSignTextCullMixin.java.tpl', False),
        ('OptParticlePhysicsMixin', 'opt/OptParticlePhysicsMixin.java.tpl', False),
        ('OptLeafCullMixin', 'opt/OptLeafCullMixin.java.tpl', False),
        ('RetainedRenderTypeDrawer', 'opt/RetainedRenderTypeDrawer.java.tpl', True),
        ('StaticBeVboCache', 'opt/StaticBeVboCache.java.tpl', True),
        ('StaticEntityVboCache', 'opt/StaticEntityVboCache.java.tpl', True),
        ('WorldOcclusionSampler', 'opt/WorldOcclusionSampler.java.tpl', True),
        ('GpuTimerProbe', 'opt/GpuTimerProbe.java.tpl', True),
        ('BenchmarkDirector', 'opt/BenchmarkDirector.java.tpl', True),
        ('OptNativeCompressionDecoderMixin', 'opt/OptNativeCompressionDecoderMixin.java.tpl', False),
        ('OptNativeCompressionEncoderMixin', 'opt/OptNativeCompressionEncoderMixin.java.tpl', False),
    )
    if v < (1, 21, 5):
        files += (
            ('OptLegacyNativeImagePboMixin', 'opt/OptLegacyNativeImagePboMixin.java.tpl', False),
        )
    elif (1, 21, 11) <= v < (26, 2):
        files += (
            ('OptGlTexturePboMixin', 'opt/OptGlTexturePboMixin.java.tpl', False),
        )
    if v == (1, 21, 11):
        files += (
            ('NativeGpuBridge', 'opt/NativeGpuBridge.java.tpl', True),
            ('OptGlBufferAccessor', 'opt/OptGlBufferAccessor.java.tpl', False),
            ('OptRenderTypeStateAccessor', 'opt/OptRenderTypeStateAccessor.java.tpl', False),
            ('OptRenderSetupAccessor', 'opt/OptRenderSetupAccessor.java.tpl', False),
            ('OptNativeModelPartMixin', 'opt/OptNativeModelPartMixin.java.tpl', False),
            ('OptNativeModelBatchMixin', 'opt/OptNativeModelBatchMixin.java.tpl', False),
            ('OptNativeNameTagMixin', 'opt/OptNativeNameTagMixin.java.tpl', False),
            ('OptNativeModelPartFeatureMixin', 'opt/OptNativeModelPartFeatureMixin.java.tpl', False),
            ('OptNameTagStorageAccessor', 'opt/OptNameTagStorageAccessor.java.tpl', False),
            ('OptModelPartStorageAccessor', 'opt/OptModelPartStorageAccessor.java.tpl', False),
        )
    if has_fast_model_cube:
        files += (
            ('FastEntityVertexWriter', 'opt/FastEntityVertexWriter.java.tpl', True),
            ('OptPoseNormalAccessor', 'opt/OptPoseNormalAccessor.java.tpl', False),
            ('OptBufferBuilderFastMixin', 'opt/OptBufferBuilderFastMixin.java.tpl', False),
            ('OptFastModelCubeMixin', 'opt/OptFastModelCubeMixin.java.tpl', False),
            ('OptSpriteExpanderFastMixin', 'opt/OptSpriteExpanderFastMixin.java.tpl', False),
        )
    if has_text_quad_cache:
        files += (
            ('TextQuadCache', 'opt/TextQuadCache.java.tpl', True),
            ('OptTextFeatureCacheMixin', 'opt/OptTextFeatureCacheMixin.java.tpl', False),
        )
        if v >= (26, 2):
            files += (
                ('OptTextFeatureAccessor', 'opt/OptTextFeatureAccessor.java.tpl', False),
            )
    if has_scoreboard_cache:
        files += (
            ('OptScoreboardCacheMixin', 'opt/OptScoreboardCacheMixin.java.tpl', False),
        )
    if has_retained_hud:
        files += (
            ('RetainedHudState', 'opt/RetainedHudState.java.tpl', True),
            ('OptRetainedHudExtractMixin', 'opt/OptRetainedHudExtractMixin.java.tpl', False),
            ('OptRetainedHudRendererMixin', 'opt/OptRetainedHudRendererMixin.java.tpl', False),
        )
    if has_retained_be:
        files += (
            ('OptRenderTypeRetainedMixin', 'opt/OptRenderTypeRetainedMixin.java.tpl', False),
            ('OptStaticBeLevelMixin', 'opt/OptStaticBeLevelMixin.java.tpl', False),
            ('OptStaticBeFeatureMixin', 'opt/OptStaticBeFeatureMixin.java.tpl', False),
        )
    if has_retained_be_26_2:
        files += (
            ('OptRenderTypeGroupCaptureMixin', 'opt/OptRenderTypeGroupCaptureMixin.java.tpl', False),
            ('OptStaticBeLevelMixin', 'opt/OptStaticBeLevelMixin.java.tpl', False),
            ('OptStaticBeSubmitMixin', 'opt/OptStaticBeSubmitMixin.java.tpl', False),
            ('OptStaticBeFeatureMixin', 'opt/OptStaticBeFeatureMixin.java.tpl', False),
            ('OptStaticBeExecuteMixin', 'opt/OptStaticBeExecuteMixin.java.tpl', False),
        )
    if has_terrain_engine:
        files += (
            ('TerrainFaceBuckets', 'opt/TerrainFaceBuckets.java.tpl', True),
            ('OptSectionOcclusionGenMixin', 'opt/OptSectionOcclusionGenMixin.java.tpl', False),
            ('OptCompiledSectionMeshMixin', 'opt/OptCompiledSectionMeshMixin.java.tpl', False),
            ('OptRenderSectionMeshMixin', 'opt/OptRenderSectionMeshMixin.java.tpl', False),
            ('OptTerrainPrepareMixin', 'opt/OptTerrainPrepareMixin.java.tpl', False),
        )
        if v >= (26, 2):
            files += (
                ('OptUberGpuBufferGenMixin', 'opt/OptUberGpuBufferGenMixin.java.tpl', False),
            )
    outputs = []
    for name, template, is_bridge in files:
        source = (TEMPLATES / template).read_text()
        for key, value in values.items():
            source = source.replace('@@' + key + '@@', value)
        if re.search('@@[A-Z_]+@@', source):
            raise ValueError('Unresolved optimization template: ' + version + ': ' + name)
        write(output, name, source, bridge=is_bridge)
        outputs.append(name)
    print('Generated optimization culling mixins for Minecraft ' + version + ': ' + ', '.join(outputs))

def generate_atmosphere_features(version, output):
    v = tuple(map(int, version.split('.')))
    modern = v >= (1, 21, 6)
    native = v >= (26, 1)
    layouts = v >= (26, 2)
    pearl = v >= (26, 3)
    samplers = v >= (1, 21, 11)
    needs_vertex = not samplers

    world_imports = 'import net.minecraft.core.registries.BuiltInRegistries;'
    day_time = 'level.getOverworldClockTime()' if native else 'level.getDayTime()'
    render_dist = 'mc.options.getEffectiveRenderDistance()'
    min_y = 'level.getMinY()' if v >= (1, 21, 2) else 'level.getMinBuildHeight()'
    block_path = 'BuiltInRegistries.BLOCK.getKey(block).getPath()'
    world_values = {
        'ATMO_WORLD_IMPORTS': world_imports,
        'ATMO_DAY_TIME': day_time,
        'ATMO_RENDER_DISTANCE': render_dist,
        'ATMO_MIN_Y': min_y,
        'ATMO_BLOCK_KEY_PATH': block_path,
    }

    if native:
        time_injects = (
            '  @Inject(method = "getOverworldClockTime()J", at = @At("HEAD"), cancellable = true)\n'
            '  private void nightclient$forceOverworldTime(CallbackInfoReturnable<Long> cir) {\n'
            '    long forced = AtmosphereWorldBridge.forcedDayTime(this);\n'
            '    if (forced >= 0L) {\n'
            '      cir.setReturnValue(Long.valueOf(forced));\n'
            '    }\n'
            '  }\n'
            '  @Inject(method = "getDefaultClockTime()J", at = @At("HEAD"), cancellable = true)\n'
            '  private void nightclient$forceDefaultTime(CallbackInfoReturnable<Long> cir) {\n'
            '    long forced = AtmosphereWorldBridge.forcedDayTime(this);\n'
            '    if (forced >= 0L) {\n'
            '      cir.setReturnValue(Long.valueOf(forced));\n'
            '    }\n'
            '  }'
        )
    else:
        time_injects = (
            '  @Inject(method = "getDayTime()J", at = @At("HEAD"), cancellable = true)\n'
            '  private void nightclient$forceDayTime(CallbackInfoReturnable<Long> cir) {\n'
            '    long forced = AtmosphereWorldBridge.forcedDayTime(this);\n'
            '    if (forced >= 0L) {\n'
            '      cir.setReturnValue(Long.valueOf(forced));\n'
            '    }\n'
            '  }'
        )

    cam_pos = 'camera.position()' if v >= (1, 21, 11) else 'camera.getPosition()'
    cam_pitch = 'camera.xRot()' if v >= (1, 21, 11) else 'camera.getXRot()'
    cam_yaw = 'camera.yRot()' if v >= (1, 21, 11) else 'camera.getYRot()'
    cam_submerged = 'camera.getFluidInCamera() != net.minecraft.world.level.material.FogType.NONE'

    if v <= (1, 20, 4):
        render_desc = 'renderLevel(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V'
        render_args = 'com.mojang.blaze3d.vertex.PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline, net.minecraft.client.Camera camera, net.minecraft.client.renderer.GameRenderer gameRenderer, net.minecraft.client.renderer.LightTexture lightTexture, org.joml.Matrix4f projectionMatrix, '
        capture_call = (
            'net.minecraft.world.phys.Vec3 p = ' + cam_pos + ';\n'
            '    float[] vr = new float[16], pr = new float[16];\n'
            '    poseStack.last().pose().get(vr);\n'
            '    projectionMatrix.get(pr);\n'
            '    AtmosphereBackend.captureFrame(p.x, p.y, p.z, ' + cam_pitch + ', ' + cam_yaw + ', ' + cam_submerged + ', vr, pr);'
        )
    elif v <= (1, 20, 6):
        render_desc = 'renderLevel(FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V'
        render_args = 'float partialTick, long finishNanoTime, boolean renderBlockOutline, net.minecraft.client.Camera camera, net.minecraft.client.renderer.GameRenderer gameRenderer, net.minecraft.client.renderer.LightTexture lightTexture, org.joml.Matrix4f frustumMatrix, org.joml.Matrix4f projectionMatrix, '
        capture_call = (
            'net.minecraft.world.phys.Vec3 p = ' + cam_pos + ';\n'
            '    float[] vr = new float[16], pr = new float[16];\n'
            '    frustumMatrix.get(vr);\n'
            '    projectionMatrix.get(pr);\n'
            '    AtmosphereBackend.captureFrame(p.x, p.y, p.z, ' + cam_pitch + ', ' + cam_yaw + ', ' + cam_submerged + ', vr, pr);'
        )
    elif v <= (1, 21, 1):
        render_desc = 'renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V'
        render_args = 'net.minecraft.client.DeltaTracker deltaTracker, boolean renderBlockOutline, net.minecraft.client.Camera camera, net.minecraft.client.renderer.GameRenderer gameRenderer, net.minecraft.client.renderer.LightTexture lightTexture, org.joml.Matrix4f frustumMatrix, org.joml.Matrix4f projectionMatrix, '
        capture_call = (
            'net.minecraft.world.phys.Vec3 p = ' + cam_pos + ';\n'
            '    float[] vr = new float[16], pr = new float[16];\n'
            '    frustumMatrix.get(vr);\n'
            '    projectionMatrix.get(pr);\n'
            '    AtmosphereBackend.captureFrame(p.x, p.y, p.z, ' + cam_pitch + ', ' + cam_yaw + ', ' + cam_submerged + ', vr, pr);'
        )
    elif v <= (1, 21, 3):
        render_desc = 'renderLevel(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V'
        render_args = 'com.mojang.blaze3d.resource.GraphicsResourceAllocator allocator, net.minecraft.client.DeltaTracker deltaTracker, boolean renderBlockOutline, net.minecraft.client.Camera camera, net.minecraft.client.renderer.GameRenderer gameRenderer, net.minecraft.client.renderer.LightTexture lightTexture, org.joml.Matrix4f frustumMatrix, org.joml.Matrix4f projectionMatrix, '
        capture_call = (
            'net.minecraft.world.phys.Vec3 p = ' + cam_pos + ';\n'
            '    float[] vr = new float[16], pr = new float[16];\n'
            '    frustumMatrix.get(vr);\n'
            '    projectionMatrix.get(pr);\n'
            '    AtmosphereBackend.captureFrame(p.x, p.y, p.z, ' + cam_pitch + ', ' + cam_yaw + ', ' + cam_submerged + ', vr, pr);'
        )
    elif v <= (1, 21, 5):
        render_desc = 'renderLevel(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V'
        render_args = 'com.mojang.blaze3d.resource.GraphicsResourceAllocator allocator, net.minecraft.client.DeltaTracker deltaTracker, boolean renderBlockOutline, net.minecraft.client.Camera camera, net.minecraft.client.renderer.GameRenderer gameRenderer, org.joml.Matrix4f frustumMatrix, org.joml.Matrix4f projectionMatrix, '
        capture_call = (
            'net.minecraft.world.phys.Vec3 p = ' + cam_pos + ';\n'
            '    float[] vr = new float[16], pr = new float[16];\n'
            '    frustumMatrix.get(vr);\n'
            '    projectionMatrix.get(pr);\n'
            '    AtmosphereBackend.captureFrame(p.x, p.y, p.z, ' + cam_pitch + ', ' + cam_yaw + ', ' + cam_submerged + ', vr, pr);'
        )
    elif v <= (1, 21, 8):
        render_desc = 'renderLevel(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V'
        render_args = 'com.mojang.blaze3d.resource.GraphicsResourceAllocator allocator, net.minecraft.client.DeltaTracker deltaTracker, boolean renderBlockOutline, net.minecraft.client.Camera camera, org.joml.Matrix4f frustumMatrix, org.joml.Matrix4f projectionMatrix, com.mojang.blaze3d.buffers.GpuBufferSlice fogBuffer, org.joml.Vector4f fogColor, boolean renderSky, '
        capture_call = (
            'net.minecraft.world.phys.Vec3 p = ' + cam_pos + ';\n'
            '    float[] vr = new float[16], pr = new float[16];\n'
            '    frustumMatrix.get(vr);\n'
            '    projectionMatrix.get(pr);\n'
            '    AtmosphereBackend.captureFrame(p.x, p.y, p.z, ' + cam_pitch + ', ' + cam_yaw + ', ' + cam_submerged + ', vr, pr);'
        )
    elif v <= (1, 21, 11):
        render_desc = 'renderLevel(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V'
        render_args = 'com.mojang.blaze3d.resource.GraphicsResourceAllocator allocator, net.minecraft.client.DeltaTracker deltaTracker, boolean renderBlockOutline, net.minecraft.client.Camera camera, org.joml.Matrix4f frustumMatrix, org.joml.Matrix4f projectionMatrix, org.joml.Matrix4f cullProjectionMatrix, com.mojang.blaze3d.buffers.GpuBufferSlice fogBuffer, org.joml.Vector4f fogColor, boolean renderSky, '
        capture_call = (
            'net.minecraft.world.phys.Vec3 p = ' + cam_pos + ';\n'
            '    float[] vr = new float[16], pr = new float[16];\n'
            '    frustumMatrix.get(vr);\n'
            '    projectionMatrix.get(pr);\n'
            '    AtmosphereBackend.captureFrame(p.x, p.y, p.z, ' + cam_pitch + ', ' + cam_yaw + ', ' + cam_submerged + ', vr, pr);'
        )
    elif v <= (26, 1, 2):
        render_desc = 'renderLevel(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZLnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;)V'
        render_args = 'com.mojang.blaze3d.resource.GraphicsResourceAllocator allocator, net.minecraft.client.DeltaTracker deltaTracker, boolean renderBlockOutline, net.minecraft.client.renderer.state.level.CameraRenderState cameraState, org.joml.Matrix4fc modelViewMatrix, com.mojang.blaze3d.buffers.GpuBufferSlice fogBuffer, org.joml.Vector4f fogColor, boolean renderSky, net.minecraft.client.renderer.chunk.ChunkSectionsToRender chunkSections, '
        capture_call = (
            'if (cameraState == null || cameraState.pos == null || cameraState.viewRotationMatrix == null || cameraState.projectionMatrix == null) return;\n'
            '    float[] vr = new float[16], pr = new float[16];\n'
            '    cameraState.viewRotationMatrix.get(vr);\n'
            '    cameraState.projectionMatrix.get(pr);\n'
            '    boolean sub = cameraState.fogType != null && cameraState.fogType != net.minecraft.world.level.material.FogType.NONE;\n'
            '    AtmosphereBackend.captureFrame(cameraState.pos.x, cameraState.pos.y, cameraState.pos.z, cameraState.xRot, cameraState.yRot, sub, vr, pr);'
        )
    elif version == '26.2':
        render_desc = 'render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V'
        render_args = 'com.mojang.blaze3d.resource.GraphicsResourceAllocator allocator, net.minecraft.client.DeltaTracker deltaTracker, boolean renderBlockOutline, net.minecraft.client.renderer.state.level.CameraRenderState cameraState, org.joml.Matrix4fc modelViewMatrix, com.mojang.blaze3d.buffers.GpuBufferSlice fogBuffer, org.joml.Vector4f fogColor, boolean renderSky, '
        capture_call = (
            'if (cameraState == null || cameraState.pos == null || cameraState.viewRotationMatrix == null || cameraState.projectionMatrix == null) return;\n'
            '    float[] vr = new float[16], pr = new float[16];\n'
            '    cameraState.viewRotationMatrix.get(vr);\n'
            '    cameraState.projectionMatrix.get(pr);\n'
            '    boolean sub = cameraState.fogType != null && cameraState.fogType != net.minecraft.world.level.material.FogType.NONE;\n'
            '    AtmosphereBackend.captureFrame(cameraState.pos.x, cameraState.pos.y, cameraState.pos.z, cameraState.xRot, cameraState.yRot, sub, vr, pr);'
        )
    else:
        render_desc = 'render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZZ)V'
        render_args = 'com.mojang.blaze3d.resource.GraphicsResourceAllocator allocator, boolean renderBlockOutline, net.minecraft.client.renderer.state.level.CameraRenderState cameraState, com.mojang.renderpearl.api.buffers.GpuBufferSlice fogBuffer, org.joml.Vector4f fogColor, boolean renderSky, boolean flag2, '
        capture_call = (
            'if (cameraState == null || cameraState.pos == null || cameraState.viewRotationMatrix == null || cameraState.projectionMatrix == null) return;\n'
            '    float[] vr = new float[16], pr = new float[16];\n'
            '    cameraState.viewRotationMatrix.get(vr);\n'
            '    cameraState.projectionMatrix.get(pr);\n'
            '    boolean sub = cameraState.fogType != null && cameraState.fogType != net.minecraft.world.level.material.FogType.NONE;\n'
            '    AtmosphereBackend.captureFrame(cameraState.pos.x, cameraState.pos.y, cameraState.pos.z, cameraState.xRot, cameraState.yRot, sub, vr, pr);'
        )
    capture_call += '\n    CustomModelBridge.beginWorldPass(pr);'

    if not modern:
        if version == '1.21.5':
            legacy_imports = (
                'import com.mojang.blaze3d.opengl.GlDevice;\n'
                'import com.mojang.blaze3d.opengl.GlStateManager;\n'
                'import com.mojang.blaze3d.opengl.GlTexture;\n'
                'import com.mojang.blaze3d.systems.RenderSystem;'
            )
            main_fbo = 'target.getColorTexture() != null ? ((GlTexture) target.getColorTexture()).getFbo(((GlDevice) RenderSystem.getDevice()).directStateAccess(), target.getDepthTexture()) : 0'
            main_color = 'target.getColorTexture() != null ? ((GlTexture) target.getColorTexture()).glId() : 0'
            main_depth = 'target.getDepthTexture() != null ? ((GlTexture) target.getDepthTexture()).glId() : 0'
            post_restore = (
                'GlStateManager._enableBlend(); GlStateManager._disableBlend();\n'
                '        if (snap.blend) GlStateManager._enableBlend();\n'
                '        GlStateManager._blendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);\n'
                '        GlStateManager._blendFuncSeparate(snap.srcRgb, snap.dstRgb, snap.srcAlpha, snap.dstAlpha);'
            )
        else:
            legacy_imports = ''
            main_fbo = 'target.frameBufferId'
            main_color = 'target.getColorTextureId()'
            main_depth = 'target.getDepthTextureId()'
            post_restore = ''
        backend_tpl = (TEMPLATES / 'backends/LegacyAtmosphereBackend.java.tpl').read_text()
        backend_values = {
            'ATMO_LEGACY_IMPORTS': legacy_imports,
            'ATMO_MAIN_FBO': main_fbo,
            'ATMO_MAIN_COLOR': main_color,
            'ATMO_MAIN_DEPTH': main_depth,
            'ATMO_POST_RESTORE': post_restore,
        }
    else:
        pipe_pkg = 'com.mojang.renderpearl.api.pipeline' if pearl else 'com.mojang.blaze3d.pipeline'
        tex_pkg = 'com.mojang.renderpearl.api.textures' if pearl else 'com.mojang.blaze3d.textures'
        buf_pkg = 'com.mojang.renderpearl.api.buffers' if pearl else 'com.mojang.blaze3d.buffers'
        cmd_pkg = 'com.mojang.renderpearl.api.commands' if pearl else 'com.mojang.blaze3d.systems'
        uni_pkg = pipe_pkg if pearl else 'com.mojang.blaze3d.shaders'
        cmp_pkg = pipe_pkg if pearl else 'com.mojang.blaze3d.platform'
        prim_pkg = pipe_pkg if pearl else 'com.mojang.blaze3d'
        dialect = 'pearl' if pearl else ('vulkan' if layouts else 'ubo')
        rev_depth = (
            '(RenderSystem.getDevice().getDeviceInfo().isZZeroToOne() ? 1 : 3)'
            if layouts
            else ('(RenderSystem.getDevice().isZZeroToOne() ? 2 : 0)' if native else '0')
        )

        imp = [
            f'import {pipe_pkg}.RenderPipeline;',
            f'import {pipe_pkg}.BlendFunction;',
            f'import {tex_pkg}.GpuTexture;',
            f'import {tex_pkg}.GpuTextureView;',
            ('import com.mojang.renderpearl.api.GpuFormat;' if pearl else ('import com.mojang.blaze3d.GpuFormat;' if layouts else f'import {tex_pkg}.TextureFormat;')),
            f'import {tex_pkg}.FilterMode;',
            f'import {buf_pkg}.GpuBuffer;',
            f'import {cmd_pkg}.CommandEncoder;',
            f'import {cmd_pkg}.RenderPass;',
            f'import {uni_pkg}.UniformType;',
        ]
        if not samplers:
            imp.append(f'import {tex_pkg}.AddressMode;')
        if native:
            imp.append(f'import {pipe_pkg}.ColorTargetState;')
            imp.append(f'import {pipe_pkg}.DepthStencilState;')
            imp.append(f'import {cmp_pkg}.CompareOp;')
        else:
            imp.append('import com.mojang.blaze3d.platform.DepthTestFunction;')
        if layouts:
            imp.append(f'import {pipe_pkg}.BindGroupLayout;')
            imp.append(f'import {prim_pkg}.PrimitiveTopology;')
        else:
            imp.append('import com.mojang.blaze3d.vertex.DefaultVertexFormat;')
            imp.append('import com.mojang.blaze3d.vertex.VertexFormat;')

        def pipe_bindings(samplers_list, ubo_name):
            if layouts:
                parts = ['BindGroupLayout.builder()']
                for s in samplers_list:
                    if pearl:
                        parts.append(f'.withUniform("{s}", UniformType.COMBINED_IMAGE_SAMPLER)')
                    else:
                        parts.append(f'.withSampler("{s}")')
                parts.append(f'.withUniform("{ubo_name}", UniformType.UNIFORM_BUFFER).build()')
                return '.withBindGroupLayout(' + ''.join(parts) + ')'
            else:
                parts = [f'.withSampler("{s}")' for s in samplers_list]
                parts.append(f'.withUniform("{ubo_name}", UniformType.UNIFORM_BUFFER)')
                return ''.join(parts)

        if native:
            state_fs = '.withColorTargetState(ColorTargetState.DEFAULT).withDepthStencilState(Optional.empty())'
            state_precip_le = '.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))'
            state_precip_ge = '.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))'
        else:
            state_fs = '.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).withoutBlend()'
            state_precip = '.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST).withDepthWrite(false).withBlend(BlendFunction.TRANSLUCENT)'

        topo_tail = (
            '.withCull(false).withPrimitiveTopology(PrimitiveTopology.TRIANGLES).build()'
            if layouts
            else '.withCull(false).withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES).build()'
        )

        def build_pipe_expr(loc_id, vsh_id, fsh_id, s_list, ubo_name, state_expr):
            return (
                f'RenderPipeline.builder().withLocation(GameBridge.identifier("{loc_id}"))'
                f'.withVertexShader(GameBridge.identifier("{vsh_id}"))'
                f'.withFragmentShader(GameBridge.identifier("{fsh_id}"))'
                + pipe_bindings(s_list, ubo_name)
                + state_expr
                + topo_tail
            )

        fs_vsh = f'nightclient:core/fullscreen_{dialect}'
        build_lines = [
            'pipeBokehEx = ' + build_pipe_expr('nightclient:pipeline/atmo_bokeh_ex', fs_vsh, f'nightclient:core/atmosphere_bokeh_extract_{dialect}', ['Sampler0', 'Sampler1'], 'NightAtmoBokehExtract', state_fs) + ';',
            'pipeBokehDisc = ' + build_pipe_expr('nightclient:pipeline/atmo_bokeh_disc', fs_vsh, f'nightclient:core/atmosphere_bokeh_disc_{dialect}', ['Sampler0'], 'NightAtmoBokehDisc', state_fs) + ';',
            'pipeBokehStreak = ' + build_pipe_expr('nightclient:pipeline/atmo_bokeh_streak', fs_vsh, f'nightclient:core/atmosphere_bokeh_streak_{dialect}', ['Sampler0', 'Sampler1'], 'NightAtmoBokehStreak', state_fs) + ';',
            'pipeDawn = ' + build_pipe_expr('nightclient:pipeline/atmo_dawn', fs_vsh, f'nightclient:core/atmosphere_dawn_{dialect}', ['Sampler0', 'Sampler1'], 'NightAtmoDawn', state_fs) + ';',
            'pipeCustomSky = ' + build_pipe_expr('nightclient:pipeline/atmo_custom_sky', fs_vsh, f'nightclient:core/atmosphere_custom_sky_{dialect}', ['Sampler0', 'Sampler1', 'Sampler2'], 'NightCustomSky', state_fs) + ';',
            'pipeMotionBlur = ' + build_pipe_expr('nightclient:pipeline/atmo_motion_blur', fs_vsh, f'nightclient:core/atmosphere_motion_blur_{dialect}', ['Sampler0', 'Sampler1'], 'NightMotionBlur', state_fs) + ';',
        ]
        for idx, mname in enumerate(['dust', 'snow', 'sakura', 'autumn', 'fireflies', 'mystic']):
            build_lines.append(
                f'pipeMood[{idx}] = ' + build_pipe_expr(f'nightclient:pipeline/atmo_mood_{mname}', fs_vsh, f'nightclient:core/atmosphere_mood_{mname}_{dialect}', ['Sampler0', 'Sampler1', 'Sampler2', 'Sampler3'], 'NightAtmoMood', state_fs) + ';'
            )
        if native:
            build_lines.append(
                'pipePrecipLe = ' + build_pipe_expr('nightclient:pipeline/atmo_precip_le', f'nightclient:core/atmosphere_precip_{dialect}', f'nightclient:core/atmosphere_precip_{dialect}', ['Columns', 'Scene'], 'NightAtmoPrecip', state_precip_le) + ';'
            )
            build_lines.append(
                'pipePrecipGe = ' + build_pipe_expr('nightclient:pipeline/atmo_precip_ge', f'nightclient:core/atmosphere_precip_{dialect}', f'nightclient:core/atmosphere_precip_{dialect}', ['Columns', 'Scene'], 'NightAtmoPrecip', state_precip_ge) + ';'
            )
        else:
            build_lines.append(
                'pipePrecip = ' + build_pipe_expr('nightclient:pipeline/atmo_precip', f'nightclient:core/atmosphere_precip_{dialect}', f'nightclient:core/atmosphere_precip_{dialect}', ['Columns', 'Scene'], 'NightAtmoPrecip', state_precip) + ';'
            )

        def bind_tex(name, view_expr, mode):
            if pearl:
                return f'pass.setUniform("{name}", {view_expr}, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.{mode}));'
            elif samplers:
                return f'pass.bindTexture("{name}", {view_expr}, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.{mode}));'
            else:
                return f'pass.bindSampler("{name}", {view_expr});'

        set_pipe = lambda p: (f'pass.setPipeline(RenderSystem.getCompiledPipeline({p}));' if pearl else f'pass.setPipeline({p});')
        empty_color = 'Optional.empty()' if layouts else 'OptionalInt.empty()'

        backend_tpl = (TEMPLATES / 'backends/NativeAtmosphereBackend.java.tpl').read_text()
        backend_values = {
            'ATMO_NATIVE_IMPORTS': '\n'.join(imp),
            'ATMO_MAIN_TARGET': 'mc.gameRenderer.mainRenderTarget()' if layouts else 'mc.getMainRenderTarget()',
            'ATMO_RGBA8_FORMAT': 'GpuFormat.RGBA8_UNORM' if layouts else 'TextureFormat.RGBA8',
            'ATMO_REVERSED_DEPTH': rev_depth,
            'ATMO_PRECIP_PIPE_DECL': 'private static RenderPipeline pipePrecipLe, pipePrecipGe;' if native else 'private static RenderPipeline pipePrecip;',
            'ATMO_PRECIP_PIPE_EXPR': '((revDepth == 1 || revDepth == 3) ? pipePrecipGe : pipePrecipLe)' if native else 'pipePrecip',
            'ATMO_PRECIP_PIPE_CLOSE': 'pipePrecipLe = pipePrecipGe = null;' if native else 'pipePrecip = null;',
            'ATMO_VERTEX_DECL': 'private static GpuBuffer dummyVertex;' if needs_vertex else '',
            'ATMO_VERTEX_CREATE': 'if (dummyVertex == null) dummyVertex = RenderSystem.getDevice().createBuffer(() -> "NightClient Atmo Vertex", GpuBuffer.USAGE_VERTEX, 64);' if needs_vertex else '',
            'ATMO_VERTEX_BIND': 'pass.setVertexBuffer(0, dummyVertex);' if needs_vertex else '',
            'ATMO_VERTEX_CLOSE': 'if (dummyVertex != null) { dummyVertex.close(); dummyVertex = null; }' if needs_vertex else '',
            'ATMO_PASS_MAIN_NODEPTH': f'encoder.createRenderPass(() -> "NightClient Atmosphere", mainColorView, {empty_color})',
            'ATMO_PASS_MAIN_DEPTH': f'encoder.createRenderPass(() -> "NightClient Atmosphere 3D", mainColorView, {empty_color}, mainDepthView, OptionalDouble.empty())',
            'ATMO_PASS_QA': f'encoder.createRenderPass(() -> "NightClient Bokeh qA", qAView, {empty_color})',
            'ATMO_PASS_QB': f'encoder.createRenderPass(() -> "NightClient Bokeh qB", qBView, {empty_color})',
            'ATMO_PASS_QC': f'encoder.createRenderPass(() -> "NightClient Bokeh qC", qCView, {empty_color})',
            'ATMO_SET_PIPE_BOKEH_EX': set_pipe('pipeBokehEx'),
            'ATMO_SET_PIPE_BOKEH_DISC': set_pipe('pipeBokehDisc'),
            'ATMO_SET_PIPE_BOKEH_STREAK': set_pipe('pipeBokehStreak'),
            'ATMO_SET_PIPE_DAWN': set_pipe('pipeDawn'),
            'ATMO_SET_PIPE_CUSTOM_SKY': set_pipe('pipeCustomSky'),
            'ATMO_SET_PIPE_MOTION_BLUR': set_pipe('pipeMotionBlur'),
            'ATMO_SET_PIPE_MOOD': set_pipe('moodPipe'),
            'ATMO_SET_PIPE_PRECIP': set_pipe('precipPipe'),
            'ATMO_DRAW_FS': 'pass.draw(3, 1, 0, 0);' if layouts else 'pass.draw(0, 3);',
            'ATMO_DRAW_PRECIP': 'pass.draw(plan.precipVertexCount, 1, 0, 0);' if layouts else 'pass.draw(0, plan.precipVertexCount);',
            'ATMO_DEPTH_FILTER_PREP': '' if samplers else 'mainDepth.setAddressMode(AddressMode.CLAMP_TO_EDGE); mainDepth.setTextureFilter(FilterMode.NEAREST, false);',
            'ATMO_COLUMNS_TEX_SETUP': '' if samplers else 'columnsTex.setAddressMode(AddressMode.CLAMP_TO_EDGE); columnsTex.setTextureFilter(FilterMode.NEAREST, false);',
            'ATMO_LINEAR_ZERO_SETUP': '' if samplers else 'zeroTex.setAddressMode(AddressMode.CLAMP_TO_EDGE); zeroTex.setTextureFilter(FilterMode.LINEAR, false);',
            'ATMO_LINEAR_SKY_ATLAS_SETUP': '' if samplers else 'skyAtlasTex.setAddressMode(AddressMode.CLAMP_TO_EDGE); skyAtlasTex.setTextureFilter(FilterMode.LINEAR, false);',
            'ATMO_LINEAR_SCENE_SETUP': '' if samplers else 'sceneCopy.setAddressMode(AddressMode.CLAMP_TO_EDGE); sceneCopy.setTextureFilter(FilterMode.LINEAR, false);',
            'ATMO_LINEAR_QUARTER_SETUP': '' if samplers else 'qA.setAddressMode(AddressMode.CLAMP_TO_EDGE); qA.setTextureFilter(FilterMode.LINEAR, false); qB.setAddressMode(AddressMode.CLAMP_TO_EDGE); qB.setTextureFilter(FilterMode.LINEAR, false); qC.setAddressMode(AddressMode.CLAMP_TO_EDGE); qC.setTextureFilter(FilterMode.LINEAR, false);',
            'ATMO_BIND_BOKEH_EX': bind_tex('Sampler0', 'sceneCopyView', 'LINEAR') + ' ' + bind_tex('Sampler1', 'mainDepthView', 'NEAREST'),
            'ATMO_BIND_BOKEH_DISC': bind_tex('Sampler0', 'qAView', 'LINEAR'),
            'ATMO_BIND_BOKEH_STREAK_WIDE': bind_tex('Sampler0', 'qAView', 'LINEAR') + ' ' + bind_tex('Sampler1', 'zeroView', 'LINEAR'),
            'ATMO_BIND_BOKEH_STREAK_FINE': bind_tex('Sampler0', 'qCView', 'LINEAR') + ' ' + bind_tex('Sampler1', 'qBView', 'LINEAR'),
            'ATMO_BIND_DAWN': bind_tex('Sampler0', 'sceneCopyView', 'LINEAR') + ' ' + bind_tex('Sampler1', 'mainDepthView', 'NEAREST'),
            'ATMO_BIND_CUSTOM_SKY': bind_tex('Sampler0', 'sceneCopyView', 'LINEAR') + ' ' + bind_tex('Sampler1', 'mainDepthView', 'NEAREST') + ' ' + bind_tex('Sampler2', 'skyAtlasView', 'LINEAR'),
            'ATMO_BIND_MOTION_BLUR': bind_tex('Sampler0', 'sceneCopyView', 'LINEAR') + ' ' + bind_tex('Sampler1', 'mainDepthView', 'NEAREST'),
            'ATMO_BIND_MOOD': bind_tex('Sampler0', 'sceneCopyView', 'LINEAR') + ' ' + bind_tex('Sampler1', 'mainDepthView', 'NEAREST') + ' ' + bind_tex('Sampler2', 'bokehView', 'LINEAR') + ' ' + bind_tex('Sampler3', 'columnsView', 'NEAREST'),
            'ATMO_BIND_PRECIP': bind_tex('Columns', 'columnsView', 'NEAREST') + ' ' + bind_tex('Scene', 'sceneCopyView', 'LINEAR'),
            'ATMO_BUILD_PIPELINES': '\n    '.join(build_lines),
        }

    world_src = (TEMPLATES / 'AtmosphereWorldBridge.java.tpl').read_text()
    for k, val in world_values.items():
        world_src = world_src.replace('@@' + k + '@@', val)

    time_src = (TEMPLATES / 'AtmosphereTimeMixin.java.tpl').read_text().replace('@@ATMO_TIME_INJECTS@@', time_injects)

    render_src = (
        (TEMPLATES / 'AtmosphereRenderMixin.java.tpl')
        .read_text()
        .replace('@@ATMO_RENDER_IMPORTS@@', '')
        .replace('@@ATMO_RENDER_DESC@@', render_desc)
        .replace('@@ATMO_RENDER_ARGS@@', render_args)
        .replace('@@ATMO_CAPTURE_CALL@@', capture_call)
    )

    backend_src = backend_tpl
    for k, val in backend_values.items():
        backend_src = backend_src.replace('@@' + k + '@@', val)

    stale_proj = Path(output) / 'dev/nightclient/platform/mixin/AtmosphereProjectionMixin.java'
    if not native and stale_proj.exists():
        stale_proj.unlink()

    cloud_owner = 'net.minecraft.client.renderer.LevelRenderer' if v < (1, 21, 2) else 'net.minecraft.client.renderer.CloudRenderer'
    cloud_method = 'renderClouds' if v < (1, 21, 2) else 'render'
    cloud_src = mixin(
        'AtmosphereCloudMixin', cloud_owner,
        f'\n  @Inject(method = "{cloud_method}", at = @At("HEAD"), cancellable = true)\n'
        '  private void nightclient$replaceVanillaClouds(CallbackInfo ci) {\n'
        '    if (AtmosphereWorldBridge.shouldReplaceVanillaClouds()) ci.cancel();\n'
        '  }\n',
        'import dev.nightclient.platform.AtmosphereWorldBridge;',
    )
    client_level_body = (
        '\n  @Inject(method = "setSkyFlashTime(I)V", at = @At("HEAD"), cancellable = true)\n'
        '  private void nightclient$suppressLightningFlash(int flashTime, CallbackInfo ci) {\n'
        '    if (flashTime > 0 && AtmosphereWorldBridge.shouldClearWeather(this)) ci.cancel();\n'
        '  }\n'
    )
    if not native:
        client_level_body += (
            '\n  @Inject(method = "tickTime()V", at = @At("HEAD"))\n'
            '  private void nightclient$beginTimeTick(CallbackInfo ci) {\n'
            '    AtmosphereWorldBridge.beginTimeTick();\n'
            '  }\n'
            '  @Inject(method = "tickTime()V", at = @At("RETURN"))\n'
            '  private void nightclient$endTimeTick(CallbackInfo ci) {\n'
            '    AtmosphereWorldBridge.endTimeTick();\n'
            '  }\n'
        )
    client_level_src = mixin(
        'AtmosphereClientLevelMixin',
        'net.minecraft.client.multiplayer.ClientLevel',
        client_level_body,
        'import dev.nightclient.platform.AtmosphereWorldBridge;',
    )
    if not native:
        clock_src = mixin(
            'AtmosphereClockMixin',
            'net.minecraft.client.multiplayer.ClientLevel.ClientLevelData',
            '\n  @Inject(method = "getDayTime()J", at = @At("HEAD"), cancellable = true)\n'
            '  private void nightclient$overrideClientDataTime(CallbackInfoReturnable<Long> cir) {\n'
            '    long forced = AtmosphereWorldBridge.forcedClientDataTime(this);\n'
            '    if (forced >= 0L) {\n'
            '      cir.setReturnValue(Long.valueOf(forced));\n'
            '    }\n'
            '  }\n',
            'import dev.nightclient.platform.AtmosphereWorldBridge;',
        )
    elif not pearl:
        clock_src = mixin(
            'AtmosphereClockMixin',
            'net.minecraft.client.ClientClockManager',
            '\n  @Inject(method = "getTotalTicks(Lnet/minecraft/core/Holder;)J", at = @At("HEAD"), cancellable = true)\n'
            '  private void nightclient$overrideClockTicks(\n'
            '      net.minecraft.core.Holder<net.minecraft.world.clock.WorldClock> clock,\n'
            '      CallbackInfoReturnable<Long> cir) {\n'
            '    if (clock != null && clock.is(net.minecraft.world.clock.WorldClocks.OVERWORLD)) {\n'
            '      long forced = AtmosphereWorldBridge.forcedActiveOverworldTime();\n'
            '      if (forced >= 0L) {\n'
            '        cir.setReturnValue(Long.valueOf(forced));\n'
            '      }\n'
            '    }\n'
            '  }\n',
            'import dev.nightclient.platform.AtmosphereWorldBridge;',
        )
    else:
        clock_src = mixin(
            'AtmosphereClockMixin',
            'net.minecraft.world.timeline.AttributeTrackSampler',
            '\n  @org.spongepowered.asm.mixin.Shadow\n'
            '  @org.spongepowered.asm.mixin.Final\n'
            '  private net.minecraft.core.Holder<net.minecraft.world.clock.WorldClock> clock;\n'
            '  @org.spongepowered.asm.mixin.Shadow\n'
            '  @org.spongepowered.asm.mixin.Final\n'
            '  private net.minecraft.world.clock.ClockManager clockManager;\n'
            '\n  @Redirect(\n'
            '      method = "applyTimeBased(Ljava/lang/Object;I)Ljava/lang/Object;",\n'
            '      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/clock/ClockInstance;totalTicks()J"))\n'
            '  private long nightclient$overrideTimelineTicks(net.minecraft.world.clock.ClockInstance instance) {\n'
            '    if (clockManager instanceof net.minecraft.client.ClientClockManager\n'
            '        && clock != null\n'
            '        && clock.is(net.minecraft.world.clock.WorldClocks.OVERWORLD)) {\n'
            '      long forced = AtmosphereWorldBridge.forcedActiveOverworldTime();\n'
            '      if (forced >= 0L) {\n'
            '        return forced;\n'
            '      }\n'
            '    }\n'
            '    return instance.totalTicks();\n'
            '  }\n',
            'import dev.nightclient.platform.AtmosphereWorldBridge;',
        )
    outputs_list = [
        ('AtmosphereWorldBridge', world_src, True),
        ('AtmosphereBackend', backend_src, True),
        ('AtmosphereRenderMixin', render_src, False),
        ('AtmosphereTimeMixin', time_src, False),
        ('AtmosphereClientLevelMixin', client_level_src, False),
        ('AtmosphereClockMixin', clock_src, False),
        ('AtmosphereCloudMixin', cloud_src, False),
    ]
    if native:
        slice_cls = (
            'com.mojang.renderpearl.api.buffers.GpuBufferSlice'
            if pearl
            else 'com.mojang.blaze3d.buffers.GpuBufferSlice'
        )
        slice_desc = 'L' + slice_cls.replace('.', '/') + ';'
        proj_src = (
            (TEMPLATES / 'AtmosphereProjectionMixin.java.tpl')
            .read_text()
            .replace('@@ATMO_SLICE_DESC@@', slice_desc)
            .replace('@@ATMO_SLICE_CLASS@@', slice_cls)
        )
        outputs_list.append(('AtmosphereProjectionMixin', proj_src, False))

    for label, src_txt, is_bridge in outputs_list:
        if re.search('@@[A-Z0-9_]+@@', src_txt):
            raise ValueError('Unresolved atmosphere template token in ' + version + ': ' + label)
        write(output, label, src_txt, bridge=is_bridge)

def generate_custom_models(version, output):
    v = tuple(map(int, version.split('.')))
    modern = v >= (1, 21, 6)
    native = v >= (26, 1)
    layouts = v >= (26, 2)
    pearl = v >= (26, 3)
    samplers = v >= (1, 21, 11)

    bridge_imports = [
        'import net.minecraft.core.registries.BuiltInRegistries;'
    ]
    if v >= (1, 21, 9):
        bridge_imports.append('import net.minecraft.client.renderer.entity.state.AvatarRenderState;')
    elif v >= (1, 21, 2):
        bridge_imports.append('import net.minecraft.client.renderer.entity.state.PlayerRenderState;')

    item_path_expr = 'BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath()'
    if v <= (1, 21, 1):
        is_local = 'return entityOrState == mc.player;'
    elif v <= (1, 21, 8):
        is_local = (
            'return entityOrState == mc.player '
            '|| (entityOrState instanceof PlayerRenderState && ((PlayerRenderState) entityOrState).id == mc.player.getId());'
        )
    else:
        is_local = (
            'return entityOrState == mc.player '
            '|| (entityOrState instanceof AvatarRenderState && ((AvatarRenderState) entityOrState).id == mc.player.getId());'
        )

    read_pose = 'poseStack.last().pose().get(out);'

    if v <= (1, 20, 4):
        read_mv = 'RenderSystem.getModelViewMatrix().get(out);'
        read_hand_proj = 'RenderSystem.getProjectionMatrix().get(HAND_PROJ);'
    elif v <= (1, 21, 5):
        read_mv = 'RenderSystem.getModelViewStack().get(out);'
        read_hand_proj = 'RenderSystem.getProjectionMatrix().get(HAND_PROJ);'
    else:
        read_mv = 'RenderSystem.getModelViewStack().get(out);'
        read_hand_proj = '/* Projection UBO bound via RenderSystem.getProjectionMatrixBuffer() */'

    bridge_src = (TEMPLATES / 'CustomModelBridge.java.tpl').read_text()
    for k, val in {
        'CM_BRIDGE_IMPORTS': '\n'.join(bridge_imports),
        'CM_ITEM_PATH_EXPR': item_path_expr,
        'CM_IS_LOCAL_PLAYER': is_local,
        'CM_READ_POSE_MATRIX': read_pose,
        'CM_READ_CURRENT_MV': read_mv,
        'CM_READ_HAND_PROJ': read_hand_proj,
    }.items():
        bridge_src = bridge_src.replace('@@' + k + '@@', val)

    if not modern:
        if version == '1.21.5':
            legacy_imports = (
                'import com.mojang.blaze3d.opengl.GlDevice;\n'
                'import com.mojang.blaze3d.opengl.GlStateManager;\n'
                'import com.mojang.blaze3d.opengl.GlTexture;\n'
                'import com.mojang.blaze3d.systems.RenderSystem;'
            )
            main_fbo = (
                'target.getColorTexture() != null '
                '? ((GlTexture) target.getColorTexture()).getFbo(((GlDevice) RenderSystem.getDevice()).directStateAccess(), target.getDepthTexture()) '
                ': 0'
            )
            post_restore = (
                'GlStateManager._enableBlend(); GlStateManager._disableBlend();\n'
                '      if (snap.blend) GlStateManager._enableBlend();\n'
                '      GlStateManager._blendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);\n'
                '      GlStateManager._blendFuncSeparate(snap.srcRgb, snap.dstRgb, snap.srcAlpha, snap.dstAlpha);'
            )
        else:
            legacy_imports = ''
            main_fbo = 'target.frameBufferId'
            post_restore = 'com.mojang.blaze3d.vertex.BufferUploader.invalidate();'
        backend_src = (TEMPLATES / 'backends/LegacyCustomModelBackend.java.tpl').read_text()
        for k, val in {
            'CM_LEGACY_IMPORTS': legacy_imports,
            'CM_MAIN_FBO': main_fbo,
            'CM_LEGACY_POST_RESTORE': post_restore,
        }.items():
            backend_src = backend_src.replace('@@' + k + '@@', val)
    else:
        pipe_pkg = 'com.mojang.renderpearl.api.pipeline' if pearl else 'com.mojang.blaze3d.pipeline'
        tex_pkg = 'com.mojang.renderpearl.api.textures' if pearl else 'com.mojang.blaze3d.textures'
        buf_pkg = 'com.mojang.renderpearl.api.buffers' if pearl else 'com.mojang.blaze3d.buffers'
        cmd_pkg = 'com.mojang.renderpearl.api.commands' if pearl else 'com.mojang.blaze3d.systems'
        uni_pkg = pipe_pkg if pearl else 'com.mojang.blaze3d.shaders'
        cmp_pkg = pipe_pkg if pearl else 'com.mojang.blaze3d.platform'
        prim_pkg = pipe_pkg if pearl else 'com.mojang.blaze3d'
        dialect = 'pearl' if pearl else ('vulkan' if layouts else 'ubo')
        rev_depth = (
            '(RenderSystem.getDevice().getDeviceInfo().isZZeroToOne() ? 1 : 3)'
            if layouts
            else ('(RenderSystem.getDevice().isZZeroToOne() ? 2 : 0)' if native else '0')
        )

        imp = [
            f'import {pipe_pkg}.RenderPipeline;',
            f'import {pipe_pkg}.BlendFunction;',
            f'import {tex_pkg}.GpuTexture;',
            f'import {tex_pkg}.GpuTextureView;',
            ('import com.mojang.renderpearl.api.GpuFormat;' if pearl else ('import com.mojang.blaze3d.GpuFormat;' if layouts else f'import {tex_pkg}.TextureFormat;')),
            f'import {tex_pkg}.FilterMode;',
            f'import {buf_pkg}.GpuBuffer;',
            f'import {buf_pkg}.GpuBufferSlice;',
            f'import {cmd_pkg}.CommandEncoder;',
            f'import {cmd_pkg}.RenderPass;',
            f'import {uni_pkg}.UniformType;',
            'import com.mojang.blaze3d.vertex.DefaultVertexFormat;',
        ]
        if not samplers:
            imp.append(f'import {tex_pkg}.AddressMode;')
        if native:
            imp.append(f'import {pipe_pkg}.ColorTargetState;')
            imp.append(f'import {pipe_pkg}.DepthStencilState;')
            imp.append(f'import {cmp_pkg}.CompareOp;')
        else:
            imp.append('import com.mojang.blaze3d.platform.DepthTestFunction;')
        if layouts:
            imp.append(f'import {pipe_pkg}.BindGroupLayout;')
            imp.append('import net.minecraft.client.renderer.BindGroupLayouts;')
            imp.append(f'import {prim_pkg}.PrimitiveTopology;')
            imp.append(f'import {prim_pkg}.IndexType;')
        else:
            imp.append('import com.mojang.blaze3d.vertex.VertexFormat;')

        vfmt = 'DefaultVertexFormat.ENTITY' if native else 'DefaultVertexFormat.NEW_ENTITY'
        if layouts:
            if pearl:
                bindings = (
                    '.withBindGroupLayout(BindGroupLayouts.PROJECTION)'
                    '.withBindGroupLayout(BindGroupLayout.builder()'
                    '.withUniform("Sampler0", UniformType.COMBINED_IMAGE_SAMPLER)'
                    '.withUniform("NightModelDraw", UniformType.UNIFORM_BUFFER).build())'
                )
            else:
                bindings = (
                    '.withBindGroupLayout(BindGroupLayouts.PROJECTION)'
                    '.withBindGroupLayout(BindGroupLayout.builder()'
                    '.withSampler("Sampler0")'
                    '.withUniform("NightModelDraw", UniformType.UNIFORM_BUFFER).build())'
                )
            topo = f'.withVertexBinding(0, {vfmt}).withPrimitiveTopology(PrimitiveTopology.TRIANGLES)'
        else:
            bindings = (
                '.withUniform("Projection", UniformType.UNIFORM_BUFFER)'
                '.withUniform("NightModelDraw", UniformType.UNIFORM_BUFFER)'
                '.withSampler("Sampler0")'
            )
            topo = f'.withVertexFormat({vfmt}, VertexFormat.Mode.TRIANGLES)'

        sh_id = f'nightclient:core/cosmetic_model_{dialect}'

        def make_pipe(loc_id, state_expr, cull_flag):
            return (
                f'RenderPipeline.builder().withLocation(GameBridge.identifier("{loc_id}"))'
                f'.withVertexShader(GameBridge.identifier("{sh_id}"))'
                f'.withFragmentShader(GameBridge.identifier("{sh_id}"))'
                + bindings
                + state_expr
                + f'.withCull({"true" if cull_flag else "false"})'
                + topo
                + '.build()'
            )

        if native:
            pipe_decls = (
                'private static RenderPipeline pipeOpaqueCullLe, pipeOpaqueCullGe;\n'
                '  private static RenderPipeline pipeOpaqueNoCullLe, pipeOpaqueNoCullGe;\n'
                '  private static RenderPipeline pipeTranslucentLe, pipeTranslucentGe;\n'
                '  private static RenderPipeline pipeAdditiveLe, pipeAdditiveGe;'
            )
            pipe_check = 'pipeOpaqueCullLe != null'
            st_op_le = '.withColorTargetState(ColorTargetState.DEFAULT).withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true))'
            st_op_ge = '.withColorTargetState(ColorTargetState.DEFAULT).withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))'
            st_tr_le = '.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))'
            st_tr_ge = '.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))'
            st_ad_le = '.withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE)).withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))'
            st_ad_ge = '.withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE)).withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))'
            build_pipes = '\n    '.join([
                'pipeOpaqueCullLe = ' + make_pipe('nightclient:pipeline/cosmetic_opaque_cull_le', st_op_le, True) + ';',
                'pipeOpaqueCullGe = ' + make_pipe('nightclient:pipeline/cosmetic_opaque_cull_ge', st_op_ge, True) + ';',
                'pipeOpaqueNoCullLe = ' + make_pipe('nightclient:pipeline/cosmetic_opaque_nocull_le', st_op_le, False) + ';',
                'pipeOpaqueNoCullGe = ' + make_pipe('nightclient:pipeline/cosmetic_opaque_nocull_ge', st_op_ge, False) + ';',
                'pipeTranslucentLe = ' + make_pipe('nightclient:pipeline/cosmetic_translucent_le', st_tr_le, False) + ';',
                'pipeTranslucentGe = ' + make_pipe('nightclient:pipeline/cosmetic_translucent_ge', st_tr_ge, False) + ';',
                'pipeAdditiveLe = ' + make_pipe('nightclient:pipeline/cosmetic_additive_le', st_ad_le, False) + ';',
                'pipeAdditiveGe = ' + make_pipe('nightclient:pipeline/cosmetic_additive_ge', st_ad_ge, False) + ';',
            ])
            clear_pipes = (
                'pipeOpaqueCullLe = pipeOpaqueCullGe = null;\n'
                '    pipeOpaqueNoCullLe = pipeOpaqueNoCullGe = null;\n'
                '    pipeTranslucentLe = pipeTranslucentGe = null;\n'
                '    pipeAdditiveLe = pipeAdditiveGe = null;'
            )
            p_op_cull = '((revDepth == 1 || revDepth == 3) ? pipeOpaqueCullGe : pipeOpaqueCullLe)'
            p_op_nocull = '((revDepth == 1 || revDepth == 3) ? pipeOpaqueNoCullGe : pipeOpaqueNoCullLe)'
            p_trans = '((revDepth == 1 || revDepth == 3) ? pipeTranslucentGe : pipeTranslucentLe)'
            p_add = '((revDepth == 1 || revDepth == 3) ? pipeAdditiveGe : pipeAdditiveLe)'
        else:
            pipe_decls = (
                'private static RenderPipeline pipeOpaqueCull, pipeOpaqueNoCull;\n'
                '  private static RenderPipeline pipeTranslucent, pipeAdditive;'
            )
            pipe_check = 'pipeOpaqueCull != null'
            st_op = '.withoutBlend().withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST).withDepthWrite(true)'
            st_tr = '.withBlend(BlendFunction.TRANSLUCENT).withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST).withDepthWrite(false)'
            st_ad = '.withBlend(BlendFunction.ADDITIVE).withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST).withDepthWrite(false)'
            build_pipes = '\n    '.join([
                'pipeOpaqueCull = ' + make_pipe('nightclient:pipeline/cosmetic_opaque_cull', st_op, True) + ';',
                'pipeOpaqueNoCull = ' + make_pipe('nightclient:pipeline/cosmetic_opaque_nocull', st_op, False) + ';',
                'pipeTranslucent = ' + make_pipe('nightclient:pipeline/cosmetic_translucent', st_tr, False) + ';',
                'pipeAdditive = ' + make_pipe('nightclient:pipeline/cosmetic_additive', st_ad, False) + ';',
            ])
            clear_pipes = 'pipeOpaqueCull = pipeOpaqueNoCull = pipeTranslucent = pipeAdditive = null;'
            p_op_cull = 'pipeOpaqueCull'
            p_op_nocull = 'pipeOpaqueNoCull'
            p_trans = 'pipeTranslucent'
            p_add = 'pipeAdditive'

        empty_color = 'Optional.empty()' if layouts else 'OptionalInt.empty()'
        if pearl:
            bind_tex = 'pass.setUniform("Sampler0", gpu.texView, RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR));'
        elif samplers:
            bind_tex = 'pass.bindTexture("Sampler0", gpu.texView, RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR));'
        else:
            bind_tex = 'pass.bindSampler("Sampler0", gpu.texView);'

        backend_src = (TEMPLATES / 'backends/NativeCustomModelBackend.java.tpl').read_text()
        for k, val in {
            'CM_NATIVE_IMPORTS': '\n'.join(imp),
            'CM_PIPE_DECLS': pipe_decls,
            'CM_PIPE_CHECK': pipe_check,
            'CM_BUILD_PIPELINES': build_pipes,
            'CM_CLEAR_PIPELINES': clear_pipes,
            'CM_MAIN_TARGET': 'mc.gameRenderer.mainRenderTarget()' if layouts else 'mc.getMainRenderTarget()',
            'CM_HAND_COLOR_VIEW': (
                'target.getColorTextureView()'
                if pearl
                else '(RenderSystem.outputColorTextureOverride != null ? RenderSystem.outputColorTextureOverride : target.getColorTextureView())'
            ),
            'CM_HAND_DEPTH_VIEW': (
                'target.getDepthTextureView()'
                if pearl
                else '(RenderSystem.outputDepthTextureOverride != null ? RenderSystem.outputDepthTextureOverride : target.getDepthTextureView())'
            ),
            'CM_REVERSED_DEPTH': rev_depth,
            'CM_PIPE_OPAQUE_CULL': p_op_cull,
            'CM_PIPE_OPAQUE_NOCULL': p_op_nocull,
            'CM_PIPE_TRANSLUCENT': p_trans,
            'CM_PIPE_ADDITIVE': p_add,
            'CM_CREATE_PASS': f'encoder.createRenderPass(() -> label, colorView, {empty_color}, depthView, OptionalDouble.empty())',
            'CM_SET_PIPE': (
                'pass.setPipeline(RenderSystem.getCompiledPipeline(activePipe));'
                if pearl
                else 'pass.setPipeline(activePipe);'
            ),
            'CM_BIND_TEXTURE': bind_tex,
            'CM_BIND_VERTEX': 'pass.setVertexBuffer(0, gpu.vbo.slice());' if layouts else 'pass.setVertexBuffer(0, gpu.vbo);',
            'CM_BIND_INDEX': 'pass.setIndexBuffer(gpu.ibo, IndexType.INT);' if layouts else 'pass.setIndexBuffer(gpu.ibo, VertexFormat.IndexType.INT);',
            'CM_DRAW_INDEXED': (
                'pass.drawIndexed(sub.indexCount, 1, sub.firstIndex, 0, 0);'
                if layouts
                else 'pass.drawIndexed(0, sub.firstIndex, sub.indexCount, 1);'
            ),
            'CM_RGBA8_FORMAT': 'GpuFormat.RGBA8_UNORM' if layouts else 'TextureFormat.RGBA8',
            'CM_TEX_SETUP': '' if samplers else 'tex.setAddressMode(AddressMode.REPEAT); tex.setTextureFilter(FilterMode.LINEAR, false);',
        }.items():
            backend_src = backend_src.replace('@@' + k + '@@', val)

    cam_profiles = json.loads((ROOT / 'modules/platform/compatibility/camera-features.json').read_text())
    cap = cam_profiles[version]['capture']
    if pearl:
        hand_flush_body = (
            '  @Inject(method = "' + cap['selector'] + '", at = @At("HEAD"))\n'
            '  private void nightclient$beginHand3d(CallbackInfo ci) {\n'
            '    CustomModelBridge.beginHandPass();\n'
            '  }\n'
            '  @Inject(method = "' + cap['selector'] + '", at = @At("RETURN"))\n'
            '  private void nightclient$flushHand3d(' + signature(cap['args']) + ', CallbackInfo ci) {\n'
            '    CustomModelBackend.flushHandsWithDepth(a2);\n'
            '  }'
        )
    else:
        hand_flush_body = (
            '  @Inject(method = "' + cap['selector'] + '", at = @At("HEAD"))\n'
            '  private void nightclient$beginHand3d(CallbackInfo ci) {\n'
            '    CustomModelBridge.beginHandPass();\n'
            '  }\n'
            '  @Inject(method = "' + cap['selector'] + '", at = @At("RETURN"))\n'
            '  private void nightclient$flushHand3d(CallbackInfo ci) {\n'
            '    CustomModelBackend.flushHands();\n'
            '  }'
        )
    hand_flush_src = mixin(
        'CustomModelHandFlushMixin',
        'net.minecraft.client.renderer.GameRenderer',
        hand_flush_body,
        'import dev.nightclient.platform.CustomModelBackend;\nimport dev.nightclient.platform.CustomModelBridge;',
    )

    if pearl:
        fp_sel = (
            'submitArmWithItem('
            'Lnet/minecraft/client/renderer/state/level/PlayerRenderState;'
            'Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;'
            'FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;F'
            'Lcom/mojang/blaze3d/vertex/PoseStack;'
            'Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V'
        )
        fp_body = (
            '  @Inject(method = "' + fp_sel + '", at = @At("HEAD"))\n'
            '  private void nightclient$stashFpItem('
            'net.minecraft.client.renderer.state.level.PlayerRenderState playerState, '
            'net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState handState, '
            'float partialTick, float pitch, net.minecraft.world.InteractionHand hand, float swingProgress, '
            'net.minecraft.world.item.ItemStack stack, float equipProgress, '
            'com.mojang.blaze3d.vertex.PoseStack poseStack, '
            'net.minecraft.client.renderer.SubmitNodeCollector collector, int light, CallbackInfo ci) {\n'
            '    boolean mainRight = playerState != null && playerState.avatarRenderState != null '
            '&& playerState.avatarRenderState.mainArm == net.minecraft.world.entity.HumanoidArm.RIGHT;\n'
            '    boolean rightHand = (hand == net.minecraft.world.InteractionHand.MAIN_HAND) ? mainRight : !mainRight;\n'
            '    CustomModelBridge.stashFirstPersonContext(stack, rightHand);\n'
            '  }\n'
            '  @Inject(method = "' + fp_sel + '", at = @At("RETURN"))\n'
            '  private void nightclient$clearFpItem(CallbackInfo ci) {\n'
            '    CustomModelBridge.clearFirstPersonContext();\n'
            '  }\n'
            '  @Redirect(method = "' + fp_sel + '", at = @At(value = "INVOKE", '
            'target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))\n'
            '  private void nightclient$redirectFpItem('
            'net.minecraft.client.renderer.item.ItemStackRenderState itemState, '
            'com.mojang.blaze3d.vertex.PoseStack poseStack, '
            'net.minecraft.client.renderer.SubmitNodeCollector collector, int light, int overlay, int outlineColor) {\n'
            '    if (!CustomModelBridge.capturePendingFirstPerson(poseStack, light)) {\n'
            '      itemState.submit(poseStack, collector, light, overlay, outlineColor);\n'
            '    }\n'
            '  }'
        )
        fp_src = mixin(
            'CustomModelFirstPersonItemMixin',
            'net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer',
            fp_body,
            'import dev.nightclient.platform.CustomModelBridge;',
        )
    else:
        if v <= (1, 19, 3):
            ctx_type = 'net.minecraft.client.renderer.block.model.ItemTransforms.TransformType'
            ctx_desc = 'Lnet/minecraft/client/renderer/block/model/ItemTransforms$TransformType;'
            has_left_bool = True
            buf_type = 'net.minecraft.client.renderer.MultiBufferSource'
            buf_desc = 'Lnet/minecraft/client/renderer/MultiBufferSource;'
            include_3p = True
        elif v <= (1, 21, 4):
            ctx_type = 'net.minecraft.world.item.ItemDisplayContext'
            ctx_desc = 'Lnet/minecraft/world/item/ItemDisplayContext;'
            has_left_bool = True
            buf_type = 'net.minecraft.client.renderer.MultiBufferSource'
            buf_desc = 'Lnet/minecraft/client/renderer/MultiBufferSource;'
            include_3p = v <= (1, 21, 1)
        elif v <= (1, 21, 8):
            ctx_type = 'net.minecraft.world.item.ItemDisplayContext'
            ctx_desc = 'Lnet/minecraft/world/item/ItemDisplayContext;'
            has_left_bool = False
            buf_type = 'net.minecraft.client.renderer.MultiBufferSource'
            buf_desc = 'Lnet/minecraft/client/renderer/MultiBufferSource;'
            include_3p = False
        else:
            ctx_type = 'net.minecraft.world.item.ItemDisplayContext'
            ctx_desc = 'Lnet/minecraft/world/item/ItemDisplayContext;'
            has_left_bool = False
            buf_type = 'net.minecraft.client.renderer.SubmitNodeCollector'
            buf_desc = 'Lnet/minecraft/client/renderer/SubmitNodeCollector;'
            include_3p = False

        fp_sel = (
            'renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;'
            + ctx_desc
            + ('Z' if has_left_bool else '')
            + 'Lcom/mojang/blaze3d/vertex/PoseStack;'
            + buf_desc
            + 'I)V'
        )
        left_param = 'boolean leftHand, ' if has_left_bool else ''
        right_expr = '!leftHand' if has_left_bool else f'context == {ctx_type}.FIRST_PERSON_RIGHT_HAND'
        tp_block = ''
        if include_3p:
            tp_block = (
                f'    if (context == {ctx_type}.THIRD_PERSON_RIGHT_HAND || context == {ctx_type}.THIRD_PERSON_LEFT_HAND) {{\n'
                '      net.minecraft.world.entity.HumanoidArm arm = leftHand '
                '? net.minecraft.world.entity.HumanoidArm.LEFT : net.minecraft.world.entity.HumanoidArm.RIGHT;\n'
                '      if (CustomModelBridge.captureThirdPersonWeapon(poseStack, entity, arm, stack, packedLight)) ci.cancel();\n'
                '    }\n'
            )
        fp_body = (
            '  @Inject(method = "' + fp_sel + '", at = @At("HEAD"), cancellable = true)\n'
            '  private void nightclient$replaceCustomWeapon('
            'net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.item.ItemStack stack, '
            + ctx_type + ' context, ' + left_param
            + 'com.mojang.blaze3d.vertex.PoseStack poseStack, ' + buf_type + ' buffer, int packedLight, CallbackInfo ci) {\n'
            f'    if (context == {ctx_type}.FIRST_PERSON_RIGHT_HAND || context == {ctx_type}.FIRST_PERSON_LEFT_HAND) {{\n'
            f'      if (CustomModelBridge.isLocalPlayer(entity) && CustomModelBridge.captureFirstPersonWeapon(poseStack, stack, {right_expr}, packedLight)) {{\n'
            '        ci.cancel();\n'
            '        return;\n'
            '      }\n'
            '    }\n'
            + tp_block
            + '  }'
        )
        fp_src = mixin(
            'CustomModelFirstPersonItemMixin',
            'net.minecraft.client.renderer.ItemInHandRenderer',
            fp_body,
            'import dev.nightclient.platform.CustomModelBridge;',
        )

    if v <= (1, 21, 1):
        armor_outer_sel = 'render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V'
        armor_outer_args = (
            'com.mojang.blaze3d.vertex.PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource buffer, '
            'int packedLight, net.minecraft.world.entity.LivingEntity target, '
            'float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch'
        )
        armor_piece_sel = 'renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;)V'
        armor_piece_args = (
            'com.mojang.blaze3d.vertex.PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource buffer, '
            'net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.entity.EquipmentSlot slot, '
            'int packedLight, net.minecraft.client.model.HumanoidModel<?> armorModel'
        )
    elif v <= (1, 21, 8):
        armor_outer_sel = 'render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V'
        armor_outer_args = (
            'com.mojang.blaze3d.vertex.PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource buffer, '
            'int packedLight, net.minecraft.client.renderer.entity.state.HumanoidRenderState target, '
            'float yRot, float xRot'
        )
        armor_piece_sel = 'renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;)V'
        armor_piece_args = (
            'com.mojang.blaze3d.vertex.PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource buffer, '
            'net.minecraft.world.item.ItemStack stack, net.minecraft.world.entity.EquipmentSlot slot, '
            'int packedLight, net.minecraft.client.model.HumanoidModel<?> armorModel'
        )
    else:
        armor_outer_sel = 'submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V'
        armor_outer_args = (
            'com.mojang.blaze3d.vertex.PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector collector, '
            'int packedLight, net.minecraft.client.renderer.entity.state.HumanoidRenderState target, '
            'float yRot, float xRot'
        )
        armor_piece_sel = 'renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V'
        armor_piece_args = (
            'com.mojang.blaze3d.vertex.PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector collector, '
            'net.minecraft.world.item.ItemStack stack, net.minecraft.world.entity.EquipmentSlot slot, '
            'int packedLight, net.minecraft.client.renderer.entity.state.HumanoidRenderState state'
        )

    armor_body = (
        '  @org.spongepowered.asm.mixin.Unique\n'
        '  private int nightclient$activeArmorSlots;\n'
        '  @Inject(method = "' + armor_outer_sel + '", at = @At("HEAD"))\n'
        '  private void nightclient$captureCustomArmor(' + armor_outer_args + ', CallbackInfo ci) {\n'
        '    Object parent = ((net.minecraft.client.renderer.entity.layers.RenderLayer<?, ?>) (Object) this).getParentModel();\n'
        '    nightclient$activeArmorSlots = (parent instanceof net.minecraft.client.model.HumanoidModel<?>)\n'
        '        ? CustomModelBridge.captureArmorSet(poseStack, target, (net.minecraft.client.model.HumanoidModel<?>) parent, packedLight)\n'
        '        : 0;\n'
        '  }\n'
        '  @Inject(method = "' + armor_outer_sel + '", at = @At("RETURN"))\n'
        '  private void nightclient$clearCustomArmor(CallbackInfo ci) {\n'
        '    nightclient$activeArmorSlots = 0;\n'
        '  }\n'
        '  @Inject(method = "' + armor_piece_sel + '", at = @At("HEAD"), cancellable = true)\n'
        '  private void nightclient$hideVanillaArmorPiece(' + armor_piece_args + ', CallbackInfo ci) {\n'
        '    if (CustomModelBridge.shouldHideVanillaSlot(nightclient$activeArmorSlots, slot)) ci.cancel();\n'
        '  }'
    )
    armor_src = mixin(
        'CustomModelArmorMixin',
        'net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer',
        armor_body,
        'import dev.nightclient.platform.CustomModelBridge;',
    )

    outputs_list = [
        ('CustomModelBridge', bridge_src, True),
        ('CustomModelBackend', backend_src, True),
        ('CustomModelHandFlushMixin', hand_flush_src, False),
        ('CustomModelFirstPersonItemMixin', fp_src, False),
        ('CustomModelArmorMixin', armor_src, False),
    ]

    stale_tp = Path(output) / 'dev/nightclient/platform/mixin/CustomModelThirdPersonItemMixin.java'
    if v < (1, 21, 2):
        if stale_tp.exists():
            stale_tp.unlink()
    else:
        if v <= (1, 21, 3):
            tp_sel = 'renderArmWithItem(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V'
            tp_body = (
                '  @Inject(method = "' + tp_sel + '", at = @At("HEAD"))\n'
                '  private void nightclient$stash3p('
                'net.minecraft.client.renderer.entity.state.LivingEntityRenderState state, '
                'net.minecraft.client.resources.model.BakedModel model, '
                'net.minecraft.world.item.ItemStack stack, '
                'net.minecraft.world.item.ItemDisplayContext ctx, '
                'net.minecraft.world.entity.HumanoidArm arm, '
                'com.mojang.blaze3d.vertex.PoseStack poseStack, '
                'net.minecraft.client.renderer.MultiBufferSource buffer, int light, CallbackInfo ci) {\n'
                '    CustomModelBridge.stashThirdPersonContext(state, stack, arm);\n'
                '  }\n'
                '  @Inject(method = "' + tp_sel + '", at = @At("RETURN"))\n'
                '  private void nightclient$clear3p(CallbackInfo ci) {\n'
                '    CustomModelBridge.clearThirdPersonContext();\n'
                '  }\n'
                '  @Redirect(method = "' + tp_sel + '", at = @At(value = "INVOKE", '
                'target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V"))\n'
                '  private void nightclient$redirect3p('
                'net.minecraft.client.renderer.entity.ItemRenderer renderer, '
                'net.minecraft.world.item.ItemStack stack, '
                'net.minecraft.world.item.ItemDisplayContext ctx, boolean leftHand, '
                'com.mojang.blaze3d.vertex.PoseStack poseStack, '
                'net.minecraft.client.renderer.MultiBufferSource buffer, int light, int overlay, '
                'net.minecraft.client.resources.model.BakedModel model) {\n'
                '    if (!CustomModelBridge.capturePendingThirdPerson(poseStack, light)) {\n'
                '      renderer.render(stack, ctx, leftHand, poseStack, buffer, light, overlay, model);\n'
                '    }\n'
                '  }'
            )
        elif v <= (1, 21, 8):
            tp_sel = 'renderArmWithItem(Lnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V'
            tp_body = (
                '  @Inject(method = "' + tp_sel + '", at = @At("HEAD"))\n'
                '  private void nightclient$stash3p('
                'net.minecraft.client.renderer.entity.state.ArmedEntityRenderState state, '
                'net.minecraft.client.renderer.item.ItemStackRenderState itemState, '
                'net.minecraft.world.entity.HumanoidArm arm, '
                'com.mojang.blaze3d.vertex.PoseStack poseStack, '
                'net.minecraft.client.renderer.MultiBufferSource buffer, int light, CallbackInfo ci) {\n'
                '    CustomModelBridge.stashThirdPersonContext(state, null, arm);\n'
                '  }\n'
                '  @Inject(method = "' + tp_sel + '", at = @At("RETURN"))\n'
                '  private void nightclient$clear3p(CallbackInfo ci) {\n'
                '    CustomModelBridge.clearThirdPersonContext();\n'
                '  }\n'
                '  @Redirect(method = "' + tp_sel + '", at = @At(value = "INVOKE", '
                'target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"))\n'
                '  private void nightclient$redirect3p('
                'net.minecraft.client.renderer.item.ItemStackRenderState itemState, '
                'com.mojang.blaze3d.vertex.PoseStack poseStack, '
                'net.minecraft.client.renderer.MultiBufferSource buffer, int light, int overlay) {\n'
                '    if (!CustomModelBridge.capturePendingThirdPerson(poseStack, light)) {\n'
                '      itemState.render(poseStack, buffer, light, overlay);\n'
                '    }\n'
                '  }'
            )
        elif v <= (1, 21, 10):
            tp_sel = 'submitArmWithItem(Lnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V'
            tp_body = (
                '  @Inject(method = "' + tp_sel + '", at = @At("HEAD"))\n'
                '  private void nightclient$stash3p('
                'net.minecraft.client.renderer.entity.state.ArmedEntityRenderState state, '
                'net.minecraft.client.renderer.item.ItemStackRenderState itemState, '
                'net.minecraft.world.entity.HumanoidArm arm, '
                'com.mojang.blaze3d.vertex.PoseStack poseStack, '
                'net.minecraft.client.renderer.SubmitNodeCollector collector, int light, CallbackInfo ci) {\n'
                '    CustomModelBridge.stashThirdPersonContext(state, null, arm);\n'
                '  }\n'
                '  @Inject(method = "' + tp_sel + '", at = @At("RETURN"))\n'
                '  private void nightclient$clear3p(CallbackInfo ci) {\n'
                '    CustomModelBridge.clearThirdPersonContext();\n'
                '  }\n'
                '  @Redirect(method = "' + tp_sel + '", at = @At(value = "INVOKE", '
                'target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))\n'
                '  private void nightclient$redirect3p('
                'net.minecraft.client.renderer.item.ItemStackRenderState itemState, '
                'com.mojang.blaze3d.vertex.PoseStack poseStack, '
                'net.minecraft.client.renderer.SubmitNodeCollector collector, int light, int overlay, int outlineColor) {\n'
                '    if (!CustomModelBridge.capturePendingThirdPerson(poseStack, light)) {\n'
                '      itemState.submit(poseStack, collector, light, overlay, outlineColor);\n'
                '    }\n'
                '  }'
            )
        else:
            tp_sel = 'submitArmWithItem(Lnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V'
            tp_body = (
                '  @Inject(method = "' + tp_sel + '", at = @At("HEAD"))\n'
                '  private void nightclient$stash3p('
                'net.minecraft.client.renderer.entity.state.ArmedEntityRenderState state, '
                'net.minecraft.client.renderer.item.ItemStackRenderState itemState, '
                'net.minecraft.world.item.ItemStack stack, '
                'net.minecraft.world.entity.HumanoidArm arm, '
                'com.mojang.blaze3d.vertex.PoseStack poseStack, '
                'net.minecraft.client.renderer.SubmitNodeCollector collector, int light, CallbackInfo ci) {\n'
                '    CustomModelBridge.stashThirdPersonContext(state, stack, arm);\n'
                '  }\n'
                '  @Inject(method = "' + tp_sel + '", at = @At("RETURN"))\n'
                '  private void nightclient$clear3p(CallbackInfo ci) {\n'
                '    CustomModelBridge.clearThirdPersonContext();\n'
                '  }\n'
                '  @Redirect(method = "' + tp_sel + '", at = @At(value = "INVOKE", '
                'target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))\n'
                '  private void nightclient$redirect3p('
                'net.minecraft.client.renderer.item.ItemStackRenderState itemState, '
                'com.mojang.blaze3d.vertex.PoseStack poseStack, '
                'net.minecraft.client.renderer.SubmitNodeCollector collector, int light, int overlay, int outlineColor) {\n'
                '    if (!CustomModelBridge.capturePendingThirdPerson(poseStack, light)) {\n'
                '      itemState.submit(poseStack, collector, light, overlay, outlineColor);\n'
                '    }\n'
                '  }'
            )
        tp_src = mixin(
            'CustomModelThirdPersonItemMixin',
            'net.minecraft.client.renderer.entity.layers.ItemInHandLayer',
            tp_body,
            'import dev.nightclient.platform.CustomModelBridge;',
        )
        outputs_list.append(('CustomModelThirdPersonItemMixin', tp_src, False))

    for label, src_txt, is_bridge in outputs_list:
        if re.search('@@[A-Z0-9_]+@@', src_txt):
            raise ValueError('Unresolved custom model template token in ' + version + ': ' + label)
        write(output, label, src_txt, bridge=is_bridge)

def main_generate_world_features():
    parser = argparse.ArgumentParser()
    parser.add_argument('--version', required=True)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    generate_world_features(args.version, args.output)

def _generate_icon_assets_cli():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    target = args.output / 'assets/nightclient'
    shutil.rmtree(target / 'textures/ui', ignore_errors=True)
    (target / 'icons').mkdir(parents=True, exist_ok=True)
    icons = []
    sources = sorted((source for module in ('renderer', 'ui') for source in (ROOT / 'modules' / module / 'src/main/resources/assets/nightclient/icons').glob('*.svg')))
    identifiers = set()
    for source in sources:
        if source.stem in identifiers:
            raise ValueError('Duplicate SVG identifier: ' + source.stem)
        identifiers.add(source.stem)
        tree = ET.parse(source).getroot()
        if tree.attrib.get('viewBox') != '0 0 24 24':
            raise ValueError('Icon must use the canonical 24-unit viewbox: ' + source.name)
        shapes = []
        for node in tree:
            tag = node.tag.split('}')[-1]
            filled = node.attrib.get('fill', tree.attrib.get('fill', 'black')) != 'none'
            stroked = node.attrib.get('stroke', tree.attrib.get('stroke', 'none')) != 'none'
            if tag == 'path':
                for shape in points(node.attrib['d']):
                    if filled:
                        shapes.append((3, triangles(shape)))
                    if stroked:
                        shapes.append((0, [coordinate for point in shape for coordinate in point]))
            elif tag == 'circle':
                geometry = [float(node.attrib[name]) for name in ('cx', 'cy', 'r')]
                if filled:
                    shapes.append((4, geometry))
                if stroked:
                    shapes.append((1, geometry))
            elif tag == 'rect':
                geometry = [float(node.attrib[name]) for name in ('x', 'y', 'width', 'height')] + [float(node.attrib.get('rx', '0'))]
                if filled:
                    shapes.append((5, geometry))
                if stroked:
                    shapes.append((2, geometry))
            else:
                raise ValueError('Unsupported SVG primitive: ' + tag)
        if not shapes or any((not math.isfinite(v) for _, values in shapes for v in values)):
            raise ValueError('Empty or invalid icon: ' + source.name)
        icons.append((source.stem, shapes))
    if not icons:
        raise ValueError('SVG icon library is empty')
    binary = bytearray(struct.pack('>III', 1313032515, 2, len(icons)))
    for name, shapes in icons:
        binary.extend(pack_utf(name))
        binary.extend(struct.pack('>I', len(shapes)))
        for kind, values in shapes:
            binary.extend(struct.pack('>BI', kind, len(values)))
            binary.extend(struct.pack('>' + 'f' * len(values), *values))
    (target / 'icons/compiled.bin').write_bytes(binary)
    pixels = bytearray()
    seed = 1953917299
    for y in range(256):
        pixels.append(0)
        for x in range(256):
            seed ^= seed << 13 & 4294967295
            seed ^= seed >> 17
            seed ^= seed << 5 & 4294967295
            seed &= 4294967295
            value = seed >> 24 & 255
            pixels.extend((value, value, value, 255))
    (target / 'textures/ui').mkdir(parents=True, exist_ok=True)
    png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 256, 256, 8, 6, 0, 0, 0))
    png += chunk(b'IDAT', zlib.compress(bytes(pixels), 9)) + chunk(b'IEND', b'')
    (target / 'textures/ui/grain.png').write_bytes(png)
    (target / 'textures/ui/grain.png.mcmeta').write_text('{"texture":{"blur":false,"clamp":false}}\n')
    print('Compiled ' + str(len(icons)) + ' original SVG icons and deterministic glass grain')

def _generate_shader_assets_cli():
    root = ROOT
    sys.path.insert(0, str(root / 'tools'))
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=root / 'modules/renderer/build/generated/resources')
    args = parser.parse_args()
    base = args.output / 'assets/nightclient/shaders'
    function = (root / 'modules/renderer/src/main/resources/assets/nightclient/shaders/include/saturation.glsl').read_text()
    rounded_function = (root / 'modules/renderer/src/main/resources/assets/nightclient/shaders/include/rounded_surface.glsl').read_text()
    (base / 'core').mkdir(parents=True, exist_ok=True)
    for dialect in ('scalar', 'ubo', 'vulkan', 'pearl'):
        version = '330' if dialect in ('vulkan', 'pearl') else '150'
        explicit = 'layout(location = 0) ' if dialect == 'pearl' else ''
        extra = '#extension GL_ARB_separate_shader_objects : enable\n' if dialect == 'pearl' else ''
        vertex_id = 'gl_VertexIndex' if dialect == 'pearl' else 'gl_VertexID'
        vertex = f'#version {version}\n{extra}{explicit}out vec2 texCoord;\nvoid main() {{\n    vec2 uv = vec2(({vertex_id} << 1) & 2, {vertex_id} & 2);\n    texCoord = uv;\n    gl_Position = vec4(uv * 2.0 - 1.0, 0.0, 1.0);\n}}\n'
        uniform = 'uniform float Strength;' if dialect == 'scalar' else 'layout(std140) uniform NightGradeConfig { float Strength; };'
        fragment = f'#version {version}\n{extra}uniform sampler2D InSampler;\n{uniform}\n{explicit}in vec2 texCoord;\n{explicit}out vec4 fragColor;\n{function}\nvoid main() {{\n    vec4 source = texture(InSampler, texCoord);\n    fragColor = vec4(ncSaturation(source.rgb, Strength), source.a);\n}}\n'
        (base / f'core/fullscreen_{dialect}.vsh').write_text(vertex)
        (base / f'core/saturation_{dialect}.fsh').write_text(fragment)
    for dialect in ('ubo', 'vulkan', 'pearl'):
        version = '150' if dialect == 'ubo' else '330'
        extra = '#extension GL_ARB_separate_shader_objects : enable\n' if dialect == 'pearl' else ''
        uv_location = 'layout(location = 0) ' if dialect == 'pearl' else ''
        color_location = 'layout(location = 1) ' if dialect == 'pearl' else ''
        fragment = f'#version {version}\n{extra}{uv_location}in vec2 texCoord0;\n{color_location}in vec4 vertexColor;\n{uv_location}out vec4 fragColor;\n{rounded_function}\nvoid main() {{\n    float coverage = nightRoundedCoverage(texCoord0);\n    if (coverage <= 0.0 || vertexColor.a <= 0.0) discard;\n    fragColor = vec4(vertexColor.rgb, vertexColor.a * coverage);\n}}\n'
        (base / f'core/rounded_surface_{dialect}.fsh').write_text(fragment)
    _generate_atmosphere_shader_assets(root, base)
    _generate_cosmetic_model_shader_assets(root, base)
    print('Generated colour, analytic surface, atmosphere, and 3D cosmetic model shader dialects')

def _generate_cosmetic_model_shader_assets(root, base):
    inc = root / 'modules/renderer/src/main/resources/assets/nightclient/shaders/include'
    vsh_body = (inc / 'cosmetic_model_vsh.glsl').read_text()
    fsh_body = (inc / 'cosmetic_model_fsh.glsl').read_text()
    draw_ubo = (
        'layout(std140) uniform NightModelDraw {\n'
        '    mat4 ModelViewMat;\n'
        '    mat4 NormalMat;\n'
        '    vec4 LightTint;\n'
        '    vec4 AccentColor;\n'
        '    vec4 HurtColor;\n'
        '    vec4 ExtraParams;\n'
        '    mat4 FallbackProjMat;\n'
        '};\n'
    )
    proj_ubo = (
        'layout(std140) uniform Projection {\n'
        '    mat4 ProjMat;\n'
        '};\n'
    )
    for dialect in ('legacy', 'ubo', 'vulkan', 'pearl'):
        ver = '150' if dialect in ('legacy', 'ubo') else '330'
        ext = '#extension GL_ARB_separate_shader_objects : enable\n' if dialect == 'pearl' else ''
        loc = lambda i: f'layout(location = {i}) ' if dialect == 'pearl' else ''
        in_attrs = (
            f'{loc(0)}in vec3 Position;\n'
            f'{loc(1)}in vec4 Color;\n'
            f'{loc(2)}in vec2 UV0;\n'
            f'{loc(3)}in ivec2 UV1;\n'
            f'{loc(4)}in ivec2 UV2;\n'
            f'{loc(5)}in vec3 Normal;\n'
        )
        v_out = (
            f'{loc(0)}out vec3 vViewPos;\n'
            f'{loc(1)}out vec3 vNormal;\n'
            f'{loc(2)}out vec2 vUv;\n'
            f'{loc(3)}out vec4 vColor;\n'
            f'{loc(4)}out vec4 vMat;\n'
            f'{loc(5)}out vec4 vEmissive;\n'
        )
        f_in = (
            f'{loc(0)}in vec3 vViewPos;\n'
            f'{loc(1)}in vec3 vNormal;\n'
            f'{loc(2)}in vec2 vUv;\n'
            f'{loc(3)}in vec4 vColor;\n'
            f'{loc(4)}in vec4 vMat;\n'
            f'{loc(5)}in vec4 vEmissive;\n'
            f'{loc(0)}out vec4 OutColor;\n'
        )
        hdr = f'#version {ver}\n{ext}'
        proj_decl = '' if dialect == 'legacy' else proj_ubo
        proj_macro = '#define TV_PROJ_MAT FallbackProjMat\n' if dialect == 'legacy' else '#define TV_PROJ_MAT ProjMat\n'
        (base / f'core/cosmetic_model_{dialect}.vsh').write_text(
            f'{hdr}{proj_decl}{draw_ubo}{proj_macro}{in_attrs}{v_out}{vsh_body}'
        )
        (base / f'core/cosmetic_model_{dialect}.fsh').write_text(
            f'{hdr}uniform sampler2D Sampler0;\n{draw_ubo}{f_in}{fsh_body}'
        )

def _generate_atmosphere_shader_assets(root, base):
    inc = root / 'modules/renderer/src/main/resources/assets/nightclient/shaders/include/atmosphere'
    col_decode = (inc / 'columns_decode.glsl').read_text()
    view_depth = (inc / 'view_depth.glsl').read_text()
    dawn_body = (inc / 'dawn.glsl').read_text()
    custom_sky_body = (inc / 'custom_sky.glsl').read_text()
    motion_blur_body = (inc / 'motion_blur.glsl').read_text()
    bokeh_ex_body = (inc / 'bokeh_extract.glsl').read_text()
    bokeh_dc_body = (inc / 'bokeh_disc.glsl').read_text()
    bokeh_st_body = (inc / 'bokeh_streak.glsl').read_text()
    bokeh_cp_body = (inc / 'bokeh_composite.glsl').read_text()
    botanical_sprites = (inc / 'botanical_sprites.glsl').read_text()
    mood_body = (inc / 'mood_body.glsl').read_text()
    precip_vsh_body = (inc / 'precip_vsh.glsl').read_text()
    precip_fsh_body = (inc / 'precip_fsh.glsl').read_text()

    dawn_ubo = (
        'layout(std140) uniform NightAtmoDawn {\n'
        '    mat4 InvViewProj;\n'
        '    vec4 DawnScalars[11];\n'
        '};\n'
        '#define CamPos (DawnScalars[0].xyz)\n'
        '#define Time (DawnScalars[0].w)\n'
        '#define Resolution (DawnScalars[1].xy)\n'
        '#define FogDensity (DawnScalars[1].z)\n'
        '#define FogMinHeight (DawnScalars[1].w)\n'
        '#define FogMaxHeight (DawnScalars[2].x)\n'
        '#define ViewDistance (DawnScalars[2].y)\n'
        '#define GodRays (DawnScalars[2].z)\n'
        '#define Softness (DawnScalars[2].w)\n'
        '#define SunDir (DawnScalars[3].xyz)\n'
        '#define ReversedDepth (int(DawnScalars[3].w + 0.5))\n'
        '#define SunScreen (DawnScalars[4].xyz)\n'
        '#define PalZenith (DawnScalars[5].xyz)\n'
        '#define PalHorizonWarm (DawnScalars[6].xyz)\n'
        '#define PalHorizonCool (DawnScalars[7].xyz)\n'
        '#define PalFogWarm (DawnScalars[8].xyz)\n'
        '#define PalFogCool (DawnScalars[9].xyz)\n'
        '#define PalRay (DawnScalars[10].xyz)\n'
    )
    custom_sky_ubo = (
        'layout(std140) uniform NightCustomSky {\n'
        '    mat4 InvViewProj;\n'
        '    vec4 SkyScalars[8];\n'
        '};\n'
        '#define CamPos (SkyScalars[0].xyz)\n'
        '#define Time (SkyScalars[0].w)\n'
        '#define Brightness (SkyScalars[1].x)\n'
        '#define RotationSpeed (SkyScalars[1].y)\n'
        '#define Stars (SkyScalars[1].z)\n'
        '#define Clouds (SkyScalars[1].w)\n'
        '#define CelestialDir (SkyScalars[2].xyz)\n'
        '#define ReversedDepth (int(SkyScalars[2].w + 0.5))\n'
        '#define TopColor (SkyScalars[3].xyz)\n'
        '#define StyleKind (int(SkyScalars[3].w + 0.5))\n'
        '#define HorizonColor (SkyScalars[4].xyz)\n'
        '#define AtlasTile (int(SkyScalars[4].w + 0.5))\n'
        '#define AccentColor (SkyScalars[5].xyz)\n'
        '#define HorizonGlow (SkyScalars[5].w)\n'
        '#define CelestialTint (SkyScalars[6].xyz)\n'
        '#define CelestialGlow (SkyScalars[6].w)\n'
        '#define CelestialSize (SkyScalars[7].x)\n'
        '#define CelestialEnabled (SkyScalars[7].y)\n'
        '#define SubmergedFactor (SkyScalars[7].z)\n'
    )
    motion_blur_ubo = (
        'layout(std140) uniform NightMotionBlur {\n'
        '    mat4 ReprojMat;\n'
        '    vec4 BlurParams;\n'
        '    vec4 BlurExtra;\n'
        '};\n'
        '#define BlurScale (BlurParams.x)\n'
        '#define SampleCount (BlurParams.y)\n'
        '#define ReversedDepth (int(BlurParams.z + 0.5))\n'
        '#define BlurSky (BlurParams.w)\n'
        '#define FramePhase (BlurExtra.x)\n'
        '#define MaxSpan (BlurExtra.y)\n'
        'const mat4 InvViewProj = mat4(1.0);\n'
        'const vec3 CamPos = vec3(0.0);\n'
    )
    bokeh_ex_ubo = (
        'layout(std140) uniform NightAtmoBokehExtract {\n'
        '    mat4 InvViewProj;\n'
        '    vec4 BokEx0;\n'
        '    vec4 BokEx1;\n'
        '    vec4 BokEx2;\n'
        '};\n'
        '#define CamPos (BokEx0.xyz)\n'
        '#define ReversedDepth (int(BokEx0.w + 0.5))\n'
        '#define OutSize (BokEx1.xy)\n'
        '#define SourceTexel (BokEx1.zw)\n'
        '#define Threshold (BokEx2.x)\n'
        '#define Focus (BokEx2.y)\n'
    )
    bokeh_dc_ubo = (
        'layout(std140) uniform NightAtmoBokehDisc {\n'
        '    vec4 BokDisc0;\n'
        '};\n'
        '#define OutSize (BokDisc0.xy)\n'
        '#define MaxRadius (BokDisc0.z)\n'
        '#define Gain (BokDisc0.w)\n'
    )
    bokeh_st_ubo = (
        'layout(std140) uniform NightAtmoBokehStreak {\n'
        '    vec4 BokStr0;\n'
        '    vec4 BokStr1;\n'
        '};\n'
        '#define OutSize (BokStr0.xy)\n'
        '#define Step (BokStr0.z)\n'
        '#define Gain (BokStr0.w)\n'
        '#define Tint (BokStr1.xyz)\n'
        '#define Mix (BokStr1.w)\n'
    )
    bokeh_cp_ubo = (
        'layout(std140) uniform NightAtmoBokehComp {\n'
        '    vec4 BokComp0;\n'
        '};\n'
    )
    mood_ubo = (
        'layout(std140) uniform NightAtmoMood {\n'
        '    mat4 InvViewProj;\n'
        '    vec4 MoodScalars[19];\n'
        '    vec4 MoodDetail[8];\n'
        '    vec4 Lamps[32];\n'
        '};\n'
        '#define CamPos (MoodScalars[0].xyz)\n'
        '#define Time (MoodScalars[0].w)\n'
        '#define Resolution (MoodScalars[1].xy)\n'
        '#define PixelWorld (MoodScalars[1].z)\n'
        '#define ViewDistance (MoodScalars[1].w)\n'
        '#define SunDir (MoodScalars[2].xyz)\n'
        '#define GroundY (MoodScalars[2].w)\n'
        '#define SunScreen (MoodScalars[3].xyz)\n'
        '#define Amount (MoodScalars[3].w)\n'
        '#define Speed (MoodScalars[4].x)\n'
        '#define Haze (MoodScalars[4].y)\n'
        '#define Grade (MoodScalars[4].z)\n'
        '#define Lights (MoodScalars[4].w)\n'
        '#define Vignette (MoodScalars[5].x)\n'
        '#define Rays (MoodScalars[5].y)\n'
        '#define Shimmer (MoodScalars[5].z)\n'
        '#define SkyMix (MoodScalars[5].w)\n'
        '#define Chroma (MoodScalars[6].x)\n'
        '#define Relight (MoodScalars[6].y)\n'
        '#define Occlusion (MoodScalars[6].z)\n'
        '#define SnowCover (MoodScalars[6].w)\n'
        '#define Ground (MoodScalars[7].x)\n'
        '#define LampCount (int(MoodScalars[7].y + 0.5))\n'
        '#define LampReach (MoodScalars[7].z)\n'
        '#define HasMap (int(MoodScalars[7].w + 0.5))\n'
        '#define MapOrigin (MoodScalars[8].xyz)\n'
        '#define ReversedDepth (int(MoodScalars[8].w + 0.5))\n'
        '#define SkyTop (MoodScalars[9].xyz)\n'
        '#define BokehFocus (MoodScalars[9].w)\n'
        '#define SkyHorizon (MoodScalars[10].xyz)\n'
        '#define SkySun (MoodScalars[11].xyz)\n'
        '#define HazeColor (MoodScalars[12].xyz)\n'
        '#define ParticleColor (MoodScalars[13].xyz)\n'
        '#define ShadowTint (MoodScalars[14].xyz)\n'
        '#define LightTint (MoodScalars[15].xyz)\n'
        '#define LightDir (MoodScalars[16].xyz)\n'
        '#define LightGain (MoodScalars[17].xyz)\n'
        '#define GradeParams (MoodScalars[18])\n'
    )
    precip_ubo = (
        'layout(std140) uniform NightAtmoPrecip {\n'
        '    mat4 ViewProj;\n'
        '    vec4 PrecipScalars[5];\n'
        '    vec4 FallDetail[8];\n'
        '};\n'
        '#define GridOrigin (PrecipScalars[0].xyz)\n'
        '#define BoxSize (PrecipScalars[0].w)\n'
        '#define BoxOrigin (PrecipScalars[1].xyz)\n'
        '#define Time (PrecipScalars[1].w)\n'
        '#define Speed (PrecipScalars[2].x)\n'
        '#define PixelWorld (PrecipScalars[2].y)\n'
        '#define Ground (PrecipScalars[2].z)\n'
        '#define Kind (int(PrecipScalars[2].w + 0.5))\n'
        '#define Resolution (PrecipScalars[3].xy)\n'
        '#define Vignette (PrecipScalars[3].z)\n'
        '#define ReversedDepth (int(PrecipScalars[3].w + 0.5))\n'
        '#define Tint (PrecipScalars[4].xyz)\n'
    )
    mood_variants = [
        ('dust', 0),
        ('snow', 1),
        ('sakura', 3),
        ('autumn', 4),
        ('fireflies', 5),
        ('mystic', 6),
    ]

    for dialect in ('ubo', 'vulkan', 'pearl'):
        ver = '150' if dialect == 'ubo' else '330'
        ext = '#extension GL_ARB_separate_shader_objects : enable\n' if dialect == 'pearl' else ''
        loc = lambda i: f'layout(location = {i}) ' if dialect == 'pearl' else ''
        vid = '#define TV_VERTEX_ID gl_VertexIndex\n' if dialect == 'pearl' else '#define TV_VERTEX_ID gl_VertexID\n'
        hdr = f'#version {ver}\n{ext}'

        fs_io = f'{loc(0)}in vec2 texCoord;\n{loc(0)}out vec4 OutColor;\n'

        (base / f'core/atmosphere_dawn_{dialect}.fsh').write_text(
            f'{hdr}uniform sampler2D Sampler0;\nuniform sampler2D Sampler1;\n{dawn_ubo}{fs_io}{view_depth}{dawn_body}'
        )
        (base / f'core/atmosphere_custom_sky_{dialect}.fsh').write_text(
            f'{hdr}uniform sampler2D Sampler0;\nuniform sampler2D Sampler1;\nuniform sampler2D Sampler2;\n{custom_sky_ubo}{fs_io}{view_depth}{custom_sky_body}'
        )
        (base / f'core/atmosphere_motion_blur_{dialect}.fsh').write_text(
            f'{hdr}uniform sampler2D Sampler0;\nuniform sampler2D Sampler1;\n{motion_blur_ubo}{fs_io}{view_depth}{motion_blur_body}'
        )
        (base / f'core/atmosphere_bokeh_extract_{dialect}.fsh').write_text(
            f'{hdr}uniform sampler2D Sampler0;\nuniform sampler2D Sampler1;\n{bokeh_ex_ubo}{fs_io}{view_depth}{bokeh_ex_body}'
        )
        (base / f'core/atmosphere_bokeh_disc_{dialect}.fsh').write_text(
            f'{hdr}uniform sampler2D Sampler0;\n{bokeh_dc_ubo}{fs_io}{bokeh_dc_body}'
        )
        (base / f'core/atmosphere_bokeh_streak_{dialect}.fsh').write_text(
            f'{hdr}uniform sampler2D Sampler0;\nuniform sampler2D Sampler1;\n{bokeh_st_ubo}{fs_io}{bokeh_st_body}'
        )
        (base / f'core/atmosphere_bokeh_composite_{dialect}.fsh').write_text(
            f'{hdr}uniform sampler2D Sampler0;\nuniform sampler2D Sampler1;\n{bokeh_cp_ubo}{fs_io}{bokeh_cp_body}'
        )

        for mname, mkind in mood_variants:
            (base / f'core/atmosphere_mood_{mname}_{dialect}.fsh').write_text(
                f'{hdr}uniform sampler2D Sampler0;\nuniform sampler2D Sampler1;\nuniform sampler2D Sampler2;\nuniform sampler2D Sampler3;\nconst int Kind = {mkind};\n{mood_ubo}{fs_io}{view_depth}{col_decode}{botanical_sprites}{mood_body}'
            )

        precip_out = (
            f'{loc(0)}out vec2 precipUv;\n'
            f'{loc(1)}flat out vec4 precipLook;\n'
            f'{loc(2)}flat out vec4 hallowLook;\n'
            f'{loc(3)}flat out vec3 hallowLight;\n'
            f'{loc(4)}flat out vec4 precipAutumn;\n'
            f'{loc(5)}out vec4 precipClip;\n'
        )
        precip_in = (
            f'{loc(0)}in vec2 precipUv;\n'
            f'{loc(1)}flat in vec4 precipLook;\n'
            f'{loc(2)}flat in vec4 hallowLook;\n'
            f'{loc(3)}flat in vec3 hallowLight;\n'
            f'{loc(4)}flat in vec4 precipAutumn;\n'
            f'{loc(5)}in vec4 precipClip;\n'
            f'{loc(0)}out vec4 OutColor;\n'
        )
        (base / f'core/atmosphere_precip_{dialect}.vsh').write_text(
            f'{hdr}{vid}uniform sampler2D Columns;\n{precip_ubo}{precip_out}{col_decode}{precip_vsh_body}'
        )
        (base / f'core/atmosphere_precip_{dialect}.fsh').write_text(
            f'{hdr}uniform sampler2D Scene;\n{precip_ubo}{precip_in}{botanical_sprites}{precip_fsh_body}'
        )

backdrop_source = source

COMMANDS = {
    "build-universal": main_build_universal,
    "build-matrix": main_build_matrix,
    "assemble-universal": main_assemble_universal,
    "audit-compatibility": main_audit_compatibility,
    "generate-backdrop-assets": main_generate_backdrop_assets,
    "generate-camera-features": main_generate_camera_features,
    "generate-features": main_generate_features,
    "generate-platform": main_generate_platform,
    "generate-renderers": main_generate_renderers,
    "generate-resources": main_generate_resources,
    "generate-world-features": main_generate_world_features,
    "generate-icon-assets": _generate_icon_assets_cli,
    "generate-shader-assets": _generate_shader_assets_cli,
}

if __name__ == "__main__":
    if len(sys.argv) == 1:
        command = main_build_universal
    else:
        name = sys.argv.pop(1)
        if name not in COMMANDS:
            raise SystemExit("Usage: python3 tools/build.py <" + ", ".join(COMMANDS) + "> [options]")
        command = COMMANDS[name]
    sys.argv[0] = str(Path(__file__).resolve())
    command()

