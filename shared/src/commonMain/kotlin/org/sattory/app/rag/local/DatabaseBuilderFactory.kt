package org.sattory.app.rag.local

interface DatabaseBuilderFactory {
    fun create(): SattoryDatabase
}
