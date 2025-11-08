package com.coreyd97.montoyautilities

import burp.api.montoya.MontoyaApi
import burp.api.montoya.persistence.PersistedObject
import burp.api.montoya.persistence.Preferences
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.apache.logging.log4j.LogManager
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KType

enum class StorageType { PROJECT, EXTENSION, TEMP }

private val montoya: MontoyaApi = MontoyaUtilities.montoya
private val preferenceData: Preferences = montoya.persistence().preferences()
private val projectData: PersistedObject = montoya.persistence().extensionData()
private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
private val preferences: MutableMap<String, BurpPreference<*>> = mutableMapOf()


class PreferenceProxy<T>(
    val key: String,
    val serializer: KSerializer<in T>? = null,
    val listener: ((old: T, new: T) -> Unit)? = null
) : ReadWriteProperty<Any?, T> {
    val log = LogManager.getLogger(BurpPreference::class.java)
    private var _pref: BurpPreference<T>
    init {
        try {
            _pref = preferences.getOrElse(key) {
                throw RuntimeException(
                    "Cannot use preference $key before it has been declared.\n" +
                            "Use 'by Preference(...)' instead, or declare the preference elsewhere before using 'by PreferenceProxy(...)'"
                )
            } as BurpPreference<T>
            _pref.listeners.add { old, new ->
                listener?.invoke(old ?: new!!, new!!)
            }
        } catch (e: ClassCastException) {
            throw RuntimeException("Preference $key was previously declared as a different type.")
        }
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        if(!_pref.initialized) {
            runCatching {
                val serializer: KSerializer<in T> = serializer ?: serializer(property.returnType)
                _pref.tryInitializeWithSerializer(serializer)
            }
        }
        runCatching {
            return _pref.value ?: _pref.default as T
        }.onFailure { err ->
            log.error("Preference $key was previously declared as a different type.", err)
        }.getOrThrow()
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        runCatching {
            _pref.value = value
        }.onFailure { err ->
            log.error("Preference $key was previously declared as a different type.", err)
        }.getOrThrow()
    }
}

//todo store preferences as class
class BurpPreference<T : @Serializable Any?>(
    val key: String,
    val default: T? = null,
    val storage: StorageType,
    var serializer: KSerializer<in T>? = null
) {
    val log = LogManager.getLogger(BurpPreference::class.java)
    val listeners = mutableListOf<(old: T?, new: T?) -> Unit>()
    //If we don't yet have a serializer, we need to defer loading until we do.
    //This will typically be done when the return type is known from the KProperty.returnType in the delegate
    var initialized = false
    var value: T? = null
        get() {
            if(!initialized) {
                runCatching {
                    tryInitialize()
                }
            }
            return field
        }
        set(value) {
            val old = field
            field = value
            if(initialized) {
                listeners.forEach {
                    runCatching {
                        it.invoke(old, value)
                    }.onFailure { err ->
                        log.error(err)
                    }
                }
                runCatching {
                    saveValue()
                }.onFailure { err -> log.error(err) }
            }
        }

    fun tryInitialize() {
        check(!this.initialized) {"Field already initialized."}
        checkNotNull(this.serializer) {"Serializer not yet known."}
        value = loadValueFromPreference()
        this.initialized = true
    }

    fun tryInitializeWithSerializer(serializer: KSerializer<in T>){
        check(!this.initialized) {"Field already initialized."}
        check(this.serializer == null) {"Serializer is already set!"}
        runCatching {
            this.serializer = serializer
            value = loadValueFromPreference()
        }.onFailure { err ->
            this.serializer = null
            log.error(err)
        }

        this.initialized = true
    }

    init {
        montoya.extension().registerUnloadingHandler {
            saveValue()
        }
    }

    private fun loadValueFromPreference(): T? {
        val (exists, value) = when (storage) {
            StorageType.EXTENSION -> {
                Pair(preferenceData.stringKeys().contains(key), preferenceData.getString(key))
            }

            StorageType.PROJECT -> {
                Pair(projectData.stringKeys().contains(key), projectData.getString(key))
            }

            StorageType.TEMP -> {
                Pair(false, null)
            }
        }


        val deserialized = if (exists) {
            runCatching {
                json.decodeFromString(serializer!!, value!!)
            }.onFailure { error ->
                log.error("Couldn't load preference ${key}: ${error.message}", error)
            }.getOrThrow()
        } else {
            default
        }

        return deserialized as T
    }

    private fun saveValue() {
        if(!initialized || storage == StorageType.TEMP) return //Don't actually save it.
        check(serializer != null || value == null) {
            "No serializer found for preference \"$key\". Try specifying the serializer."
        }
        if(value == null){
            if (storage == StorageType.EXTENSION)
                preferenceData.deleteString(this.key)
            else
                projectData.deleteString(this.key)
        }else{
            val encoded = json.encodeToString(serializer!!, value!!)
            if (storage == StorageType.EXTENSION)
                preferenceData.setString(this.key, encoded)
            else
                projectData.setString(this.key, encoded)
        }
    }
}

open class NullablePreference<T : @Serializable Any?>(
    key: String,
    default: T? = null,
    storage: StorageType = StorageType.EXTENSION,
    customSerializer: KSerializer<in T>? = null,
    listener: ((old: T?, new: T?) -> Unit)? = null
) : ReadWriteProperty<Any?, T?> {
    val log = LogManager.getLogger(NullablePreference::class.java)
    protected var _pref: BurpPreference<T>

    init {
        try {
            _pref = preferences.getOrPut(key) {
                BurpPreference(key, default, storage, customSerializer)
            } as BurpPreference<T>
            if(customSerializer != null && _pref.serializer == null) {
                runCatching {
                    _pref.tryInitializeWithSerializer(customSerializer)
                }
            }
            if(listener != null) _pref.listeners.add(listener)
        }catch (e: ClassCastException){
            throw RuntimeException("Preference $key was previously declared as a different type.")
        }
    }

    override operator fun getValue(thisRef: Any?, property: KProperty<*>): T? {
        tryInitSerializerIfNeeded(property.returnType)
        return _pref.value
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) {
        tryInitSerializerIfNeeded(property.returnType)
        _pref.value = value
    }

    private fun tryInitSerializerIfNeeded(type: KType){
        if(_pref.initialized) return
        runCatching {
            _pref.tryInitializeWithSerializer(serializer(type) as KSerializer<T>)
        }
    }
}

open class Preference<T : @Serializable Any>(
    private val key: String,
    val default: T,
    storage: StorageType = StorageType.EXTENSION,
    serializer: KSerializer<in T>? = null,
    listener: ((old: T, new: T) -> Unit)? = null
) : NullablePreference<T>(key, default, storage, serializer, null) {

    init {
        //The listener
        _pref.listeners.add { old, new ->
            listener?.invoke(old ?: default, new ?: default)
        }
    }

    override operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return super.getValue(thisRef, property) ?: default
    }
}

inline fun <reified T : @Serializable Any?> nullablePreference(
    key: String,
    default: T? = null,
    storage: StorageType = StorageType.EXTENSION,
    noinline listener: ((old: T?, new: T?) -> Unit)? = null
): NullablePreference<T> {
    return NullablePreference(key, default, storage, serializer<T>(), listener)
}

inline fun <reified T : @Serializable Any> preference(
    key: String,
    default: T,
    storage: StorageType = StorageType.EXTENSION,
    noinline listener: ((old: T, new: T) -> Unit)? = null
): Preference<T> {
    return Preference(key, default, storage, serializer<T>(), listener)
}