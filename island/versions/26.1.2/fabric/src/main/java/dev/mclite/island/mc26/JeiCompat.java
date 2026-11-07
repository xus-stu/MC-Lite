package dev.mclite.island.mc26;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientListOverlay;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * JEI 可选集成：只读取「物品列表里鼠标悬停的那个物品」，不注册配方、不依赖 JEI 内部类。
 *
 * <p>JEI 不存在时这个类不会被加载（入口点由 JEI 自己调用），所以不会 NoClassDefFoundError。
 */
@JeiPlugin
public final class JeiCompat implements IModPlugin {

    private static volatile IJeiRuntime runtime;

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath("mclite-island", "jei");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    /** @return 形如 "钻石|minecraft"；没有悬停内容时返回 null。 */
    static String hoveredIngredient() {
        IJeiRuntime current = runtime;
        if (current == null) {
            return null;
        }
        IIngredientListOverlay overlay = current.getIngredientListOverlay();
        if (overlay == null || !overlay.isListDisplayed()) {
            return null;
        }
        Optional<ITypedIngredient<?>> hovered = overlay.getIngredientUnderMouse();
        if (hovered == null || hovered.isEmpty()) {
            return null;
        }
        ITypedIngredient<?> typed = hovered.get();
        Optional<ItemStack> stack = typed.getItemStack();
        if (stack.isPresent()) {
            ItemStack item = stack.get();
            Identifier id = BuiltInRegistries.ITEM.getKey(item.getItem());
            return item.getHoverName().getString() + "|" + (id == null ? "" : id.getNamespace());
        }
        return String.valueOf(typed.getIngredient()) + "|";
    }
}
