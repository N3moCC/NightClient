#!/usr/bin/env python3
"""Audit packaged metadata, real JVM references and Mixin targets; never imply an in-game run."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import struct
import subprocess
import sys
from types import SimpleNamespace
import zipfile

ROOT = Path(__file__).resolve().parents[2]
COMMON = ['api', 'core', 'renderer', 'ui', 'runtime']
GAME_PREFIXES = ('net/minecraft/', 'com/mojang/blaze3d/', 'com/mojang/renderpearl/', 'org/lwjgl/')

def argument_types(descriptor):
    """Read JVM argument descriptors, including primitive/reference array types."""
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
        if reader.u4() != 0xCAFEBABE:
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
        return tag, self.class_name(owner), self.utf(name), self.utf(descriptor)

    def attributes_from(self, reader):
        output = {}
        for _ in range(reader.u2()):
            name = self.utf(reader.u2())
            output[name] = reader.take(reader.u4())
        return output

    def members(self, reader):
        output = []
        for _ in range(reader.u2()):
            access, name, descriptor = reader.u2(), self.utf(reader.u2()), self.utf(reader.u2())
            output.append({'access': access, 'name': name, 'descriptor': descriptor,
                           'attributes': self.attributes_from(reader)})
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
                output.extend(self.annotation(reader) for _ in range(reader.u2()))
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
            if 0xB2 <= op <= 0xB9:
                ref = self.member_ref(struct.unpack('>H', code[pos + 1:pos + 3])[0])
                if ref:
                    output.append({'opcode': op, 'offset': pos, 'reference': ref})
            size = 1
            if op in (0x10, 0x12, 0xBC, 0xA9) or 0x15 <= op <= 0x19 or 0x36 <= op <= 0x3A:
                size = 2
            elif op in (0x11, 0x13, 0x14, 0x84, 0xBB, 0xBD, 0xC0, 0xC1, 0xC6, 0xC7) or 0x99 <= op <= 0xA8 or 0xB2 <= op <= 0xB8:
                size = 3
            elif op == 0xC5:
                size = 4
            elif op in (0xB9, 0xBA, 0xC8, 0xC9):
                size = 5
            elif op == 0xC4:
                size = 6 if code[pos + 1] == 0x84 else 4
            elif op in (0xAA, 0xAB):
                start = pos + 1
                while start % 4:
                    start += 1
                if op == 0xAA:
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
        result = subprocess.run([self.javap, '-p', '-s', name.replace('/', '.')],
                                capture_output=True, text=True)
        if result.returncode:
            return None
        fields, methods = [], []
        parent = None if name == 'java/lang/Object' else 'java/lang/Object'
        interfaces = []
        previous = ''
        for line in result.stdout.splitlines():
            stripped = line.strip()
            if stripped.endswith('{') and (' class ' in stripped or ' interface ' in stripped):
                header = stripped
                while re.search(r'<[^<>]*>', header):
                    header = re.sub(r'<[^<>]*>', '', header)
                match = re.search(r'extends\s+([\w.$]+)', header)
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
        return SimpleNamespace(name=name, parent=parent, interfaces=interfaces,
                               fields=fields, methods=methods)

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
                    return cls, member
            if cls.parent:
                queue.append(cls.parent)
            queue.extend(cls.interfaces)
        return None

def architecture_audit():
    errors = []
    pattern = re.compile(r'\b(?:net\.minecraft|net\.fabricmc|org\.lwjgl|com\.mojang\.(?:blaze3d|renderpearl))\b')
    for module in COMMON:
        for source in (ROOT / 'modules' / module / 'src').rglob('*.java'):
            text = re.sub(r'/\*.*?\*/|//[^\n]*', '', source.read_text(), flags=re.S)
            if pattern.search(text):
                errors.append(str(source.relative_to(ROOT)) + ': common code references a platform API')
    return errors

def mixin_audit(cls, resolver, errors):
    mixin = next((a for a in cls.annotations(cls.attributes)
                  if a['annotation'] == 'Lorg/spongepowered/asm/mixin/Mixin;'), None)
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
        if getattr(cls, 'access', 0) & 0x0200 and not getattr(target_class, 'access', 0) & 0x0200:
            for method in cls.methods:
                if method['access'] & 0x1000:
                    continue
                annotations = cls.annotations(method['attributes'])
                if not any(a['annotation'].endswith(('/Accessor;', '/Invoker;')) for a in annotations):
                    errors.append(f'{cls.name}: interface mixin is not a pure accessor for class target '
                                  f'{target}; unannotated method {method["name"]}{method["descriptor"]}')
        for field in cls.fields:
            for annotation in cls.annotations(field['attributes']):
                if not annotation['annotation'].endswith('/Shadow;'):
                    continue
                prefix = annotation.get('prefix', 'shadow$')
                name = field['name'][len(prefix):] if field['name'].startswith(prefix) else field['name']
                candidates = [name] + annotation.get('aliases', [])
                found = next((resolver.find_member(target, candidate, field['descriptor'], field=True)
                              for candidate in candidates
                              if resolver.find_member(target, candidate, field['descriptor'], field=True)), None)
                if not found:
                    errors.append(f'{cls.name}: shadow field/type mismatch {target}.{name}{field["descriptor"]}')
                elif (field['access'] & 0x0008) != (found[1]['access'] & 0x0008):
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
                        errors.append(f'{cls.name}: shadow method/type mismatch {target}.{name}{method["descriptor"]}')
                    elif (method['access'] & 0x0008) != (found[1]['access'] & 0x0008):
                        errors.append(f'{cls.name}: shadow method static modifier mismatch {target}.{name}')
                    else:
                        count += 1
                elif kind.endswith('/Accessor;'):
                    field_name = annotation.get('value')
                    if not field_name:
                        errors.append(f'{cls.name}: accessor must name its exact target field')
                        continue
                    found = resolver.find_member(target, field_name, None, field=True)
                    arguments=argument_types(method['descriptor'])
                    returned=method['descriptor'].split(')',1)[1]
                    descriptor=returned if not arguments and returned!='V' else arguments[0] if len(arguments)==1 and returned=='V' else None
                    if not found or found[1]['descriptor'] != descriptor:
                        errors.append(f'{cls.name}: accessor field/type mismatch {target}.{field_name}')
                    elif (method['access'] & 0x0008)!=(found[1]['access'] & 0x0008):
                        errors.append(f'{cls.name}: accessor static modifier mismatch {target}.{field_name}')
                    else:
                        count += 1
                elif kind.endswith('/Invoker;'):
                    name=annotation.get('value')
                    if not name:
                        errors.append(f'{cls.name}: invoker must name its exact target method')
                        continue
                    found=resolver.find_member(target,name,method['descriptor'])
                    if not found:
                        errors.append(f'{cls.name}: invoker method/type mismatch {target}.{name}{method["descriptor"]}')
                    elif (method['access'] & 0x0008)!=(found[1]['access'] & 0x0008):
                        errors.append(f'{cls.name}: invoker static modifier mismatch {target}.{name}')
                    else:
                        count+=1
                elif kind.endswith(('/Inject;', '/ModifyVariable;', '/Redirect;', '/ModifyArg;', '/ModifyArgs;')):
                    selectors = annotation.get('method', [])
                    if isinstance(selectors, str):
                        selectors = [selectors]
                    methods = []
                    for selector in selectors:
                        name = selector.split('(', 1)[0]
                        descriptor = selector[len(name):] or None
                        selected = [m for m in target_class.methods if m['name'] == name
                                    and (descriptor is None or m['descriptor'] == descriptor)]
                        if not selected:
                            errors.append(f'{cls.name}: injection method missing {target}.{selector}')
                        methods.extend(selected)
                    ats = annotation.get('at', [])
                    if isinstance(ats, dict):
                        ats = [ats]
                    for at in ats:
                        invoke = at.get('target', '')
                        if at.get('value') == 'INVOKE' and invoke:
                            match = re.fullmatch(r'L([^;]+);([^()]+)(\(.*)', invoke)
                            if not match:
                                errors.append(f'{cls.name}: unsupported invoke selector {invoke}')
                                continue
                            owner, name, descriptor = match.groups()
                            hits = sum(1 for method_target in methods for ref in target_class.code_refs(method_target)
                                       if ref[1:] == (owner, name, descriptor))
                            if hits == 0:
                                errors.append(f'{cls.name}: invoke absent inside injection method: {invoke}')
                            else:
                                count += 1
                            if kind.endswith('/ModifyArg;'):
                                args=argument_types(method['descriptor']);returned=method['descriptor'].split(')',1)[1]
                                invoked=argument_types(descriptor);index=annotation.get('index',-1)
                                if len(args)!=1 or args[0]!=returned or (index>=0 and (index>=len(invoked) or invoked[index]!=returned)) or (index<0 and invoked.count(returned)!=1):
                                    errors.append(f'{cls.name}: ModifyArg handler type/index mismatch: {invoke}')
                            elif kind.endswith('/ModifyArgs;'):
                                args=argument_types(method['descriptor']);returned=method['descriptor'].split(')',1)[1]
                                if not args or args[0]!='Lorg/spongepowered/asm/mixin/injection/invoke/arg/Args;' or returned!='V':
                                    errors.append(f'{cls.name}: ModifyArgs handler descriptor mismatch: {invoke}')
                            if kind.endswith('/Redirect;'):
                                handler_args=argument_types(method['descriptor'])
                                handler_return=method['descriptor'].split(')',1)[1]
                                for target_method in methods:
                                    for instruction in target_class.member_instructions(target_method):
                                        if instruction['reference'][1:]!=(owner,name,descriptor):continue
                                        expected=argument_types(descriptor)
                                        if instruction['opcode']!=0xB8:expected=[f'L{owner};']+expected
                                        result_type=descriptor.split(')',1)[1]
                                        if handler_args[:len(expected)]!=expected or handler_return!=result_type:
                                            errors.append(f'{cls.name}: invoke redirect handler descriptor mismatch: {invoke}')
                        elif at.get('value') == 'FIELD' and invoke:
                            match = re.fullmatch(r'L([^;]+);([^:]+):(.+)', invoke)
                            if not match:
                                errors.append(f'{cls.name}: unsupported field selector {invoke}')
                                continue
                            owner, name, descriptor = match.groups()
                            opcode = at.get('opcode', -1)
                            if not resolver.find_member(owner, name, descriptor, field=True):
                                errors.append(f'{cls.name}: unavailable injection field: {invoke}')
                            for target_method in methods:
                                instructions = target_class.member_instructions(target_method)
                                hits = [instruction for instruction in instructions
                                        if instruction['reference'][1:] == (owner, name, descriptor)
                                        and (opcode == -1 or instruction['opcode'] == opcode)]
                                ordinal = at.get('ordinal', -1)
                                if not hits or (ordinal >= 0 and ordinal >= len(hits)):
                                    errors.append(f'{cls.name}: field access/opcode/ordinal absent in target: {invoke}')
                                else:
                                    count += 1
                                if kind.endswith('/Redirect;') and hits:
                                    handler_args=argument_types(method['descriptor'])
                                    handler_return=method['descriptor'].split(')',1)[1]
                                    for instruction in hits:
                                        operation=instruction['opcode']
                                        expected_args=([f'L{owner};'] if operation in (0xB4,0xB5) else [])
                                        if operation in (0xB3,0xB5):expected_args.append(descriptor)
                                        expected_return=descriptor if operation in (0xB2,0xB4) else 'V'
                                        if (handler_return!=expected_return or handler_args[:len(expected_args)]!=expected_args):
                                            errors.append(f'{cls.name}: field redirect handler descriptor mismatch: {invoke}')
                                    required=annotation.get('require',-1)
                                    if required>0 and len(hits)<required:
                                        errors.append(f'{cls.name}: field redirect has fewer sites than require={required}: {invoke}')
                                if cls.name.endswith('/SaturationMixin') and name == 'hideGui':
                                    if len(hits) != 1 or opcode != 0xB4:
                                        errors.append(f'{cls.name}: Saturation requires one exact GETFIELD hideGui before HUD branching')
                                        continue
                                    point = hits[0]['offset']
                                    before = [instruction for instruction in instructions
                                              if instruction['reference'][2] in ('renderLevel', 'renderConfusionOverlay')
                                              or instruction['reference'][1:3] == ('net/minecraft/client/renderer/PostChain', 'process')]
                                    after = [instruction for instruction in instructions
                                             if instruction['reference'][2] == 'renderItemActivationAnimation'
                                             or instruction['reference'][1:3] == ('net/minecraft/client/gui/Gui', 'render')]
                                    if not before or not after or any(i['offset'] >= point for i in before) or any(i['offset'] <= point for i in after):
                                        errors.append(f'{cls.name}: colour pass must follow world/post effects and precede item animation/HUD')
                    if methods:
                        count += len(methods)
                    if kind.endswith('/Inject;') and 'locals' not in annotation:
                        handler_args = argument_types(method['descriptor'])
                        callbacks = [i for i, argument in enumerate(handler_args)
                                     if argument in ('Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;',
                                                     'Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;')]
                        if len(callbacks) != 1 or callbacks[0] != len(handler_args) - 1:
                            errors.append(f'{cls.name}: injection handler must end with its callback parameter')
                        else:
                            captured = handler_args[:-1]
                            for target_method in methods:
                                if captured and captured != argument_types(target_method['descriptor']):
                                    errors.append(f'{cls.name}: captured injection arguments do not match '
                                                  f'{target}.{target_method["name"]}{target_method["descriptor"]}')
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
                                errors.append(f'{cls.name}: ModifyVariable argument type/ordinal absent: '
                                              f'{target}.{target_method["name"]}{target_method["descriptor"]}')
    return count

def version_audit(version):
    info_file = ROOT / 'build/compatibility' / version / 'classpath.json'
    if not info_file.is_file():
        raise ValueError('Build the version with :mcVERSION:writeAuditClasspath first.')
    info = json.loads(info_file.read_text())
    errors = architecture_audit()
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
                    errors.append(f'{name}: bytecode exceeds target Java {info["java"]}')
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
                if not any(annotation['annotation'] == 'Lorg/spongepowered/asm/mixin/Mixin;'
                           for annotation in packaged_class.annotations(packaged_class.attributes)):
                    errors.append('Mixin configuration references a non-mixin class ' + entry)
        for name in jar.namelist():
            if name.startswith(mixin_package.replace('.', '/') + '/') and name.endswith('.class'):
                qualified = name[:-6]
                if qualified not in declared_mixins and '$' not in qualified:
                    errors.append('Ordinary class is inside the reserved Mixin package: ' + name)
        refmap = mixin_config.get('refmap')
        if refmap and refmap not in jar.namelist():
            errors.append('Mixin config references a missing refmap')
    result = {'minecraft': version, 'compiledArtifact': str(artifact),
              'artifactSha256': hashlib.sha256(artifact.read_bytes()).hexdigest(),
              'namedApiReferencesChecked': checked_refs, 'mixinChecks': checked_hooks,
              'errors': errors, 'passed': not errors, 'runtime': 'not_run',
              'scope': 'Static JVM API, injection/accessor targets, invoke sites, packaging and Java bytecode.'}
    (info_file.parent / 'audit.json').write_text(json.dumps(result, indent=2) + '\n')
    print(f'{version}: {checked_refs} API references; {checked_hooks} Mixin checks; '
          + ('PASS (runtime not run)' if not errors else 'FAIL'))
    for error in errors:
        print(error, file=sys.stderr)
    return errors

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--architecture-only', action='store_true')
    parser.add_argument('--version')
    args = parser.parse_args()
    if args.architecture_only:
        errors = architecture_audit()
        for error in errors:
            print(error, file=sys.stderr)
        print('Common-module architecture: ' + ('FAIL' if errors else 'PASS'))
    elif args.version:
        errors = version_audit(args.version)
    else:
        parser.error('Choose --architecture-only or --version VER.')
    raise SystemExit(1 if errors else 0)

if __name__ == '__main__':
    main()
