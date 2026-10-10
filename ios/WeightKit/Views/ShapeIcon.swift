import SwiftUI

/// Simple filled cross-section pictogram for each shape (drawn in a unit square).
struct ShapeIcon: View {
    let shape: ShapeKind
    var color: Color = .primary

    var body: some View {
        Canvas { ctx, size in
            let s = min(size.width, size.height)
            let ox = (size.width - s) / 2, oy = (size.height - s) / 2
            func p(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: ox + x * s, y: oy + y * s) }
            func poly(_ pts: [(Double, Double)]) -> Path {
                var path = Path()
                path.move(to: p(pts[0].0, pts[0].1))
                for q in pts.dropFirst() { path.addLine(to: p(q.0, q.1)) }
                path.closeSubpath()
                return path
            }
            func circle(_ cx: Double, _ cy: Double, _ r: Double) -> Path {
                Path(ellipseIn: CGRect(x: ox + (cx - r) * s, y: oy + (cy - r) * s, width: 2 * r * s, height: 2 * r * s))
            }
            let fill = GraphicsContext.Shading.color(color)
            switch shape {
            case .plate:
                ctx.fill(poly([(0.08, 0.62), (0.62, 0.62), (0.92, 0.38), (0.38, 0.38)]), with: fill)
                ctx.fill(poly([(0.08, 0.62), (0.62, 0.62), (0.62, 0.70), (0.08, 0.70)]), with: .color(color.opacity(0.6)))
                ctx.fill(poly([(0.62, 0.62), (0.92, 0.38), (0.92, 0.46), (0.62, 0.70)]), with: .color(color.opacity(0.4)))
            case .round:
                ctx.fill(circle(0.5, 0.5, 0.38), with: fill)
            case .square:
                ctx.fill(poly([(0.14, 0.14), (0.86, 0.14), (0.86, 0.86), (0.14, 0.86)]), with: fill)
            case .hex:
                var pts: [(Double, Double)] = []
                for i in 0..<6 {
                    let a = Double(i) * .pi / 3
                    pts.append((0.5 + 0.4 * cos(a), 0.5 + 0.4 * sin(a)))
                }
                ctx.fill(poly(pts), with: fill)
            case .flat:
                ctx.fill(poly([(0.06, 0.38), (0.94, 0.38), (0.94, 0.62), (0.06, 0.62)]), with: fill)
            case .pipe:
                var path = circle(0.5, 0.5, 0.4)
                path.addPath(circle(0.5, 0.5, 0.28))
                ctx.fill(path, with: fill, style: FillStyle(eoFill: true))
            case .rectTube:
                var path = poly([(0.1, 0.18), (0.9, 0.18), (0.9, 0.82), (0.1, 0.82)])
                path.addPath(poly([(0.22, 0.30), (0.78, 0.30), (0.78, 0.70), (0.22, 0.70)]))
                ctx.fill(path, with: fill, style: FillStyle(eoFill: true))
            case .angle:
                ctx.fill(poly([(0.15, 0.1), (0.3, 0.1), (0.3, 0.75), (0.88, 0.75), (0.88, 0.9), (0.15, 0.9)]), with: fill)
            case .channel:
                ctx.fill(poly([(0.2, 0.1), (0.8, 0.1), (0.8, 0.24), (0.36, 0.24), (0.36, 0.76), (0.8, 0.76), (0.8, 0.9), (0.2, 0.9)]), with: fill)
            case .ibeam, .section:
                ctx.fill(poly([(0.12, 0.1), (0.88, 0.1), (0.88, 0.23), (0.57, 0.23), (0.57, 0.77), (0.88, 0.77),
                               (0.88, 0.9), (0.12, 0.9), (0.12, 0.77), (0.43, 0.77), (0.43, 0.23), (0.12, 0.23)]), with: fill)
            case .tee:
                ctx.fill(poly([(0.1, 0.12), (0.9, 0.12), (0.9, 0.27), (0.57, 0.27), (0.57, 0.9), (0.43, 0.9), (0.43, 0.27), (0.1, 0.27)]), with: fill)
            case .rebar:
                ctx.fill(circle(0.5, 0.5, 0.3), with: fill)
                for i in 0..<8 {
                    let a = Double(i) * .pi / 4
                    ctx.fill(circle(0.5 + 0.32 * cos(a), 0.5 + 0.32 * sin(a), 0.06), with: fill)
                }
            }
        }
        .accessibilityHidden(true)
    }
}
