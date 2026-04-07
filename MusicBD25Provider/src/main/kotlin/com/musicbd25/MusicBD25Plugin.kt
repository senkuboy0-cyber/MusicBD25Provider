package com.musicbd25

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class MusicBD25Plugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(MusicBD25Provider())
    }
}
