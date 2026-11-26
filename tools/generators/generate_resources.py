#!/usr/bin/env python3
"""Generate typed, Loader-only resource mounting for verified Minecraft releases.

PackResources changed independently of screen/render APIs. Only those small
interface differences vary; the allowlisted classloader-backed asset pack and
reload lifecycle are shared. No filesystem pack, reflection, or Fabric API.
"""
import argparse
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
def resource_assets():
    """New feature assets join the pack without editing a version adapter."""
    roots = [ROOT / 'modules' / module / 'src/main/resources/assets/nightclient'
             for module in ('api', 'core', 'renderer', 'ui', 'runtime', 'platform')]
    roots.append(ROOT / 'modules/renderer/build/generated/resources/assets/nightclient')
    assets = set()
    for root in roots:
        if root.exists():
            for path in root.rglob('*'):
                if path.is_file():
                    name = path.relative_to(root).as_posix()
                    if re.fullmatch(r'[a-z0-9/._-]+', name):
                        assets.add(name)
    return sorted(assets)

def generate(version, output):
    profiles = json.loads((ROOT / 'modules/platform/compatibility/bridges.json').read_text())
    if version not in profiles:
        raise ValueError('Minecraft release is outside the verified support matrix')
    v = tuple(int(part) for part in version.split('.'))
    has_location = v >= (1, 20, 5)
    metadata = 'MetadataSectionType' if v >= (1, 21, 4) else 'MetadataSectionSerializer'
    identifier = profiles[version]['identifierType']
    make_id = identifier + '.parse("nightclient:"+path)' if v >= (1, 21) else 'new '+identifier+'("nightclient",path)'
    imports = [
        'java.io.FileNotFoundException', 'java.io.IOException', 'java.io.InputStream',
        'java.util.Arrays', 'java.util.Collections', 'java.util.LinkedHashSet', 'java.util.Set',
        'net.minecraft.resources.'+identifier, 'net.minecraft.server.packs.PackResources',
        'net.minecraft.server.packs.PackType', 'net.minecraft.server.packs.metadata.'+metadata,
    ]
    if has_location:
        imports += ['java.util.Optional', 'net.minecraft.network.chat.Component',
                    'net.minecraft.server.packs.PackLocationInfo', 'net.minecraft.server.packs.repository.PackSource']
    imports += ['net.minecraft.server.packs.resources.IoSupplier']
    methods = '''    @Override public IoSupplier<InputStream> getRootResource(String... names) {return null;}
    @Override public IoSupplier<InputStream> getResource(PackType type,@@ID@@ id) {
        return contains(type,id)?() -> open(id.getPath()):null;
    }
    @Override public void listResources(PackType type,String namespace,String folder,ResourceOutput output) {
        if(type!=PackType.CLIENT_RESOURCES || !"nightclient".equals(namespace))return;
        String prefix=prefix(folder);
        for(String path:FILES)if(path.startsWith(prefix))output.accept(id(path),() -> open(path));
    }
'''
    if has_location:
        methods += '''    private static final PackLocationInfo LOCATION=new PackLocationInfo("nightclient",Component.literal(GameBridge.title()),PackSource.BUILT_IN,Optional.empty());
    @Override public PackLocationInfo location() {return LOCATION;}
'''
    else:
        methods += '''    @Override public String packId() {return "nightclient";}
    @Override public boolean isBuiltin() {return true;}
'''
    methods = methods.replace('@@ID@@',identifier)
    source = '''package dev.nightclient.platform;

@@IMPORTS@@

/** Built-in, client-only assets supplied directly by this mod's classloader. */
public final class NightPackResources implements PackResources {
    private static final Set<String> FILES=Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(
        @@ASSETS@@)));
    private static final Set<String> NAMESPACES=Collections.singleton("nightclient");
    private static String prefix(String folder) {return folder.isEmpty() || folder.endsWith("/")?folder:folder+"/";}
    private static @@ID@@ id(String path) {return @@MAKE_ID@@;}
    private static boolean contains(PackType type,@@ID@@ id) {
        return type==PackType.CLIENT_RESOURCES && "nightclient".equals(id.getNamespace()) && FILES.contains(id.getPath());
    }
    private static InputStream open(String path) throws IOException {
        InputStream stream=NightPackResources.class.getResourceAsStream("/assets/nightclient/"+path);
        if(stream==null)throw new FileNotFoundException("Missing packaged NightClient asset: "+path);
        return stream;
    }
@@METHODS@@
    @Override public Set<String> getNamespaces(PackType type) {return type==PackType.CLIENT_RESOURCES?NAMESPACES:Collections.emptySet();}
    /** No pack-level metadata; texture metadata is served as its .mcmeta asset. */
    @Override public <T> T getMetadataSection(@@METADATA@@<T> section) {return null;}
    /** Resource callers own their streams; this pack holds no file/zip handles. */
    @Override public void close() {}
}
'''
    replacements = {'IMPORTS':'\n'.join('import '+item+';' for item in imports),
                    'ASSETS':',\n        '.join(json.dumps(path) for path in resource_assets()),
                    'ID':identifier,'MAKE_ID':make_id,'METHODS':methods,'METADATA':metadata}
    for key,value in replacements.items():
        source=source.replace('@@'+key+'@@',value)
    manager='ReloadableResourceManager'
    method='createReload'
    mixin = '''package dev.nightclient.platform.mixin;

import dev.nightclient.platform.NightPackResources;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.@@MANAGER@@;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Loader-only mounting on every client reload, including startup and F3+T. */
@Mixin(@@MANAGER@@.class)
public abstract class ResourcePacksMixin {
    @Shadow @Final private PackType type;
    @ModifyVariable(method="@@METHOD@@",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private List<PackResources> nightclientResources(List<PackResources> packs) {
        if(type!=PackType.CLIENT_RESOURCES)return packs;
        for(PackResources pack:packs)if(pack instanceof NightPackResources)return packs;
        List<PackResources> mounted=new ArrayList<PackResources>(packs);
        mounted.add(new NightPackResources());return mounted;
    }
}
'''.replace('@@MANAGER@@',manager).replace('@@METHOD@@',method)
    package=output / 'dev/nightclient/platform'
    (package / 'mixin').mkdir(parents=True,exist_ok=True)
    (package / 'NightPackResources.java').write_text(source)
    (package / 'mixin/ResourcePacksMixin.java').write_text(mixin)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--version',required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args();generate(args.version,args.output)

if __name__=='__main__':
    main()
