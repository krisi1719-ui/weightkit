import Foundation

/// One input dimension of a shape. `key` is shared across shapes so values
/// survive switching between e.g. flat bar and plate.
struct DimField: Hashable {
    let key: String
    let labelKey: String
    let symbol: String
}

enum ShapeKind: String, CaseIterable, Identifiable, Codable {
    case plate, round, square, hex, flat, pipe, rectTube, angle, channel, ibeam, tee, rebar, section
    var id: String { rawValue }

    var titleKey: String {
        switch self {
        case .plate: return "shape_plate"
        case .round: return "shape_round"
        case .square: return "shape_square"
        case .hex: return "shape_hex"
        case .flat: return "shape_flat"
        case .pipe: return "shape_pipe"
        case .rectTube: return "shape_rect_tube"
        case .angle: return "shape_angle"
        case .channel: return "shape_channel"
        case .ibeam: return "shape_ibeam"
        case .tee: return "shape_tee"
        case .rebar: return "shape_rebar"
        case .section: return "shape_section"
        }
    }

    /// Plate has no bar length: its length/width are dimensions.
    var isLinear: Bool { self != .plate }

    var fields: [DimField] {
        switch self {
        case .plate: return [.init(key: "t", labelKey: "dim_thickness", symbol: "t"),
                             .init(key: "w", labelKey: "dim_width", symbol: "W"),
                             .init(key: "pl", labelKey: "dim_length", symbol: "L")]
        case .round: return [.init(key: "d", labelKey: "dim_diameter", symbol: "D")]
        case .square: return [.init(key: "a", labelKey: "dim_side", symbol: "A")]
        case .hex: return [.init(key: "s", labelKey: "dim_af", symbol: "S")]
        case .flat: return [.init(key: "w", labelKey: "dim_width", symbol: "W"),
                            .init(key: "t", labelKey: "dim_thickness", symbol: "t")]
        case .pipe: return [.init(key: "od", labelKey: "dim_od", symbol: "D"),
                            .init(key: "wt", labelKey: "dim_wall", symbol: "t")]
        case .rectTube: return [.init(key: "h", labelKey: "dim_height", symbol: "H"),
                                .init(key: "w", labelKey: "dim_width", symbol: "W"),
                                .init(key: "wt", labelKey: "dim_wall", symbol: "t")]
        case .angle: return [.init(key: "a", labelKey: "dim_leg_a", symbol: "A"),
                             .init(key: "b", labelKey: "dim_leg_b", symbol: "B"),
                             .init(key: "t", labelKey: "dim_thickness", symbol: "t")]
        case .channel, .ibeam, .tee:
            return [.init(key: "h", labelKey: "dim_height", symbol: "H"),
                    .init(key: "b", labelKey: "dim_flange_width", symbol: "B"),
                    .init(key: "tw", labelKey: "dim_web", symbol: "s"),
                    .init(key: "tf", labelKey: "dim_flange", symbol: "t")]
        case .rebar, .section: return []
        }
    }
}

enum GeoError: Error { case missing, wall }

/// Cross-section of a linear shape, in mm / mm².
struct CrossSection {
    let areaMM2: Double
    /// Painted / coated perimeter (outer + inner for hollow sections). nil if unknown.
    let perimeterMM: Double?
}

enum Geometry {
    /// Cross-section formulas (all dimensions in mm):
    ///  round      A = π/4·D²                 P = π·D
    ///  square     A = a²                     P = 4a
    ///  hex        A = (√3/2)·s²  (s = across flats)   P = 2√3·s
    ///  flat       A = w·t                    P = 2(w+t)
    ///  pipe       A = π/4·(D² − (D−2t)²)     P = π·D + π·(D−2t)
    ///  rect tube  A = H·W − (H−2t)(W−2t)     P = 2(H+W) + 2(H+W−4t)   (sharp corners)
    ///  angle      A = t·(a + b − t)          P = 2(a+b)
    ///  channel/I  A = 2·B·tf + (H − 2tf)·tw  P = 2H + 4B − 2tw        (no root radii)
    ///  tee        A = B·tf + (H − tf)·tw     P = 2(B+H)
    static func section(_ shape: ShapeKind, _ v: [String: Double]) throws -> CrossSection {
        func g(_ k: String) throws -> Double {
            guard let x = v[k], x > 0 else { throw GeoError.missing }
            return x
        }
        switch shape {
        case .round:
            let d = try g("d")
            return .init(areaMM2: .pi / 4 * d * d, perimeterMM: .pi * d)
        case .square:
            let a = try g("a")
            return .init(areaMM2: a * a, perimeterMM: 4 * a)
        case .hex:
            let s = try g("s")
            return .init(areaMM2: 3.0.squareRoot() / 2 * s * s, perimeterMM: 2 * 3.0.squareRoot() * s)
        case .flat:
            let w = try g("w"), t = try g("t")
            return .init(areaMM2: w * t, perimeterMM: 2 * (w + t))
        case .pipe:
            let d = try g("od"), t = try g("wt")
            guard 2 * t <= d else { throw GeoError.wall }
            let di = d - 2 * t
            return .init(areaMM2: .pi / 4 * (d * d - di * di), perimeterMM: .pi * d + .pi * di)
        case .rectTube:
            let h = try g("h"), w = try g("w"), t = try g("wt")
            guard 2 * t <= min(h, w) else { throw GeoError.wall }
            return .init(areaMM2: h * w - (h - 2 * t) * (w - 2 * t),
                         perimeterMM: 2 * (h + w) + 2 * (h + w - 4 * t))
        case .angle:
            let a = try g("a"), b = try g("b"), t = try g("t")
            guard t < min(a, b) else { throw GeoError.wall }
            return .init(areaMM2: t * (a + b - t), perimeterMM: 2 * (a + b))
        case .channel, .ibeam:
            let h = try g("h"), b = try g("b"), tw = try g("tw"), tf = try g("tf")
            guard 2 * tf < h, tw <= b else { throw GeoError.wall }
            return .init(areaMM2: 2 * b * tf + (h - 2 * tf) * tw, perimeterMM: 2 * h + 4 * b - 2 * tw)
        case .tee:
            let h = try g("h"), b = try g("b"), tw = try g("tw"), tf = try g("tf")
            guard tf < h, tw <= b else { throw GeoError.wall }
            return .init(areaMM2: b * tf + (h - tf) * tw, perimeterMM: 2 * (b + h))
        case .plate, .rebar, .section:
            throw GeoError.missing
        }
    }
}
