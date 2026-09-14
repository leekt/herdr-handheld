package dev.herdr.handheld.input

enum class LogicalAction {
    UP, DOWN, LEFT, RIGHT, CONFIRM, BACK, ACTIONS, COMPOSE, PREVIOUS, NEXT, INPUT, HOME, SYSTEM, FONT_SMALL, FONT_LARGE;
    val directional: Boolean get() = this in setOf(UP, DOWN, LEFT, RIGHT)
}

/** No Android or network dependency. Each edge belongs to the mode in which it began. */
class InputRouter(private val emit: (LogicalAction) -> Unit,private val longPress: ((LogicalAction,Boolean)->Unit)?=null) {
    private data class Held(val action: LogicalAction, val epoch: Long,val started: Long,var long: Boolean=false)
    private val held = mutableMapOf<String, Held>()
    private val lastDirection = mutableMapOf<LogicalAction, Long>()
    private var epoch = 0L
    private var repeating: LogicalAction? = null
    private var nextRepeat = Long.MAX_VALUE

    fun edge(token: String, action: LogicalAction, down: Boolean, timeMs: Long, repeatCount: Int = 0) {
        if (down) {
            if (repeatCount != 0 || held.containsKey(token)) return
            val alreadyDown = held.values.any { it.action == action && it.epoch == epoch }
            held[token] = Held(action, epoch,timeMs)
            if (action.directional && !alreadyDown && timeMs - (lastDirection[action] ?: -1000) >= 65) {
                lastDirection[action] = timeMs
                repeating = action
                nextRepeat = timeMs + 360
                emit(action)
            }
        } else {
            val press = held.remove(token) ?: return
            if (press.action != action || press.epoch != epoch) return
            if (action.directional) {
                if (held.values.none { it.action == action && it.epoch == epoch }) {
                    if (repeating == action) repeating = null
                }
            } else if(press.long)longPress?.invoke(action,false) else emit(action)
        }
    }

    fun tick(timeMs: Long) {
        if(longPress!=null)for(press in held.values.toList()) {
            if(press.action in setOf(LogicalAction.INPUT,LogicalAction.HOME,LogicalAction.COMPOSE) && !press.long && timeMs-press.started>=600) {
                press.long=true;longPress.invoke(press.action,true)
            }
        }
        val action = repeating ?: return
        if (timeMs >= nextRepeat && held.values.any { it.action == action && it.epoch == epoch }) {
            nextRepeat = timeMs + 100
            emit(action)
        }
    }

    /** Clears presses and prevents a key-up after a transition from becoming Enter. */
    fun reset() {
        epoch++
        val released=held.values.filter { it.long }.map { it.action }
        held.clear()
        repeating = null
        nextRepeat = Long.MAX_VALUE
        lastDirection.clear()
        released.forEach { longPress?.invoke(it,false) }
    }
}
