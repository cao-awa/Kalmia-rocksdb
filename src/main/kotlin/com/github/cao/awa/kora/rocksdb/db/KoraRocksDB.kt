package com.github.cao.awa.kora.rocksdb.db

import com.github.cao.awa.cason.binary.JSONBinaryDecoder
import com.github.cao.awa.cason.binary.JSONBinaryEncoder
import com.github.cao.awa.cason.codec.JSONCodec
import com.github.cao.awa.cason.util.math.Base256
import com.github.cao.awa.kora.plugin.registerCleaner
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.rocksdb.RocksDB
import java.io.File
import java.nio.charset.StandardCharsets

class KoraRocksDB(val database: RocksDB, private val name: String) {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger("KoraRocksDB")
        private var REAL_INSTANCES: MutableMap<String, KoraRocksDB>? = mutableMapOf()
        private val INSTANCES: Map<String, KoraRocksDB>
            get() = REAL_INSTANCES!!
        val TRUE: ByteArray = byteArrayOf(0x01)
        val FALSE: ByteArray = byteArrayOf(0x00)

        fun init() {
            registerCleaner("kora-rocksdb") {
                LOGGER.info("Closing all RocksDB...")
                REAL_INSTANCES?.forEach { (_, db) ->
                    db.close()
                }
                REAL_INSTANCES?.clear()
                REAL_INSTANCES = null
            }
        }

        fun open(name: String): KoraRocksDB {
            if (REAL_INSTANCES?.get(name) == null) {
                val file = File("databases/$name")
                file.parentFile.mkdirs()
                val db = KoraRocksDB(RocksDB.open(file.absolutePath), name)
                REAL_INSTANCES?.put(name, db)
                return db
            } else {
                return INSTANCES.get(name)!!
            }
        }

        private fun close(name: String) {
            INSTANCES[name]?.let {
                it.database.close()
                REAL_INSTANCES?.remove(name)
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
        synchronized(this) {
            val data = this.database[key.toByteArray(StandardCharsets.UTF_8)]
            return when (T::class) {
                String::class -> String(data, StandardCharsets.UTF_8)
                Boolean::class -> data[0].toInt() == 0x01
                Byte::class -> data[0]
                Char::class -> Base256.tagFromBuf(data)
                Short::class -> Base256.tagFromBuf(data)
                Int::class -> Base256.intFromBuf(data)
                Long::class -> Base256.longFromBuf(data)
                else -> {
                    if (T::class.isData) {
                        JSONCodec.decode<T>(
                            JSONBinaryDecoder.decodeObject(
                                data
                            )
                        )
                    } else {
                        throw IllegalArgumentException("Unsupported type '${T::class}', it must be basic types or data class")
                    }
                }
            } as? T
        }
    }

    inline operator fun <reified T : Any> set(key: String, value: T) {
        synchronized(this) {
            val key = key.toByteArray(StandardCharsets.UTF_8)
            if (T::class.isData) {
                val any = JSONCodec.encode<T>(value)
                this.database.put(
                    key,
                    JSONBinaryEncoder.encode(any)
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