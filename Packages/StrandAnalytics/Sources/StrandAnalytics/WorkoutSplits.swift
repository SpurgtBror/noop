import Foundation

/// A recorded GPS fix in integer microdegrees; same stored units as Android's route point.
public struct TimedWorkoutPoint: Equatable, Sendable {
    public let latE6: Int
    public let lonE6: Int
    public let tMs: Int64
    public let activeElapsedMs: Int64?
    public let segment: Int

    public init(latE6: Int, lonE6: Int, tMs: Int64, activeElapsedMs: Int64? = nil, segment: Int = 0) {
        self.latE6 = latE6; self.lonE6 = lonE6; self.tMs = tMs
        self.activeElapsedMs = activeElapsedMs; self.segment = segment
    }
}

public struct WorkoutSplit: Equatable, Sendable {
    public let number: Int
    public let distanceM: Double
    public let durationMs: Int64
    public let cumulativeMs: Int64
    public let partial: Bool
}

public enum WorkoutSplits {
    public static let kilometerM = 1000.0
    public static let mileM = 1609.344
    private static let earthR = 6_371_000.0

    public static func calculate(_ points: [TimedWorkoutPoint], unitMeters: Double) -> [WorkoutSplit] {
        guard points.count >= 2, unitMeters.isFinite, unitMeters > 0,
              let first = points.first, first.tMs > 0 else { return [] }
        func elapsed(_ point: TimedWorkoutPoint) -> Int64 {
            point.activeElapsedMs ?? (point.tMs - first.tMs)
        }
        var previous = first
        var previousTime = elapsed(first)
        var totalDistance = 0.0
        var nextBoundary = unitMeters
        var lastBoundaryTime: Int64 = 0
        var result: [WorkoutSplit] = []
        for current in points.dropFirst() {
            let currentTime = elapsed(current)
            guard current.tMs > previous.tMs, currentTime >= previousTime,
                  current.segment >= previous.segment,
                  valid(previous), valid(current) else { return [] }
            if current.segment == previous.segment {
                let segmentDistance = haversine(previous, current)
                guard segmentDistance.isFinite else { return [] }
                let endDistance = totalDistance + segmentDistance
                while segmentDistance > 0 && nextBoundary <= endDistance {
                    let fraction = (nextBoundary - totalDistance) / segmentDistance
                    let crossingMs = previousTime + Int64((Double(currentTime - previousTime) * fraction).rounded())
                    result.append(WorkoutSplit(number: result.count + 1, distanceM: unitMeters,
                        durationMs: max(0, crossingMs - lastBoundaryTime), cumulativeMs: crossingMs,
                        partial: false))
                    lastBoundaryTime = crossingMs
                    nextBoundary += unitMeters
                }
                totalDistance = endDistance
            }
            previous = current
            previousTime = currentTime
        }
        let remainder = totalDistance - Double(result.count) * unitMeters
        if remainder >= 5, previousTime > lastBoundaryTime {
            result.append(WorkoutSplit(number: result.count + 1, distanceM: remainder,
                durationMs: previousTime - lastBoundaryTime, cumulativeMs: previousTime,
                partial: true))
        }
        return result
    }

    private static func valid(_ p: TimedWorkoutPoint) -> Bool {
        (-90_000_000...90_000_000).contains(p.latE6) &&
        (-180_000_000...180_000_000).contains(p.lonE6) && p.tMs > 0
    }

    private static func haversine(_ a: TimedWorkoutPoint, _ b: TimedWorkoutPoint) -> Double {
        let lat1 = Double(a.latE6) / 1_000_000 * .pi / 180
        let lat2 = Double(b.latE6) / 1_000_000 * .pi / 180
        let dLat = lat2 - lat1
        let dLon = Double(b.lonE6 - a.lonE6) / 1_000_000 * .pi / 180
        let h = pow(sin(dLat / 2), 2) + cos(lat1) * cos(lat2) * pow(sin(dLon / 2), 2)
        return earthR * 2 * atan2(sqrt(h), sqrt(1 - h))
    }
}
