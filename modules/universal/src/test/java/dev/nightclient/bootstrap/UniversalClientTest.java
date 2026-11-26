package dev.nightclient.bootstrap;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

final class UniversalClientTest {
  @Test
  void onlySelectedAdapterIsLoadedEvenWhenTheJarContainsUnsupportedBytecode() throws Exception {
    AdapterIndex.Adapter selected =
        AdapterIndexTest.read(AdapterIndexTest.indexText()).select("1.20", 17);
    String unusedName = "dev.nightclient.adapter.mc9_99_9.platform.ObsoleteClient";
    RecordingLoader loader = new RecordingLoader();
    loader.classes.put(selected.entrypoint, initializer(selected.entrypoint, Opcodes.V17));
    loader.classes.put(unusedName, initializer(unusedName, 65535));
    System.clearProperty("nightclient.test.initialized");
    try {
      UniversalClient.initialize(selected, loader);
      assertEquals(selected.entrypoint, System.getProperty("nightclient.test.initialized"));
      assertTrue(loader.loaded.contains(selected.entrypoint));
      assertFalse(loader.loaded.contains(unusedName));
      assertThrows(
          UnsupportedClassVersionError.class, () -> Class.forName(unusedName, true, loader));
    } finally {
      System.clearProperty("nightclient.test.initialized");
    }
  }

  @Test
  void missingSelectedAdapterFailsWithTheMinecraftVersion() throws Exception {
    AdapterIndex.Adapter adapter =
        AdapterIndexTest.read(AdapterIndexTest.indexText()).select("26.3", 25);
    IllegalStateException error = assertThrows(IllegalStateException.class,
        () -> UniversalClient.initialize(adapter, new RecordingLoader()));
    assertTrue(error.getMessage().contains("26.3"));
    assertInstanceOf(ClassNotFoundException.class, error.getCause());
  }

  @Test
  void pluginRequiresEarlySelectionAndRejectsAnIncorrectReservedPackage() {
    UniversalMixinPlugin plugin = new UniversalMixinPlugin();
    assertThrows(IllegalStateException.class, plugin::getMixins);
    assertThrows(IllegalStateException.class, () -> plugin.shouldApplyMixin("Target", "x"));
    assertThrows(IllegalStateException.class, () -> plugin.onLoad("dev.nightclient.adapter"));
  }

  private static byte[] initializer(String name, int classVersion) {
    ClassWriter writer = new ClassWriter(0);
    writer.visit(classVersion, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, name.replace('.', '/'), null,
        "java/lang/Object", new String[] {"net/fabricmc/api/ClientModInitializer"});
    MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
    constructor.visitCode();
    constructor.visitVarInsn(Opcodes.ALOAD, 0);
    constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
    constructor.visitInsn(Opcodes.RETURN);
    constructor.visitMaxs(1, 1);
    constructor.visitEnd();
    MethodVisitor initialize =
        writer.visitMethod(Opcodes.ACC_PUBLIC, "onInitializeClient", "()V", null, null);
    initialize.visitCode();
    initialize.visitLdcInsn("nightclient.test.initialized");
    initialize.visitLdcInsn(name);
    initialize.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "setProperty",
        "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
    initialize.visitInsn(Opcodes.POP);
    initialize.visitInsn(Opcodes.RETURN);
    initialize.visitMaxs(2, 1);
    initialize.visitEnd();
    writer.visitEnd();
    return writer.toByteArray();
  }

  private static final class RecordingLoader extends ClassLoader {
    final Map<String, byte[]> classes = new HashMap<String, byte[]>();
    final List<String> loaded = new ArrayList<String>();

    RecordingLoader() {
      super(UniversalClientTest.class.getClassLoader());
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
      loaded.add(name);
      byte[] data = classes.get(name);
      if (data == null) throw new ClassNotFoundException(name);
      return defineClass(name, data, 0, data.length);
    }
  }
}
