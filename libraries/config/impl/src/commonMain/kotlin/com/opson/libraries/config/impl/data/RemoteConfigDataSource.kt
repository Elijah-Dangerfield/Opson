package com.opson.libraries.config.impl.data

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.core.Catching

interface RemoteConfigDataSource {
    suspend fun getConfig(): Catching<AppConfigMap>
}
