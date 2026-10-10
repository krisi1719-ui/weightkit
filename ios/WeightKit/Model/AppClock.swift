import Foundation

/// The clock the app runs on. Release: always the real clock. Debug: may be shifted by UI tests
/// (see DebugHooks.swift, which is compiled only in DEBUG).
enum AppClock {
    static var current: Clock {
        #if DEBUG
        return DebugHooks.clock
        #else
        return SystemClock()
        #endif
    }
}
