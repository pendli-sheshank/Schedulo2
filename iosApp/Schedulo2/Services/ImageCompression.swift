import UIKit

/// Shared image downscaling for anything uploaded to Firebase Storage — team chat
/// photos and feedback screenshots. Both storage rules cap an upload at 2 MB, and
/// a modern phone camera clears that several times over, so every upload path has
/// to compress first rather than discovering the limit at the network layer.
func compressImageForUpload(
    _ image: UIImage,
    maxDim: CGFloat = 800,
    quality: CGFloat = 0.5
) -> Data? {
    let scale = min(maxDim / image.size.width, maxDim / image.size.height, 1)
    let newSize = CGSize(width: image.size.width * scale, height: image.size.height * scale)
    let renderer = UIGraphicsImageRenderer(size: newSize)
    let resized = renderer.image { _ in image.draw(in: CGRect(origin: .zero, size: newSize)) }
    return resized.jpegData(compressionQuality: quality)
}
