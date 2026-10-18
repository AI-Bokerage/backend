package io.genfin.api.spi;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.api.port.spi.Extension;
import org.junit.jupiter.api.Test;

class ExtensionRegistryTest {

  interface Greeter extends Extension {
    String greet();
  }

  @Test
  void registeredExtensionsAreFoundByType() {
    ExtensionRegistry registry = ExtensionRegistries.create();
    Greeter english = () -> "hello";
    Greeter french = () -> "bonjour";

    registry.register(Greeter.class, english);
    registry.register(Greeter.class, french);

    assertThat(registry.find(Greeter.class)).contains(english);
    assertThat(registry.findAll(Greeter.class)).containsExactly(english, french);
  }

  @Test
  void unregisteredTypeReturnsEmpty() {
    ExtensionRegistry registry = ExtensionRegistries.create();

    assertThat(registry.find(Greeter.class)).isEmpty();
    assertThat(registry.findAll(Greeter.class)).isEmpty();
  }
}
