import com.minhacamera.PhoneBackgroundHandoffPolicy

fun main() {
    val policy = PhoneBackgroundHandoffPolicy
    fun result(running: Boolean, starting: Boolean, releasing: Boolean,
               first: Boolean, error: Boolean, elapsed: Long) =
        policy.evaluate(running, starting, releasing, first, error, elapsed)
    check(result(false, false, false, false, false, 0) == PhoneBackgroundHandoffPolicy.Decision.WAIT_START)
    check(result(false, true, false, false, false, 14_001) == PhoneBackgroundHandoffPolicy.Decision.WAIT_START)
    check(result(false, true, false, false, false, 29_999) == PhoneBackgroundHandoffPolicy.Decision.WAIT_START)
    check(result(false, true, false, false, false, 30_000) == PhoneBackgroundHandoffPolicy.Decision.FAILED)
    check(result(true, false, false, false, false, 22_000) == PhoneBackgroundHandoffPolicy.Decision.WAIT_FRAMES)
    check(result(true, false, false, false, false, 45_001) == PhoneBackgroundHandoffPolicy.Decision.FAILED)
    check(result(true, false, false, true, false, 45_001) == PhoneBackgroundHandoffPolicy.Decision.READY)
    check(result(false, false, false, false, true, 5) == PhoneBackgroundHandoffPolicy.Decision.FAILED)
    check(result(false, false, true, false, true, 49_000) == PhoneBackgroundHandoffPolicy.Decision.WAIT_RELEASE)
    check(!policy.canResumeLocal(true, false, false))
    check(!policy.canResumeLocal(false, true, false))
    check(!policy.canResumeLocal(false, false, true))
    check(policy.canResumeLocal(false, false, false))
    println("PhoneBackgroundHandoffPolicy: 13 verificacoes aprovadas")
}
