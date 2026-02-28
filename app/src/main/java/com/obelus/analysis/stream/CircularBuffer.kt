package com.obelus.analysis.stream

import java.util.ArrayDeque

/**
 * Una implementación de buffer circular thread-safe optimizada para memoria.
 * Mantiene una capacidad fija y descarta el elemento más antiguo cuando se alcanza el límite.
 */
class CircularBuffer<T>(private val capacity: Int) {
    private val queue = ArrayDeque<T>(capacity)

    /**
     * Agrega un elemento al buffer. Si el buffer está lleno, elimina el más antiguo.
     */
    @Synchronized
    fun add(item: T) {
        if (queue.size >= capacity) {
            queue.removeFirst()
        }
        queue.addLast(item)
    }

    /**
     * Retorna una copia de los elementos actuales como una lista.
     */
    @Synchronized
    fun toList(): List<T> {
        return queue.toList()
    }

    /**
     * Retorna la cantidad de elementos actuales.
     */
    @Synchronized
    fun size(): Int {
        return queue.size
    }

    /**
     * Limpia todos los elementos del buffer.
     */
    @Synchronized
    fun clear() {
        queue.clear()
    }
    
    /**
     * Retorna el elemento más reciente (el último agregado).
     */
    @Synchronized
    fun peekLast(): T? {
        return queue.peekLast()
    }
    
    /**
     * Retorna el elemento más antiguo (el primero agregado).
     */
    @Synchronized
    fun peekFirst(): T? {
        return queue.peekFirst()
    }
}
