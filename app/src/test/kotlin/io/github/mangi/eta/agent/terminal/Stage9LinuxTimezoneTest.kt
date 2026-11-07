package io.github.mangi.eta.agent.terminal

import java.io.File
import java.util.TimeZone
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.Assert.*
import org.junit.Test

class Stage9LinuxTimezoneTest {
    @get:Rule val temporary = TemporaryFolder()
    private inline fun inZone(id: String, block: () -> Unit) {
        val original = TimeZone.getDefault()
        try { TimeZone.setDefault(TimeZone.getTimeZone(id)); block() }
        finally { TimeZone.setDefault(original) }
    }

    @Test fun everyChrootEnvBranchKeepsTheDeviceTimezone() = inZone("Asia/Shanghai") {
        val script = ShellProcessSupervisor().buildLinuxPayload("/data/local/eta/alpine/rootfs", "date")
        val branches = script.split("env -i").drop(1)
        assertEquals("command and interactive, GNU env and BusyBox", 4, branches.size)
        branches.forEachIndexed { index, branch ->
            assertTrue("chroot env -i branch $index drops TZ", branch.substringBefore("PATH=").contains("TZ="))
        }
    }

    @Test fun prootCommandAndInteractiveShellKeepTheDeviceTimezone() = inZone("Asia/Kathmandu") {
        for (command in listOf("date", null)) {
            val script = ProotCommandBuilder.payload("/private/rootfs", command,
                nativeDirectory = File("/native"), workspace = "/workspace",
                tempDirectory = object : File("/tmp") { override fun getAbsolutePath() = "/tmp" }, publicStorageGranted = false)
            assertTrue("proot $command drops TZ", script.contains("TZ="))
        }
    }

    private fun fallback(zone: String, expected: String) = inZone(zone) {
        val script = ShellProcessSupervisor().buildLinuxPayload("/data/local/eta/alpine/rootfs", "date")
        assertTrue("$zone needs POSIX fallback $expected", script.contains(expected))
        assertTrue("zoneinfo must be probed in the root script", script.contains("usr/share/zoneinfo/$zone"))
    }
    @Test fun shanghaiUsesTheReversedPosixOffset() = fallback("Asia/Shanghai", "<+08>-8")
    @Test fun kolkataKeepsItsHalfHourOffset() = fallback("Asia/Kolkata", "<+0530>-5:30")
    @Test fun newYorkKeepsBothSeasonalTransitions() = fallback("America/New_York", "<-05>5<-04>,M3.2.0,M11.1.0")
    @Test fun londonUsesWallClockTransitionTimes() = fallback("Europe/London", "<+00>0<+01>,M3.5.0/1,M10.5.0")
    @Test fun sydneyKeepsSouthernHemisphereTransitions() = fallback("Australia/Sydney", "<+10>-10<+11>,M10.1.0,M4.1.0/3")

    @Test fun prootUsesExistingZoneinfoWithoutChangingTheRootfs() = inZone("Asia/Shanghai") {
        val root = temporary.newFolder("rootfs")
        val zoneFile = File(root, "usr/share/zoneinfo/Asia/Shanghai")
        requireNotNull(zoneFile.parentFile).mkdirs(); zoneFile.writeBytes(byteArrayOf(1, 2, 3))
        val before = zoneFile.readBytes()
        val script = ProotCommandBuilder.payload(root.path, "date", nativeDirectory = File("/native"),
            workspace = "/workspace", tempDirectory = object : File("/tmp") { override fun getAbsolutePath() = "/tmp" }, publicStorageGranted = false)
        assertTrue(script.contains("TZ=Asia/Shanghai"))
        assertArrayEquals(before, zoneFile.readBytes())
        assertEquals(listOf(zoneFile), root.walkTopDown().filter { it.isFile }.toList())
    }

    @Test fun generatedPosixRulesMatchJavaTimeInWinterSummerAndRamadan() {
        val zones = listOf("Asia/Shanghai", "Asia/Kolkata", "America/New_York", "Europe/London", "Australia/Sydney",
            "Australia/Lord_Howe", "Africa/Casablanca")
        val instants = listOf("2026-01-15T12:00:00Z", "2026-03-01T12:00:00Z", "2026-07-15T12:00:00Z").map(Instant::parse)
        for (name in zones) for (instant in instants) {
            val zone = ZoneId.of(name)
            val tz = LinuxTimezone.posix(zone, instant)
            val process = ProcessBuilder("date", "--date=@${instant.epochSecond}", "+%z").apply { environment()["TZ"] = tz }.start()
            try {
                assertTrue("date timed out", process.waitFor(5, TimeUnit.SECONDS))
                val output = process.inputStream.bufferedReader().readText().trim()
                val error = process.errorStream.bufferedReader().readText()
                assertEquals(error, 0, process.exitValue())
                assertEquals("$name at $instant / $tz", DateTimeFormatter.ofPattern("xx").format(instant.atZone(zone)), output)
            } finally { if (process.isAlive) process.destroyForcibly() }
        }
    }
    @Test fun posixMatchesAtTransitionBoundariesIncludingMidnightAndHalfHourDst() {
        for (name in listOf("America/Havana", "America/Santiago", "Australia/Lord_Howe", "Europe/London")) {
            val zone = ZoneId.of(name)
            var cursor = Instant.parse("2026-01-01T00:00:00Z")
            repeat(2) {
                val transition = requireNotNull(zone.rules.nextTransition(cursor))
                for (instant in listOf(transition.instant.minusSeconds(1), transition.instant, transition.instant.plusSeconds(1))) {
                    val tz = LinuxTimezone.posix(zone, instant)
                    val process = ProcessBuilder("date", "--date=@${instant.epochSecond}", "+%z")
                        .apply { environment()["TZ"] = tz }.start()
                    try {
                        assertTrue(process.waitFor(5, TimeUnit.SECONDS))
                        assertEquals("$name / $tz / $instant", DateTimeFormatter.ofPattern("xx").format(instant.atZone(zone)),
                            process.inputStream.bufferedReader().readText().trim())
                        assertEquals(process.errorStream.bufferedReader().readText(), 0, process.exitValue())
                    } finally { if (process.isAlive) process.destroyForcibly() }
                }
                cursor = transition.instant.plusSeconds(1)
            }
        }
    }

}
