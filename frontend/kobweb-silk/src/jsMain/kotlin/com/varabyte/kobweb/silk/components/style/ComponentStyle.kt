package com.varabyte.kobweb.silk.components.style

import androidx.compose.runtime.*
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.asStyleBuilder
import com.varabyte.kobweb.compose.ui.classNames
import com.varabyte.kobweb.silk.theme.SilkTheme
import com.varabyte.kobweb.silk.theme.colors.ColorMode
import com.varabyte.kobweb.silk.theme.colors.getColorMode
import org.jetbrains.compose.web.css.StyleBuilder
import org.jetbrains.compose.web.css.StylePropertyValue
import org.jetbrains.compose.web.css.StyleSheet

// We need our own implementation of StyleBuilder, so we can both test equality and pull values out of it later
private class SimpleStyleBuilder : StyleBuilder {
    val properties = LinkedHashMap<String, String>() // Preserve insertion order
    val variables = LinkedHashMap<String, String>() // Preserve insertion order

    override fun property(propertyName: String, value: StylePropertyValue) {
        properties[propertyName] = value.toString()
    }

    override fun variable(variableName: String, value: StylePropertyValue) {
        variables[variableName] = value.toString()
    }

    override fun equals(other: Any?): Boolean {
        return (other is SimpleStyleBuilder) && properties == other.properties && variables == other.variables
    }

    override fun hashCode(): Int {
        return properties.hashCode() + variables.hashCode()
    }
}

/**
 * Class used as the receiver to a callback, allowing the user to define various state-dependent styles.
 */
class ComponentModifiers {
    /** Base styles for this component, will always be applied first. */
    var base: Modifier? = null

    /**
     * Styles to apply to components that represent navigation links which have not yet been visited.
     *
     * See also: https://developer.mozilla.org/en-US/docs/Web/CSS/:link
     */
    var link: Modifier? = null

    /**
     * Styles to apply to components that represent navigation links which have previously been visited.
     *
     * See also: https://developer.mozilla.org/en-US/docs/Web/CSS/:visited
     */
    var visited: Modifier? = null

    /**
     * Styles to apply to components when a cursor is pointing at them.
     *
     * See also: https://developer.mozilla.org/en-US/docs/Web/CSS/:hover
     */
    var hover: Modifier? = null

    /**
     * Styles to apply to components when a cursor is interactiving with them.
     *
     * See also: https://developer.mozilla.org/en-US/docs/Web/CSS/:active
     */
    var active: Modifier? = null
}

class ComponentStyle internal constructor(private val name: String) {
    companion object {
        operator fun invoke(name: String, init: ComponentModifiers.(ColorMode) -> Unit) =
            ComponentStyleBuilder(name, init)
    }

    @Composable
    fun toModifier(): Modifier {
        return Modifier.classNames(name, "$name-${getColorMode().name.lowercase()}")
    }
}

private sealed interface StyleGroup {
    class Light(val styles: SimpleStyleBuilder) : StyleGroup
    class Dark(val styles: SimpleStyleBuilder) : StyleGroup
    class ColorAgnostic(val styles: SimpleStyleBuilder) : StyleGroup
    class ColorAware(val lightStyles: SimpleStyleBuilder, val darkStyles: SimpleStyleBuilder) : StyleGroup

    companion object {
        @Suppress("NAME_SHADOWING") // Shadowing used to turn nullable into non-null
        fun from(lightModifiers: Modifier?, darkModifiers: Modifier?): StyleGroup? {
            val lightStyles = lightModifiers?.let { lightModifiers ->
                SimpleStyleBuilder().apply { lightModifiers.asStyleBuilder().invoke(this) }
            }
            val darkStyles = darkModifiers?.let { darkModifiers ->
                SimpleStyleBuilder().apply { darkModifiers.asStyleBuilder().invoke(this) }
            }

            if (lightStyles == null && darkStyles == null) return null
            if (lightStyles != null && darkStyles == null) return Light(lightStyles)
            if (lightStyles == null && darkStyles != null) return Dark(darkStyles)
            check(lightStyles != null && darkStyles != null)
            return if (lightStyles == darkStyles) {
                ColorAgnostic(lightStyles)
            } else {
                ColorAware(lightStyles, darkStyles)
            }
        }
    }
}

class ComponentVariant(internal val style: ComponentStyleBuilder)

class ComponentStyleBuilder internal constructor(
    val name: String,
    private val init: ComponentModifiers.(ColorMode) -> Unit,
) {
    internal val variants = mutableListOf<ComponentVariant>()

    fun addVariant(name: String, init: ComponentModifiers.(ColorMode) -> Unit): ComponentVariant {
        return ComponentVariant(ComponentStyleBuilder("${this.name}-$name", init)).also {
            variants.add(it)
        }
    }

    private fun StyleSheet.addStyles(name: String, pseudoClass: String?, styles: SimpleStyleBuilder) {
        val classSelector = if (pseudoClass != null) ".$name:$pseudoClass" else ".$name"
        this.apply {
            classSelector style {
                styles.properties.forEach { entry -> property(entry.key, entry.value) }
                styles.variables.forEach { entry -> variable(entry.key, entry.value) }
            }
        }
    }

    private fun StyleSheet.addStyles(name: String, pseudoClass: String?, group: StyleGroup) {
        when (group) {
            is StyleGroup.Light -> addStyles("$name-light", pseudoClass, group.styles)
            is StyleGroup.Dark -> addStyles("$name-dark", pseudoClass, group.styles)
            is StyleGroup.ColorAgnostic -> addStyles(name, pseudoClass, group.styles)
            is StyleGroup.ColorAware -> {
                addStyles("$name-light", pseudoClass, group.lightStyles)
                addStyles("$name-dark", pseudoClass, group.darkStyles)
            }
        }
    }

    internal fun addStyles(styleSheet: StyleSheet) {
        val lightModifiers = ComponentModifiers().apply { init(ColorMode.LIGHT) }
        val darkModifiers = ComponentModifiers().apply { init(ColorMode.DARK) }

        StyleGroup.from(lightModifiers.base, darkModifiers.base)?.let { group ->
            styleSheet.addStyles(name, null, group)
        }
        StyleGroup.from(lightModifiers.link, darkModifiers.link)?.let { group ->
            styleSheet.addStyles(name, "link", group)
        }
        StyleGroup.from(lightModifiers.visited, darkModifiers.visited)?.let { group ->
            styleSheet.addStyles(name, "visited", group)
        }
        StyleGroup.from(lightModifiers.hover, darkModifiers.hover)?.let { group ->
            styleSheet.addStyles(name, "hover", group)
        }
        StyleGroup.from(lightModifiers.active, darkModifiers.active)?.let { group ->
            styleSheet.addStyles(name, "active", group)
        }
    }
}

@Composable
fun ComponentStyleBuilder.toModifier(variant: ComponentVariant? = null): Modifier {
    return SilkTheme.componentStyles.getValue(name).toModifier().then(
        variant?.style?.toModifier() ?: Modifier
    )
}