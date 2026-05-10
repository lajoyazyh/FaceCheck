package com.facecheck.face.service;

import com.facecheck.common.error.BusinessException;
import com.facecheck.common.error.ErrorCode;
import java.io.ByteArrayInputStream;
import java.net.URLConnection;
import java.util.Locale;
import java.util.Set;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FaceImageValidationService {

    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024L * 1024L;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final ImageHashService imageHashService;

    public FaceImageValidationService(ImageHashService imageHashService) {
        this.imageHashService = imageHashService;
    }

    public ValidatedImage validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE, "Image file is required.");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE, "Image file exceeds the 10 MB limit.");
        }

        try {
            byte[] content = file.getBytes();
            String extension = extension(file.getOriginalFilename());
            if (!ALLOWED_EXTENSIONS.contains(extension)) {
                throw new BusinessException(ErrorCode.INVALID_IMAGE, "Only JPEG, PNG, and WEBP files are supported.");
            }

            String detectedContentType = detectContentType(content, file.getContentType(), extension);
            if (!ALLOWED_CONTENT_TYPES.contains(detectedContentType) || !extensionMatches(extension, detectedContentType)) {
                throw new BusinessException(ErrorCode.INVALID_IMAGE, "Image type and extension do not match.");
            }

            if ("image/webp".equals(detectedContentType)) {
                if (!isWebp(content)) {
                    throw new BusinessException(ErrorCode.INVALID_IMAGE, "WEBP image content is invalid.");
                }
            } else if (ImageIO.read(new ByteArrayInputStream(content)) == null) {
                throw new BusinessException(ErrorCode.INVALID_IMAGE, "Image payload cannot be decoded.");
            }

            return new ValidatedImage(content, detectedContentType, extension, imageHashService.sha256(content));
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE, "Image payload cannot be processed.");
        }
    }

    private String extension(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE, "Image filename is missing a valid extension.");
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private String detectContentType(byte[] content, String providedContentType, String extension) {
        try {
            String detected = URLConnection.guessContentTypeFromStream(new ByteArrayInputStream(content));
            if (detected != null) {
                return detected;
            }
        } catch (Exception ignored) {
        }
        if (isWebp(content) || "webp".equals(extension)) {
            return "image/webp";
        }
        return providedContentType == null ? "" : providedContentType.toLowerCase(Locale.ROOT);
    }

    private boolean extensionMatches(String extension, String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> extension.equals("jpg") || extension.equals("jpeg");
            case "image/png" -> extension.equals("png");
            case "image/webp" -> extension.equals("webp");
            default -> false;
        };
    }

    private boolean isWebp(byte[] content) {
        return content.length >= 12
                && content[0] == 'R'
                && content[1] == 'I'
                && content[2] == 'F'
                && content[3] == 'F'
                && content[8] == 'W'
                && content[9] == 'E'
                && content[10] == 'B'
                && content[11] == 'P';
    }

    public record ValidatedImage(
            byte[] content,
            String contentType,
            String extension,
            String sha256
    ) {
    }
}
