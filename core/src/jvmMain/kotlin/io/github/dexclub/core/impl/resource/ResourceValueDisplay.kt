package io.github.dexclub.core.impl.resource

import com.reandroid.arsc.value.Entry

internal fun Entry.toDisplayValue(): String? {
    getValueAsReference()?.let { return it.buildReference() }
    getValueAsString()?.let { return it }
    getValueAsBoolean()?.let { return it.toString() }
    getValueAsInteger()?.let { return it.toString() }
    getValueAsFloat()?.let { return it.toString() }
    getValueAsColor()?.let { return it.toString() }
    return null
}
