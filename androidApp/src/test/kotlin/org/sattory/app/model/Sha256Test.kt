package org.sattory.app.model

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlinx.coroutines.runBlocking

class Sha256Test {
    @Test
    fun calculatesKnownHash() = runBlocking {
        val folder = TemporaryFolder().apply { create() }
        try {
            val file = folder.newFile("sample").apply { writeText("abc") }
            assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                Sha256.of(file),
            )
        } finally {
            folder.delete()
        }
    }
}
