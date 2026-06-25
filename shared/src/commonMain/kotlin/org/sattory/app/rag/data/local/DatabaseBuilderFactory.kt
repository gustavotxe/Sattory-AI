package org.sattory.app.rag.data.local

interface DatabaseBuilderFactory {
    fun create(): SattoryDatabase
}
