// Prints the text of a PDF (macOS PDFKit). Usage: swift scripts/pdftext.swift file.pdf
import Foundation
import PDFKit

guard CommandLine.arguments.count > 1, let doc = PDFDocument(url: URL(fileURLWithPath: CommandLine.arguments[1])) else {
    FileHandle.standardError.write("cannot open PDF\n".data(using: .utf8)!)
    exit(1)
}
print("pages: \(doc.pageCount)")
print(doc.string ?? "")
