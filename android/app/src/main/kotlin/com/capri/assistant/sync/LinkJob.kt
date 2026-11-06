package com.capri.assistant.sync

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.capri.assistant.CapriApp

// About every 15 minutes Android wakes Capri Link for one sync, even with her bubble off. It's Android's own job scheduler:
// no extra permission, it runs only with a network, and Android picks the moment to spare the battery.
class LinkJob : JobService() {
    companion object {
        private const val ID = 47917

        fun schedule(context: Context) {
            val jobs = context.getSystemService(JobScheduler::class.java) ?: return
            if (jobs.getPendingJob(ID) != null) return
            val info = JobInfo.Builder(ID, ComponentName(context, LinkJob::class.java))
                .setPeriodic(15 * 60_000L)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .build()
            try { jobs.schedule(info) } catch (_: Exception) { }
        }

        fun cancel(context: Context) {
            try { context.getSystemService(JobScheduler::class.java)?.cancel(ID) } catch (_: Exception) { }
        }
    }

    override fun onStartJob(params: JobParameters): Boolean {
        val capri = CapriApp.capri(this)
        capri.io.execute {
            try { capri.sync.periodic() } finally { jobFinished(params, false) }
        }
        return true
    }

    // Android stopped it (no network any more): the next one comes as planned.
    override fun onStopJob(params: JobParameters): Boolean = false
}
