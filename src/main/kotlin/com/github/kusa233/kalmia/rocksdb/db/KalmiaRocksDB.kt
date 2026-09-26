package com.github.kusa233.kalmia.rocksdb.db

import com.github.cao.awa.cason.binary.decoder.JSONBinaryDecoder
import com.github.cao.awa.cason.binary.encoder.JSONBinaryEncoder
import com.github.cao.awa.cason.codec.decoder.JSONDecoder
import com.github.cao.awa.cason.codec.encoder.JSONEncoder
import com.github.cao.awa.cason.util.math.Base256
import com.github.kusa233.kalmia.plugin.registerCleaner
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.rocksdb.*
import java.io.File
import java.nio.charset.StandardCharsets

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
            name: String,
            blockCache: LRUCache = buildDefaultLRU(),
            cfDescriptors: List<ColumnFamilyDescriptor> = listOf(
                ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, buildDefaultCf(blockCache)),
            ),
            cfHandles: List<ColumnFamilyHandle> = listOf()
        ): KalmiaRocksDB {
            val writeBufferManager = WriteBufferManager(
                1024 * 1024 * 1024,
                blockCache
            )

            return open(
                name,
                DBOptions().apply {
                    setCreateIfMissing(true)
                    setCreateMissingColumnFamilies(true)

                    setMaxBackgroundJobs(8)
                    setBytesPerSync(1 shl 20)
                    setMaxOpenFiles(-1)

                    setWriteBufferManager(writeBufferManager)
                    setDbWriteBufferSize(0)

                    // WAL.
                    setMaxTotalWalSize(512L * 1024 * 1024)
                    setWalTtlSeconds(3600)
                    setWalSizeLimitMB(1024)

                    setUseFsync(false)

                    setAtomicFlush(true)

                    // Write optimization.
                    setTwoWriteQueues(true)
                    setAllowConcurrentMemtableWrite(true)
                    setEnableWriteThreadAdaptiveYield(true)
                    setEnablePipelinedWrite(true)

                    // Recovery policy.
                    setAvoidFlushDuringRecovery(true)
                    setAvoidFlushDuringShutdown(false)

                    // Log and stats.
                    setInfoLogLevel(InfoLogLevel.INFO_LEVEL)
                    setStatsDumpPeriodSec(60)
                },
                cfDescriptors,
                cfHandles
            )
        }

        fun buildDefaultLRU(): LRUCache {
            return LRUCache(
                512 * 1024 * 1024,
                -1,
                false,
                20.0
            )
        }

        fun buildDefaultCf(blockCache: LRUCache): ColumnFamilyOptions {
            val table: BlockBasedTableConfig? = BlockBasedTableConfig()
                .setBlockCache(blockCache)
                .setBlockSize(4 * 1024)
                .setCacheIndexAndFilterBlocks(true)
                .setCacheIndexAndFilterBlocksWithHighPriority(true)
                .setFilterPolicy(BloomFilter(10.0, false))
                .setFormatVersion(6)

            return ColumnFamilyOptions()
                .setTableFormatConfig(table)
                .setCompressionType(CompressionType.LZ4_COMPRESSION)
                .setCompactionStyle(CompactionStyle.LEVEL)
                .setLevelCompactionDynamicLevelBytes(true)
                .setWriteBufferSize(8L * 1024 * 1024)
                .setMaxWriteBufferNumber(2)
                .setTargetFileSizeBase(16L * 1024 * 1024)
                .setMaxBytesForLevelBase(32L * 1024 * 1024)
                .setMaxBytesForLevelMultiplier(10.0)
        }

        fun open(
            name: String,
            options: DBOptions,
            cfDescriptors: List<ColumnFamilyDescriptor>,
            cfHandles: List<ColumnFamilyHandle>
        ): KalmiaRocksDB {
            if (REAL_INSTANCES[name] == null) {
                val file = File("databases/$name")
                file.parentFile.mkdirs()
                val db = KalmiaRocksDB(
                    RocksDB.open(
                        options,
                        file.absolutePath,
                        cfDescriptors,
                        cfHandles
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

    inline operator fun <reified T> get(key: ByteArray): T? {
        synchronized(this) {
            val data = this.database[key]
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
                        JSONDecoder.decodeDataClass(
                            JSONBinaryDecoder.decodeObject(
                                data
                            ),
                            T::class
                        ) as T
                    } else {
                        throw IllegalArgumentException("Unsupported type '${T::class}', it must be basic types or data class")
                    }
                }
            } as? T
        }
    }

    inline operator fun <reified T : Any> set(key: String, value: T) {
        set(key.toByteArray(StandardCharsets.UTF_8), value)
    }

    inline operator fun <reified T : Any> set(key: ByteArray, value: T) {
        synchronized(this) {
            if (T::class.isData) {
                val any = JSONEncoder.encodeData<T>(value)
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