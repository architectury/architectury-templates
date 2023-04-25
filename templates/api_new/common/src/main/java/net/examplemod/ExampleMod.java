package net.examplemod;

import com.google.common.base.Suppliers;
import dev.architectury.registry.CreativeTabRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

public class ExampleMod {
    public static final String MOD_ID = "examplemod";
    // We can use this if we don't want to use DeferredRegister
    public static final Supplier<RegistrarManager> REGISTRIES = Suppliers.memoize(() -> RegistrarManager.get(MOD_ID));
    // Registering a new creative tab
    public static final CreativeTabRegistry.TabSupplier EXAMPLE_TAB = CreativeTabRegistry.create(new ResourceLocation(MOD_ID, "example_tab"), () -> new ItemStack(ExampleMod.EXAMPLE_ITEM.get()));

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(MOD_ID, Registries.ITEM);
    public static final RegistrySupplier<Item> EXAMPLE_ITEM = ITEMS.register("example_item", () ->
            new Item(new Item.Properties().arch$tab(ExampleMod.EXAMPLE_TAB)));

    public static void init() {
        ITEMS.register();

        System.out.println(ExampleExpectPlatform.getConfigDirectory().toAbsolutePath().normalize().toString());
    }
}