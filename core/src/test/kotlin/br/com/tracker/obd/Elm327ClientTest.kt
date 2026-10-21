package br.com.tracker.obd

import br.com.tracker.obd.obd.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.*
import java.io.EOFException
import kotlin.test.*

internal class FakeTransport(private val reply: (String) -> String? = { "OK>" }) : ElmTransport {
    val writes = mutableListOf<String>()
    private val bytes = Channel<Int>(Channel.UNLIMITED)
    var closed = false
    var pending = 0
    var maximumPending = 0
    override suspend fun write(bytes: ByteArray) {
        check(!closed)
        val command = bytes.toString(Charsets.US_ASCII).trim()
        writes += command
        pending++; maximumPending = maxOf(maximumPending, pending)
        reply(command)?.forEach { this.bytes.send(it.code) }
    }
    override suspend fun readByte(): Int {
        val result = bytes.receiveCatching().getOrNull() ?: throw EOFException("link lost")
        if (result == '>'.code) pending--
        return result
    }
    override fun close() { closed = true; bytes.close() }
}
@OptIn(ExperimentalCoroutinesApi::class)
class Elm327ClientTest {
    @Test fun initializationOrderAndOptionalCloneCommands() = runTest {
        val transport = FakeTransport { if (it == "ATZ") "ELM327 v1.5>" else if (it == "ATS0" || it == "ATE0") "?>" else "OK>" }
        val warnings = Elm327Client(transport).initialize()
        assertEquals(listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0"), transport.writes)
        assertEquals(2, warnings.size)
        assertEquals(1, transport.maximumPending)
    }
    @Test fun criticalUnsupportedCommandStopsInitialization() = runTest {
        val transport = FakeTransport { when(it) { "ATZ" -> "ELM327>"; "ATH0" -> "?>"; else -> "OK>" } }
        assertFailsWith<ElmProtocolException> { Elm327Client(transport).initialize() }
        assertFalse("ATSP0" in transport.writes)
    }
    @Test fun concurrentCallersAreSerializedUntilPrompt() = runTest {
        val base = FakeTransport { if(it == "010C") "410C0000>" else "410D00>" }
        val slow = object : ElmTransport by base { override suspend fun readByte(): Int { delay(5); return base.readByte() } }
        val client = Elm327Client(slow)
        val a = async { client.execute(ElmCommand.RPM) }
        val b = async { client.execute(ElmCommand.SPEED) }
        a.await(); b.await()
        assertEquals(1, base.maximumPending)
        assertEquals(listOf("010C", "010D"), base.writes)
    }
    @Test fun timeoutClosesAndPreventsNextCommand() = runTest {
        val transport = FakeTransport { "41 0C 00 00" } // No prompt.
        val client = Elm327Client(transport)
        assertFailsWith<ElmTimeoutException> { client.execute(ElmCommand.RPM) }
        assertEquals(2000L, testScheduler.currentTime)
        assertTrue(transport.closed)
        assertFailsWith<IllegalStateException> { client.execute(ElmCommand.SPEED) }
        assertEquals(listOf("010C"), transport.writes)
    }
    @Test fun disconnectPropagatesAndCloses() = runTest {
        val transport = FakeTransport { null }
        val client = Elm327Client(transport)
        val request = async { assertFailsWith<EOFException> { client.execute(ElmCommand.SPEED) } }
        runCurrent(); transport.close(); request.await()
        assertTrue(transport.closed)
    }
    @Test fun cancellationClosesPendingTransport() = runTest {
        val transport = FakeTransport { null }
        val client = Elm327Client(transport)
        val request = launch { client.execute(ElmCommand.SPEED) }
        runCurrent(); request.cancelAndJoin()
        assertTrue(transport.closed)
    }
    @Test fun commandAllowlistContainsOnlyPermittedOperations() {
        assertEquals(setOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0", "0100", "010C", "010D"), ElmCommand.entries.map { it.wire }.toSet())
    }
    @Test fun timeoutDuringWriteAlsoClosesTransport() = runTest {
        val base = FakeTransport()
        val stalled = object : ElmTransport by base {
            override suspend fun write(bytes: ByteArray) { delay(10_000); base.write(bytes) }
        }
        assertFailsWith<ElmTimeoutException> { Elm327Client(stalled).execute(ElmCommand.SPEED) }
        assertTrue(base.closed)
        assertTrue(base.writes.isEmpty())
    }
    @Test fun oversizedResponseClosesInsteadOfGrowingWithoutBound() = runTest {
        val transport = FakeTransport { "X".repeat(8193) + ">" }
        assertFailsWith<ElmProtocolException> { Elm327Client(transport).execute(ElmCommand.SPEED) }
        assertTrue(transport.closed)
    }
}
