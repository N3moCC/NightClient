package dev.nightclient.bootstrap;

import java.lang.reflect.InvocationTargetException;
import net.fabricmc.api.ClientModInitializer;

public final class UniversalClient implements ClientModInitializer {
  @Override
  public void onInitializeClient() {
    initialize(UniversalRuntime.clientAdapter(), UniversalClient.class.getClassLoader());
  }

  static void initialize(AdapterIndex.Adapter adapter, ClassLoader loader) {
    try {
      Class<? extends ClientModInitializer> type =
          Class.forName(adapter.entrypoint, true, loader).asSubclass(ClientModInitializer.class);
      type.getDeclaredConstructor().newInstance().onInitializeClient();
    } catch (InvocationTargetException exception) {
      throw new IllegalStateException(
          "NightClient universal: Adapter initialization failed for Minecraft " + adapter.minecraft,
          exception.getCause());
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException(
          "NightClient universal: Cannot initialize adapter for Minecraft " + adapter.minecraft,
          exception);
    } catch (LinkageError exception) {
      throw new IllegalStateException(
          "NightClient universal: Incompatible adapter for Minecraft " + adapter.minecraft,
          exception);
    }
  }
}
