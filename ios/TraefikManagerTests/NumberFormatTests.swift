import XCTest
@testable import TraefikManager

final class NumberFormatTests: XCTestCase {
    private let base = Locale(identifier: "en_US")

    func testPersianCountsUsePersianDigits() {
        let fa = NumberFormat.locale(language: "fa", base: base)
        XCTAssertEqual(NumberFormat.count(13, locale: fa), "۱۳")
        XCTAssertEqual(NumberFormat.count(1250, locale: fa), "۱٬۲۵۰")
        XCTAssertEqual(NumberFormat.decimal(1.5, digits: 2...2, locale: fa), "۱٫۵۰")
    }

    func testLatinLanguagesKeepLatinDigits() {
        XCTAssertEqual(NumberFormat.count(1250, locale: NumberFormat.locale(language: "en", base: base)), "1,250")
        XCTAssertEqual(NumberFormat.count(1250, locale: NumberFormat.locale(language: "de", base: Locale(identifier: "de_DE"))), "1.250")
    }

    func testLatinLanguagesKeepTheRegionLocale() {
        let region = Locale(identifier: "fa_IR")
        XCTAssertEqual(NumberFormat.locale(language: "en", base: region), region)
    }

    func testPersianKeepsTheRegionCalendar() {
        let fa = NumberFormat.locale(language: "fa", base: base)
        XCTAssertEqual(fa.calendar.identifier, .gregorian)
    }
}
