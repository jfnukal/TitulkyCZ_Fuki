package cz.titulky.tv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper

/**
 * [Fuki] Úplné vypnutí / restart aplikace (hlavně pro TV, kde ji systém drží v paměti).
 * Služby mají START_STICKY, proto je nejdřív zastavíme – jinak by je Android po ukončení znovu spustil.
 */
object Vypinac {
    private fun zastavSluzby(c: Context) {
        runCatching { PrijemService.zastav(c) }
        runCatching { c.startService(Intent(c, RemotePlayService::class.java).setAction(RemotePlayService.ACTION_STOP)) }
        listOf(RemotePlayService::class.java, OverlayService::class.java, PrijemService::class.java, SnimekService::class.java)
            .forEach { runCatching { c.stopService(Intent(c, it)) } }
    }

    /** Zastaví vše a ukončí proces. */
    fun vypnout(a: Activity) {
        zastavSluzby(a)
        Handler(Looper.getMainLooper()).postDelayed({
            a.finishAndRemoveTask()
            android.os.Process.killProcess(android.os.Process.myPid())
        }, 700)
    }

    /** Zastaví vše, ukončí proces a aplikaci spustí znovu načisto. */
    fun restart(a: Activity) {
        zastavSluzby(a)
        Handler(Looper.getMainLooper()).postDelayed({
            a.startActivity(Intent(a, Obnova::class.java)
                .putExtra("pid", android.os.Process.myPid())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            a.finishAndRemoveTask()
            android.os.Process.killProcess(android.os.Process.myPid())
        }, 700)
    }

    /** Běží ve vlastním procesu (:obnova): počká, až starý proces skončí, a spustí aplikaci znovu. */
    class Obnova : Activity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            val pid = intent.getIntExtra("pid", -1)
            if (pid > 0) runCatching { android.os.Process.killProcess(pid) }
            Handler(Looper.getMainLooper()).postDelayed({
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                finish()
                android.os.Process.killProcess(android.os.Process.myPid())
            }, 900)
        }
    }
}
