package com.softdial.app

/** Two servers: Server 1 and Server 2. */
interface SipEngine {
    fun start()
    fun register(username: String, password: String, domain: String)
    fun call(number: String, domain: String): Boolean
    fun hangUp()
}
