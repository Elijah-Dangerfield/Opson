package com.opson.libraries.config.impl.model

import com.opson.libraries.config.AppConfigMap

class BasicMapAppConfig(
    override val map: Map<String, *>
) : AppConfigMap()
