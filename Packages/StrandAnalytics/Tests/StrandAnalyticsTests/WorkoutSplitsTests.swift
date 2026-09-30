import XCTest
@testable import StrandAnalytics

final class WorkoutSplitsTests: XCTestCase {
    private func point(_ lonE6: Int, _ seconds: Int64, active: Int64? = nil,
                       segment: Int = 0) -> TimedWorkoutPoint {
        TimedWorkoutPoint(latE6: 0, lonE6: lonE6,
                          tMs: 1_700_000_000_000 + seconds * 1000,
                          activeElapsedMs: active.map { $0 * 1000 }, segment: segment)
    }

    func testInterpolatesMultipleBoundariesAndPartial() {
        let route = [point(0, 0), point(22_500, 600)]
        let splits = WorkoutSplits.calculate(route, unitMeters: WorkoutSplits.kilometerM)
        XCTAssertEqual(splits.count, 3)
        XCTAssertEqual(splits.map(\.partial), [false, false, true])
        XCTAssertLessThan(abs(splits[0].durationMs - 239_831), 1000)
        XCTAssertLessThan(abs(splits[1].durationMs - 239_831), 1000)
        XCTAssertTrue((490...510).contains(splits[2].distanceM))
        XCTAssertEqual(splits.last?.cumulativeMs, 600_000)
        let miles = WorkoutSplits.calculate(route, unitMeters: WorkoutSplits.mileM)
        XCTAssertEqual(miles.count, 2)
        XCTAssertEqual(miles.first?.distanceM, WorkoutSplits.mileM)
    }

    func testPausedSegmentDoesNotAddDistanceAcrossGap() {
        let route = [point(0, 0, active: 0), point(9_000, 300, active: 300),
                     point(50_000, 600, active: 300, segment: 1),
                     point(59_000, 900, active: 600, segment: 1)]
        let splits = WorkoutSplits.calculate(route, unitMeters: 1000)
        XCTAssertEqual(splits.filter { !$0.partial }.count, 2)
        XCTAssertLessThanOrEqual(splits.last?.cumulativeMs ?? 0, 600_000)
    }

    func testRejectsBackwardsTime() {
        XCTAssertTrue(WorkoutSplits.calculate([point(0, 1), point(9_000, 0)],
            unitMeters: 1000).isEmpty)
    }
}
