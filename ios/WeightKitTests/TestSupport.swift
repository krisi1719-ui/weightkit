import Foundation
@testable import WeightKit

let hour: TimeInterval = 3600
let day: TimeInterval = 24 * hour

/// Injectable clock: `wall` can be set back like the device date; `mono` only moves forward.
final class FakeClock: Clock {
    var wall = Date(timeIntervalSince1970: 1_760_000_000)
    var mono: TimeInterval = 1000
    /// Real time passes (both clocks move).
    func advance(_ s: TimeInterval) { wall = wall.addingTimeInterval(s); mono += s }
}

final class MemStore: KeyValueStore {
    var map: [String: Any] = [:]
    func double(_ key: String) -> Double? { map[key] as? Double }
    func set(_ key: String, _ value: Double) { map[key] = value }
    func bool(_ key: String) -> Bool { map[key] as? Bool ?? false }
    func set(_ key: String, _ value: Bool) { map[key] = value }
}
