package com.gmail.chauhanparth86.mymemory.models

enum class BoardSize(val numCards: Int) {
    Easy(8),
    Medium(18),
    Hard(24),
    Insane(48);

    companion object {
        fun getByValue(value: Int) = values().first { it.numCards == value }
    }

    fun getWidth(): Int {
        return when (this) {
            Easy -> 2
            Medium -> 3
            Hard -> 4
            Insane -> 6
        }
    }

    fun getHeight(): Int {
        return numCards / getWidth()
    }

    fun getNumPairs(): Int {
        return numCards/2
    }

}
