package android.database

/**
 * Stands in for android.jar's stub, which drops its message. The bundled
 * SQLite driver throws this type on Android, so without a real one every
 * database failure in the suite would read as "SQLException: null".
 */
open class SQLException : RuntimeException {
    constructor() : super()
    constructor(message: String?) : super(message)
    constructor(message: String?, cause: Throwable?) : super(message, cause)
}
