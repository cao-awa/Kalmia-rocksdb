package com.github.kusa233.kalmia.rocksdb.db

import com.github.cao.awa.cason.binary.decoder.JSONBinaryDecoder
import com.github.cao.awa.cason.binary.encoder.JSONBinaryEncoder
import com.github.cao.awa.cason.codec.decoder.JSONDecoder
import com.github.cao.awa.cason.codec.encoder.JSONEncoder
import com.github.cao.awa.cason.serialize.parser.JSONParser
import com.github.cao.awa.cason.util.math.Base256
import com.github.kusa233.kalmia.plugin.registerCleaner
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.rocksdb.*
import java.io.File
import java.nio.charset.StandardCharsets
import kotlin.reflect.KClass
import kotlin.reflect.KType

class KalmiaRocksDB(val database: RocksDB, private val name: String) {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger("KalmiaRocksDB")
        private var REAL_INSTANCES: MutableMap<String, KalmiaRocksDB> = mutableMapOf()
        private val INSTANCES: Map<String, KalmiaRocksDB>
            get() = REAL_INSTANCES
        val TRUE: ByteArray = byteArrayOf(0x01)
        val FALSE: ByteArray = byteArrayOf(0x00)

        fun init() {
            registerCleaner("kalmia-rocksdb") {
                LOGGER.info("Closing all RocksDB...")
                REAL_INSTANCES.forEach { (_, db) ->
                    db.close()
                }
                REAL_INSTANCES.clear()
            }
        }

        fun open(
            name: String
        ): KalmiaRocksDB {
            if (REAL_INSTANCES[name] == null) {
                val file = File("databases/$name")
                file.parentFile.mkdirs()
                val db = KalmiaRocksDB(
                    RocksDB.open(
                        file.absolutePath,
                    ), name
                )
                REAL_INSTANCES[name] = db
                return db
            } else {
                return INSTANCES[name]!!
            }
        }

        private fun close(name: String) {
            INSTANCES[name]?.let {
                it.database.close()
                REAL_INSTANCES.remove(name)
            }
        }
    }

    fun close() {
        close(this.name)
    }

    fun getString(key: String): String? {
        return get(key)
    }

    inline operator fun <reified T> get(key: String): T? {
        return get(key.toByteArray(StandardCharsets.UTF_8))
    }

    inline operator fun <reified T : Any> get(key: ByteArray): T? {
        return get(key, T::class)
    }

    @Suppress("unchecked_cast")
    operator fun <T : Any> get(key: ByteArray, type: KClass<T>): T? {
        synchronized(this) {
            val data = this.database[key] ?: return null
            if (type.isData) {
                return JSONDecoder.decodeDataClass(
                    JSONParser.parseObject(
                        String(data, StandardCharsets.UTF_8)
                    ),
                    type
                )
            }
            return when (type) {
                String::class -> String(data, StandardCharsets.UTF_8)
                Boolean::class -> data[0].toInt() == 0x01
                Byte::class -> data[0]
                Char::class -> Base256.tagFromBuf(data)
                Short::class -> Base256.tagFromBuf(data)
                Int::class -> Base256.intFromBuf(data)
                Long::class -> Base256.longFromBuf(data)
                else -> {
                    throw IllegalArgumentException("Unsupported type '$type', it must be basic types or data class")
                }
            } as? T
        }
    }

    inline operator fun <reified T : Any> set(key: String, value: T) {
        set(key.toByteArray(StandardCharsets.UTF_8), value)
    }

    inline operator fun <reified T : Any> set(key: ByteArray, value: T) {
        synchronized(this) {
            if (value::class.isData) {
                this.database.put(
                    key,
                    JSONEncoder.encodeData(value).toString().toByteArray(StandardCharsets.UTF_8)
                )
            } else {
                when (value) {
                    is String -> this.database.put(key, value.toByteArray(StandardCharsets.UTF_8))
                    is Boolean -> {
                        if (value) {
                            this.database.put(key, TRUE)
                        } else {
                            this.database.put(key, FALSE)
                        }
                    }

                    is Byte -> this.database.put(key, byteArrayOf(value))
                    is Char -> this.database.put(key, Base256.tagToBuf(value.code))
                    is Short -> this.database.put(key, Base256.tagToBuf(value.toInt()))
                    is Int -> this.database.put(key, Base256.intToBuf(value))
                    is Long -> this.database.put(key, Base256.longToBuf(value))
                    else -> throw IllegalArgumentException("Unsupported type '${T::class}', it must be basic types or data class")
                }
            }
        }
    }
}