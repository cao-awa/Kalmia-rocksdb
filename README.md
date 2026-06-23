# Kora-redis
A Rocks DB plugin for Kora webserver.

## Usage
Add dependencies first: 
```groovy
repositories {
    maven {
        url 'https://jitpack.io'
    }
}

dependencies {
    implementation 'com.github.cao-awa:Kora-rocksdb:{version}'
}
```

For the versions, see [JitPack](https://jitpack.io/#cao-awa/Kora-redis).

And use redis client in your code:
```kotlin
import com.github.cao.awa.kora.rocksdb.db.KoraRedisClient

object Test {
    @JvmStatic
    fun entry() {
        val db = KoraRedisClient.open(File(""))
        // Set data to redis.
        db["test-key"] = "test"
        // Get data from redis.
        println(db.getString("test-key"))
    }
}
```

In produce environment, you need put the ``kora-rocksdb`` jar to ``libs/`` directory and declare entrypoint:
```json
{
    "entrypoint": [
        "kora-rocksdb",
        "com.yourservice.xxx.ServiceEntrypoint#entry"
    ]
}
```

For entrypoint, please see [Kora's document](https://github.com/cao-awa/Kora/tree/main/docs/entrypoint).